package com.s3arch.app.torrent

import com.s3arch.app.util.AppLog
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Une connexion a un pair : handshake BitTorrent, protocole de messages, extension
 * `ut_metadata` (BEP 9/10) et transfert de blocs.
 *
 * La classe ne decide de rien : elle lit et ecrit, et remonte chaque evenement a son
 * ecouteur. La strategie (quelles pieces demander, quand se deconnecter) vit dans le
 * moteur.
 */
class PeerWire(
    val address: PeerAddress,
    private val infoHash: ByteArray,
    private val peerId: ByteArray,
    private val listener: Listener,
) {

    interface Listener {
        fun onReady(wire: PeerWire, metadataSize: Int, utMetadataId: Int) {}
        fun onExtended(wire: PeerWire, extensionId: Int, payload: ByteArray) {}
        fun onBitfield(wire: PeerWire, bits: BooleanArray) {}
        fun onUnchoke(wire: PeerWire) {}
        fun onChoke(wire: PeerWire) {}
        fun onHave(wire: PeerWire, index: Int) {}
        fun onBlock(wire: PeerWire, index: Int, begin: Int, data: ByteArray) {}
        fun onClosed(wire: PeerWire, reason: String?) {}
    }

    @Volatile private var socket: Socket? = null
    @Volatile private var closed = false
    private var output: OutputStream? = null
    private val writeLock = Any()
    private var utMetadataId = 0

    val isClosed: Boolean get() = closed

    fun run() {
        var reason: String? = null
        try {
            val connection = Socket()
            connection.connect(InetSocketAddress(address.host, address.port), 12_000)
            connection.soTimeout = 75_000
            connection.tcpNoDelay = true
            socket = connection
            val input = BufferedInputStream(connection.getInputStream(), 64 * 1024)
            output = connection.getOutputStream()

            sendHandshake()
            val reserved = readHandshake(input)
            if (reserved[5].toInt() and 0x10 != 0) sendExtensionHandshake()

            loop(input)
        } catch (e: EOFException) {
            reason = "pair déconnecté"
        } catch (e: Exception) {
            reason = e.message ?: e.javaClass.simpleName
        } finally {
            close(reason)
        }
    }

    private fun sendHandshake() {
        val bytes = ByteArray(68)
        bytes[0] = 19
        "BitTorrent protocol".toByteArray(Charsets.US_ASCII).copyInto(bytes, 1)
        // Extension protocol (BEP 10) : bit 20 de la cinquieme ligne de reserve.
        bytes[25] = 0x10
        infoHash.copyInto(bytes, 28)
        peerId.copyInto(bytes, 48)
        writeRaw(bytes)
    }

    private fun readHandshake(input: InputStream): ByteArray {
        val header = ByteArray(68)
        readFully(input, header)
        if (header[0].toInt() != 19) throw IOException("Handshake BitTorrent invalide")
        val protocol = String(header, 1, 19, Charsets.US_ASCII)
        if (protocol != "BitTorrent protocol") throw IOException("Protocole inconnu : $protocol")
        val reserved = header.copyOfRange(20, 28)
        val remoteHash = header.copyOfRange(28, 48)
        if (!remoteHash.contentEquals(infoHash)) throw IOException("Pair sur un autre torrent")
        return reserved
    }

    private fun sendExtensionHandshake() {
        sendExtended(0, BValue.dict(mapOf(
            "m" to BValue.dict(mapOf("ut_metadata" to BValue.of(1))),
            "v" to BValue.of("S3ARCH 1.5"),
            "reqq" to BValue.of(64),
            "p" to BValue.of(0),
        )))
    }

    fun sendInterested() = sendMessage(2, ByteArray(0))

    fun sendHave(index: Int) {
        val payload = ByteArray(4)
        putInt(payload, 0, index)
        sendMessage(4, payload)
    }

    fun request(index: Int, begin: Int, length: Int) {
        val payload = ByteArray(12)
        putInt(payload, 0, index)
        putInt(payload, 4, begin)
        putInt(payload, 8, length)
        sendMessage(6, payload)
    }

    fun cancel(index: Int, begin: Int, length: Int) {
        val payload = ByteArray(12)
        putInt(payload, 0, index)
        putInt(payload, 4, begin)
        putInt(payload, 8, length)
        sendMessage(8, payload)
    }

    fun sendExtended(extensionId: Int, value: BValue) {
        val encoded = Bencode.encode(value)
        val payload = ByteArray(encoded.size + 1)
        payload[0] = extensionId.toByte()
        encoded.copyInto(payload, 1)
        sendMessage(20, payload)
    }

    /** Demande un morceau de metadonnees (magnet) a ce pair. */
    fun requestMetadata(piece: Int) {
        if (utMetadataId == 0) return
        sendExtended(
            utMetadataId,
            BValue.dict(
                mapOf(
                    "msg_type" to BValue.of(0),
                    "piece" to BValue.of(piece),
                ),
            ),
        )
    }

    private fun loop(input: InputStream) {
        val header = ByteArray(4)
        while (!closed) {
            readFully(input, header)
            val length = ((header[0].toInt() and 0xFF) shl 24) or
                ((header[1].toInt() and 0xFF) shl 16) or
                ((header[2].toInt() and 0xFF) shl 8) or
                (header[3].toInt() and 0xFF)
            if (length == 0) continue
            if (length < 0 || length > 1_200_000) throw IOException("Message de $length octets refusé")
            val id = input.read()
            if (id < 0) throw EOFException("Flux clos")
            val payload = ByteArray(length - 1)
            if (payload.isNotEmpty()) readFully(input, payload)
            when (id) {
                0 -> listener.onChoke(this)
                1 -> listener.onUnchoke(this)
                4 -> if (payload.size >= 4) listener.onHave(this, readInt(payload, 0))
                5 -> listener.onBitfield(this, decodeBits(payload))
                7 -> if (payload.size >= 8) {
                    val index = readInt(payload, 0)
                    val begin = readInt(payload, 4)
                    listener.onBlock(this, index, begin, payload.copyOfRange(8, payload.size))
                }
                20 -> if (payload.isNotEmpty()) {
                    val extensionId = payload[0].toInt() and 0xFF
                    val body = payload.copyOfRange(1, payload.size)
                    if (extensionId == 0) {
                        readExtensionHandshake(body)
                    } else {
                        listener.onExtended(this, extensionId, body)
                    }
                }
                else -> Unit
            }
        }
    }

    private fun readExtensionHandshake(body: ByteArray) {
        val dict = Bencode.decodeOrNull(body) as? BValue.BDict ?: return
        val metadataId = dict.dict("m")?.int("ut_metadata")?.toInt() ?: 0
        val metadataSize = dict.int("metadata_size")?.toInt() ?: 0
        utMetadataId = metadataId
        listener.onReady(this, metadataSize, metadataId)
    }

    private fun sendMessage(id: Int, payload: ByteArray) {
        val frame = ByteArray(payload.size + 5)
        putInt(frame, 0, payload.size + 1)
        frame[4] = id.toByte()
        payload.copyInto(frame, 5)
        writeRaw(frame)
    }

    private fun writeRaw(bytes: ByteArray) {
        val stream = output ?: throw IOException("Connexion fermée")
        synchronized(writeLock) {
            stream.write(bytes)
            stream.flush()
        }
    }

    fun close(reason: String? = null) {
        if (closed) return
        closed = true
        runCatching { socket?.close() }
        runCatching { listener.onClosed(this, reason) }
        if (reason != null) AppLog.info("Torrent", "Pair $address clôturé : $reason")
    }

    private fun readFully(input: InputStream, target: ByteArray) {
        var offset = 0
        while (offset < target.size) {
            val count = input.read(target, offset, target.size - offset)
            if (count < 0) throw EOFException("Flux clos au milieu d'un message")
            offset += count
        }
    }

    private fun decodeBits(payload: ByteArray): BooleanArray {
        val bits = BooleanArray(payload.size * 8)
        for (byteIndex in payload.indices) {
            val value = payload[byteIndex].toInt() and 0xFF
            for (bit in 0 until 8) {
                bits[byteIndex * 8 + bit] = (value and (0x80 ushr bit)) != 0
            }
        }
        return bits
    }

    companion object {
        fun putInt(target: ByteArray, offset: Int, value: Int) {
            target[offset] = (value ushr 24).toByte()
            target[offset + 1] = (value ushr 16).toByte()
            target[offset + 2] = (value ushr 8).toByte()
            target[offset + 3] = value.toByte()
        }

        fun readInt(source: ByteArray, offset: Int): Int =
            ((source[offset].toInt() and 0xFF) shl 24) or
                ((source[offset + 1].toInt() and 0xFF) shl 16) or
                ((source[offset + 2].toInt() and 0xFF) shl 8) or
                (source[offset + 3].toInt() and 0xFF)
    }
}

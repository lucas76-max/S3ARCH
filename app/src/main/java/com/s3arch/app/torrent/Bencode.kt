package com.s3arch.app.torrent

import java.io.ByteArrayOutputStream

/**
 * Valeur bencode : le format de sérialisation utilisé par BitTorrent
 * (fichiers `.torrent`, réponses de trackers, messages DHT et échange de métadonnées).
 */
sealed class BValue {
    data class BInt(val value: Long) : BValue()

    class BStr(val bytes: ByteArray) : BValue() {
        val text: String get() = String(bytes, Charsets.UTF_8)

        override fun equals(other: Any?): Boolean = other is BStr && bytes.contentEquals(other.bytes)
        override fun hashCode(): Int = bytes.contentHashCode()
        override fun toString(): String = text
    }

    class BList(val items: List<BValue>) : BValue() {
        override fun toString(): String = items.joinToString(prefix = "[", postfix = "]")
    }

    class BDict(val entries: Map<String, BValue>) : BValue() {
        operator fun get(key: String): BValue? = entries[key]
        fun str(key: String): String? = (entries[key] as? BStr)?.text
        fun int(key: String): Long? = (entries[key] as? BInt)?.value
        fun dict(key: String): BDict? = entries[key] as? BDict
        fun list(key: String): BList? = entries[key] as? BList

        override fun toString(): String = entries.keys.joinToString(prefix = "{", postfix = "}")
    }

    companion object {
        fun of(value: String): BValue = BStr(value.toByteArray(Charsets.UTF_8))
        fun of(value: ByteArray): BValue = BStr(value)
        fun of(value: Long): BValue = BInt(value)
        fun of(value: Int): BValue = BInt(value.toLong())
        fun of(value: Boolean): BValue = BInt(if (value) 1L else 0L)
        fun list(values: List<BValue>): BValue = BList(values)
        fun dict(values: Map<String, BValue>): BValue = BDict(values)
    }
}

/**
 * Décodeur/encodeur bencode.
 *
 * La lecture est volontairement bornée : un `length` annoncé plus grand que le tampon
 * restant est refusé, ce qui évite qu'une réponse corrompue (tracker ou pair malveillant)
 * ne provoque une allocation géante.
 */
object Bencode {

    fun decode(bytes: ByteArray): BValue {
        val reader = Reader(bytes)
        val value = reader.read()
        return value
    }

    fun decodeOrNull(bytes: ByteArray): BValue? = runCatching { decode(bytes) }.getOrNull()

    /**
     * Longueur exacte de la premiere valeur bencode.
     *
     * Le protocole `ut_metadata` colle des octets bruts juste apres un dictionnaire :
     * il faut donc savoir ou ce dictionnaire se termine pour recuperer la suite.
     */
    fun consumedLength(bytes: ByteArray): Int = Reader(bytes).also { it.read() }.pos

    /**
     * Octets bruts du bloc `info`, tels qu'ils figurent dans le fichier.
     *
     * L'info-hash est le SHA-1 de **ces octets exacts** : les ré-encoder peut changer
     * l'ordre des clés et produire un hash différent de celui des trackers.
     */
    fun infoSlice(bytes: ByteArray): ByteArray? {
        val reader = Reader(bytes)
        if (reader.pos >= bytes.size || bytes[reader.pos] != 'd'.code.toByte()) return null
        reader.pos++
        var found: ByteArray? = null
        while (reader.pos < bytes.size) {
            if (bytes[reader.pos] == 'e'.code.toByte()) return found
            val key = reader.read() as? BValue.BStr ?: return found
            val start = reader.pos
            reader.read()
            if (key.text == "info" && found == null) {
                found = bytes.copyOfRange(start, reader.pos)
            }
        }
        return found
    }

    fun encode(value: BValue): ByteArray {
        val out = ByteArrayOutputStream()
        write(value, out)
        return out.toByteArray()
    }

    private fun write(value: BValue, out: ByteArrayOutputStream) {
        when (value) {
            is BValue.BInt -> {
                out.write('i'.code)
                out.write(value.value.toString().toByteArray(Charsets.US_ASCII))
                out.write('e'.code)
            }
            is BValue.BStr -> {
                out.write(value.bytes.size.toString().toByteArray(Charsets.US_ASCII))
                out.write(':'.code)
                out.write(value.bytes)
            }
            is BValue.BList -> {
                out.write('l'.code)
                value.items.forEach { write(it, out) }
                out.write('e'.code)
            }
            is BValue.BDict -> {
                out.write('d'.code)
                // Clés triées : c'est la forme canonique attendue par les autres clients.
                value.entries.entries
                    .sortedBy { it.key }
                    .forEach { (key, item) ->
                        write(BValue.of(key), out)
                        write(item, out)
                    }
                out.write('e'.code)
            }
        }
    }

    internal class Reader(private val bytes: ByteArray, var pos: Int = 0) {

        fun read(): BValue {
            if (pos >= bytes.size) throw BencodeException("Flux bencode tronqué")
            return when (val marker = bytes[pos].toInt().toChar()) {
                'i' -> readInt()
                'l' -> readList()
                'd' -> readDict()
                in '0'..'9' -> readString()
                else -> throw BencodeException("Marqueur bencode inconnu : $marker")
            }
        }

        private fun readInt(): BValue.BInt {
            expect('i')
            val end = bytes.indexOf('e'.code.toByte(), pos)
            if (end < 0) throw BencodeException("Entier non terminé")
            val text = String(bytes, pos, end - pos, Charsets.US_ASCII)
            pos = end + 1
            val value = text.toLongOrNull() ?: throw BencodeException("Entier invalide : $text")
            return BValue.BInt(value)
        }

        private fun readString(): BValue.BStr {
            val colon = bytes.indexOf(':'.code.toByte(), pos)
            if (colon < 0) throw BencodeException("Longueur de chaîne absente")
            val length = String(bytes, pos, colon - pos, Charsets.US_ASCII).toIntOrNull()
                ?: throw BencodeException("Longueur de chaîne invalide")
            if (length < 0) throw BencodeException("Longueur négative")
            val start = colon + 1
            if (start + length > bytes.size) throw BencodeException("Chaîne bencode tronquée")
            pos = start + length
            return BValue.BStr(bytes.copyOfRange(start, start + length))
        }

        private fun readList(): BValue.BList {
            expect('l')
            val items = mutableListOf<BValue>()
            while (true) {
                if (pos >= bytes.size) throw BencodeException("Liste non terminée")
                if (bytes[pos] == 'e'.code.toByte()) {
                    pos++
                    return BValue.BList(items)
                }
                items += read()
            }
        }

        private fun readDict(): BValue.BDict {
            expect('d')
            val entries = LinkedHashMap<String, BValue>()
            while (true) {
                if (pos >= bytes.size) throw BencodeException("Dictionnaire non terminé")
                if (bytes[pos] == 'e'.code.toByte()) {
                    pos++
                    return BValue.BDict(entries)
                }
                val key = readString().text
                entries[key] = read()
            }
        }

        private fun expect(marker: Char) {
            if (pos >= bytes.size || bytes[pos] != marker.code.toByte()) {
                throw BencodeException("Marqueur attendu : $marker")
            }
            pos++
        }
    }

    private fun ByteArray.indexOf(value: Byte, from: Int): Int {
        var index = from
        while (index < size) {
            if (this[index] == value) return index
            index++
        }
        return -1
    }
}

class BencodeException(message: String) : Exception(message)

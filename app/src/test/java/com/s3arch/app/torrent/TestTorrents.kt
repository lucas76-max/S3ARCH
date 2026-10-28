package com.s3arch.app.torrent

import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/** Construit de vrais fichiers `.torrent` pour les tests (bencode + SHA-1). */
object TestTorrents {

    fun singleFile(payload: ByteArray, pieceLength: Int): ByteArray {
        val info = BValue.dict(
            mapOf(
                "name" to BValue.of("test.bin"),
                "piece length" to BValue.of(pieceLength.toLong()),
                "length" to BValue.of(payload.size.toLong()),
                "pieces" to BValue.of(hashes(payload, pieceLength)),
            ),
        )
        return Bencode.encode(
            BValue.dict(
                mapOf(
                    "announce" to BValue.of("http://tracker.example/announce"),
                    "info" to info,
                ),
            ),
        )
    }

    fun multiFile(files: List<Pair<String, Long>>, pieceLength: Int): ByteArray {
        val total = files.sumOf { it.second }
        val payload = ByteArray(total.toInt()) { (it % 251).toByte() }
        val fileEntries = files.map { (path, length) ->
            BValue.dict(
                mapOf(
                    "length" to BValue.of(length),
                    "path" to BValue.list(path.split('/').map { BValue.of(it) }),
                ),
            )
        }
        val info = BValue.dict(
            mapOf(
                "name" to BValue.of("Dossier"),
                "piece length" to BValue.of(pieceLength.toLong()),
                "files" to BValue.list(fileEntries),
                "pieces" to BValue.of(hashes(payload, pieceLength)),
            ),
        )
        return Bencode.encode(BValue.dict(mapOf("info" to info)))
    }

    private fun hashes(payload: ByteArray, pieceLength: Int): ByteArray {
        val out = ByteArrayOutputStream()
        var offset = 0
        val digest = MessageDigest.getInstance("SHA-1")
        while (offset < payload.size) {
            val end = minOf(offset + pieceLength, payload.size)
            digest.reset()
            out.write(digest.digest(payload.copyOfRange(offset, end)))
            offset = end
        }
        return out.toByteArray()
    }
}

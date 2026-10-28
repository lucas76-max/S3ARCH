package com.s3arch.app.torrent

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class BencodeTest {

    @Test
    fun `relit les entiers chaines listes et dictionnaires`() {
        val value = Bencode.decode("d3:agei37e4:listli1ei2ee4:name4:teste".toByteArray())
        val dict = value as BValue.BDict
        assertEquals(37L, dict.int("age"))
        assertEquals("test", dict.str("name"))
        assertEquals(2, dict.list("list")?.items?.size)
        assertEquals(2L, (dict.list("list")?.items?.get(1) as BValue.BInt).value)
    }

    @Test
    fun `encode les dictionnaires avec des cles triees`() {
        val encoded = Bencode.encode(
            BValue.dict(mapOf("b" to BValue.of(1), "a" to BValue.of(2))),
        )
        assertEquals("d1:ai2e1:bi1ee", String(encoded, Charsets.US_ASCII))
    }

    @Test
    fun `refuse une chaine tronquee`() {
        assertNull(Bencode.decodeOrNull("4:ab".toByteArray()))
    }

    @Test
    fun `extrait le bloc info tel quel`() {
        val bytes = "d1:ai1e4:infod4:name1:x6:lengthi1eee".toByteArray()
        val slice = Bencode.infoSlice(bytes)
        assertNotNull(slice)
        assertEquals("d4:name1:x6:lengthi1ee", String(slice!!, Charsets.US_ASCII))
    }

    @Test
    fun `calcule la longueur consommee par un dictionnaire`() {
        val payload = "d8:msg_typei1e5:piecei0ee".toByteArray() + byteArrayOf(1, 2, 3)
        val length = Bencode.consumedLength(payload)
        assertEquals(payload.size - 3, length)
        assertArrayEquals(byteArrayOf(1, 2, 3), payload.copyOfRange(length, payload.size))
    }

    @Test
    fun `analyse un torrent mono-fichier`() {
        val payload = ByteArray(10) { it.toByte() }
        val torrent = TestTorrents.singleFile(payload, pieceLength = 4)
        val meta = TorrentMeta.parse(torrent)

        assertEquals("test.bin", meta.name)
        assertEquals(10L, meta.totalLength)
        assertEquals(3, meta.pieceCount)
        assertEquals(4, meta.pieceSize(0))
        assertEquals(2, meta.pieceSize(2))
        assertTrue(meta.matches(0, payload.copyOfRange(0, 4)))
        assertEquals("http://tracker.example/announce", meta.trackers.first())
    }

    @Test
    fun `l'info-hash est le sha1 du bloc info brut`() {
        val torrent = TestTorrents.singleFile(ByteArray(8) { 7 }, pieceLength = 4)
        val meta = TorrentMeta.parse(torrent)
        val expected = MessageDigest.getInstance("SHA-1").digest(Bencode.infoSlice(torrent)!!)
        assertArrayEquals(expected, meta.infoHash)
        assertEquals(expected.toHex(), meta.infoHashHex)
    }

    @Test
    fun `analyse un torrent multi-fichiers et calcule les positions`() {
        val torrent = TestTorrents.multiFile(
            listOf("film.mkv" to 6L, "subs/fr.srt" to 2L),
            pieceLength = 4,
        )
        val meta = TorrentMeta.parse(torrent)
        assertEquals(8L, meta.totalLength)
        assertEquals(2, meta.pieceCount)
        assertEquals("Dossier/film.mkv", meta.files[0].path)
        assertEquals("Dossier/subs/fr.srt", meta.files[1].path)
        assertEquals(6L, meta.files[1].offset)
        assertEquals("Dossier/film.mkv", meta.fileFor(5)?.path)
        assertEquals("Dossier/subs/fr.srt", meta.fileFor(7)?.path)
    }

    @Test
    fun `reconstruit les metadonnees depuis le bloc info d'un magnet`() {
        val torrent = TestTorrents.singleFile(ByteArray(12) { 3 }, pieceLength = 4)
        val info = Bencode.infoSlice(torrent)!!
        val meta = TorrentMeta.fromInfo(info, listOf("udp://tracker:1337/announce"))
        assertEquals(12L, meta.totalLength)
        assertEquals(3, meta.pieceCount)
        assertEquals(listOf("udp://tracker:1337/announce"), meta.trackers)
    }

    @Test
    fun `nettoie les chemins dangereux d'un torrent`() {
        assertEquals("", sanitizeSegment(".."))
        assertEquals("", sanitizeSegment("."))
        assertEquals("a_b", sanitizeSegment("a/b"))
        assertEquals("film", sanitizeSegment("film."))
    }
}

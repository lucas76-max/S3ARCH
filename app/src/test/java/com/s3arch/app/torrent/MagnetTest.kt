package com.s3arch.app.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MagnetTest {

    private val hash = "0123456789abcdef0123456789abcdef01234567"

    @Test
    fun `lit un magnet complet`() {
        val link = MagnetLink.parse(
            "magnet:?xt=urn:btih:$hash&dn=Mon%20Film&tr=" +
                "https%3A%2F%2Ftracker.example%2Fannounce&xl=123456",
        )
        assertNotNull(link)
        assertEquals(hash, link!!.infoHash)
        assertEquals("Mon Film", link.name)
        assertEquals(listOf("https://tracker.example/announce"), link.trackers)
        assertEquals(123456L, link.sizeBytes)
    }

    @Test
    fun `normalise un hash hexadecimal en minuscules`() {
        val link = MagnetLink.parse("magnet:?xt=urn:btih:${hash.uppercase()}")
        assertEquals(hash, link?.infoHash)
    }

    @Test
    fun `accepte un hash base32`() {
        // Vingt octets 0x01 encodés en base32 (RFC 4648).
        val magnet = "magnet:?xt=urn:btih:AEAQCAIBAEAQCAIBAEAQCAIBAEAQCAIB"
        val link = MagnetLink.parse(magnet)
        assertEquals("0101010101010101010101010101010101010101", link?.infoHash)
    }

    @Test
    fun `refuse un magnet sans xt ni hash valide`() {
        assertNull(MagnetLink.parse("magnet:?dn=test"))
        assertNull(MagnetLink.parse("magnet:?xt=urn:btih:xyz"))
        assertNull(MagnetLink.parse("https://example.com/file.torrent"))
    }

    @Test
    fun `reconnait les liens colles`() {
        assertTrue(phoneSourceOf("magnet:?xt=urn:btih:$hash") is PhoneSource.Magnet)
        assertTrue(phoneSourceOf("https://example.com/a.torrent") is PhoneSource.Link)
        assertNull(phoneSourceOf("pas un lien"))
    }

    @Test
    fun `decoupe un bloc de liens en ignorant les lignes vides`() {
        val text = "magnet:?xt=urn:btih:$hash\n\n  https://example.com/a.torrent  \r\nmagnet:?xt=urn:btih:$hash"
        assertEquals(2, parseSourceLines(text).size)
        assertFalse(parseSourceLines("").isNotEmpty())
    }

    @Test
    fun `avertit au-dela de 10 Go et refuse quand la place manque`() {
        assertFalse(requiresSizeConfirmation(9L * 1024 * 1024 * 1024))
        assertTrue(requiresSizeConfirmation(10L * 1024 * 1024 * 1024))

        val problem = spaceProblem("sur le téléphone", 5_000L, 1_000L)
        assertNotNull(problem)
        assertTrue(problem!!.contains("Plus de place sur le téléphone"))
        assertNull(spaceProblem("sur le téléphone", 500L, 1_000L))
        assertNull(spaceProblem("sur le téléphone", 0L, 1_000L))
    }

    @Test
    fun `formate les tailles sans dependre d'Android`() {
        assertEquals("0 o", humanSize(0))
        assertEquals("1,0 Ko", humanSize(1024))
        assertEquals("1,0 Go", humanSize(1024L * 1024 * 1024))
    }
}

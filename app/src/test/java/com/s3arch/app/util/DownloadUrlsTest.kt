package com.s3arch.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadUrlsTest {

    private val base = "https://c411.org/api"

    @Test
    fun `conserve une URL absolue`() {
        assertEquals(
            "https://tracker.org/dl/12?passkey=abc",
            DownloadUrls.sanitize("https://tracker.org/dl/12?passkey=abc", base),
        )
    }

    @Test
    fun `resout une URL relative`() {
        assertEquals(
            "https://c411.org/torrents/12/download",
            DownloadUrls.sanitize("/torrents/12/download", base),
        )
    }

    @Test
    fun `encode les espaces`() {
        assertEquals(
            "https://tracker.org/a%20b.torrent",
            DownloadUrls.sanitize("https://tracker.org/a b.torrent", base),
        )
    }

    @Test
    fun `decode les entites HTML`() {
        assertEquals(
            "https://tracker.org/dl?id=1&passkey=abc",
            DownloadUrls.sanitize("https://tracker.org/dl?id=1&amp;passkey=abc", base),
        )
    }

    @Test
    fun `accepte un magnet complet`() {
        val magnet = "magnet:?xt=urn:btih:0123456789abcdef&dn=Test"
        assertEquals(magnet, DownloadUrls.sanitize(magnet, base))
        assertTrue(DownloadUrls.isMagnet(magnet))
    }

    @Test
    fun `refuse un magnet sans info hash`() {
        assertEquals("", DownloadUrls.sanitize("magnet:?dn=Test", base))
    }

    @Test
    fun `refuse les schemas inattendus`() {
        assertEquals("", DownloadUrls.sanitize("javascript:alert(1)", base))
        assertEquals("", DownloadUrls.sanitize("file:///etc/passwd", base))
    }

    @Test
    fun `gere le protocole relatif`() {
        assertEquals(
            "https://tracker.org/dl/1.torrent",
            DownloadUrls.sanitize("//tracker.org/dl/1.torrent", base),
        )
    }

    @Test
    fun `conserve ftp`() {
        assertEquals("ftp://host/file.torrent", DownloadUrls.sanitize("ftp://host/file.torrent", base))
    }
}

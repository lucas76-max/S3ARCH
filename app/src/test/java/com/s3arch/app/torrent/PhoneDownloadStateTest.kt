package com.s3arch.app.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneDownloadStateTest {

    private fun download(state: String, done: Long = 100L, total: Long = 100L) = PhoneDownload(
        id = "abc12345",
        name = "Test",
        source = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567",
        state = state,
        doneBytes = done,
        totalBytes = total,
    )

    @Test
    fun `le partage garde le transfert actif mais pas termine`() {
        val seeding = download(PhoneDownload.STATE_SEEDING)
        assertTrue(seeding.isSeeding)
        assertTrue(seeding.isActive)
        assertFalse(seeding.isDone)
        assertEquals(1f, seeding.progress, 0.001f)
    }

    @Test
    fun `un transfert termine n'est plus actif`() {
        val done = download(PhoneDownload.STATE_DONE)
        assertTrue(done.isDone)
        assertFalse(done.isActive)
        assertFalse(done.isSeeding)
    }

    @Test
    fun `une pause et une erreur ne sont pas actives`() {
        assertFalse(download(PhoneDownload.STATE_PAUSED).isActive)
        assertFalse(download(PhoneDownload.STATE_ERROR).isActive)
    }

    @Test
    fun `la progression reste bornee meme sans taille connue`() {
        assertEquals(0f, download(PhoneDownload.STATE_DOWNLOADING, done = 10, total = 0).progress, 0.001f)
        assertEquals(1f, download(PhoneDownload.STATE_DOWNLOADING, done = 200, total = 100).progress, 0.001f)
    }
}

package com.s3arch.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Filtre « masquer les torrents a 0 seeder » (actif par defaut). */
class SeederFilterTest {

    private fun result(tracker: String, seeders: Int, title: String = "r$seeders-$tracker") = TorrentResult(
        id = "$tracker-$title",
        title = title,
        sizeBytes = 1_000L,
        seeders = seeders,
        leechers = 0,
        trackerId = tracker,
        trackerLabel = tracker,
        downloadUrl = "https://example.org/$tracker.torrent",
    )

    private val list = listOf(
        result("c411", 12),
        result("c411", 0),
        result("ygg", 3),
        result("ygg", 0),
    )

    @Test
    fun `filtre actif retire les resultats sans source`() {
        val kept = list.withoutZeroSeeders(true)

        assertEquals(2, kept.size)
        assertEquals(listOf(12, 3), kept.map { it.seeders })
    }

    @Test
    fun `filtre desactive conserve tout`() {
        assertEquals(list.size, list.withoutZeroSeeders(false).size)
    }

    @Test
    fun `un tracker qui n'expose pas les seeders n'est jamais vide`() {
        val silent = listOf(result("zxxclub", 0, "a"), result("zxxclub", 0, "b"))
        assertEquals(2, silent.withoutZeroSeeders(true).size)

        // Mixte : le tracker muet garde ses resultats, le tracker bavard est filtre.
        val mixed = list + silent
        val kept = mixed.withoutZeroSeeders(true)
        assertEquals(4, kept.size)
        assertEquals(2, kept.count { it.trackerId == "zxxclub" })
    }
}

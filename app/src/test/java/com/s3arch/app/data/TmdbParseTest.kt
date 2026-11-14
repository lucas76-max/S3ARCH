package com.s3arch.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Le decodage TMDB doit tolerer les champs nuls et les nombres entiers. */
class TmdbParseTest {

    private val client = TmdbClient()

    @Test
    fun litUneReponseComplete() {
        val body = """
            {"page":1,"results":[
              {"adult":false,"genre_ids":[16],"id":502356,"original_language":"en",
               "original_title":"The Super Mario Bros. Movie","overview":"Deux plombiers...",
               "popularity":1234.5,"poster_path":"/qNBAXBIQlnOThrVvA6mA2B5ggV6.jpg",
               "release_date":"2023-04-05","title":"Super Mario Bros. le film","video":false,
               "vote_average":7.7,"vote_count":9000}
            ],"total_pages":1,"total_results":1}
        """.trimIndent()

        val info = client.parse(body)
        assertNotNull(info)
        assertEquals("Super Mario Bros. le film", info!!.displayTitle)
        assertEquals("Deux plombiers...", info.displayOverview)
        assertEquals("2023", info.year)
        assertEquals(7.7, info.rating, 0.001)
        assertEquals("https://image.tmdb.org/t/p/w342/qNBAXBIQlnOThrVvA6mA2B5ggV6.jpg", info.posterUrl)
        assertEquals("https://image.tmdb.org/t/p/w500/qNBAXBIQlnOThrVvA6mA2B5ggV6.jpg", info.largePosterUrl)
    }

    @Test
    fun tolereUneAfficheNulle() {
        val body = """
            {"results":[
              {"title":"Sans affiche","poster_path":null,"release_date":null,
               "overview":null,"vote_average":0}
            ]}
        """.trimIndent()

        val info = client.parse(body)
        assertNotNull(info)
        assertEquals("Sans affiche", info!!.displayTitle)
        assertNull(info.posterUrl)
        assertEquals("", info.year)
    }

    @Test
    fun ignoreUnResultatSansAfficheSiUnAutreEnAUne() {
        val body = """
            {"results":[
              {"title":"Sans affiche","poster_path":null},
              {"title":"Avec affiche","poster_path":"/abc.jpg"}
            ]}
        """.trimIndent()

        val info = client.parse(body)
        assertEquals("Avec affiche", info?.displayTitle)
    }

    @Test
    fun reponseIllisibleNePlantePas() {
        assertNull(client.parse(""))
        assertNull(client.parse("{pas du json"))
        assertNull(client.parse("{\"results\":\"erreur\"}"))
    }

    @Test
    fun normaliseLesTitresAvantLaRecherche() {
        assertEquals(
            "Super Mario Bros le film",
            MediaTitle.normalize("Super.Mario.Bros.le.film.2023.MULTI.VF2.720p.BluRay.AAC.2.0.x265-EPICSNOOPYQC"),
        )
        assertEquals(
            "Harry Potter Et La Chambre Des Secrets",
            MediaTitle.normalize(
                "Harry.Potter.Et.La.Chambre.Des.Secrets.2002.MULTI.VFF.2160p.WEBRip.DV.HDR.EAC3.5.1.x265-Amen",
            ),
        )
        assertEquals(
            "Un Ptit Truc En Plus",
            MediaTitle.normalize("Un.Ptit.Truc.En.Plus.2024.VOF.1080p.BluRay.mHD.x264.AC3-ROMKENT"),
        )
        assertEquals(2024, MediaTitle.yearOf("Un.Ptit.Truc.En.Plus.2024.VOF.1080p.BluRay.mHD.x264"))
    }

    @Test
    fun filtresAvancesReconnaissentLesJetons() {
        val result = TorrentResult(
            id = "1",
            title = "Un.Film.2024.MULTI.VFF.1080p.WEB-DL.DDP5.1.x265-GROUP",
            sizeBytes = 3L * 1024 * 1024 * 1024,
            seeders = 42,
            leechers = 3,
            trackerId = "test",
            trackerLabel = "Test",
            downloadUrl = "https://example.org/a.torrent",
        )

        assertEquals(
            true,
            SearchFilters(languages = setOf("vff")).matches(result),
        )
        assertEquals(
            false,
            SearchFilters(languages = setOf("vostfr")).matches(result),
        )
        assertEquals(
            true,
            SearchFilters(videoQualities = setOf("1080p", "webdl")).matches(result),
        )
        assertEquals(
            true,
            SearchFilters(yearMin = 2020, yearMax = 2025, minSeeders = 10).matches(result),
        )
        assertEquals(
            false,
            SearchFilters(minSizeBytes = 5L * 1024 * 1024 * 1024).matches(result),
        )
        assertEquals(
            false,
            SearchFilters(audioCodecs = setOf("flac")).matches(result),
        )
        assertEquals(
            true,
            SearchFilters(audioCodecs = setOf("5.1")).matches(result),
        )
    }
}

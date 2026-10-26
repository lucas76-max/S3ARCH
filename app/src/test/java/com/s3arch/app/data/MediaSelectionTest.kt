package com.s3arch.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * La lecture VLC depend entierement de ce choix de fichier : un torrent de film
 * contient un dossier, un .nfo, des sous-titres et un echantillon, et VLC ne peut
 * lire que le vrai media.
 */
class MediaSelectionTest {

    private fun file(name: String, size: Long, mime: String = "") = FreeboxFile(
        name = name,
        path = "/Disque dur/S3ARCH/film/$name",
        mimeType = mime,
        size = size,
    )

    @Test
    fun `choisit la plus grosse video et ignore sous-titres et nfo`() {
        val files = listOf(
            file("film.nfo", 3_000, "text/plain"),
            file("film.srt", 80_000, "application/x-subrip"),
            file("sample.mkv", 18_000_000, "video/x-matroska"),
            file("film.mkv", 2_400_000_000, "video/x-matroska"),
        )
        assertEquals("film.mkv", files.playableMedia()?.name)
    }

    @Test
    fun `reconnait un media par son extension quand le mimetype est vide`() {
        val files = listOf(
            file("image.iso", 900_000_000),
            file("concert.mp4", 700_000_000),
        )
        assertEquals("concert.mp4", files.playableMedia()?.name)
    }

    @Test
    fun `ne renvoie rien quand la tache ne contient aucun media`() {
        val files = listOf(
            file("film.nfo", 3_000, "text/plain"),
            file("archive.zip", 12_000_000, "application/zip"),
        )
        assertNull(files.playableMedia())
    }

    @Test
    fun `une liste vide ne propose pas de lecture`() {
        assertNull(emptyList<FreeboxFile>().playableMedia())
    }

    @Test
    fun `le fichier retenu expose le chemin exact transmis au proxy`() {
        val media = listOf(file("film.mkv", 5L)).playableMedia()
        assertEquals("/Disque dur/S3ARCH/film/film.mkv", media?.path)
    }

    @Test
    fun `un chemin absolu est conserve tel quel`() {
        assertEquals(
            "/Calinette/Téléchargements/S3ARCH/film/film.mkv",
            freeboxPath(
                folder = "/Calinette/Téléchargements/S3ARCH/",
                name = "The.Exorcist.mkv",
                declared = "/Calinette/Téléchargements/S3ARCH/film/film.mkv",
            ),
        )
    }

    /** Certains firmwares ne renvoient qu'un nom relatif : sans le dossier, `/dl/` echoue. */
    @Test
    fun `un chemin relatif est rattache au dossier de la tache`() {
        assertEquals(
            "/Calinette/Téléchargements/S3ARCH/film/film.mkv",
            freeboxPath(
                folder = "/Calinette/Téléchargements/S3ARCH/",
                name = "film",
                declared = "film/film.mkv",
            ),
        )
    }

    @Test
    fun `un chemin absent retombe sur le dossier et le nom de la tache`() {
        assertEquals(
            "/Disque dur/S3ARCH/Mon.Film",
            freeboxPath(folder = "/Disque dur/S3ARCH", name = "Mon.Film", declared = "  "),
        )
    }
}

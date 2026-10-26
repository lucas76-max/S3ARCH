package com.s3arch.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FreeboxPathsTest {

    @Test
    fun `normalise un chemin sans slash final`() {
        assertEquals("/Disque dur/S3ARCH/", FreeboxPaths.normalizeDir("/Disque dur/S3ARCH"))
        assertEquals("/Disque dur/S3ARCH/", FreeboxPaths.normalizeDir("Disque dur/S3ARCH"))
        assertEquals("/Disque dur/S3ARCH/", FreeboxPaths.normalizeDir("/Disque dur//S3ARCH//"))
    }

    @Test
    fun `utilise le dossier par defaut si vide`() {
        assertEquals("/Disque dur/S3ARCH/", FreeboxPaths.normalizeDir("   "))
    }

    @Test
    fun `le parent de mkdir ne doit pas finir par un slash`() {
        val levels = FreeboxPaths.levels("/Disque dur/S3ARCH/")
        assertEquals(listOf("/" to "Disque dur", "/Disque dur" to "S3ARCH"), levels)
        assertEquals("L0Rpc3F1ZSBkdXI=", FreeboxPaths.base64(levels[1].first))
    }

    @Test
    fun `encode le dossier de telechargement avec un slash final`() {
        // Reference de la documentation Freebox OS pour /Disque dur/Telechargements/
        assertEquals(
            "L0Rpc3F1ZSBkdXIvVMOpbMOpY2hhcmdlbWVudHMv",
            FreeboxPaths.base64(FreeboxPaths.normalizeDir("/Disque dur/Téléchargements")),
        )
    }

    @Test
    fun `parentOf renvoie le dossier parent sans slash`() {
        assertEquals("/Disque dur", FreeboxPaths.parentOf("/Disque dur/S3ARCH/"))
        assertEquals("/Disque dur/Téléchargements", FreeboxPaths.parentOf("/Disque dur/Téléchargements/film.mkv"))
    }
}

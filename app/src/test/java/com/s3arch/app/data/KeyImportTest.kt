package com.s3arch.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyImportTest {

    @Test
    fun `le format demande est reconnu`() {
        val keys = KeyImport.asMap(
            """
            V3X = cle-v3x
            C411 = cle-c411
            YGG_REBORN = passkey-ygg
            ASTRATORRENT = cle-astra
            ZXX_CLUB = cle-zxx
            """.trimIndent(),
        )

        assertEquals("cle-v3x", keys[Tracker.V3X])
        assertEquals("cle-c411", keys[Tracker.C411])
        assertEquals("passkey-ygg", keys[Tracker.YGG_REBORN])
        assertEquals("cle-astra", keys[Tracker.ASTRATORRENT])
        assertEquals("cle-zxx", keys[Tracker.ZXX_CLUB])
    }

    @Test
    fun `les variantes de nom et de separateur sont acceptees`() {
        val keys = KeyImport.asMap(
            """
            # mes clés
            v3x: minuscule
            YGG Reborn = avec espaces
            ZXX = club
            inconnu = ignoree
            """.trimIndent(),
        )

        assertEquals("minuscule", keys[Tracker.V3X])
        assertEquals("avec espaces", keys[Tracker.YGG_REBORN])
        assertEquals("club", keys[Tracker.ZXX_CLUB])
        assertTrue("un nom inconnu ne doit rien ajouter", keys.keys.none { it == Tracker.TR4KER })
        assertEquals(3, keys.size)
    }

    @Test
    fun `une cle vide est ignoree`() {
        val keys = KeyImport.asMap("C411 =\nTR4KER = vraie")
        assertEquals(1, keys.size)
        assertEquals("vraie", keys[Tracker.TR4KER])
    }
}

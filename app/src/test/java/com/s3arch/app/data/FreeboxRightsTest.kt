package com.s3arch.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeboxRightsTest {

    private fun parse(raw: String) = FreeboxRights.parse(Json.parseToJsonElement(raw).jsonObject)

    @Test
    fun `ne conserve que les droits accordes`() {
        val rights = parse(
            """{"downloader": true, "explorer": true, "settings": false, "profile": false}""",
        )
        assertEquals(setOf("downloader", "explorer"), rights)
        assertFalse(rights!!.contains("settings"))
    }

    @Test
    fun `un droit absent vaut refuse`() {
        // La box ne liste que ce qu'elle accorde : `settings` absent = VPN impossible.
        val rights = parse("""{"downloader": true}""")
        assertFalse(rights!!.contains("settings"))
    }

    @Test
    fun `le droit settings est detecte quand il est accorde`() {
        val rights = parse("""{"settings": true, "downloader": true, "explorer": true}""")
        assertTrue(rights!!.contains("settings"))
    }

    @Test
    fun `absence de bloc permissions reste inconnue`() {
        // Sans bloc `permissions`, on ne doit pas conclure a un refus : null = inconnu.
        assertNull(FreeboxRights.parse(null))
        assertNull(FreeboxRights.parse(Json.parseToJsonElement("\"oops\"")))
    }
}

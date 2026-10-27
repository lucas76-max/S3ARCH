package com.s3arch.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionTest {

    @Test
    fun `une version superieure est detectee`() {
        assertTrue(UpdateRepository.compareVersions("1.3.0", "1.2.0") > 0)
        assertTrue(UpdateRepository.compareVersions("v1.10.0", "1.9.9") > 0)
        assertTrue(UpdateRepository.compareVersions("2.0", "1.99.99") > 0)
    }

    @Test
    fun `la meme version ou une version plus ancienne ne declenche rien`() {
        assertEquals(0, UpdateRepository.compareVersions("1.3.0", "1.3.0"))
        assertEquals(0, UpdateRepository.compareVersions("v1.3.0", "1.3.0"))
        assertTrue(UpdateRepository.compareVersions("1.2.0", "1.3.0") < 0)
    }

    @Test
    fun `les suffixes de pre-release sont ignores`() {
        assertEquals(0, UpdateRepository.compareVersions("1.4.0-beta2", "1.4.0"))
    }
}

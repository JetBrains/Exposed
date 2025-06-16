package org.jetbrains.exposed.v1.tests

import org.jetbrains.exposed.v1.core.Version
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionTests {
    @Test
    fun testFromString() {
        assertEquals(Version(1, 0, 0), Version.from("1"))
        assertEquals(Version(1, 0, 0), Version.from("1 test"))
        assertEquals(Version(1, 0, 0), Version.from("1-test"))
        assertEquals(Version(1, 2, 0), Version.from("1.2"))
        assertEquals(Version(1, 2, 0), Version.from("1.2-test"))
        assertEquals(Version(1, 2, 0), Version.from("1.2."))
        assertEquals(Version(1, 2, 0), Version.from("1.2.0"))
        assertEquals(Version(1, 2, 0), Version.from("1.2.-test"))
        assertEquals(Version(1, 2, 3), Version.from("1.2.3"))
        assertEquals(Version(1, 2, 3), Version.from("1.2.3-test"))
        assertEquals(Version(1, 2, 3), Version.from("1.2.3."))
        assertEquals(Version(1, 2, 3), Version.from("1.2.3test"))

        assertFails { Version.from("") }
        assertFails { Version.from("version-1.0.0") }
        assertFails { Version.from("version") }
    }

    @Test
    fun testFromBigInteger() {
        assertEquals(Version(1, 0, 0), Version.from(BigDecimal("1")))
        assertEquals(Version(1, 2, 0), Version.from(BigDecimal("1.2")))
    }

    @Test
    fun testCovers() {
        val version = Version.from("1.2.3")
        assertTrue { version.covers("1") }
        assertTrue { version.covers("1.2") }
        assertTrue { version.covers("1.2.3") }
        assertTrue { version.covers("1.1.100") }
        assertTrue { version.covers("0") }
        assertTrue { version.covers("0.100") }
        assertTrue { version.covers("0.0.100") }

        assertFalse { version.covers("2") }
        assertFalse { version.covers("2.0") }
        assertFalse { version.covers("2.0.1") }
    }

    @Test
    fun testToString() {
        assertEquals("1.2.3", Version.from("1.2.3").toString())
        assertEquals("1.2.0", Version.from(BigDecimal("1.2")).toString())
        assertEquals("1.0.1", Version(1, 0, 1).toString())
    }
}

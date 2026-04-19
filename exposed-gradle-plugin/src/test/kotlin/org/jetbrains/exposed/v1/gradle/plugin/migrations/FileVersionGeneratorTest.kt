package org.jetbrains.exposed.v1.gradle.plugin.migrations

import org.jetbrains.exposed.v1.gradle.plugin.VersionFormat
import org.jetbrains.exposed.v1.gradle.plugin.nextVersion
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

private class FixedClock(private val fixedTime: Instant) : Clock {
    override fun now(): Instant = fixedTime
}

class FileVersionGeneratorTest {
    private val scriptDirectory = "src/test/resources"
    private val filePrefix = "V"
    private val fileSeparator = "__"
    private val currentMajor = 3
    private val versionTsPattern by lazy { Regex("^$filePrefix(\\d{12,14})$fileSeparator.*$") }
    private val versionMajorTsPattern by lazy { Regex("^$filePrefix(\\d+)_(\\d{12,14})$fileSeparator.*$") }
    private val testClock by lazy { FixedClock(Instant.parse("2026-04-17T22:10:43Z")) }

    private lateinit var testFilePath: File
    private lateinit var testMajorTsFile: File
    private lateinit var testMajorMinorFile: File
    private lateinit var testMajorFile: File

    @BeforeEach
    fun setup() {
        testFilePath = File(scriptDirectory)
        testMajorTsFile = File("$scriptDirectory/V${currentMajor}_20260301113322__create_table_users.sql")
        testMajorMinorFile = File("$scriptDirectory/V${currentMajor}_2__create_table_students.sql")
        testMajorFile = File("$scriptDirectory/V${currentMajor}__create_table_employes.sql")
        assertTrue { testFilePath.exists() }
        assertTrue { testMajorTsFile.createNewFile() }
        assertTrue { testMajorMinorFile.createNewFile() }
        assertTrue { testMajorFile.createNewFile() }
    }

    @AfterEach
    fun teardown() {
        if (testMajorTsFile.exists()) assertTrue { testMajorTsFile.delete() }
        if (testMajorMinorFile.exists()) assertTrue { testMajorMinorFile.delete() }
        if (testMajorFile.exists()) assertTrue { testMajorFile.delete() }
    }

    @Test
    fun `test timestamp-only formats ignore existing files and args`() {
        val version1 = VersionFormat.TIMESTAMP_ONLY.nextVersion(testFilePath, testClock, filePrefix, fileSeparator).invoke(1)
        val match1 = versionTsPattern.matchEntire(version1)
        assertNotNull(match1)
        assertTrue { match1.groupValues[1].length == 14 }

        val version2 = VersionFormat.TIMESTAMP_WITHOUT_SECONDS.nextVersion(testFilePath, testClock, filePrefix, fileSeparator).invoke(1)
        val match2 = versionTsPattern.matchEntire(version2)
        assertNotNull(match2)
        assertTrue { match2.groupValues[1].length == 12 }
    }

    @Test
    fun `test timestamp-with-major formats use existing files but ignores args`() {
        val version1 = VersionFormat.MAJOR_TIMESTAMP.nextVersion(testFilePath, testClock, filePrefix, fileSeparator).invoke(1)
        val match1 = versionMajorTsPattern.matchEntire(version1)
        assertNotNull(match1)
        assertEquals(currentMajor + 1, match1.groupValues[1].toInt())
        assertTrue { match1.groupValues[2].length == 14 }

        val version2 = VersionFormat.MAJOR_TIMESTAMP_WITHOUT_SECONDS.nextVersion(testFilePath, testClock, filePrefix, fileSeparator).invoke(1)
        val match2 = versionMajorTsPattern.matchEntire(version2)
        assertNotNull(match2)
        assertEquals(currentMajor + 1, match2.groupValues[1].toInt())
        assertTrue { match2.groupValues[2].length == 12 }
    }

    @Test
    fun `test major-minor format uses existing files and args`() {
        val version = VersionFormat.MAJOR_MINOR.nextVersion(testFilePath, testClock, filePrefix, fileSeparator).invoke(1)
        val (major, minor) = version.substringAfter(filePrefix).substringBefore(fileSeparator).split('_')
        assertEquals(currentMajor + 1, major.toInt())
        assertEquals(1, minor.toInt())
    }

    @Test
    fun `test major-only format uses existing files but ignores args`() {
        val version = VersionFormat.MAJOR_ONLY.nextVersion(testFilePath, testClock, filePrefix, fileSeparator).invoke(1)
        val major = version.substringAfter(filePrefix).substringBefore(fileSeparator)
        assertEquals(currentMajor + 1, major.toInt())
    }

    @Test
    fun `test major formats when directory is empty`() {
        assertTrue { testMajorTsFile.delete() }
        assertTrue { testMajorMinorFile.delete() }
        assertTrue { testMajorFile.delete() }

        val version1 = VersionFormat.MAJOR_TIMESTAMP.nextVersion(testFilePath, testClock, filePrefix, fileSeparator).invoke(1)
        val match1 = versionMajorTsPattern.matchEntire(version1)
        assertNotNull(match1)
        assertEquals(1, match1.groupValues[1].toInt())
        assertTrue { match1.groupValues[2].length == 14 }

        val version2 = VersionFormat.MAJOR_TIMESTAMP_WITHOUT_SECONDS.nextVersion(testFilePath, testClock, filePrefix, fileSeparator).invoke(1)
        val match2 = versionMajorTsPattern.matchEntire(version2)
        assertNotNull(match2)
        assertEquals(1, match2.groupValues[1].toInt())
        assertTrue { match2.groupValues[2].length == 12 }

        val version3 = VersionFormat.MAJOR_MINOR.nextVersion(testFilePath, testClock, filePrefix, fileSeparator).invoke(1)
        val (major3, minor3) = version3.substringAfter(filePrefix).substringBefore(fileSeparator).split('_')
        assertEquals(1, major3.toInt())
        assertEquals(1, minor3.toInt())

        val version4 = VersionFormat.MAJOR_ONLY.nextVersion(testFilePath, testClock, filePrefix, fileSeparator).invoke(1)
        val major4 = version4.substringAfter(filePrefix).substringBefore(fileSeparator)
        assertEquals(1, major4.toInt())
    }

    @Test
    fun `test correct format used even when directory uses other formats`() {
        assertTrue { testMajorMinorFile.delete() }
        // directory now only contains #_TS__ and #__ formats

        val version = VersionFormat.MAJOR_MINOR.nextVersion(testFilePath, testClock, filePrefix, fileSeparator).invoke(1)
        val (major, minor) = version.substringAfter(filePrefix).substringBefore(fileSeparator).split('_')
        assertEquals(currentMajor + 1, major.toInt())
        assertEquals(1, minor.toInt())
    }
}

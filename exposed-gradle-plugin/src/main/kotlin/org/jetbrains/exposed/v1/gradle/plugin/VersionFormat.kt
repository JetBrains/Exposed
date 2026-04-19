package org.jetbrains.exposed.v1.gradle.plugin

import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import java.io.File
import kotlin.time.Clock

/**
 * The standardized format used for the version sub-pattern of a migration script's filename.
 * The version sub-pattern is the part of the filename that occurs between the prefix and the description separator.
 */
enum class VersionFormat {
    /**
     * Uses the format `<prefix>YYYYMMDDHHMMSS<separator><description><extension>`
     *
     * For example, V20260417195521__create_table_users.sql
     */
    TIMESTAMP_ONLY,

    /**
     * Uses the format `<prefix>YYYYMMDDHHMM<separator><description><extension>`
     *
     * For example, V202604171955__create_table_users.sql
     */
    TIMESTAMP_WITHOUT_SECONDS,

    /**
     * Uses the format `<prefix>#_YYYYMMDDHHMMSS<separator><description><extension>`
     *
     * If existing scripts are detected, the length of the major versions will be copied.
     * For example, V3_20260417195521__create_table_users.sql or V003_20260417195521__create_table_users.sql
     */
    MAJOR_TIMESTAMP,

    /**
     * Uses the format `<prefix>#_YYYYMMDDHHMM<separator><description><extension>`
     *
     * If existing scripts are detected, the length of the major versions will be copied.
     * For example, V3_202604171955__create_table_users.sql or V003_202604171955__create_table_users.sql
     */
    MAJOR_TIMESTAMP_WITHOUT_SECONDS,

    /**
     * Uses the format `<prefix>#_#<separator><description><extension>`
     *
     * If existing scripts are detected, the length of the major versions will be copied.
     * For example, V3_1__create_table_users.sql or V003_001__create_table_users.sql
     */
    MAJOR_MINOR,

    /**
     * Uses the format `<prefix>#<separator><description><extension>`
     *
     * If existing scripts are detected, the length of the major versions will be copied.
     * For example, V3__create_table_users.sql or V003__create_table_users.sql
     */
    MAJOR_ONLY,
}

/**
 * Retrieves a standardized filename sub-pattern for the next version. If the pattern involves major versions,
 * the expected migrations directory will be searched to find the current highest major version.
 *
 * @return A function that generates an appropriate next version string for the filename
 */
internal fun VersionFormat.nextVersion(
    migrationsDirectory: File,
    clock: Clock,
    prefix: String,
    separator: String,
): (Int) -> String {
    return when (this) {
        VersionFormat.TIMESTAMP_ONLY -> { _: Int ->
            "$prefix${getCurrentTimestamp(clock, withSeconds = true)}$separator"
        }
        VersionFormat.TIMESTAMP_WITHOUT_SECONDS -> { _: Int ->
            "$prefix${getCurrentTimestamp(clock, withSeconds = false)}$separator"
        }
        VersionFormat.MAJOR_TIMESTAMP -> { _: Int ->
            val majorPadded = findNextMajor(migrationsDirectory, prefix, separator)
            "$prefix${majorPadded}_${getCurrentTimestamp(clock, withSeconds = true)}$separator"
        }
        VersionFormat.MAJOR_TIMESTAMP_WITHOUT_SECONDS -> { _: Int ->
            val majorPadded = findNextMajor(migrationsDirectory, prefix, separator)
            "$prefix${majorPadded}_${getCurrentTimestamp(clock, withSeconds = false)}$separator"
        }
        VersionFormat.MAJOR_MINOR -> { index: Int ->
            val majorPadded = findNextMajor(migrationsDirectory, prefix, separator)
            val minorPadded = index.toString().padStart(majorPadded.length, '0')
            "$prefix${majorPadded}_$minorPadded$separator"
        }
        VersionFormat.MAJOR_ONLY -> { _: Int ->
            val majorPadded = findNextMajor(migrationsDirectory, prefix, separator)
            "$prefix$majorPadded$separator"
        }
    }
}

/**
 * Returns the next highest major version, based on existing files in directory.
 * The string will be padded with preceding '0' characters if this is detected in existing files.
 * The first attempt will use the expected pattern from this [VersionFormat]; otherwise, if no match is found,
 * it will attempt to find files matching patterns in this order: `MAJOR_TIMESTAMP`, `MAJOR_MINOR`, `MAJOR_ONLY`.
 * This allows users to configure a new version format that may not match their existing filename formats.
 */
private fun VersionFormat.findNextMajor(
    directory: File,
    prefix: String,
    separator: String,
): String {
    val versionXTsPattern: Regex by lazy { getVersionXTsPattern(prefix, separator) }
    val versionXYPattern: Regex by lazy { getVersionXYPattern(prefix, separator) }
    val versionXPattern: Regex by lazy { getVersionXPattern(prefix, separator) }

    var highestMajor = 0
    var highestMajorLength = 0

    directory.listFiles()?.forEach { file ->
        val fileName = file.name
        var version = 0
        var versionLength = 0

        val stringVersion = when (this) {
            VersionFormat.MAJOR_TIMESTAMP, VersionFormat.MAJOR_TIMESTAMP_WITHOUT_SECONDS -> {
                versionXTsPattern.matchEntire(fileName)?.let { it.groupValues[1] }
                    ?: versionXYPattern.matchEntire(fileName)?.let { it.groupValues[1] }
                    ?: versionXPattern.matchEntire(fileName)?.let { it.groupValues[1] }
            }
            VersionFormat.MAJOR_MINOR -> {
                versionXYPattern.matchEntire(fileName)?.let { it.groupValues[1] }
                    ?: versionXTsPattern.matchEntire(fileName)?.let { it.groupValues[1] }
                    ?: versionXPattern.matchEntire(fileName)?.let { it.groupValues[1] }
            }
            VersionFormat.MAJOR_ONLY -> {
                versionXPattern.matchEntire(fileName)?.let { it.groupValues[1] }
                    ?: versionXTsPattern.matchEntire(fileName)?.let { it.groupValues[1] }
                    ?: versionXYPattern.matchEntire(fileName)?.let { it.groupValues[1] }
            }
            else -> error("Unexpected format without expected major version: $this")
        }
        stringVersion?.let {
            version = it.toInt()
            versionLength = it.length
        }

        if (version > highestMajor) {
            highestMajor = version
        }
        if (versionLength > version.toString().length && versionLength > highestMajorLength) {
            highestMajorLength = versionLength
        }
    }

    highestMajor++

    return highestMajor.toString().padStart(highestMajorLength, '0')
}

/**
 * Returns Regex for description prefixes like V3_YYYYMMDDHHMMSS__description or V003_YYYYMMDDHHMMSS__description
 * or V3_YYYYMMDDHHMM__description.
 */
private fun getVersionXTsPattern(prefix: String, separator: String): Regex = Regex("^$prefix(\\d+)_(\\d{12,14})$separator.*$")

/**
 * Returns Regex for description prefixes like V3_1__description or V003_001__description.
 */
private fun getVersionXYPattern(prefix: String, separator: String): Regex = Regex("^$prefix(\\d+)_(\\d+)$separator.*$")

/**
 * Returns Regex for description prefixes like V3__description or V003__description.
 */
private fun getVersionXPattern(prefix: String, separator: String): Regex = Regex("^$prefix(\\d+)$separator.*$")

/** Returns current timestamp as a String with format YYYYMMDDHHMMSS */
private fun getCurrentTimestamp(clock: Clock, withSeconds: Boolean): String {
    val ts = clock.now()
    val customFormat = DateTimeComponents.Format {
        date(LocalDate.Formats.ISO_BASIC)
        hour()
        minute()
        if (withSeconds) second()
    }
    return ts.format(customFormat)
}

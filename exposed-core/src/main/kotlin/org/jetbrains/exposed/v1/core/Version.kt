package org.jetbrains.exposed.v1.core

import java.math.BigDecimal
import java.util.*

/**
 * Represents a standard semantic versioning three-part format for a dependency's version number.
 * Primarily intended to parse and more reliably compare String version numbers.
 *
 * @property major Given a version number X.Y.Z, the MAJOR version X denoting incompatible API changes.
 * @property minor Given a version number X.Y.Z, the MINOR version Y denoting backward compatible API changes.
 * @property patch Given a version number X.Y.Z, the PATCH version Z denoting backward compatible bug fixes.
 */
class Version @InternalApi constructor(val major: Int, val minor: Int, val patch: Int) {

    /**
     * Returns whether this [Version] is greater than or equal to the specified [version] instance.
     *
     * ```kotlin
     * Version.from("2.5.250").covers(Version.from("1.4.200")) // true
     * Version.from("2.5.250").covers(Version.from("2.1.214")) // true
     * Version.from("2.5.250").covers(Version.from("2.5.250")) // true
     * Version.from("2.3.230").covers(Version.from("2.3.232")) // false
     * ```
     */
    fun covers(version: Version): Boolean {
        if (major > version.major) return true
        if (major < version.major) return false

        if (minor > version.minor) return true
        if (minor < version.minor) return false

        if (patch >= version.patch) return true
        return false
    }

    /**
     * Returns whether this [Version] is greater than or equal to the specified String [version].
     *
     * ```kotlin
     * Version.from("2.5.250").covers("1.4.200") // true
     * Version.from("2.5.250").covers("2.1.214") // true
     * Version.from("2.5.250").covers("2.5.250") // true
     * Version.from("2.3.230").covers("2.3.232") // false
     * ```
     */
    fun covers(version: String): Boolean = covers(from(version))

    /**
     * Returns whether this [Version] is greater than or equal to the specified BigDecimal [version].
     *
     * ```kotlin
     * Version.from("2.5.250").covers(BigDecimal("1.4.200")) // true
     * Version.from("2.5.250").covers(BigDecimal("2.1.214")) // true
     * Version.from("2.5.250").covers(BigDecimal("2.5.250")) // true
     * Version.from("2.3.230").covers(BigDecimal("2.3.232")) // false
     * ```
     */
    fun covers(version: BigDecimal): Boolean = covers(from(version))

    /**
     * Returns whether this [Version] is greater than or equal to the specified version number parts.
     *
     * ```kotlin
     * Version.from("2.5.250").covers(1, 4, 200)) // true
     * Version.from("2.5.250").covers(2, 1, 214)) // true
     * Version.from("2.5.250").covers(2, 5, 250)) // true
     * Version.from("2.3.230").covers(2, 3, 232)) // false
     * ```
     */
    fun covers(major: Int, minor: Int = 0, patch: Int = 0): Boolean {
        @OptIn(InternalApi::class)
        return covers(Version(major, minor, patch))
    }

    override fun toString() = "$major.$minor.$patch"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Version

        return major == other.major && minor == other.minor && patch == other.patch
    }

    override fun hashCode(): Int {
        return Objects.hash(major, minor, patch)
    }

    companion object {
        private val versionRegex = Regex("""^(\d+)(?:\.(\d+))?(?:\.(\d+))?(\D)?(.*)?""")

        /**
         * Parses the provided [version] in String format and returns a new [Version] representing
         * a standard semantic versioning format.
         *
         * @throws IllegalArgumentException If the provided [version] is empty or starts with any character
         * that is not a digit. Valid formats must have at minimum a MAJOR number. MINOR and PATCH numbers separated
         * by a '.' character are optional. Pre-release versions that add a suffix appended by a '-' character
         * are optional and valid.
         */
        fun from(version: String): Version {
            val matchResult = versionRegex.find(version)
                ?: throw IllegalArgumentException("Invalid version format: $version")

            val parts = (1..3).map { part ->
                matchResult.groupValues[part].takeIf { it.isNotEmpty() }?.toInt() ?: 0
            }

            @OptIn(InternalApi::class)
            return Version(parts[0], parts[1], parts[2])
        }

        /**
         * Parses the provided BigDecimal [version] by its String representation and returns a new [Version]
         * representing a standard semantic versioning format.
         */
        fun from(version: BigDecimal): Version = from(version.toString())
    }
}

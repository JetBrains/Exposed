package org.jetbrains.exposed.v1.core

import java.math.BigDecimal
import java.util.*

class Version @InternalApi constructor(val major: Int, val minor: Int, val patch: Int) {

    fun covers(version: Version): Boolean {
        if (major > version.major) return true
        if (major < version.major) return false

        if (minor > version.minor) return true
        if (minor < version.minor) return false

        if (patch >= version.patch) return true
        return false
    }

    fun covers(version: String): Boolean = covers(from(version))

    fun covers(version: BigDecimal): Boolean = covers(from(version))

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

        fun from(version: String): Version {
            val matchResult = versionRegex.find(version)
                ?: throw IllegalArgumentException("Invalid version format: $version")

            val parts = (1..3).map { part ->
                matchResult.groupValues[part].takeIf { it.isNotEmpty() }?.toInt() ?: 0
            }

            @OptIn(InternalApi::class)
            return Version(parts[0], parts[1], parts[2])
        }

        fun from(version: BigDecimal): Version = from(version.toString())
    }
}

package org.jetbrains.exposed.v1.core

import java.math.BigDecimal

data class Version(val major: Int, val minor: Int, val patch: Int) {
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

    fun covers(major: Int, minor: Int = 0, patch: Int = 0): Boolean = covers(Version(major, minor, patch))

    override fun toString() = "$major.$minor.$patch"

    companion object {
        private val versionRegex = Regex("""^(\d+)(?:\.(\d+))?(?:\.(\d+))?(\D)?(.*)?""")

        fun from(version: String): Version {
            val matchResult = versionRegex.find(version)
                ?: throw IllegalArgumentException("Invalid version format: $version")

            val parts = (1..3).map { matchResult.groupValues[it].takeIf { it.isNotEmpty() }?.toInt() ?: 0 }

            return Version(parts[0], parts[1], parts[2])
        }

        fun from(version: BigDecimal): Version = from(version.toString())
    }
}

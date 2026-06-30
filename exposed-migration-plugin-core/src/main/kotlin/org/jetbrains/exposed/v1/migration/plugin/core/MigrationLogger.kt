package org.jetbrains.exposed.v1.migration.plugin.core

/**
 * Build-tool-agnostic logger SPI for [MigrationGenerator].
 *
 * Plugin layers should adapt their native logging facility to this interface:
 *  - Gradle:  wrap `org.gradle.api.logging.Logger`
 *  - Maven:   wrap `org.apache.maven.plugin.logging.Log`
 */
interface MigrationLogger {
    /** Logs a high-visibility message (Gradle `lifecycle`, Maven `info`). */
    fun lifecycle(message: String)

    /** Logs a debug-level message. Implementations may suppress when debug is disabled. */
    fun debug(message: String)

    /** Whether debug-level messages will actually be emitted. */
    val isDebugEnabled: Boolean

    companion object {
        /** Stdout-only logger. Intended for tests and ad-hoc invocations outside a build tool. */
        fun stdout(debug: Boolean = false): MigrationLogger = object : MigrationLogger {
            override fun lifecycle(message: String) = println(message)
            override fun debug(message: String) {
                if (debug) println(message)
            }
            override val isDebugEnabled: Boolean = debug
        }
    }
}

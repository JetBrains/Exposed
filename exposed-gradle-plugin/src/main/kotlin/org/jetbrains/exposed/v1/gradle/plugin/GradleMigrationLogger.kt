package org.jetbrains.exposed.v1.gradle.plugin

import org.gradle.api.logging.Logger
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.plugin.core.migration.MigrationLogger

/**
 * Adapts a Gradle [Logger] to the build-tool-agnostic logger SPI of `exposed-plugin-core`, for use by every work action
 * of this plugin.
 */
@OptIn(InternalApi::class)
internal class GradleMigrationLogger(
    private val logger: Logger,
    override val isDebugEnabled: Boolean,
) : MigrationLogger {
    override fun lifecycle(message: String) {
        logger.lifecycle(message)
    }

    override fun debug(message: String) {
        if (!isDebugEnabled) return
        logger.debug(message)
    }
}

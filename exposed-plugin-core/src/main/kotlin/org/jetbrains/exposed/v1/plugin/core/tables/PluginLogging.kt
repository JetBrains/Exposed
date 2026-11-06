package org.jetbrains.exposed.v1.plugin.core.tables

import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.plugin.core.migration.MigrationLogger

/** Logs [message] as a high-visibility warning, prefixed uniformly so that users can grep generation output for it. */
@OptIn(InternalApi::class)
internal fun MigrationLogger.warn(message: String) {
    lifecycle("Warning: $message")
}

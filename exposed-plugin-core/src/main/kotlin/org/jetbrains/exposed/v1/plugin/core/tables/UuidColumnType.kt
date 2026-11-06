package org.jetbrains.exposed.v1.plugin.core.tables

/**
 * The Exposed [org.jetbrains.exposed.v1.core.Table] method that returns a [org.jetbrains.exposed.v1.core.Column],
 * which a database `UUID` column should be mapped to when generating table definitions.
 */
enum class UuidColumnType {
    /**
     * Maps to `uuid()`, which stores [kotlin.uuid.Uuid] values.
     */
    KOTLIN,

    /**
     * Maps to `javaUUID()`, which stores [java.util.UUID] values.
     */
    JAVA,
}

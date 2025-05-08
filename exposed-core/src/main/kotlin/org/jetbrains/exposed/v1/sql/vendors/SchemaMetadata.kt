package org.jetbrains.exposed.v1.sql.vendors

/**
 * Represents metadata information about the current schema and its associated tables.
 */
data class SchemaMetadata(
    /** Name of the current schema. */
    val schemaName: String,
    /** Names of the existing tables in the current schema. */
    val tableNames: List<String>
)

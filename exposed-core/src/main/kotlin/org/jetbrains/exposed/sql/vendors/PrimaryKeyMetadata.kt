package org.jetbrains.exposed.sql.vendors

/**
 * Represents metadata information about a specific table's primary key.
 */
data class PrimaryKeyMetadata(
    /** Name of the primary key. */
    val name: String,
    /** Names of the primary key's columns. */
    val columnNames: List<String>
)

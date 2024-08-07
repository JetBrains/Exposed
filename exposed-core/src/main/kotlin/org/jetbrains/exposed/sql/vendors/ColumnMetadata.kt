package org.jetbrains.exposed.sql.vendors

/**
 * Represents metadata information about a specific column.
 */
data class ColumnMetadata(
    /** Name of the column. */
    val name: String,
    /**
     * Type of the column.
     *
     * @see java.sql.Types
     */
    val type: Int,
    /** Whether the column is nullable or not. */
    val nullable: Boolean,
    /** Optional size of the column. */
    val size: Int?,
    /** Optional amount of fractional digits allowed in the column. */
    val scale: Int?,
    /** Whether the column is auto-incremented. */
    val autoIncrement: Boolean,
    /** Default value of the column. */
    val defaultDbValue: String?,
)

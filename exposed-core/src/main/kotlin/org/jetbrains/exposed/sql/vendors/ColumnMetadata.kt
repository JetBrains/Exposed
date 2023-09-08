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
    /** Whether the column if nullable or not. */
    val nullable: Boolean,
    /** Optional size of the column. */
    val size: Int?,
    /** Is the column auto increment */
    val autoIncrement: Boolean,
    /** Default value */
    val defaultDbValue: String?,
)

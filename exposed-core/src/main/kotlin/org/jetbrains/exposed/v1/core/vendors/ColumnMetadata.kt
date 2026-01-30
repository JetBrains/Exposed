package org.jetbrains.exposed.v1.core.vendors

/**
 * Represents metadata information about a specific column.
 */
data class ColumnMetadata(
    /** Name of the column. */
    val name: String,
    /**
     * JDBC type of the column.
     *
     * @see java.sql.Types
     */
    val jdbcType: Int,
    /** SQL type of the column. */
    val sqlType: String,
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
    /** Optional comment on the column. */
    val comment: String? = null,
)

package org.jetbrains.exposed.sql

/**
 * Represents differences between a column definition and database metadata for the existing column.
 */
data class ColumnDiff(
    /** Whether the nullability of the existing column is correct. */
    val nullability: Boolean,
    /** Whether the existing column has a matching auto-increment sequence. */
    val autoInc: Boolean,
    /** Whether the default value of the existing column is correct. */
    val defaults: Boolean,
    /** Whether the existing column identifier matches and has the correct casing. */
    val caseSensitiveName: Boolean,
) {
    /** Returns `true` if there is a difference between the column definition and the existing column in the database. */
    fun hasDifferences() = this != NoneChanged

    companion object {
        /** A [ColumnDiff] with no differences. */
        val NoneChanged = ColumnDiff(
            nullability = false,
            autoInc = false,
            defaults = false,
            caseSensitiveName = false,
        )

        /** A [ColumnDiff] with differences for every matched property. */
        val AllChanged = ColumnDiff(
            nullability = true,
            autoInc = true,
            defaults = true,
            caseSensitiveName = true,
        )
    }
}

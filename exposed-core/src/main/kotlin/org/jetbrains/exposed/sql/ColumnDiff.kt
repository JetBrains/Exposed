package org.jetbrains.exposed.sql

/**
 * Represents differences between a column definition and database metadata for the existing column.
 */
data class ColumnDiff(
    /** Whether there is a mismatch between nullability of the existing column and the defined column. */
    val nullability: Boolean,
    /** Whether there is a mismatch between type of the existing column and the defined column. */
    val type: Boolean,
    /** Whether there is a mismatch between auto-increment status of the existing column and the defined column. */
    val autoInc: Boolean,
    /** Whether the default value of the existing column matches that of the defined column. */
    val defaults: Boolean,
    /** Whether the existing column identifier matches that of the defined column and has the correct casing. */
    val caseSensitiveName: Boolean,
    /** Whether the size and scale of the existing column, if applicable, match those of the defined column. */
    val sizeAndScale: Boolean,
) {
    /** Returns `true` if there is a difference between the column definition and the existing column in the database. */
    fun hasDifferences() = this != NoneChanged

    companion object {
        /** A [ColumnDiff] with no differences. */
        val NoneChanged = ColumnDiff(
            nullability = false,
            type = false,
            autoInc = false,
            defaults = false,
            caseSensitiveName = false,
            sizeAndScale = false,
        )

        /** A [ColumnDiff] with differences for every matched property. */
        val AllChanged = ColumnDiff(
            nullability = true,
            type = true,
            autoInc = true,
            defaults = true,
            caseSensitiveName = true,
            sizeAndScale = true,
        )
    }
}

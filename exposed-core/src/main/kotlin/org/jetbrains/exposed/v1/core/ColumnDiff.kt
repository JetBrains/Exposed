package org.jetbrains.exposed.v1.core

/**
 * Represents differences between a column definition and database metadata for the existing column.
 */
data class ColumnDiff(
    /** Whether there is a mismatch between the nullability of the existing column and that the defined column. */
    val nullability: Boolean,
    /** Whether there is a mismatch between the type of the existing column and that of the defined column. */
    val type: Boolean,
    /** Whether there is a mismatch between the auto-increment status of the existing column and that of the defined column. */
    val autoInc: Boolean,
    /** Whether there is a mismatch between the default value of the existing column and that of the defined column. */
    val defaults: Boolean,
    /**
     * Whether there is a mismatch between the identifier of the existing column and that of the defined column,
     * both for the string value itself and for the casing.
     */
    val caseSensitiveName: Boolean,
    /**
     * Whether there is a mismatch between the size and scale of the existing column, if applicable,
     * and those values of the defined column.
     */
    val sizeAndScale: Boolean,
    /** Whether there is a mismatch between the comment of the existing column and that of the defined column. */
    val comment: Boolean = false,
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
            comment = false,
        )

        /** A [ColumnDiff] with differences for every matched property. */
        val AllChanged = ColumnDiff(
            nullability = true,
            type = true,
            autoInc = true,
            defaults = true,
            caseSensitiveName = true,
            sizeAndScale = true,
            comment = true,
        )
    }
}

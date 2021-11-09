package org.jetbrains.exposed.sql

data class ColumnDiff(
    val nullability: Boolean,
    val autoInc: Boolean,
    val defaults: Boolean,
    val caseSensitiveName: Boolean,
) {

    fun hasDifferences() = this != NoneChanged

    companion object {
        val NoneChanged = ColumnDiff(
            nullability = false,
            autoInc = false,
            defaults = false,
            caseSensitiveName = false,
        )

        val AllChanged = ColumnDiff(
            nullability = true,
            autoInc = true,
            defaults = true,
            caseSensitiveName = true,
        )
    }
}

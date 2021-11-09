package org.jetbrains.exposed.sql

data class ColumnDiff(
    val nullability: Boolean,
    val autoInc: Boolean,
    val defaults: Boolean,
) {

    fun hasDifferences() = this != NoneChanged

    companion object {
        val NoneChanged = ColumnDiff(
            nullability = false,
            autoInc = false,
            defaults = false,
        )

        val AllChanged = ColumnDiff(
            nullability = true,
            autoInc = true,
            defaults = true,
        )
    }
}

package org.jetbrains.exposed.v1.core

/** Represents the SQL keywords for defining sort order in an `ORDER BY` clause. */
enum class SortOrder(val code: String) {
    /** The SQL keyword `ASC` for sorting records in ascending order. */
    ASC(code = "ASC"),

    /** The SQL keyword `DESC` for sorting records in descending order. */
    DESC(code = "DESC"),

    /**
     * The SQL keyword `ASC` for sorting records in ascending order, but modified with `NULLS FIRST` to position
     * SQL NULL values at the start.
     */
    ASC_NULLS_FIRST(code = "ASC NULLS FIRST"),

    /**
     * The SQL keyword `DESC` for sorting records in descending order, but modified with `NULLS FIRST` to position
     * SQL NULL values at the start preceding non-null records.
     */
    DESC_NULLS_FIRST(code = "DESC NULLS FIRST"),

    /**
     * The SQL keyword `ASC` for sorting records in ascending order, but modified with `NULLS LAST` to position
     * SQL NULL values at the end following non-null records.
     */
    ASC_NULLS_LAST(code = "ASC NULLS LAST"),

    /**
     * The SQL keyword `DESC` for sorting records in descending order, but modified with `NULLS LAST` to position
     * SQL NULL values at the end.
     */
    DESC_NULLS_LAST(code = "DESC NULLS LAST")
}

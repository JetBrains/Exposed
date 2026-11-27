package org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect

/**
 * Information specific for exactly how to interpret strings and codes reported by an SQLite database.
 */
internal object SQLiteSyntax : DialectSyntax {
    override val unboundedLength: Int
        get() = UNDECLARED_COLUMN_SIZE

    // The JDBC type of a column is only its type affinity (integer, real or text), which says too little to map on.
    // Fore example, `BIGINT` and `BOOLEAN` are both reported as an integer, and `BLOB` and `DATE` both as a string.
    override val usesJdbcTypeFallback: Boolean
        get() = false

    // Exposed creates a unique index as a plain index statement turned unique, so the name carries no `_unique` suffix.
    override val omitsUniqueSuffixInIndexNames: Boolean
        get() = true

    // The index that backs a primary key or a `UNIQUE` constraint is named `sqlite_autoindex_<table>_<n>`, whatever
    // the constraint itself is called. Exposed's migration utilities ignore every index whose name starts with `sqlite_`.
    override fun isInternalIndexName(name: String): Boolean = name.startsWith(INTERNAL_INDEX_PREFIX)

    private const val UNDECLARED_COLUMN_SIZE: Int = 2_000_000_000

    private const val INTERNAL_INDEX_PREFIX: String = "sqlite_"
}

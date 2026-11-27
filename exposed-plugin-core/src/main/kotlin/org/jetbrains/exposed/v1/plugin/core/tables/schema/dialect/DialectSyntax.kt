package org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect

import org.jetbrains.exposed.v1.core.ReferenceOption
import java.sql.DatabaseMetaData

/**
 * The part of dialect support that holds any information for exactly how to interpret reported strings and codes,
 * without any need for an actual database connection. Any defaults without overrides will fall back to what
 * standard JDBC metadata allows.
 */
internal interface DialectSyntax {
    /**
     * The size from which a character or binary column is considered unbounded when only JDBC metadata is available.
     * This may be necessary when drivers report a very large size, rather than none, for such columns.
     */
    val unboundedLength: Int
        get() = DEFAULT_UNBOUNDED_LENGTH

    /**
     * Whether a column whose declared type is unknown to the mapper may be mapped from its JDBC type instead.
     * This must be `false` for a database whose driver reports only a broad type class rather than the column's own
     * type (like SQLite's type affinity), so that such a column is reported as unsupported instead of being guessed.
     */
    val usesJdbcTypeFallback: Boolean
        get() = true

    /**
     * Whether Exposed creates a unique index under its default name without the `_unique` suffix on this database,
     * so that `<table>_<columns>` is a default name for a unique index as well as `<table>_<columns>_unique`.
     */
    val omitsUniqueSuffixInIndexNames: Boolean
        get() = false

    /**
     * Whether the comment of a column is generated as `.comment("...")`. This is `true` only for a database on which
     * Exposed both declares column comments and compares them during migrations, so that leaving a comment out of the
     * generated definition would make a later migration propose to remove it.
     */
    val generatesColumnComments: Boolean
        get() = false

    /**
     * Whether a column declared as `UUID` is generated with Exposed's `uuid()` or `javaUUID()`. This is `false` for a
     * database that has a `UUID` type of its own which Exposed does not declare for those functions (like MariaDB,
     * where they declare `BINARY(16)`), so that such a column is reported as unsupported instead.
     */
    val mapsUuidType: Boolean
        get() = true

    /**
     * Whether the index [name] is one that the database keeps for its own use, such as the index that backs a `UNIQUE`
     * constraint, and that Exposed's migration utilities ignore on this database.
     */
    fun isInternalIndexName(name: String): Boolean = false

    /** Maps a JDBC `importedKey*` rule code from a foreign key to a [ReferenceOption]. */
    fun referenceOption(jdbcRule: Int): ReferenceOption = when (jdbcRule) {
        DatabaseMetaData.importedKeyCascade -> ReferenceOption.CASCADE
        DatabaseMetaData.importedKeyRestrict -> ReferenceOption.RESTRICT
        DatabaseMetaData.importedKeySetNull -> ReferenceOption.SET_NULL
        DatabaseMetaData.importedKeySetDefault -> ReferenceOption.SET_DEFAULT
        else -> ReferenceOption.NO_ACTION
    }

    /**
     * Splits a declared one-dimensional array type into its element type and maximum cardinality,
     * or returns `null` when the type is not an array.
     */
    fun arrayElement(declaredType: String): ArrayElement? = null

    /**
     * The values of a declared enumeration type in order, or `null` when the type does not have its values
     * referenced in its definition.
     */
    fun enumerationValues(declaredType: String): List<String>? = null

    /**
     * The sequence that a column [defaultValue] draws its values from, or `null` when the default is not a sequence
     * reference. The names are returned as reported by the database.
     */
    fun sequenceInDefault(defaultValue: String): SequenceReference? = null

    /**
     * Whether [name] is what the database itself named an unnamed constraint on [columnNames] of [tableName], rather
     * than a name the user chose.
     */
    fun isGeneratedConstraintName(name: String, tableName: String, columnNames: List<String>): Boolean = false

    /**
     * Returns a column default, as reported by the database and optionally normalized as needed.
     */
    fun normalizeDefault(reported: String): String = reported

    companion object {
        const val DEFAULT_UNBOUNDED_LENGTH: Int = 1_000_000_000
    }
}

/**
 * A reported array type split into its: SQL type, maximum cardinality (when the type declares one), and the
 * number of dimensions.
 */
internal data class ArrayElement(val sqlType: String, val maximumCardinality: Int?, val dimensions: Int = 1)

/** A sequence referenced by a column default, with the name parts exactly as the database reported. */
internal data class SequenceReference(val schema: String?, val name: String)

/** The dialect-neutral baseline, which provides nothing beyond how standard JDBC metadata is interpreted. */
internal object JdbcSyntax : DialectSyntax

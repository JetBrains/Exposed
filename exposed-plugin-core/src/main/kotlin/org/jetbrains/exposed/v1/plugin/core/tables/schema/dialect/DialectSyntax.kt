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

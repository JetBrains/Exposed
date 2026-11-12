package org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect

import org.jetbrains.exposed.v1.core.statements.api.IdentifierManagerApi
import org.jetbrains.exposed.v1.core.vendors.DatabaseDialect
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import java.sql.Connection

/**
 * The part of dialect support responsible for obtaining metadata from a database that cannot be obtained from standard
 * JDBC metadata, and which must instead be queried through the database's own catalog view.
 */
internal interface DialectSchemaSupport {
    /** Interprets the strings and codes that this support reads. */
    val syntax: DialectSyntax

    /**
     * Whether the database creates an index on the columns of every foreign key by itself,
     * which must not be generated.
     */
    val createsIndexForForeignKeys: Boolean

    /** Whether the schema [name], as reported by the database, holds unneeded system objects rather than user tables. */
    fun isSystemSchema(name: String): Boolean

    /**
     * Returns the declared type of every column of a table, keyed by raw column name, in the normalized form that
     * Exposed's migration utilities compare a column's `sqlType()` against. Mapping on it keeps generated
     * definitions free of future migration differences.
     *
     * `SchemaReader` maps on these values whenever Exposed's own metadata helper reports no SQL type, which is the
     * case for every dialect but H2. On H2 that helper consumes the values itself and normalizes them, so
     * [H2SchemaSupport] may return them as declared; any other implementation must return them already normalized.
     *
     * Returns an empty map when the dialect offers no better source than the JDBC type of each column.
     */
    fun declaredColumnTypes(connection: Connection, schema: String, tableName: String): Map<String, String>

    /**
     * Returns the generation expression of every column of a table whose value is always computed by the database,
     * keyed by raw column name. Columns with a plain `DEFAULT`, including default expressions such as `CURRENT_TIMESTAMP`,
     * and identity columns are never included.
     */
    fun generatedColumns(connection: Connection, schema: String, tableName: String): Map<String, String>

    /**
     * Returns the name of every `UNIQUE` constraint of a table, keyed by the raw name of the index that backs it.
     */
    fun uniqueConstraintNames(connection: Connection, schema: String, tableName: String): Map<String, String>

    /**
     * Returns the parameters of the sequence [sequenceName] in [schema] that `autoIncrement(sequenceName)`
     * would not reproduce, or `null` when it would.
     *
     * Returns `null` when the database offers no view of its sequences' parameters.
     */
    fun sequenceParameters(connection: Connection, schema: String, sequenceName: String, sequenceMaxValue: Long): SequenceParameters?

    /**
     * Returns the sequences that columns of a table own, keyed by raw column name with the raw sequence name as value,
     * for databases whose auto-increment columns are backed by a sequence of their own (like PostgreSQL's `SERIAL`). Such
     * a column is an ordinary auto-increment column, and its sequence is neither generated nor reported as unused.
     *
     * Returns an empty map when the database has no such columns.
     */
    fun ownedSequences(connection: Connection, schema: String, tableName: String): Map<String, String>

    /**
     * Returns the enumeration types declared in [schema], keyed by the type name as [declaredColumnTypes] reports it
     * for a column of that type. The values are always in declaration order for databases where an enumeration is a named
     * type (like PostgreSQL's `CREATE TYPE ... AS ENUM`) rather than a literal in the column's type.
     *
     * Returns an empty map when the database provides the values directly in the column type itself (like H2).
     */
    fun enumerationTypes(connection: Connection, schema: String): Map<String, List<String>>

    companion object {
        fun forDialect(
            dialect: DatabaseDialect,
            identifierManager: IdentifierManagerApi,
        ): DialectSchemaSupport = when (dialect) {
            is H2Dialect -> H2SchemaSupport(identifierManager)
            is PostgreSQLDialect -> PostgreSqlSchemaSupport
            else -> JdbcSchemaSupport
        }
    }
}

/**
 * The parameters of a sequence that differ from what `autoIncrement(sequenceName)` creates. Each will be `null`
 * when the database value is the one that the call implies. Rendered as the arguments of `Sequence(name, ...)`.
 */
internal data class SequenceParameters(
    val startWith: Long?,
    val incrementBy: Long?,
    val minValue: Long?,
    val maxValue: Long?,
    val cycle: Boolean?,
    val cache: Long?,
) {
    /** Whether every parameter is the implied one, so that `autoIncrement(sequenceName)` reproduces the sequence. */
    val isDefault: Boolean
        get() = startWith == null && incrementBy == null && minValue == null && maxValue == null && cycle == null && cache == null
}

/** The dialect-neutral baseline, which provides nothing beyond the available standard JDBC metadata. */
internal object JdbcSchemaSupport : DialectSchemaSupport {
    override val syntax: DialectSyntax = JdbcSyntax

    override val createsIndexForForeignKeys: Boolean = false

    override fun isSystemSchema(name: String): Boolean = false

    override fun declaredColumnTypes(
        connection: Connection,
        schema: String,
        tableName: String
    ): Map<String, String> = emptyMap()

    override fun generatedColumns(
        connection: Connection,
        schema: String,
        tableName: String
    ): Map<String, String> = emptyMap()

    override fun uniqueConstraintNames(
        connection: Connection,
        schema: String,
        tableName: String
    ): Map<String, String> = emptyMap()

    override fun sequenceParameters(
        connection: Connection,
        schema: String,
        sequenceName: String,
        sequenceMaxValue: Long
    ): SequenceParameters? = null

    override fun ownedSequences(
        connection: Connection,
        schema: String,
        tableName: String
    ): Map<String, String> = emptyMap()

    override fun enumerationTypes(connection: Connection, schema: String): Map<String, List<String>> = emptyMap()
}

package org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect

import org.jetbrains.exposed.v1.core.statements.api.IdentifierManagerApi
import org.jetbrains.exposed.v1.core.vendors.DatabaseDialect
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.core.vendors.MariaDBDialect
import org.jetbrains.exposed.v1.core.vendors.MysqlDialect
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.core.vendors.SQLiteDialect
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

    /**
     * Whether the database groups its tables into schemas. When `false`, the tables of the connection form a single
     * unnamed namespace and no schema can be configured for a specific search scope.
     */
    val supportsSchemas: Boolean

    /**
     * Whether a schema of this database is what JDBC calls a catalog (like a MySQL database). When `true`, the name of
     * a schema is read from and passed to the catalog argument of `DatabaseMetaData`, and the schema argument is unused.
     */
    val schemasAreCatalogs: Boolean

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

    /**
     * Returns one row per column of every foreign key of a table, for a database whose driver does not report its
     * foreign keys reliably through `DatabaseMetaData.getImportedKeys`.
     *
     * Should return `null` when the standard JDBC metadata is to be read instead.
     */
    fun foreignKeys(connection: Connection, schema: String, tableName: String): List<ReportedForeignKeyColumn>?

    /**
     * Returns corrections to what the driver reports for columns of a table, keyed by raw column name, for a database
     * whose driver metadata does not tell the whole truth about a column.
     *
     * Should return an empty map when the driver's column metadata can be used as reported.
     */
    fun columnOverrides(connection: Connection, schema: String, tableName: String): Map<String, ColumnOverride>

    /**
     * Returns the default of every column of a table, keyed by raw column name, for a database whose driver reports
     * defaults in a form that cannot be interpreted without knowing more about the column (like MySQL, which reports
     * a string default without its quotes). A value is in the form that the driver of other databases reports: a
     * string literal in single quotes, anything else as written. A column without a default has a `null` value.
     *
     * Should return `null` when the default that the driver reports for each column is to be used instead.
     */
    fun columnDefaults(connection: Connection, schema: String, tableName: String): Map<String, String?>?

    companion object {
        fun forDialect(
            dialect: DatabaseDialect,
            identifierManager: IdentifierManagerApi,
        ): DialectSchemaSupport = when (dialect) {
            is H2Dialect -> H2SchemaSupport(identifierManager)
            is PostgreSQLDialect -> PostgreSqlSchemaSupport
            is SQLiteDialect -> SQLiteSchemaSupport
            is MariaDBDialect -> JdbcSchemaSupport
            is MysqlDialect -> MySqlSchemaSupport
            else -> JdbcSchemaSupport
        }
    }
}

/**
 * One column of a foreign key, with every name as reported by the database.
 */
internal data class ReportedForeignKeyColumn(
    /** What identifies the key among the keys of its table, so that the rows of a composite key can be grouped. */
    val groupKey: String,

    /** The name of the constraint, or an empty string when the database reports none. */
    val name: String,

    /** The 1-based position of the column within the key. */
    val position: Int,
    val columnName: String,
    val targetSchema: String,
    val targetTable: String,
    val targetColumnName: String,

    /** The JDBC `importedKey*` code of the delete rule. */
    val deleteRule: Int,

    /** The JDBC `importedKey*` code of the update rule. */
    val updateRule: Int,
)

/**
 * A correction to the driver's metadata of a single column. A `null` value leaves what the driver reported.
 */
internal data class ColumnOverride(
    val autoIncrement: Boolean? = null,
    val nullable: Boolean? = null,

    /** Logged when [autoIncrement] changes what the driver reported, to say why. */
    val autoIncrementNote: String? = null,

    /** Logged as a warning when [nullable] changes what the driver reported, to say why. */
    val nullableWarning: String? = null,

    /** Something about the column that the generated definition cannot express. */
    val unsupportedNote: String? = null,
)

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

    override val supportsSchemas: Boolean = true

    override val schemasAreCatalogs: Boolean = false

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

    override fun foreignKeys(connection: Connection, schema: String, tableName: String): List<ReportedForeignKeyColumn>? = null

    override fun columnDefaults(connection: Connection, schema: String, tableName: String): Map<String, String?>? = null

    override fun columnOverrides(
        connection: Connection,
        schema: String,
        tableName: String
    ): Map<String, ColumnOverride> = emptyMap()
}

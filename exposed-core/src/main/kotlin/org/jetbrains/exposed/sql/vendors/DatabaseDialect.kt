package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager

/**
 * Common interface for all database dialects.
 */
@Suppress("TooManyFunctions")
interface DatabaseDialect {
    /** Name of this dialect. */
    val name: String

    /** Data type provider of this dialect. */
    val dataTypeProvider: DataTypeProvider

    /** Function provider of this dialect. */
    val functionProvider: FunctionProvider

    /** Returns `true` if the dialect supports the `IF EXISTS`/`IF NOT EXISTS` option when creating, altering or dropping objects, `false` otherwise. */
    val supportsIfNotExists: Boolean get() = true

    /** Returns `true` if the dialect supports the creation of sequences, `false` otherwise. */
    val supportsCreateSequence: Boolean get() = true

    /** Returns `true` if the dialect requires the use of a sequence to create an auto-increment column, `false` otherwise. */
    val needsSequenceToAutoInc: Boolean get() = false

    /** Returns the default reference option for the dialect. */
    val defaultReferenceOption: ReferenceOption get() = ReferenceOption.RESTRICT

    /** Returns `true` if the dialect requires the use of quotes when using symbols in object names, `false` otherwise. */
    val needsQuotesWhenSymbolsInNames: Boolean get() = true

    /** Returns `true` if the dialect supports returning multiple generated keys as a result of an insert operation, `false` otherwise. */
    val supportsMultipleGeneratedKeys: Boolean

    /** Returns `true` if the dialect supports returning generated keys obtained from a sequence. */
    val supportsSequenceAsGeneratedKeys: Boolean get() = supportsCreateSequence

    /** Returns `true` if the dialect supports only returning generated keys that are identity columns. */
    val supportsOnlyIdentifiersInGeneratedKeys: Boolean get() = false

    /** Returns `true` if the dialect supports an upsert operation returning an affected-row value of 0, 1, or 2. */
    val supportsTernaryAffectedRowValues: Boolean get() = false

    /** Returns`true` if the dialect supports schema creation. */
    val supportsCreateSchema: Boolean get() = true

    /** Returns `true` if the dialect supports subqueries within a UNION/EXCEPT/INTERSECT statement. */
    val supportsSubqueryUnions: Boolean get() = false

    /** Returns `true` if the dialect provides a special dummy DUAL table, accessible by all users. */
    val supportsDualTableConcept: Boolean get() = false

    /** Returns `true` if the dialect provides options to configure how nulls are sorted compared to non-null values. */
    val supportsOrderByNullsFirstLast: Boolean get() = false

    /** Returns `true` if the dialect supports window function definitions with GROUPS mode in frame clause */
    val supportsWindowFrameGroupsMode: Boolean get() = false

    /** Returns `true` if the dialect supports using the ON UPDATE clause with a foreign key constraint. */
    val supportsOnUpdate: Boolean get() = true

    /** Returns `true` if the dialect supports the SET DEFAULT action as part of a foreign key constraint clause. */
    val supportsSetDefaultReferenceOption: Boolean get() = true

    /** Returns `true` if the dialect supports the RESTRICT action as part of a foreign key constraint clause. */
    val supportsRestrictReferenceOption: Boolean get() = true

    /** Returns a mapping of dialect-specific characters to be escaped when used alongside the LIKE operator. */
    val likePatternSpecialChars: Map<Char, Char?> get() = defaultLikePatternSpecialChars

    /** Returns true if autoCommit should be enabled to create/drop a database. */
    val requiresAutoCommitOnCreateDrop: Boolean get() = false

    /** Returns the allowed maximum sequence value for a dialect, as a [Long]. */
    val sequenceMaxValue: Long get() = Long.MAX_VALUE

    /** Returns the name of the current database. */
    fun getDatabase(): String

    /**
     * Returns a list with the names of all the defined tables in the current database schema.
     * The names will be returned with schema prefixes if the database supports it.
     */
    fun allTablesNames(): List<String>

    /**
     * Returns a list with the names of all the tables in all database schemas.
     * The names will be returned with schema prefixes, if the database supports it, and non-user defined tables,
     * like system information table names, will be included.
     */
    fun allTablesNamesInAllSchemas(): List<String>

    /** Checks if the specified table exists in the database. */
    fun tableExists(table: Table): Boolean

    /** Checks if the specified schema exists. */
    fun schemaExists(schema: Schema): Boolean

    /** Returns whether the specified sequence exists. */
    fun sequenceExists(sequence: Sequence): Boolean

    fun checkTableMapping(table: Table): Boolean = true

    /** Returns a map with the column metadata of all the defined columns in each of the specified [tables]. */
    fun tableColumns(vararg tables: Table): Map<Table, List<ColumnMetadata>> = emptyMap()

    /** Returns a map with the foreign key constraints of all the defined columns sets in each of the specified [tables]. */
    fun columnConstraints(
        vararg tables: Table
    ): Map<Pair<Table, LinkedHashSet<Column<*>>>, List<ForeignKeyConstraint>> = emptyMap()

    /** Returns a map with all the defined indices in each of the specified [tables]. */
    fun existingIndices(vararg tables: Table): Map<Table, List<Index>> = emptyMap()

    /** Returns a map with the primary key metadata in each of the specified [tables]. */
    fun existingPrimaryKeys(vararg tables: Table): Map<Table, PrimaryKeyMetadata?> = emptyMap()

    /**
     * Returns a map with all the defined sequences that hold a relation to the specified [tables] in the database.
     *
     * **Note** PostgreSQL is currently the only database that maps relational dependencies for sequences created when
     * a SERIAL column is registered to an `IdTable`. Using this method with any other database returns an empty map.
     *
     * Any sequence created using the CREATE SEQUENCE command will be ignored
     * as it is not necessarily bound to any particular table. Sequences that are used in a table via triggers will also
     * not be returned.
     */
    fun existingSequences(vararg tables: Table): Map<Table, List<Sequence>> = emptyMap()

    /** Returns a list of the names of all sequences in the database. */
    fun sequences(): List<String>

    /** Returns `true` if the dialect supports `SELECT FOR UPDATE` statements, `false` otherwise. */
    fun supportsSelectForUpdate(): Boolean

    /** Returns `true` if the specified [e] is allowed as a default column value in the dialect, `false` otherwise. */
    fun isAllowedAsColumnDefault(e: Expression<*>): Boolean = e is LiteralOp<*>

    /** Returns the catalog name of the connection of the specified [transaction]. */
    fun catalog(transaction: Transaction): String = transaction.connection.catalog

    /** Clears any cached values. */
    fun resetCaches()

    /** Clears any cached values including schema names. */
    fun resetSchemaCaches()

    // Specific SQL statements

    /** Returns the SQL statement that creates the specified [index]. */
    fun createIndex(index: Index): String

    /** Returns the SQL statement that drops the specified [indexName] from the specified [tableName]. */
    fun dropIndex(tableName: String, indexName: String, isUnique: Boolean, isPartialOrFunctional: Boolean): String

    /** Returns the SQL statement that modifies the specified [column]. */
    fun modifyColumn(column: Column<*>, columnDiff: ColumnDiff): List<String>

    /** Returns the SQL statement that adds a primary key specified [pkName] to an existing [table]. */
    fun addPrimaryKey(table: Table, pkName: String?, vararg pkColumns: Column<*>): String

    /** Returns the SQL statement that creates a database with the specified [name]. */
    fun createDatabase(name: String) = "CREATE DATABASE IF NOT EXISTS ${name.inProperCase()}"

    /** Returns the SQL query that retrieves a set of existing databases. */
    fun listDatabases(): String = "SHOW DATABASES"

    /** Returns the SQL statement that drops the database with the specified [name]. */
    fun dropDatabase(name: String) = "DROP DATABASE IF EXISTS ${name.inProperCase()}"

    /** Returns the SQL statement that sets the current schema to the specified [schema]. */
    fun setSchema(schema: Schema): String = "SET SCHEMA ${schema.identifier}"

    /** Returns the SQL statement that creates the specified [schema]. */
    fun createSchema(schema: Schema): String = buildString {
        append("CREATE SCHEMA IF NOT EXISTS ")
        append(schema.identifier)
        appendIfNotNull(" AUTHORIZATION ", schema.authorization)
    }

    /** Returns the SQL statement that drops the specified [schema], as well as all its objects if [cascade] is `true`. */
    fun dropSchema(schema: Schema, cascade: Boolean): String = buildString {
        append("DROP SCHEMA IF EXISTS ", schema.identifier)

        if (cascade) {
            append(" CASCADE")
        }
    }

    @Deprecated(
        message = "This function will be removed in future releases.",
        level = DeprecationLevel.WARNING
    )
    fun resolveRefOptionFromJdbc(refOption: Int): ReferenceOption {
        @OptIn(InternalApi::class)
        return TransactionManager.current().db.metadata { resolveReferenceOption(refOption.toString())!! }
    }

    companion object {
        private val defaultLikePatternSpecialChars = mapOf('%' to null, '_' to null)
    }
}

private val explicitDialect = ThreadLocal<DatabaseDialect?>()

internal fun <T> withDialect(dialect: DatabaseDialect, body: () -> T): T {
    return try {
        explicitDialect.set(dialect)
        body()
    } finally {
        explicitDialect.set(null)
    }
}

/** Returns the dialect used in the current transaction, may throw an exception if there is no current transaction. */
val currentDialect: DatabaseDialect get() = explicitDialect.get() ?: TransactionManager.current().db.dialect

internal val currentDialectIfAvailable: DatabaseDialect?
    get() = if (TransactionManager.isInitialized() && TransactionManager.currentOrNull() != null) {
        currentDialect
    } else {
        null
    }

internal fun String.inProperCase(): String =
    TransactionManager.currentOrNull()?.db?.identifierManager?.inProperCase(this@inProperCase) ?: this

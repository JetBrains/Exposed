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

    /** Returns`true` if the dialect supports returning generated keys obtained from a sequence. */
    val supportsSequenceAsGeneratedKeys: Boolean get() = supportsCreateSequence
    val supportsOnlyIdentifiersInGeneratedKeys: Boolean get() = false

    /** Returns `true` if the dialect supports an upsert operation returning an affected-row value of 0, 1, or 2. */
    val supportsTernaryAffectedRowValues: Boolean get() = false

    /** Returns`true` if the dialect supports schema creation. */
    val supportsCreateSchema: Boolean get() = true

    /** Returns `true` if the dialect supports subqueries within a UNION/EXCEPT/INTERSECT statement */
    val supportsSubqueryUnions: Boolean get() = false

    val supportsDualTableConcept: Boolean get() = false

    val supportsOrderByNullsFirstLast: Boolean get() = false

    /** Returns `true` if the dialect supports window function definitions with GROUPS mode in frame clause */
    val supportsWindowFrameGroupsMode: Boolean get() = false

    val likePatternSpecialChars: Map<Char, Char?> get() = defaultLikePatternSpecialChars

    /** Returns true if autoCommit should be enabled to create/drop database */
    val requiresAutoCommitOnCreateDrop: Boolean get() = false

    /** Returns the name of the current database. */
    fun getDatabase(): String

    /** Returns a list with the names of all the defined tables. */
    fun allTablesNames(): List<String>

    /** Checks if the specified table exists in the database. */
    fun tableExists(table: Table): Boolean

    /** Checks if the specified schema exists. */
    fun schemaExists(schema: Schema): Boolean

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

    /** Returns the SQL command that creates the specified [index]. */
    fun createIndex(index: Index): String

    /** Returns the SQL command that drops the specified [indexName] from the specified [tableName]. */
    fun dropIndex(tableName: String, indexName: String, isUnique: Boolean, isPartialOrFunctional: Boolean): String

    /** Returns the SQL command that modifies the specified [column]. */
    fun modifyColumn(column: Column<*>, columnDiff: ColumnDiff): List<String>

    /** Returns the SQL command that adds a primary key specified [pkName] to an existing [table]. */
    fun addPrimaryKey(table: Table, pkName: String?, vararg pkColumns: Column<*>): String

    fun createDatabase(name: String) = "CREATE DATABASE IF NOT EXISTS ${name.inProperCase()}"

    fun listDatabases(): String = "SHOW DATABASES"

    fun dropDatabase(name: String) = "DROP DATABASE IF EXISTS ${name.inProperCase()}"

    fun setSchema(schema: Schema): String = "SET SCHEMA ${schema.identifier}"

    fun createSchema(schema: Schema): String = buildString {
        append("CREATE SCHEMA IF NOT EXISTS ")
        append(schema.identifier)
        appendIfNotNull(" AUTHORIZATION ", schema.authorization)
    }

    fun dropSchema(schema: Schema, cascade: Boolean): String = buildString {
        append("DROP SCHEMA IF EXISTS ", schema.identifier)

        if (cascade) {
            append(" CASCADE")
        }
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

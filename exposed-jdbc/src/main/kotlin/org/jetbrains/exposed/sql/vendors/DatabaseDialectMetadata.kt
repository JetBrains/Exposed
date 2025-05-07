package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Common interface for all database dialect metadata.
 */
// TODO document that class name was changed from DatabaseDialect to DatabaseDialectMetadata for those who extend it
abstract class DatabaseDialectMetadata {
    private var _allTableNames: Map<String, List<String>>? = null

    private var _allSchemaNames: List<String>? = null

    /** Returns `true` if the database supports the `LIMIT` clause with update and delete statements. */
    open fun supportsLimitWithUpdateOrDelete(): Boolean = true

    /** Returns a list with the names of all the defined tables in the current schema. */
    val allTablesNames: List<String>
        get() {
            val connection = TransactionManager.current().connection
            return connection.metadata { tableNamesByCurrentSchema(getAllTableNamesCache()).tableNames }
        }

    protected fun getAllTableNamesCache(): Map<String, List<String>> {
        if (_allTableNames == null) {
            _allTableNames = TransactionManager.current().connection.metadata { tableNames }
        }
        return _allTableNames!!
    }

    private fun getAllSchemaNamesCache(): List<String> {
        if (_allSchemaNames == null) {
            _allSchemaNames = TransactionManager.current().connection.metadata { schemaNames }
        }
        return _allSchemaNames!!
    }

    /** Returns the name of the current database. */
    fun getDatabase(): String = catalog(TransactionManager.current())

    /** Returns the catalog name of the connection of the specified [transaction]. */
    fun catalog(transaction: JdbcTransaction): String = transaction.connection.catalog

    /**
     * Returns a list with the names of all the defined tables in the current database schema.
     * The names will be returned with schema prefixes if the database supports it.
     */
    fun allTablesNames(): List<String> {
        return TransactionManager.current().connection.metadata {
            tableNamesByCurrentSchema(null).tableNames
        }
    }

    /**
     * Returns a list with the names of all the tables in all database schemas.
     * The names will be returned with schema prefixes, if the database supports it, and non-user defined tables,
     * like system information table names, will be included.
     */
    fun allTablesNamesInAllSchemas(): List<String> {
        return getAllSchemaNamesCache().flatMap { schema ->
            getAllTableNamesCache().getValue(schema)
        }
    }

    /** Checks if the specified table exists in the database. */
    fun tableExists(table: Table): Boolean {
        return table.schemaName?.let { schema ->
            getAllTableNamesCache().getValue(schema.inProperCase()).any {
                it == table.nameInDatabaseCase()
            }
        } ?: run {
            val (schema, allTables) = TransactionManager.current().connection.metadata {
                tableNamesByCurrentSchema(getAllTableNamesCache())
            }
            allTables.any {
                it.metadataMatchesTable(schema, table)
            }
        }
    }

    protected open fun String.metadataMatchesTable(schema: String, table: Table): Boolean {
        return when {
            schema.isEmpty() -> this == table.nameInDatabaseCaseUnquoted()
            else -> {
                @OptIn(InternalApi::class)
                val sanitizedTableName = table.tableNameWithoutSchemeSanitized
                val nameInDb = "$schema.$sanitizedTableName".inProperCase()
                this == nameInDb
            }
        }
    }

    /** Checks if the specified schema exists. */
    fun schemaExists(schema: Schema): Boolean {
        val allSchemas = getAllSchemaNamesCache()
        return allSchemas.any { it == schema.identifier.inProperCase() }
    }

    /** Returns whether the specified sequence exists. */
    fun sequenceExists(sequence: Sequence): Boolean {
        return sequences().any { it == sequence.identifier.inProperCase() }
    }

    /** Returns a map with the column metadata of all the defined columns in each of the specified [tables]. */
    fun tableColumns(vararg tables: Table): Map<Table, List<ColumnMetadata>> {
        return TransactionManager.current().connection.metadata { columns(*tables) }
    }

    protected val columnConstraintsCache: MutableMap<String, Collection<ForeignKeyConstraint>> = ConcurrentHashMap()

    protected open fun fillConstraintCacheForTables(tables: List<Table>) {
        val tx = TransactionManager.current()
        columnConstraintsCache.putAll(tx.db.metadata { tableConstraints(tables) })
    }

    /** Returns a map with the foreign key constraints of all the defined columns sets in each of the specified [tables]. */
    fun columnConstraints(
        vararg tables: Table
    ): Map<Pair<Table, LinkedHashSet<Column<*>>>, List<ForeignKeyConstraint>> {
        val constraints = HashMap<Pair<Table, LinkedHashSet<Column<*>>>, MutableList<ForeignKeyConstraint>>()

        val tablesToLoad = tables.filter { !columnConstraintsCache.containsKey(it.nameInDatabaseCaseUnquoted()) }

        fillConstraintCacheForTables(tablesToLoad)
        tables.forEach { table ->
            columnConstraintsCache[table.nameInDatabaseCaseUnquoted()].orEmpty().forEach {
                constraints.getOrPut(table to it.from) { arrayListOf() }.add(it)
            }
        }
        return constraints
    }

    /** Returns a map with all the defined indices in each of the specified [tables]. */
    open fun existingIndices(vararg tables: Table): Map<Table, List<Index>> {
        return TransactionManager.current().db.metadata { existingIndices(*tables) }
    }

    /** Returns a map with all the defined check constraints in each of the specified [tables]. */
    fun existingCheckConstraints(vararg tables: Table): Map<Table, List<CheckConstraint>> {
        return TransactionManager.current().db.metadata { existingCheckConstraints(*tables) }
    }

    /** Returns a map with the primary key metadata in each of the specified [tables]. */
    fun existingPrimaryKeys(vararg tables: Table): Map<Table, PrimaryKeyMetadata?> {
        return TransactionManager.current().db.metadata { existingPrimaryKeys(*tables) }
    }

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
    fun existingSequences(vararg tables: Table): Map<Table, List<Sequence>> {
        return TransactionManager.current().db.metadata { existingSequences(*tables) }
    }

    /** Returns a list of the names of all sequences in the database. */
    fun sequences(): List<String> {
        return TransactionManager.current().db.metadata { sequences() }
    }

    /** Clears any cached values. */
    fun resetCaches() {
        _allTableNames = null
        columnConstraintsCache.clear()
        TransactionManager.current().db.metadata { cleanCache() }
    }

    /** Clears any cached values including schema names. */
    fun resetSchemaCaches() {
        _allSchemaNames = null
        resetCaches()
    }
}

private val explicitDialect = ThreadLocal<DatabaseDialectMetadata?>()

/** Returns the dialect used in the current transaction, may throw an exception if there is no current transaction. */
val currentDialectMetadata: DatabaseDialectMetadata
    get() = explicitDialect.get() ?: TransactionManager.current().db.dialectMetadata

internal fun String.inProperCase(): String =
    TransactionManager.currentOrNull()?.db?.identifierManager?.inProperCase(this@inProperCase) ?: this

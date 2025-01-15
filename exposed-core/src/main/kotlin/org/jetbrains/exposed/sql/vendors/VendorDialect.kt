package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Function
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Base implementation of a vendor dialect
 */
abstract class VendorDialect(
    override val name: String,
    override val dataTypeProvider: DataTypeProvider,
    override val functionProvider: FunctionProvider
) : DatabaseDialect {

    protected val identifierManager
        get() = TransactionManager.current().db.identifierManager

    @Suppress("UnnecessaryAbstractClass")
    abstract class DialectNameProvider(val dialectName: String)

    /* Cached values */
    private var _allTableNames: Map<String, List<String>>? = null
    private var _allSchemaNames: List<String>? = null

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

    override val supportsMultipleGeneratedKeys: Boolean = true

    override fun getDatabase(): String = catalog(TransactionManager.current())

    /**
     * Returns a list with the names of all the defined tables in the current database schema.
     * The names will be returned with schema prefixes if the database supports it.
     *
     * **Note:** This method always re-reads data from the database. Using `allTablesNames` field is
     * the preferred way to avoid unnecessary metadata queries.
     */
    override fun allTablesNames(): List<String> = TransactionManager.current().connection.metadata {
        tableNamesByCurrentSchema(null).tableNames
    }

    /**
     * Returns a list with the names of all the tables in all database schemas.
     * The names will be returned with schema prefixes, if the database supports it, and non-user defined tables,
     * like system information table names, will be included.
     */
    override fun allTablesNamesInAllSchemas(): List<String> = getAllSchemaNamesCache().flatMap { schema ->
        getAllTableNamesCache().getValue(schema)
    }

    override fun tableExists(table: Table): Boolean {
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
                val sanitizedTableName = table.tableNameWithoutSchemeSanitized
                val nameInDb = "$schema.$sanitizedTableName".inProperCase()
                this == nameInDb
            }
        }
    }

    override fun schemaExists(schema: Schema): Boolean {
        val allSchemas = getAllSchemaNamesCache()
        return allSchemas.any { it == schema.identifier.inProperCase() }
    }

    override fun sequenceExists(sequence: Sequence): Boolean {
        return sequences().any { it == sequence.identifier.inProperCase() }
    }

    override fun tableColumns(vararg tables: Table): Map<Table, List<ColumnMetadata>> =
        TransactionManager.current().connection.metadata { columns(*tables) }

    override fun columnConstraints(
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

    override fun existingIndices(vararg tables: Table): Map<Table, List<Index>> =
        TransactionManager.current().db.metadata { existingIndices(*tables) }

    override fun existingPrimaryKeys(vararg tables: Table): Map<Table, PrimaryKeyMetadata?> =
        TransactionManager.current().db.metadata { existingPrimaryKeys(*tables) }

    override fun existingSequences(vararg tables: Table): Map<Table, List<Sequence>> =
        TransactionManager.current().db.metadata { existingSequences(*tables) }

    override fun sequences(): List<String> =
        TransactionManager.current().db.metadata { sequences() }

    private val supportsSelectForUpdate: Boolean by lazy {
        TransactionManager.current().db.metadata { supportsSelectForUpdate }
    }

    override fun supportsSelectForUpdate(): Boolean = supportsSelectForUpdate

    protected fun String.quoteIdentifierWhenWrongCaseOrNecessary(tr: Transaction): String =
        tr.db.identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(this)

    protected val columnConstraintsCache: MutableMap<String, Collection<ForeignKeyConstraint>> = ConcurrentHashMap()

    protected open fun fillConstraintCacheForTables(tables: List<Table>): Unit =
        columnConstraintsCache.putAll(TransactionManager.current().db.metadata { tableConstraints(tables) })

    override fun resetCaches() {
        _allTableNames = null
        columnConstraintsCache.clear()
        TransactionManager.current().db.metadata { cleanCache() }
    }

    override fun resetSchemaCaches() {
        _allSchemaNames = null
        resetCaches()
    }

    fun filterCondition(index: Index): String? {
        return index.filterCondition?.let {
            when (currentDialect) {
                is PostgreSQLDialect, is SQLServerDialect, is SQLiteDialect -> {
                    QueryBuilder(false)
                        .append(" WHERE ").append(it)
                        .toString()
                }

                else -> {
                    exposedLogger.warn("Index creation with a filter condition is not supported in ${currentDialect.name}")
                    return null
                }
            }
        } ?: ""
    }

    private fun indexFunctionToString(function: Function<*>): String {
        val baseString = function.toString()
        return when (currentDialect) {
            // SQLite & Oracle do not support "." operator (with table prefix) in index expressions
            is SQLiteDialect, is OracleDialect -> baseString.replace(Regex("""^*[^( ]*\."""), "")
            is MysqlDialect -> if (baseString.first() != '(') "($baseString)" else baseString
            else -> baseString
        }
    }

    /**
     * Uniqueness might be required for foreign key constraints.
     *
     * In PostgreSQL (https://www.postgresql.org/docs/current/indexes-unique.html), UNIQUE means B-tree only.
     * Unique constraints can not be partial
     * Unique indexes can be partial
     */
    override fun createIndex(index: Index): String {
        val t = TransactionManager.current()
        val quotedTableName = t.identity(index.table)
        val quotedIndexName = t.db.identifierManager.cutIfNecessaryAndQuote(index.indexName)
        val keyFields = index.columns.plus(index.functions ?: emptyList())
        val fieldsList = keyFields.joinToString(prefix = "(", postfix = ")") {
            when (it) {
                is Column<*> -> t.identity(it)
                is Function<*> -> indexFunctionToString(it)
                // returned by existingIndices() mapping String metadata to stringLiteral()
                is LiteralOp<*> -> it.value.toString().trim('"')
                else -> {
                    exposedLogger.warn("Unexpected defining key field will be passed as String: $it")
                    it.toString()
                }
            }
        }
        val includesOnlyColumns = index.functions?.isEmpty() != false
        val maybeFilterCondition = filterCondition(index) ?: return ""

        return when {
            // unique and no filter -> constraint, the type is not supported
            index.unique && maybeFilterCondition.isEmpty() && includesOnlyColumns -> {
                "ALTER TABLE $quotedTableName ADD CONSTRAINT $quotedIndexName UNIQUE $fieldsList"
            }
            // unique and filter -> index only, the type is not supported
            index.unique -> {
                "CREATE UNIQUE INDEX $quotedIndexName ON $quotedTableName $fieldsList$maybeFilterCondition"
            }
            // type -> can't be unique or constraint
            index.indexType != null -> {
                createIndexWithType(
                    name = quotedIndexName, table = quotedTableName,
                    columns = fieldsList, type = index.indexType, filterCondition = maybeFilterCondition
                )
            }

            else -> {
                "CREATE INDEX $quotedIndexName ON $quotedTableName $fieldsList$maybeFilterCondition"
            }
        }
    }

    protected open fun createIndexWithType(name: String, table: String, columns: String, type: String, filterCondition: String): String {
        return "CREATE INDEX $name ON $table $columns USING $type$filterCondition"
    }

    override fun dropIndex(tableName: String, indexName: String, isUnique: Boolean, isPartialOrFunctional: Boolean): String {
        return "ALTER TABLE ${identifierManager.quoteIfNecessary(tableName)} DROP CONSTRAINT ${identifierManager.quoteIfNecessary(indexName)}"
    }

    override fun modifyColumn(column: Column<*>, columnDiff: ColumnDiff): List<String> =
        listOf("ALTER TABLE ${TransactionManager.current().identity(column.table)} MODIFY COLUMN ${column.descriptionDdl(true)}")

    override fun addPrimaryKey(table: Table, pkName: String?, vararg pkColumns: Column<*>): String {
        val transaction = TransactionManager.current()
        val columns = pkColumns.joinToString(prefix = "(", postfix = ")") { transaction.identity(it) }
        val constraint = pkName?.let { " CONSTRAINT ${identifierManager.quoteIfNecessary(it)} " } ?: " "
        return "ALTER TABLE ${transaction.identity(table)} ADD${constraint}PRIMARY KEY $columns"
    }
}

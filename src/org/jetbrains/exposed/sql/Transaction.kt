package org.jetbrains.exposed.sql

import java.sql.Connection
import java.sql.PreparedStatement
import java.util.*
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityCache

public class Key<T>()
@Suppress("UNCHECKED_CAST")
open class UserDataHolder() {
    private val userdata = HashMap<Key<*>, Any?>()

    public fun <T:Any> putUserData(key: Key<T>, value: T?) {
        userdata[key] = value
    }

    public fun <T:Any> getUserData(key: Key<T>) : T? {
        return userdata[key] as T?
    }

    public fun <T:Any> getOrCreate(key: Key<T>, init: ()->T): T {
        if (userdata.containsKey(key)) {
            return userdata[key] as T
        }

        val new = init()
        userdata[key] = new
        return new
    }
}

class Transaction(val db: Database, val connector: ()-> Connection): UserDataHolder() {
    private var _connection: Connection? = null
    val connection: Connection get() {
        if (_connection == null) {
            _connection = connector()
        }
        return _connection!!
    }

    val identityQuoteString by lazy(LazyThreadSafetyMode.NONE) { db.metadata.identifierQuoteString!! }
    val extraNameCharacters by lazy(LazyThreadSafetyMode.NONE) { db.metadata.extraNameCharacters!!}
    val keywords = arrayListOf("key")
    val logger = CompositeSqlLogger()

    var statementCount: Int = 0
    var duration: Long = 0
    var warnLongQueriesDuration: Long = 2000
    var debug = false
    var selectsForUpdate = false

    val statements = StringBuilder()
    // prepare statement as key and count to execution time as value
    val statementStats = hashMapOf<String, Pair<Int,Long>>()
    val outerTransaction = threadLocal.get()

    init {
        logger.addLogger(Slf4jSqlLogger())
        threadLocal.set(this)
    }


    fun commit() {
        val created = flushCache()
        _connection?.let {
            it.commit()
        }
        EntityCache.invalidateGlobalCaches(created)
    }

    fun flushCache(): List<Entity> {
        with(EntityCache.getOrCreate(this)) {
            val newEntities = inserts.flatMap { it.value }
            flush()
            return newEntities
        }
    }

    fun rollback() {
        _connection?. let {
            if (!it.isClosed) it.rollback()
        }
    }

    private fun describeStatement(args: List<Pair<ColumnType, Any?>>, delta: Long, stmt: String): String {
        return "[${delta}ms] ${expandArgs(stmt, args).take(1024)}\n\n"
    }

    inner class BatchContext {
        var stmt: String = ""
        var args: List<Pair<ColumnType, Any?>> = listOf()

        fun log(stmt: String, args: List<Pair<ColumnType, Any?>>) {
            this.stmt = stmt
            this.args = args
            logger.log(stmt, args)
        }
    }

    fun <T> execBatch(body: BatchContext.() -> T): T {
        val context = BatchContext()
        statementCount++

        val start = System.currentTimeMillis()
        val answer = context.body()
        val delta = System.currentTimeMillis() - start

        duration += delta

        if (debug) {
            statements.append(describeStatement(context.args, delta, context.stmt))
            (statementStats[context.stmt] ?: (0 to 0L)).let { pair ->
                statementStats[context.stmt] = (pair.first + 1) to (pair.second + delta)
            }
        }

        if (delta > warnLongQueriesDuration) {
            exposedLogger.warn("Long query: ${describeStatement(context.args, delta, context.stmt)}", RuntimeException())
        }

        return answer
    }

    fun <T> exec(stmt: String, args: List<Pair<ColumnType, Any?>> = listOf(), body: () -> T): T {
        return execBatch {
            log(stmt, args)
            body()
        }
    }

    fun createStatements (vararg tables: Table) : List<String> {
        val statements = ArrayList<String>()
        if (tables.isEmpty())
            return statements

        val newTables = ArrayList<Table>()

        for (table in tables) {

            if(table.exists()) continue else newTables.add(table)

            // create table
            val ddl = table.ddl
            statements.add(ddl)

            // create indices
            for (table_index in table.indices) {
                statements.add(createIndex(table_index.first, table_index.second))
            }
        }

        for (table in newTables) {
            // foreign keys
            for (column in table.columns) {
                if (column.referee != null) {
                    statements.add(createFKey(column))
                }
            }
        }
        return statements
    }

    fun create(vararg tables: Table) {
        val statements = createStatements(*tables)
        for (statement in statements) {
            exec(statement) {
                connection.createStatement().executeUpdate(statement)
            }
        }
        dialect.resetCaches()
    }

    private fun addMissingColumnsStatements (vararg tables: Table): List<String> {
        val statements = ArrayList<String>()
        if (tables.isEmpty())
            return statements

        val existingTableColumns = logTimeSpent("Extracting table columns") {
            dialect.tableColumns()
        }

        for (table in tables) {
            //create columns
            val missingTableColumns = table.columns.filterNot { existingTableColumns[table.tableName]?.map { it.first }?.contains(it.name) ?: true }
            for (column in missingTableColumns) {
                statements.add(column.ddl)
            }

            // create indexes with new columns
            for (table_index in table.indices) {
                if (table_index.first.any { missingTableColumns.contains(it) }) {
                    val alterTable = createIndex(table_index.first, table_index.second)
                    statements.add(alterTable)
                }
            }

            // sync nullability of existing columns
            val incorrectNullabilityColumns = table.columns.filter { existingTableColumns[table.tableName]?.contains(it.name to !it.columnType.nullable) ?: false}
            for (column in incorrectNullabilityColumns) {
                statements.add(column.modifyStatement())
            }
        }

        val existingColumnConstraint = logTimeSpent("Extracting column constraints") {
            dialect.columnConstraints(*tables)
        }

        for (table in tables) {
            for (column in table.columns) {
                if (column.referee != null) {
                    val existingConstraint = existingColumnConstraint.get(Pair(table.tableName, column.name))?.firstOrNull()
                    if (existingConstraint == null) {
                        statements.add(createFKey(column))
                    } else if (existingConstraint.referencedTable != column.referee!!.table.tableName
                            || (column.onDelete ?: ReferenceOption.RESTRICT) != existingConstraint.deleteRule) {
                        statements.add(existingConstraint.dropStatement())
                        statements.add(createFKey(column))
                    }
                }
            }
        }

        return statements
    }

    fun createMissingTablesAndColumns(vararg tables: Table) {
        withDataBaseLock {
            dialect.resetCaches()
            val statements = logTimeSpent("Preparing create statements") {
                 createStatements(*tables) + addMissingColumnsStatements(*tables)
            }
            logTimeSpent("Executing create statements") {
                for (statement in statements) {
                    exec(statement) {
                        connection.createStatement().executeUpdate(statement)
                    }
                }
            }
            logTimeSpent("Checking mapping consistence") {
                for (statement in checkMappingConsistence(*tables).filter { it !in statements }) {
                    exec(statement) {
                        connection.createStatement().executeUpdate(statement)
                    }
                }
            }
        }
    }

    fun <T>withDataBaseLock(body: () -> T) {
        connection.createStatement().executeUpdate("CREATE TABLE IF NOT EXISTS BusyTable(busy bit unique)")
        val isBusy = connection.createStatement().executeQuery("SELECT * FROM BusyTable FOR UPDATE").next()
        if (!isBusy) {
            connection.createStatement().executeUpdate("INSERT INTO BusyTable (busy) VALUES (1)")
            try {
                body()
            } finally {
                connection.createStatement().executeUpdate("DELETE FROM BusyTable")
                connection.commit()
            }
        }
    }

    fun drop(vararg tables: Table) {
        for (table in tables) {
            val ddl = table.dropStatement()
            exec(ddl) {
                connection.createStatement().executeUpdate(ddl)
            }
        }
    }

    private fun String.isIdentifier() = !isEmpty() && first().isIdentifierStart() && all { it.isIdentifierStart() || it in '0'..'9' }
    private fun Char.isIdentifierStart(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this == '_' || this in extraNameCharacters

    private fun needQuotes (identity: String) : Boolean {
        return keywords.contains (identity) || !identity.isIdentifier()
    }

    private fun quoteIfNecessary (identity: String) : String {
        return (identity.split('.').map {quoteTokenIfNecessary(it)}).joinToString(".")
    }

    private fun quoteTokenIfNecessary(token: String) : String {
        return if (needQuotes(token)) "$identityQuoteString$token$identityQuoteString" else token
    }

    fun identity(table: Table): String {
        return (table as? Alias<*>)?.let { "${identity(it.delegate)} AS ${quoteIfNecessary(it.alias)}"} ?: quoteIfNecessary(table.tableName)
    }

    fun fullIdentity(column: Column<*>): String {
        return "${quoteIfNecessary(column.table.tableName)}.${quoteIfNecessary(column.name)}"
    }

    fun identity(column: Column<*>): String {
        return quoteIfNecessary(column.name)
    }

    fun createFKey(reference: Column<*>): String = ForeignKeyConstraint.from(reference).createStatement()

    fun createIndex(columns: Array<out Column<*>>, isUnique: Boolean): String = Index.forColumns(*columns, unique = isUnique).createStatement()

    fun autoIncrement(column: Column<*>): String {
        return when (db.vendor) {
            DatabaseVendor.MySql,
            DatabaseVendor.SQLServer,
            DatabaseVendor.H2 -> {
                "AUTO_INCREMENT"
            }
            else -> throw UnsupportedOperationException("Unsupported driver: " + db.vendor)
        }
    }

    fun prepareStatement(sql: String, autoincs: List<String>? = null): PreparedStatement {
        return if (autoincs == null) {
            connection.prepareStatement(sql)!!
        } else {
            connection.prepareStatement(sql, autoincs.toTypedArray())!!
        }
    }

    fun close() {
        threadLocal.set(outerTransaction)
        _connection?.close()
    }

    companion object {
        val threadLocal = ThreadLocal<Transaction>()

        fun hasTransaction(): Boolean = currentOrNull() != null

        fun currentOrNull(): Transaction? = threadLocal.get()

        fun current(): Transaction {
            return currentOrNull() ?: error("No transaction in context. Use Database.transaction() { ... }")
        }
    }
}

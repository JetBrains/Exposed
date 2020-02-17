package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.jetbrains.exposed.sql.vendors.inProperCase
import java.util.*

object SchemaUtils {
    private class TableDepthGraph(val tables: List<Table>) {
        val graph = fetchAllTables().associate { t ->
            t to t.columns.mapNotNull { c ->
                c.referee?.let{ it.table to c.columnType.nullable }
            }.toMap()
        }

        private fun fetchAllTables(): HashSet<Table> {
            val result = HashSet<Table>()

            fun parseTable(table: Table) {
                if (result.add(table)) {
                    table.columns.forEach {
                        it.referee?.table?.let(::parseTable)
                    }
                }
            }
            tables.forEach(::parseTable)
            return result
        }

        fun sorted() : List<Table> {
            val visited = mutableSetOf<Table>()
            val result = arrayListOf<Table>()

            fun traverse(table: Table) {
                if (table !in visited) {
                    visited += table
                    graph.getValue(table).forEach { (t, _) ->
                        if (t !in visited) {
                            traverse(t)
                        }
                    }
                    result += table
                }
            }

            tables.forEach(::traverse)
            return result
        }

        fun hasCycle() : Boolean {
            val visited = mutableSetOf<Table>()
            val recursion = mutableSetOf<Table>()

            val sortedTables = sorted()

            fun traverse(table: Table) : Boolean {
                if (table in recursion) return true
                if (table in visited) return false
                recursion += table
                visited += table
                return if (graph[table]!!.any{ traverse(it.key) }) {
                    true
                } else {
                    recursion -= table
                    false
                }
            }
            return sortedTables.any { traverse(it) }
        }
    }

    fun sortTablesByReferences(tables: Iterable<Table>) = TableDepthGraph(tables.toList()).sorted()
    fun checkCycle(vararg tables: Table) = TableDepthGraph(tables.toList()).hasCycle()

    fun createStatements(vararg tables: Table): List<String> {
        if (tables.isEmpty())
            return emptyList()

        val toCreate = sortTablesByReferences(tables.toList()).filterNot { it.exists() }
        val alters = arrayListOf<String>()
        return toCreate.flatMap { table ->
            val (create, alter) = table.ddl.partition { it.startsWith("CREATE ") }
            val indicesDDL = table.indices.flatMap { createIndex(it) }
            alters += alter
            create + indicesDDL
        } + alters
    }

    fun createSequence(vararg seq: Sequence, inBatch: Boolean = false) {
        with(TransactionManager.current()) {
            val createStatements = seq.flatMap { it.createStatement() }
            execStatements(inBatch, createStatements)
        }
    }

    fun dropSequence(vararg seq: Sequence, inBatch: Boolean = false) {
        with(TransactionManager.current()) {
            val dropStatements = seq.flatMap { it.dropStatement() }
            execStatements(inBatch, dropStatements)
        }
    }

    fun createFKey(reference: Column<*>): List<String> {
        val foreignKey = reference.foreignKey
        require(foreignKey != null && (foreignKey.deleteRule != null || foreignKey.updateRule != null)) { "$reference does not reference anything" }
        return foreignKey.createStatement()
    }

    fun createIndex(index: Index) = index.createStatement()

    private fun addMissingColumnsStatements(vararg tables: Table): List<String> {
        with(TransactionManager.current()) {
            val statements = ArrayList<String>()
            if (tables.isEmpty())
                return statements

            val existingTableColumns = logTimeSpent("Extracting table columns") {
                currentDialect.tableColumns(*tables)
            }

            for (table in tables) {
                //create columns
                val thisTableExistingColumns = existingTableColumns[table].orEmpty()
                val missingTableColumns = table.columns.filterNot { c -> thisTableExistingColumns.any { it.name.equals(c.name, true) } }
                missingTableColumns.flatMapTo(statements) { it.ddl }

                if (db.supportsAlterTableWithAddColumn) {
                    // create indexes with new columns
                    for (index in table.indices) {
                        if (index.columns.any { missingTableColumns.contains(it) }) {
                            statements.addAll(createIndex(index))
                        }
                    }

                    // sync nullability of existing columns
                    val incorrectNullabilityColumns = table.columns.filter { c ->
                        thisTableExistingColumns.any { c.name.equals(it.name, true) && it.nullable != c.columnType.nullable }
                    }
                    incorrectNullabilityColumns.flatMapTo(statements) { it.modifyStatement() }
                }
            }

            if (db.supportsAlterTableWithAddColumn) {
                val existingColumnConstraint = logTimeSpent("Extracting column constraints") {
                    db.dialect.columnConstraints(*tables)
                }

                for (table in tables) {
                    for (column in table.columns) {
                        val foreignKey = column.foreignKey
                        if (foreignKey != null) {
                            val existingConstraint = existingColumnConstraint[table.tableName.inProperCase() to identity(column)]?.firstOrNull()
                            if (existingConstraint == null) {
                                statements.addAll(createFKey(column))
                            } else if (existingConstraint.targetTable != foreignKey.targetTable
                                    || foreignKey.deleteRule != existingConstraint.deleteRule
                                    || foreignKey.updateRule != existingConstraint.updateRule) {
                                statements.addAll(existingConstraint.dropStatement())
                                statements.addAll(createFKey(column))
                            }
                        }
                    }
                }
            }

            return statements
        }
    }

    private fun Transaction.execStatements(inBatch: Boolean, statements: List<String>) {
        if (inBatch)
            execInBatch(statements)
        else {
            for (statement in statements) {
                exec(statement)
            }
        }
    }
    fun <T : Table> create(vararg tables: T, inBatch: Boolean = false) {
        with(TransactionManager.current()) {
            execStatements(inBatch, createStatements(*tables))
            commit()
            currentDialect.resetCaches()
        }
    }

    /**
     * Creates databases
     *
     * @param databases the names of the databases
     * @param inBatch flag to perform database creation in a single batch
     */
    fun createDatabase(vararg databases: String, inBatch: Boolean = false) {
        with(TransactionManager.current()) {
            val createStatements = databases.flatMap { listOf(currentDialect.createDatabase(it)) }
            execStatements(inBatch, createStatements)
        }
    }

    /**
     * Drops databases
     *
     * @param databases the names of the databases
     * @param inBatch flag to perform database creation in a single batch
     */
    fun dropDatabase(vararg databases: String, inBatch: Boolean = false) {
        with(TransactionManager.current()) {
            val createStatements = databases.flatMap { listOf(currentDialect.dropDatabase(it)) }
            execStatements(inBatch, createStatements)
        }
    }

    /**
     * This function should be used in cases when you want an easy-to-use auto-actualization of database scheme.
     * It will create all absent tables, add missing columns for existing tables if it's possible (columns are nullable or have default values).
     *
     * Also if there is inconsistency in DB vs code mappings (excessive or absent indexes)
     * then DDLs to fix it will be logged to exposedLogger.
     *
     * This functionality is based on jdbc metadata what might be a bit slow, so it is recommended to call this function once
     * at application startup and provide all tables you want to actualize.
     *
     * Please note, that execution of this function concurrently might lead to unpredictable state in database due to
     * non-transactional behavior of some DBMS on processing DDL statements (e.g. MySQL) and metadata caches.

     * To prevent such cases is advised to use any "global" synchronization you prefer (via redis, memcached, etc) or
     * with Exposed's provided lock based on synchronization on a dummy "Buzy" table (@see SchemaUtils#withDataBaseLock).
     */
    fun createMissingTablesAndColumns(vararg tables: Table, inBatch: Boolean = false) {
        with(TransactionManager.current()) {
            db.dialect.resetCaches()
            val createStatements = logTimeSpent("Preparing create tables statements") {
                createStatements(*tables)
            }
            logTimeSpent("Executing create tables statements") {
                execStatements(inBatch, createStatements)
                commit()
            }

            val alterStatements = logTimeSpent("Preparing alter table statements") {
                addMissingColumnsStatements(*tables)
            }
            logTimeSpent("Executing alter table statements") {
                execStatements(inBatch, alterStatements)
                commit()
            }
            val executedStatements = createStatements + alterStatements
            logTimeSpent("Checking mapping consistence") {
                val modifyTablesStatements = checkMappingConsistence(*tables).filter { it !in executedStatements }
                execStatements(inBatch, modifyTablesStatements)
                commit()
            }
            db.dialect.resetCaches()
        }
    }


    /**
     * Creates table with name "busy" (if not present) and single column to be used as "synchronization" point. Table wont be dropped after execution.
     *
     * All code provided in _body_ closure will be executed only if there is no another code which running under "withDataBaseLock" at same time.
     * That means that concurrent execution of long running tasks under "database lock" might lead to that only first of them will be really executed.
     */
    fun <T> Transaction.withDataBaseLock(body: () -> T) {
        val buzyTable = object : Table("busy") {
            val busy = bool("busy").uniqueIndex()
        }
        create(buzyTable)
        val isBusy = buzyTable.selectAll().forUpdate().any()
        if (!isBusy) {
            buzyTable.insert { it[buzyTable.busy] = true }
            try {
                body()
            } finally {
                buzyTable.deleteAll()
                connection.commit()
            }
        }
    }

    fun drop(vararg tables: Table, inBatch: Boolean = false) {
        if (tables.isEmpty()) return
        with(TransactionManager.current()) {
            var tablesForDeletion =
                    sortTablesByReferences(tables.toList())
                            .reversed()
                            .filter { it in tables }
            if (!currentDialect.supportsIfNotExists) {
                tablesForDeletion = tablesForDeletion.filter { it.exists() }
            }
            val dropStatements = tablesForDeletion.flatMap { it.dropStatement() }
            execStatements(inBatch, dropStatements)
            currentDialect.resetCaches()
        }
    }
}

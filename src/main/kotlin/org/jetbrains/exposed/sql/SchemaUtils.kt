package org.jetbrains.exposed.sql

import org.jetbrains.exposed.dao.EntityCache
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.jetbrains.exposed.sql.vendors.inProperCase
import java.util.*

object SchemaUtils {
    fun createStatements(vararg tables: Table): List<String> {
        val statements = ArrayList<String>()
        if (tables.isEmpty())
            return statements

        val newTables = ArrayList<Table>()

        for (table in EntityCache.sortTablesByReferences(tables.toList())) {

            if (table.exists()) continue else newTables.add(table)

            // create table
            statements.addAll(table.ddl)

            // create indices
            for ((columns, isUnique) in table.indices) {
                statements.addAll(createIndex(columns, isUnique))
            }
        }

        return statements
    }

    fun createSequence(name: String) = Seq(name).createStatement()

    fun createFKey(reference: Column<*>) = ForeignKeyConstraint.from(reference).createStatement()

    fun createIndex(columns: Array<out Column<*>>, isUnique: Boolean) = Index.forColumns(*columns, unique = isUnique).createStatement()

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
                val missingTableColumns = table.columns.filterNot { c -> thisTableExistingColumns.any { it.first.equals(c.name, true) } }
                missingTableColumns.flatMapTo(statements) { it.ddl }

                if (db.supportsAlterTableWithAddColumn) {
                    // create indexes with new columns
                    for ((columns, isUnique) in table.indices) {
                        if (columns.any { missingTableColumns.contains(it) }) {
                            statements.addAll(createIndex(columns, isUnique))
                        }
                    }

                    // sync nullability of existing columns
                    val incorrectNullabilityColumns = table.columns.filter { c ->
                        thisTableExistingColumns.any { c.name.equals(it.first, true) && it.second != c.columnType.nullable }
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
                        if (column.referee != null) {
                            val existingConstraint = existingColumnConstraint[table.tableName.inProperCase() to column.name.inProperCase()]?.firstOrNull()
                            if (existingConstraint == null) {
                                statements.addAll(createFKey(column))
                            } else if (existingConstraint.referencedTable != column.referee!!.table.tableName.inProperCase()
                                    || column.onDelete != existingConstraint.deleteRule) {
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

    fun <T : Table> create(vararg tables: T) {
        with(TransactionManager.current()) {
            val statements = createStatements(*tables)
            for (statement in statements) {
                exec(statement)
            }
            commit()
            db.dialect.resetCaches()
        }
    }

    fun createMissingTablesAndColumns(vararg tables: Table) {
        with(TransactionManager.current()) {
            withDataBaseLock {
                db.dialect.resetCaches()
                val createStatements = logTimeSpent("Preparing create tables statements") {
                    createStatements(*tables)
                }
                logTimeSpent("Executing create tables statements") {
                    for (statement in createStatements) {
                        exec(statement)
                    }
                    commit()
                }

                val alterStatements = logTimeSpent("Preparing alter table statements") {
                    addMissingColumnsStatements(*tables)
                }
                logTimeSpent("Executing alter table statements") {
                    for (statement in alterStatements) {
                        exec(statement)
                    }
                    commit()
                }
                logTimeSpent("Checking mapping consistence") {
                    for (statement in checkMappingConsistence(*tables).filter { it !in statements }) {
                        exec(statement)
                    }
                    commit()
                }
                db.dialect.resetCaches()
            }
        }
    }

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

    fun drop(vararg tables: Table) {
        var tablesForDeletion = EntityCache
                .sortTablesByReferences(tables.toList())
                .reversed()
                .filter { it in tables }
        if (!currentDialect.supportsIfNotExists) {
            tablesForDeletion = tablesForDeletion.filter { it.exists()}
        }
        tablesForDeletion
                .flatMap { it.dropStatement() }
                .forEach {
                    TransactionManager.current().exec(it)
                }
        currentDialect.resetCaches()
    }
}
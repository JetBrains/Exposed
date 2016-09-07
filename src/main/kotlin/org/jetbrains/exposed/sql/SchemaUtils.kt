package org.jetbrains.exposed.sql

import org.jetbrains.exposed.dao.EntityCache
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.currentDialect
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

    fun createFKey(reference: Column<*>): String = ForeignKeyConstraint.from(reference).createStatement()

    fun createIndex(columns: Array<out Column<*>>, isUnique: Boolean): String = Index.forColumns(*columns, unique = isUnique).createStatement()

    private fun addMissingColumnsStatements(vararg tables: Table): List<String> {
        with(TransactionManager.current()) {
            val statements = ArrayList<String>()
            if (tables.isEmpty())
                return statements

            val existingTableColumns = logTimeSpent("Extracting table columns") {
                TransactionManager.current().db.dialect.tableColumns()
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
                val incorrectNullabilityColumns = table.columns.filter { existingTableColumns[table.tableName]?.contains(it.name to !it.columnType.nullable) ?: false }
                for (column in incorrectNullabilityColumns) {
                    statements.add(column.modifyStatement())
                }
            }

            val existingColumnConstraint = logTimeSpent("Extracting column constraints") {
                db.dialect.columnConstraints(*tables)
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

    }

    fun <T : Table> create(vararg tables: T) {
        with(TransactionManager.current()) {
            val statements = createStatements(*tables)
            for (statement in statements) {
                exec(statement)
            }
            db.dialect.resetCaches()
        }
    }

    fun createMissingTablesAndColumns(vararg tables: Table) {
        with(TransactionManager.current()) {
            withDataBaseLock {
                db.dialect.resetCaches()
                val statements = logTimeSpent("Preparing create statements") {
                    createStatements(*tables) + addMissingColumnsStatements(*tables)
                }
                logTimeSpent("Executing create statements") {
                    for (statement in statements) {
                        exec(statement)
                    }
                }
                logTimeSpent("Checking mapping consistence") {
                    for (statement in checkMappingConsistence(*tables).filter { it !in statements }) {
                        exec(statement)
                    }
                }
                db.dialect.resetCaches()
            }
        }
    }

    fun <T> Transaction.withDataBaseLock(body: () -> T) {
        val buzyTable = object : Table("Busy") {
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
        for (table in tables) {
            val ddl = table.dropStatement()
            TransactionManager.current().exec(ddl)
        }
        currentDialect.resetCaches()
    }
}
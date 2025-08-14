package org.example.examples

import org.example.tables.FilmsTable
import org.jetbrains.exposed.v1.core.BooleanColumnType
import org.jetbrains.exposed.v1.core.DoubleColumnType
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/*
    Important: This file is referenced by line number in `Working-with-SQL-Strings.md`.
    If you add, remove, or modify any lines, ensure you update the corresponding
    line numbers in the `code-block` element of the referenced file.
*/

private const val GOOD_RATING = 9.0
private const val BAD_RATING = 2.1

class ExecExamples {
    fun execBasicStrings() {
        transaction {
            addLogger(StdOutSqlLogger)

            val secretCode = "abc"
            exec("CREATE USER IF NOT EXISTS GUEST PASSWORD '$secretCode'")
            exec("GRANT ALL PRIVILEGES ON ${FilmsTable.nameInDatabaseCase()} TO GUEST")

            val version = exec("SELECT H2VERSION()") { result ->
                result.next()
                result.getString(1)
            }
            println(version)

            val schema = "TABLE_SCHEMA"
            val name = "TABLE_NAME"
            val rowCount = "ROW_COUNT_ESTIMATE"
            val tableInfo = exec("SELECT $schema, $name, $rowCount FROM INFORMATION_SCHEMA.TABLES") { result ->
                val info = mutableListOf<Triple<String, String, Int>>()
                while (result.next()) {
                    info += Triple(result.getString(schema), result.getString(name), result.getInt(rowCount))
                }
                info
            } ?: emptyList()
            println(tableInfo.last())

            exec(
                stmt = "DROP USER IF EXISTS GUEST",
                explicitStatementType = StatementType.DROP
            )
        }
    }

    fun execAndMapResult() {
        transaction {
            addLogger(StdOutSqlLogger)

            FilmsTable.insert {
                it[title] = "The Good Film"
                it[rating] = GOOD_RATING
                it[nominated] = true
            }
            FilmsTable.insert {
                it[title] = "The Bad Film"
                it[rating] = BAD_RATING
                it[nominated] = false
            }

            val toIgnore = "SELECT FILMS.ID, FILMS.TITLE FROM FILMS WHERE FILMS.RATING <= 3.0".execAndMap { result ->
                result.getInt("FILMS.ID") to result.getString("FILMS.TITLE")
            }
            println(toIgnore)
        }
    }

    fun execWithParameters() {
        transaction {
            addLogger(StdOutSqlLogger)

            val toWatch = exec(
                stmt = "SELECT FILMS.ID, FILMS.TITLE FROM FILMS WHERE (FILMS.NOMINATED = ?) AND (FILMS.RATING >= ?)",
                args = listOf(BooleanColumnType() to true, DoubleColumnType() to GOOD_RATING)
            ) { result ->
                val films = mutableListOf<Pair<Int, String>>()
                while (result.next()) {
                    films += result.getInt(1) to result.getString(2)
                }
                films
            }
            println(toWatch)
        }
    }

    fun execWithTypeOverride() {
        transaction {
            addLogger(StdOutSqlLogger)

            val plan = exec(
                stmt = "EXPLAIN SELECT * FROM FILMS WHERE FILMS.ID = 1",
                explicitStatementType = StatementType.EXEC
            ) { result ->
                val data = mutableListOf<Pair<String, Any?>>()
                while (result.next()) {
                    repeat(result.metaData.columnCount) {
                        data += result.metaData.getColumnName(it + 1) to result.getObject(it + 1)
                    }
                }
                data
            } ?: emptyList()
            println(plan)
        }
    }
}

package org.example.examples

import org.jetbrains.exposed.v1.DoubleColumnType
import org.jetbrains.exposed.v1.addLogger
import org.jetbrains.exposed.v1.core.BooleanColumnType
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.transactions.transaction

/*
    Important: This file is referenced by line number in `Working-with-SQL-Strings.md`.
    If you add, remove, or modify any lines, ensure you update the corresponding
    line numbers in the `code-block` element of the referenced file.
*/

private const val MAX_TITLE_LENGTH = 150
private const val NEW_TITLE = "The New Film"
private const val NEW_RATING = 9.0

class ExecMySQLExamples {
    fun execMultipleStrings() {
        transaction {
            addLogger(StdOutSqlLogger)

            val insertStmt = "INSERT INTO Films (title, rating, nominated) VALUES (?, ?, ?)"
            val lastIdAlias = "last_id"
            val selectStmt = "SELECT LAST_INSERT_ID() AS $lastIdAlias"
            val lastId = exec(
                stmt = "$insertStmt; $selectStmt;",
                args = listOf(
                    VarCharColumnType(MAX_TITLE_LENGTH) to NEW_TITLE,
                    DoubleColumnType() to NEW_RATING,
                    BooleanColumnType() to false
                ),
                explicitStatementType = StatementType.MULTI
            ) { result ->
                result.next()
                result.getInt(lastIdAlias)
            }
            println(lastId)
        }
    }
}

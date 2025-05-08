package org.example.examples

import kotlinx.datetime.LocalDateTime
import org.example.tables.StarWarsFilmsTable
import org.jetbrains.exposed.v1.sql.*
import org.jetbrains.exposed.v1.sql.SqlExpressionBuilder.times
import org.jetbrains.exposed.v1.sql.kotlin.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.sql.kotlin.datetime.datetime

private const val TITLE_MAX_LENGTH = 64
private const val MOVIE_SEQUEL_3_ID = 8
private const val PROJECT_BUDGET = 100
private const val INCREASE_BUDGET_BY = 100

object Projects : Table("projects") {
    val title = varchar("title", TITLE_MAX_LENGTH)
    val budget = integer("budget")
    val created = datetime("created").defaultExpression(CurrentDateTime)
}

class ModifiedRowsExamples {
    fun getInsertedCount() {
        val insertStatement = StarWarsFilmsTable.insertIgnore {
            it[name] = "The Last Jedi"
            it[sequelId] = MOVIE_SEQUEL_3_ID
            it[director] = "Rian Johnson"
        }
        val rowCount: Int = insertStatement.insertedCount
        println(rowCount)
    }

    fun returnData() {
        // returns all table columns by default
        val createdProjects: LocalDateTime = Projects.insertReturning {
            it[title] = "Project A"
            it[budget] = PROJECT_BUDGET
        }.single()[Projects.created]
        println(createdProjects)

        val updatedBudgets: List<Int> = Projects.updateReturning(listOf(Projects.budget)) {
            it[budget] = Projects.budget.times(INCREASE_BUDGET_BY)
        }.map {
            it[Projects.budget]
        }
        println(updatedBudgets)
    }
}

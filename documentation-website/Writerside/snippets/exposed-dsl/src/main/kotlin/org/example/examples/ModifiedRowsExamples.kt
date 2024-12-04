package org.example.examples

import kotlinx.datetime.LocalDateTime
import org.example.tables.StarWarsFilmsTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.times
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentDateTime
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object Projects : Table("projects") {
    val title = varchar("title", 64)
    val budget = integer("budget")
    val created = datetime("created").defaultExpression(CurrentDateTime)
}

class ModifiedRowsExamples {
    fun getInsertedCount() {
        val insertStatement = StarWarsFilmsTable.insertIgnore {
            it[name] = "The Last Jedi"
            it[sequelId] = 8
            it[director] = "Rian Johnson"
        }
        val rowCount: Int = insertStatement.insertedCount
    }

    fun returnData() {
        // returns all table columns by default
        val createdProjects: LocalDateTime = Projects.insertReturning {
            it[title] = "Project A"
            it[budget] = 100
        }.single()[Projects.created]

        val updatedBudgets: List<Int> = Projects.updateReturning(listOf(Projects.budget)) {
            it[budget] = Projects.budget.times(5)
        }.map {
            it[Projects.budget]
        }
    }
}

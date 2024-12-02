package org.example.examples

import org.example.tables.StarWarsFilmsTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.concat
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.stringLiteral
import org.jetbrains.exposed.sql.upsert

class UpdateOrInsertExamples {
    fun updateOrInsert() {
        // inserts a new row
        StarWarsFilmsTable.upsert {
            it[sequelId] = 9 // column pre-defined with a unique index
            it[name] = "The Rise of Skywalker"
            it[director] = "Rian Johnson"
        }
        // updates existing row with the correct [director]
        StarWarsFilmsTable.upsert {
            it[sequelId] = 9
            it[name] = "The Rise of Skywalker"
            it[director] = "JJ Abrams"
        }
    }

    fun updateAndInsertWithConfig() {
        StarWarsFilmsTable.upsert(
            StarWarsFilmsTable.sequelId,
            onUpdate = { it[StarWarsFilmsTable.sequelId] = StarWarsFilmsTable.sequelId + 1 },
            where = { StarWarsFilmsTable.director like stringLiteral("JJ%") }
        ) {
            it[sequelId] = 9
            it[name] = "The Rise of Skywalker"
            it[director] = "JJ Abrams"
        }

        StarWarsFilmsTable.upsert(
            onUpdate = {
                it[StarWarsFilmsTable.director] = concat(insertValue(StarWarsFilmsTable.director), stringLiteral(" || "), StarWarsFilmsTable.director)
            }
        ) {
            it[sequelId] = 9
            it[name] = "The Rise of Skywalker"
            it[director] = "Rian Johnson"
        }
    }

    fun onUpdateExclude() {
        // on conflict, all columns EXCEPT [director] are updated with values from the lambda block
        StarWarsFilmsTable.upsert(onUpdateExclude = listOf(StarWarsFilmsTable.director)) {
            it[sequelId] = 9
            it[name] = "The Rise of Skywalker"
            it[director] = "JJ Abrams"
        }

        // on conflict, ONLY column [director] is updated with value from the lambda block
        StarWarsFilmsTable.upsert(
            onUpdateExclude = StarWarsFilmsTable.columns - setOf(StarWarsFilmsTable.director)
        ) {
            it[sequelId] = 9
            it[name] = "The Rise of Skywalker"
            it[director] = "JJ Abrams"
        }
    }
}

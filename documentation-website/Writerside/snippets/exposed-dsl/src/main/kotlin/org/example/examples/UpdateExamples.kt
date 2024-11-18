package org.example.examples

import org.example.tables.*
import org.jetbrains.exposed.sql.update


class UpdateExamples {
    fun update() {
        val updatedRowCount = StarWarsFilmsTable.update({ StarWarsFilmsTable.name like "Episode" }) {
            it[StarWarsFilmsTable.director] = "George Lucas"
        }
    }
    //TODO
    fun updateWithAnExpression() {
        val updatedRowsWithIncrement = StarWarsFilmsTable.update({ StarWarsFilmsTable.sequelId eq 8 }) {
            it.update(StarWarsFilmsTable.sequelId, StarWarsFilmsTable.sequelId + 1)
            // or
            it[StarWarsFilmsTable.sequelId] = StarWarsFilmsTable.sequelId + 1
        }
    }
}

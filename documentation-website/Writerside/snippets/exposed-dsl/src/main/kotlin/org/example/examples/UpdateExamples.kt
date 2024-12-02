package org.example.examples

import org.example.tables.StarWarsFilmsTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.update


class UpdateExamples {

    fun updateRecords() {

        /*
            UPDATE STARWARSFILMS SET DIRECTOR='George Lucas' WHERE STARWARSFILMS."name" LIKE 'Episode'
         */

        val updatedRowCount = StarWarsFilmsTable.update({ StarWarsFilmsTable.name like "Episode" }) {
            it[director] = "George Lucas"
        }
        println(updatedRowCount)

        /*
            UPDATE STARWARSFILMS SET SEQUEL_ID=(STARWARSFILMS.SEQUEL_ID + 1) WHERE STARWARSFILMS.SEQUEL_ID = 7
         */

        val updatedRowsWithIncrement = StarWarsFilmsTable.update({ StarWarsFilmsTable.sequelId eq 7 }) {
            with(SqlExpressionBuilder) {
                it[sequelId] = sequelId + 1
                // or
                it.update(sequelId, sequelId + 1)
            }
        }
        println(updatedRowsWithIncrement)
    }
}

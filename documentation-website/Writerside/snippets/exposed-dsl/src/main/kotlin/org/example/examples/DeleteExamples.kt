package org.example.examples

import org.example.tables.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.delete
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteIgnoreWhere
import org.jetbrains.exposed.sql.deleteWhere

class DeleteExamples {
    fun delete() {
        val deletedRowsCount = StarWarsFilmsTable.deleteWhere { StarWarsFilmsTable.sequelId eq 8 }
    }

    fun deleteIgnore() {
        val deleteIgnoreRowsCount = StarWarsFilmsTable.deleteIgnoreWhere { StarWarsFilmsTable.sequelId eq 8 }
    }

    fun deleteAll() {
       val allDeletedRowsCount = StarWarsFilmsTable.deleteAll { StarWarsFilmsTable.sequelId eq 8 }
    }

    fun joinDelete() {
        val join = StarWarsFilmsTable innerJoin ActorsTable
        val deletedActorsCount = join.delete(ActorsTable) { ActorsTable.sequelId greater 2 }
    }
}

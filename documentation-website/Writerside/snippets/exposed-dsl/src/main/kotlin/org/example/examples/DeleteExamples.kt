package org.example.examples

import org.example.tables.ActorsIntIdTable
import org.example.tables.StarWarsFilmsIntIdTable
import org.example.tables.StarWarsFilmsTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

private const val MOVIE_SEQUEL_ID = 7
private const val ACTORS_SEQUEL_ID = 2

class DeleteExamples {
    fun delete() {
        /*
            DELETE FROM STARWARSFILMS WHERE STARWARSFILMS.SEQUEL_ID = 6
         */

        val deletedRowsCount = StarWarsFilmsTable.deleteWhere { StarWarsFilmsTable.sequelId eq MOVIE_SEQUEL_ID }
        println(deletedRowsCount)
    }

    fun deleteIgnore() {
        val deleteIgnoreRowsCount = StarWarsFilmsIntIdTable.deleteIgnoreWhere { StarWarsFilmsIntIdTable.sequelId eq MOVIE_SEQUEL_ID }
        println(deleteIgnoreRowsCount)
    }

    fun deleteAll() {
        val allDeletedRowsCount = StarWarsFilmsTable.deleteAll()
        println(allDeletedRowsCount)
    }

    fun joinDelete() {
        /*
            MERGE INTO ACTORS USING STAR_WARS_FILMS_TABLE
            ON STAR_WARS_FILMS_TABLE.ID = ACTORS.SEQUEL_ID
            WHEN MATCHED AND ACTORS.SEQUEL_ID > 2
            THEN DELETE
         */

        StarWarsFilmsIntIdTable.insertIgnore {
            it[sequelId] = MOVIE_SEQUEL_ID
            it[name] = "The Force Awakens"
            it[director] = "J.J. Abrams"
        }

        ActorsIntIdTable.insertIgnore {
            it[id] = ACTORS_SEQUEL_ID
            it[name] = "Harrison Ford"
            it[sequelId] = MOVIE_SEQUEL_ID
        }
        // val simpleJoin = StarWarsFilmsTable innerJoin ActorsTable
        val join = StarWarsFilmsIntIdTable.join(ActorsIntIdTable, JoinType.INNER, StarWarsFilmsIntIdTable.id, ActorsIntIdTable.sequelId)

        val deletedActorsCount = join.delete(ActorsIntIdTable) { ActorsIntIdTable.sequelId greater ACTORS_SEQUEL_ID }
        println(deletedActorsCount)
    }
}

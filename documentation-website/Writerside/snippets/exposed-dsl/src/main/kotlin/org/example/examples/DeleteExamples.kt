package org.example.examples

import org.example.tables.ActorsTable
import org.example.tables.StarWarsFilmsTable
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.delete
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteIgnoreWhere
import org.jetbrains.exposed.sql.deleteWhere

private const val MOVIE_SEQUEL_ID = 6
private const val MOVIE_SEQUEL_2_ID = 7
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
        val deleteIgnoreRowsCount = StarWarsFilmsTable.deleteIgnoreWhere { StarWarsFilmsTable.sequelId eq MOVIE_SEQUEL_2_ID }
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

        // val simpleJoin = StarWarsFilmsTable innerJoin ActorsTable
        val join = StarWarsFilmsTable.join(ActorsTable, JoinType.INNER, StarWarsFilmsTable.id, ActorsTable.sequelId)

        val deletedActorsCount = join.delete(ActorsTable) { ActorsTable.sequelId greater ACTORS_SEQUEL_ID }
        println(deletedActorsCount)
    }
}

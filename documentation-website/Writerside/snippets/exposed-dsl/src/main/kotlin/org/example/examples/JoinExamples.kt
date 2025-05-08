package org.example.examples

import org.example.tables.ActorsIntIdTable
import org.example.tables.RolesTable
import org.example.tables.StarWarsFilmsIntIdTable
import org.jetbrains.exposed.v1.sql.JoinType
import org.jetbrains.exposed.v1.sql.count
import org.jetbrains.exposed.v1.sql.unionAll

/*
    Important: The contents of this file are referenced by line number in `DSL-Joining-Tables.topic`.
    If you add, remove, or modify any lines, ensure you update the corresponding
    line numbers in the `code-block` element of the referenced file.
*/

class JoinExamples {
    fun joinAndCount() {
        ActorsIntIdTable.join(
            StarWarsFilmsIntIdTable,
            JoinType.INNER,
            onColumn = ActorsIntIdTable.sequelId,
            otherColumn = StarWarsFilmsIntIdTable.sequelId
        )
            .select(ActorsIntIdTable.name.count(), StarWarsFilmsIntIdTable.name)
            .groupBy(StarWarsFilmsIntIdTable.name)
    }

    fun useAdditionalConstraint() {
        ActorsIntIdTable.join(
            StarWarsFilmsIntIdTable,
            JoinType.INNER,
            additionalConstraint = { StarWarsFilmsIntIdTable.sequelId eq ActorsIntIdTable.sequelId }
        )
            .select(ActorsIntIdTable.name.count(), StarWarsFilmsIntIdTable.name)
            .groupBy(StarWarsFilmsIntIdTable.name)
    }

    fun innerJoin() {
        (ActorsIntIdTable innerJoin RolesTable)
            .select(RolesTable.characterName.count(), ActorsIntIdTable.name)
            .groupBy(ActorsIntIdTable.name)
            .toList()
    }

    fun join() {
        ActorsIntIdTable.join(RolesTable, JoinType.INNER, onColumn = ActorsIntIdTable.id, otherColumn = RolesTable.actorId)
            .select(RolesTable.characterName.count(), ActorsIntIdTable.name)
            .groupBy(ActorsIntIdTable.name)
            .toList()
    }

    fun union() {
        val lucasDirectedQuery =
            StarWarsFilmsIntIdTable.select(StarWarsFilmsIntIdTable.name).where { StarWarsFilmsIntIdTable.director eq "George Lucas" }
        val abramsDirectedQuery =
            StarWarsFilmsIntIdTable.select(StarWarsFilmsIntIdTable.name).where { StarWarsFilmsIntIdTable.director eq "J.J. Abrams" }
        val filmNames = lucasDirectedQuery.union(abramsDirectedQuery).map { it[StarWarsFilmsIntIdTable.name] }
        println(filmNames)

        val originalTrilogyQuery =
            StarWarsFilmsIntIdTable.select(StarWarsFilmsIntIdTable.name).where { StarWarsFilmsIntIdTable.sequelId inList (3..5) }
        val allFilmNames = lucasDirectedQuery.unionAll(originalTrilogyQuery).map { it[StarWarsFilmsIntIdTable.name] }
        println(allFilmNames)
    }
}

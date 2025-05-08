package org.example.examples

import org.example.tables.StarWarsFilmsTable
import org.jetbrains.exposed.v1.sql.JoinType
import org.jetbrains.exposed.v1.sql.alias
import org.jetbrains.exposed.v1.sql.selectAll

class AliasExamples {
    fun useAlias() {
        val filmTable1 = StarWarsFilmsTable.alias("ft1")
        val allFilms = filmTable1.selectAll() // can be used in joins etc'
        println(allFilms)

        // use the same table in a join multiple times
        val sequelTable = StarWarsFilmsTable.alias("sql")
        val originalAndSequelNames = StarWarsFilmsTable
            .join(sequelTable, JoinType.INNER, StarWarsFilmsTable.sequelId, sequelTable[StarWarsFilmsTable.id])
            .select(StarWarsFilmsTable.name, sequelTable[StarWarsFilmsTable.name])
            .map { it[StarWarsFilmsTable.name] to it[sequelTable[StarWarsFilmsTable.name]] }
        println(originalAndSequelNames)

        // selecting from subqueries
        val starWarsFilms = StarWarsFilmsTable
            .select(StarWarsFilmsTable.id, StarWarsFilmsTable.name)
            .alias("swf")
        val id = starWarsFilms[StarWarsFilmsTable.id]
        val name = starWarsFilms[StarWarsFilmsTable.name]
        val allStarWarsFilms = starWarsFilms
            .select(id, name)
            .map { it[id] to it[name] }

        allStarWarsFilms.forEach { println("${it.first} -> ${it.second}") }
    }
}

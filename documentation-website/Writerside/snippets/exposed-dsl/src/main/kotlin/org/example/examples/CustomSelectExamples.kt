package org.example.examples

import org.example.tables.StarWarsFilmsIntIdTable
import org.jetbrains.exposed.v1.Query
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.selectAll
import org.jetbrains.exposed.v1.transactions.transaction

/*
    Important: The contents of this file are referenced by line number in `DSL-Querying-Data.topic`.
    If you add, remove, or modify any lines, ensure you update the corresponding
    line numbers in the `code-block` element of the referenced file.
*/

private const val MOVIE_SEQUEL_ID = 8

fun Query.indexHint(hint: String) = IndexHintQuery(this, hint)

class IndexHintQuery(
    val source: Query,
    val indexHint: String
) : Query(source.set, source.where) {

    init {
        // copies any stored properties from the original query
        source.copyTo(this)
    }

    override fun prepareSQL(builder: QueryBuilder): String {
        val originalSql = super.prepareSQL(builder)
        val fromTableSql = " FROM ${transaction.identity(set.source as Table)} "
        return originalSql.replace(fromTableSql, "$fromTableSql$indexHint ")
    }

    override fun copy(): IndexHintQuery = IndexHintQuery(source.copy(), indexHint).also { copy ->
        copyTo(copy)
    }
}

class CustomSelectExamples {
    fun useCustomQueryWithHint() {
        val originalQuery = StarWarsFilmsIntIdTable
            .selectAll()
            .withDistinct()
            .where { StarWarsFilmsIntIdTable.sequelId less MOVIE_SEQUEL_ID }
            .groupBy(StarWarsFilmsIntIdTable.id)

        /*
            SELECT DISTINCT star_wars_films_table.id, star_wars_films_table.sequel_id, star_wars_films_table.`name`, star_wars_films_table.director
            FROM star_wars_films_table
            FORCE INDEX (PRIMARY) WHERE star_wars_films_table.sequel_id < 8
            GROUP BY star_wars_films_table.id
            ORDER BY star_wars_films_table.sequel_id ASC
         */
        originalQuery.indexHint("FORCE INDEX (PRIMARY)")
            .orderBy(StarWarsFilmsIntIdTable.sequelId)
            .forEach { println(it[StarWarsFilmsIntIdTable.name]) }
    }
}

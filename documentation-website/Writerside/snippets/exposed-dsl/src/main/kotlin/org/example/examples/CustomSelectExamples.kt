package org.example.examples

import org.example.tables.StarWarsFilmsTable
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

/*
    Important: The contents of this file are referenced by line number in `DSL-Querying-Data.topic`.
    If you add, remove, or modify any lines, ensure you update the corresponding
    line numbers in the `code-block` element of the referenced file.
*/

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
        transaction {
            val originalQuery = StarWarsFilmsTable
                .selectAll()
                .withDistinct()
                .where { StarWarsFilmsTable.sequelId less 8 }
                .groupBy(StarWarsFilmsTable.director)

            val queryWithHint = originalQuery
                .indexHint("FORCE INDEX (PRIMARY)")
                .orderBy(StarWarsFilmsTable.sequelId)
        }
    }
}



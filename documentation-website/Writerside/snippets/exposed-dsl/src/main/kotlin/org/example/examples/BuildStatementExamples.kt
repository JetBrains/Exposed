package org.example.examples

import org.example.tables.StarWarsFilmsTable
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.statements.buildStatement
import org.jetbrains.exposed.v1.jdbc.*

/*
    Important: The contents of this file are referenced by line number in `DSL-Statement-Builder.md`.
    If you add, remove, or modify any lines, ensure you update the corresponding
    line numbers in the `code-block` element of the referenced file.
*/

@Suppress("MagicNumber")
class BuildStatementExamples {
    fun Transaction.prepareQuerySql() {
        val filmQuery = StarWarsFilmsTable
            .selectAll()
            .where { StarWarsFilmsTable.sequelId lessEq 3 }

        val querySql: String = filmQuery
            .orWhere { StarWarsFilmsTable.sequelId greater 6 }
            .prepareSQL(this)
        println(querySql)

        /*
            SELECT STARWARSFILMS.ID, STARWARSFILMS.SEQUEL_ID, STARWARSFILMS."name", STARWARSFILMS.DIRECTOR
            FROM STARWARSFILMS
            WHERE (STARWARSFILMS.SEQUEL_ID <= ?) OR (STARWARSFILMS.SEQUEL_ID > ?)
         */

        val fullQuerySql = filmQuery
            .orWhere { StarWarsFilmsTable.sequelId greater 6 }
            .prepareSQL(this, prepared = false)
        println(fullQuerySql)

        /*
            SELECT STARWARSFILMS.ID, STARWARSFILMS.SEQUEL_ID, STARWARSFILMS."name", STARWARSFILMS.DIRECTOR
            FROM STARWARSFILMS
            WHERE (STARWARSFILMS.SEQUEL_ID <= 3) OR (STARWARSFILMS.SEQUEL_ID > 6)
         */
    }

    fun Transaction.prepareInsertSql() {
        val insertFilm = buildStatement {
            StarWarsFilmsTable.insert {
                it[sequelId] = 8
                it[name] = "The Last Jedi"
                it[director] = "Rian Johnson"
            }
        }

        val preparedSql: String = insertFilm.prepareSQL(this, prepared = true)
        println(preparedSql)

        /*
            INSERT INTO STARWARSFILMS (SEQUEL_ID, "name", DIRECTOR) VALUES (?, ?, ?)
         */

        val fullSql: String = insertFilm.prepareSQL(this, prepared = false)
        println(fullSql)

        /*
            INSERT INTO STARWARSFILMS (SEQUEL_ID, "name", DIRECTOR) VALUES (8, 'The Last Jedi', 'Rian Johnson')
         */
    }
}

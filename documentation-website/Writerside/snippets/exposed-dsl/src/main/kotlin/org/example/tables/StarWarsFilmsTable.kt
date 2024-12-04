package org.example.tables

import org.jetbrains.exposed.sql.Table

/*
    Important: This file is referenced by line number in `DSL-Table-Types.topic`.
    If you add, remove, or modify any lines, ensure you update the corresponding
    line numbers in the `code-block` element of the referenced file.

     CREATE TABLE IF NOT EXISTS STARWARSFILMS
     (ID INT AUTO_INCREMENT NOT NULL,
      SEQUEL_ID INT NOT NULL,
      "name" VARCHAR(50) NOT NULL,
      DIRECTOR VARCHAR(50) NOT NULL)
*/

const val MAX_VARCHAR_LENGTH = 50

object StarWarsFilmsTable : Table() {
    val id = integer("id").autoIncrement()
    val sequelId = integer("sequel_id").uniqueIndex()
    val name = varchar("name", MAX_VARCHAR_LENGTH)
    val director = varchar("director", MAX_VARCHAR_LENGTH)
}

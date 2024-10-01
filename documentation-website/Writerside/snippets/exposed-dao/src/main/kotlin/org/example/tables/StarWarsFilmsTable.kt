package org.example.tables

import org.jetbrains.exposed.dao.id.IntIdTable

const val MAX_VARCHAR_LENGTH = 50

/*
CREATE TABLE IF NOT EXISTS STARWARSFILMS
(ID INT AUTO_INCREMENT PRIMARY KEY,
SEQUEL_ID INT NOT NULL,
"name" VARCHAR(50) NOT NULL,
DIRECTOR VARCHAR(50) NOT NULL);
*/
object StarWarsFilmsTable : IntIdTable() {
    val sequelId = integer("sequel_id").uniqueIndex()
    val name = varchar("name", MAX_VARCHAR_LENGTH)
    val director = varchar("director", MAX_VARCHAR_LENGTH)
}

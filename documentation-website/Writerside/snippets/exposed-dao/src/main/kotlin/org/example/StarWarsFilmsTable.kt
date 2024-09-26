package org.example

import org.jetbrains.exposed.dao.id.IntIdTable

/*
CREATE TABLE IF NOT EXISTS STARWARSFILMS
(ID INT AUTO_INCREMENT PRIMARY KEY,
SEQUEL_ID INT NOT NULL,
"name" VARCHAR(50) NOT NULL,
DIRECTOR VARCHAR(50) NOT NULL);
*/
object StarWarsFilms : IntIdTable() {
    val sequelId = integer("sequel_id").uniqueIndex()
    val name = varchar("name", 50)
    val director = varchar("director", 50)
}

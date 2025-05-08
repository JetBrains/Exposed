import org.jetbrains.exposed.v1.sql.Table

const val MAX_VARCHAR_LENGTH = 50

/*
    CREATE TABLE IF NOT EXISTS ALL_STAR_WARS_FILMS
    (ID INT AUTO_INCREMENT NOT NULL,
    SEQUEL_ID INT NOT NULL,
    "name" VARCHAR(50) NOT NULL,
    DIRECTOR VARCHAR(50) NOT NULL)
 */

object CustomStarWarsFilmsTable : Table("all_star_wars_films") {
    val id = integer("id").autoIncrement()
    val sequelId = integer("sequel_id").uniqueIndex()
    val name = varchar("name", MAX_VARCHAR_LENGTH)
    val director = varchar("director", MAX_VARCHAR_LENGTH)
}

/*
    CREATE TABLE IF NOT EXISTS "all_star_wars_films"
    (ID INT AUTO_INCREMENT NOT NULL,
    SEQUEL_ID INT NOT NULL,
    "name" VARCHAR(50) NOT NULL,
    DIRECTOR VARCHAR(50) NOT NULL)
 */

object StarWarsFilms : Table("\"all_star_wars_films\"") {
    val id = integer("id").autoIncrement()
    val sequelId = integer("sequel_id").uniqueIndex()
    val name = varchar("name", MAX_VARCHAR_LENGTH)
    val director = varchar("director", MAX_VARCHAR_LENGTH)
}

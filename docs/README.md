<img src="./logo.png" alt="Exposed" width="315" />

[![JetBrains team project](https://jb.gg/badges/team.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![Kotlinlang Slack Channel](https://img.shields.io/badge/slack-@kotlinlang/exposed-yellow.svg?logo=slack?style=flat)](https://kotlinlang.slack.com/archives/C0CG7E0A1)
[![TC Build status](https://teamcity.jetbrains.com/app/rest/builds/buildType:(id:KotlinTools_Exposed_Build)/statusIcon)](https://teamcity.jetbrains.com/viewType.html?buildTypeId=KotlinTools_Exposed_Build&guest=1) 
[![Download](https://api.bintray.com/packages/kotlin/exposed/exposed-core/images/download.svg) ](https://bintray.com/kotlin/exposed/exposed-core/_latestVersion)
[![GitHub License](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](https://www.apache.org/licenses/LICENSE-2.0)

Welcome to **Exposed**, an ORM framework for 
[Kotlin](https://github.com/JetBrains/kotlin).
Exposed offers two levels of database access: typesafe SQL
wrapping DSL and lightweight data access objects.
Our official mascot is Cuttlefish, which is best known for its
outstanding mimicry abilities letting it blend seamlessly in
any environment. Just like our mascot, Exposed can mimic a variety
of database engines and help you build database applications
without hard dependencies on any specific database engine, and
switch between them with very little or no changes in your code.

## Supported Databases

* H2
* MySQL
* MariaDB
* [Oracle](ORACLE.md)
* PostgreSQL
* PostgreSQL using the [pgjdbc-ng](https://github.com/impossibl/pgjdbc-ng) JDBC driver
* [SQL Server](SQLServer.md)
* SQLite

## Links

Exposed is currently available for **maven/gradle builds** at https://bintray.com/kotlin/exposed (read [Getting started](https://github.com/JetBrains/Exposed/wiki/Getting-Started#download)).

* [Wiki](https://github.com/JetBrains/Exposed/wiki) with examples and docs. 
* [Roadmap](ROADMAP.md) to see what's coming next.
* [Change log](ChangeLog.md) of improvements and bug fixes.

If you have any questions feel free to ask at our [#exposed](https://kotlinlang.slack.com/archives/C0CG7E0A1) channel on [kotlinlang.slack.com](https://kotlinlang.slack.com).


## Examples

### SQL DSL

```kotlin
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

object Users : Table() {
    val id = varchar("id", 10) // Column<String>
    val name = varchar("name", length = 50) // Column<String>
    val cityId = (integer("city_id") references Cities.id).nullable() // Column<Int?>

    override val primaryKey = PrimaryKey(id, name = "PK_User_ID") // name is optional here
}

object Cities : Table() {
    val id = integer("id").autoIncrement() // Column<Int>
    val name = varchar("name", 50) // Column<String>

    override val primaryKey = PrimaryKey(id, name = "PK_Cities_ID")
}

fun main() {
    Database.connect("jdbc:h2:mem:test", driver = "org.h2.Driver", user = "root", password = "")

    transaction {
        addLogger(StdOutSqlLogger)

        SchemaUtils.create (Cities, Users)

        val saintPetersburgId = Cities.insert {
            it[name] = "St. Petersburg"
        } get Cities.id

        val munichId = Cities.insert {
            it[name] = "Munich"
        } get Cities.id

        val pragueId = Cities.insert {
            it.update(name, stringLiteral("   Prague   ").trim().substring(1, 2))
        }[Cities.id]

        val pragueName = Cities.select { Cities.id eq pragueId }.single()[Cities.name]
        assertEquals(pragueName, "Pr")

        Users.insert {
            it[id] = "andrey"
            it[name] = "Andrey"
            it[Users.cityId] = saintPetersburgId
        }

        Users.insert {
            it[id] = "sergey"
            it[name] = "Sergey"
            it[Users.cityId] = munichId
        }

        Users.insert {
            it[id] = "eugene"
            it[name] = "Eugene"
            it[Users.cityId] = munichId
        }

        Users.insert {
            it[id] = "alex"
            it[name] = "Alex"
            it[Users.cityId] = null
        }

        Users.insert {
            it[id] = "smth"
            it[name] = "Something"
            it[Users.cityId] = null
        }

        Users.update({ Users.id eq "alex"}) {
            it[name] = "Alexey"
        }

        Users.deleteWhere{ Users.name like "%thing"}

        println("All cities:")

        for (city in Cities.selectAll()) {
            println("${city[Cities.id]}: ${city[Cities.name]}")
        }

        println("Manual join:")
        (Users innerJoin Cities).slice(Users.name, Cities.name).
            select {(Users.id.eq("andrey") or Users.name.eq("Sergey")) and
                    Users.id.eq("sergey") and Users.cityId.eq(Cities.id)}.forEach {
            println("${it[Users.name]} lives in ${it[Cities.name]}")
        }

        println("Join with foreign key:")


        (Users innerJoin Cities).slice(Users.name, Users.cityId, Cities.name).
                select { Cities.name.eq("St. Petersburg") or Users.cityId.isNull()}.forEach {
            if (it[Users.cityId] != null) {
                println("${it[Users.name]} lives in ${it[Cities.name]}")
            }
            else {
                println("${it[Users.name]} lives nowhere")
            }
        }

        println("Functions and group by:")

        ((Cities innerJoin Users).slice(Cities.name, Users.id.count()).selectAll().groupBy(Cities.name)).forEach {
            val cityName = it[Cities.name]
            val userCount = it[Users.id.count()]

            if (userCount > 0) {
                println("$userCount user(s) live(s) in $cityName")
            } else {
                println("Nobody lives in $cityName")
            }
        }

        SchemaUtils.drop (Users, Cities)
    }
}

```

Generated SQL:
```sql
    SQL: CREATE TABLE IF NOT EXISTS Cities (id INT AUTO_INCREMENT NOT NULL, name VARCHAR(50) NOT NULL, CONSTRAINT PK_Cities_ID PRIMARY KEY (id))
    SQL: CREATE TABLE IF NOT EXISTS Users (id VARCHAR(10) NOT NULL, name VARCHAR(50) NOT NULL, city_id INT NULL, CONSTRAINT PK_User_ID PRIMARY KEY (id))
    SQL: ALTER TABLE Users ADD FOREIGN KEY (city_id) REFERENCES Cities(id)
    SQL: INSERT INTO Cities (name) VALUES ('St. Petersburg')
    SQL: INSERT INTO Cities (name) VALUES ('Munich')
    SQL: INSERT INTO Cities (name) VALUES ('Prague')
    SQL: INSERT INTO Users (id, name, city_id) VALUES ('andrey', 'Andrey', 1)
    SQL: INSERT INTO Users (id, name, city_id) VALUES ('sergey', 'Sergey', 2)
    SQL: INSERT INTO Users (id, name, city_id) VALUES ('eugene', 'Eugene', 2)
    SQL: INSERT INTO Users (id, name, city_id) VALUES ('alex', 'Alex', NULL)
    SQL: INSERT INTO Users (id, name, city_id) VALUES ('smth', 'Something', NULL)
    SQL: UPDATE Users SET name='Alexey' WHERE Users.id = 'alex'
    SQL: DELETE FROM Users WHERE Users.name LIKE '%thing'
    All cities:
    SQL: SELECT Cities.id, Cities.name FROM Cities
    1: St. Petersburg
    2: Munich
    3: Prague
    Manual join:
    SQL: SELECT Users.name, Cities.name FROM Users INNER JOIN Cities ON Cities.id = Users.city_id WHERE ((Users.id = 'andrey') or (Users.name = 'Sergey')) and Users.id = 'sergey' and Users.city_id = Cities.id
    Sergey lives in Munich
    Join with foreign key:
    SQL: SELECT Users.name, Users.city_id, Cities.name FROM Users INNER JOIN Cities ON Cities.id = Users.city_id WHERE (Cities.name = 'St. Petersburg') or (Users.city_id IS NULL)
    Andrey lives in St. Petersburg
    Functions and group by:
    SQL: SELECT Cities.name, COUNT(Users.id) FROM Cities INNER JOIN Users ON Cities.id = Users.city_id GROUP BY Cities.name
    1 user(s) live(s) in St. Petersburg
    2 user(s) live(s) in Munich
    SQL: DROP TABLE Users
    SQL: DROP TABLE Cities
```

### DAO

```kotlin
import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

object Users : IntIdTable() {
    val name = varchar("name", 50).index()
    val city = reference("city", Cities)
    val age = integer("age")
}

object Cities: IntIdTable() {
    val name = varchar("name", 50)
}

class User(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<User>(Users)

    var name by Users.name
    var city by City referencedOn Users.city
    var age by Users.age
}

class City(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<City>(Cities)

    var name by Cities.name
    val users by User referrersOn Users.city
}

fun main() {
    Database.connect("jdbc:h2:mem:test", driver = "org.h2.Driver", user = "root", password = "")

    transaction {
        addLogger(StdOutSqlLogger)

        SchemaUtils.create (Cities, Users)

        val stPete = City.new {
            name = "St. Petersburg"
        }

        val munich = City.new {
            name = "Munich"
        }

        User.new {
            name = "a"
            city = stPete
            age = 5
        }

        User.new {
            name = "b"
            city = stPete
            age = 27
        }

        User.new {
            name = "c"
            city = munich
            age = 42
        }

        println("Cities: ${City.all().joinToString {it.name}}")
        println("Users in ${stPete.name}: ${stPete.users.joinToString {it.name}}")
        println("Adults: ${User.find { Users.age greaterEq 18 }.joinToString {it.name}}")
    }
}
```

Generated SQL:
```sql
    SQL: CREATE TABLE IF NOT EXISTS Cities (id INT AUTO_INCREMENT NOT NULL, name VARCHAR(50) NOT NULL, CONSTRAINT pk_Cities PRIMARY KEY (id))
    SQL: CREATE TABLE IF NOT EXISTS Users (id INT AUTO_INCREMENT NOT NULL, name VARCHAR(50) NOT NULL, city INT NOT NULL, age INT NOT NULL, CONSTRAINT pk_Users PRIMARY KEY (id))
    SQL: CREATE INDEX Users_name ON Users (name)
    SQL: ALTER TABLE Users ADD FOREIGN KEY (city) REFERENCES Cities(id)
    SQL: INSERT INTO Cities (name) VALUES ('St. Petersburg'),('Munich')
    SQL: SELECT Cities.id, Cities.name FROM Cities
    Cities: St. Petersburg, Munich
    SQL: INSERT INTO Users (name, city, age) VALUES ('a', 1, 5),('b', 1, 27),('c', 2, 42)
    SQL: SELECT Users.id, Users.name, Users.city, Users.age FROM Users WHERE Users.city = 1
    Users in St. Petersburg: a, b
    SQL: SELECT Users.id, Users.name, Users.city, Users.age FROM Users WHERE Users.age >= 18
    Adults: b, c
```

## License

Apache License, Version 2.0, ([LICENSE](LICENSE.txt) or https://www.apache.org/licenses/LICENSE-2.0)

<div align="center">
<img  align="center" src="./logo.png" alt="Exposed" width="315" /></div>
<br><br>

[![JetBrains team project](https://jb.gg/badges/team.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![Kotlinlang Slack Channel](https://img.shields.io/badge/slack-@kotlinlang/exposed-yellow.svg?logo=slack?style=flat)](https://kotlinlang.slack.com/archives/C0CG7E0A1)
[![TC Build status](https://teamcity.jetbrains.com/app/rest/builds/buildType:(id:KotlinTools_Exposed_Build)/statusIcon)](https://teamcity.jetbrains.com/viewType.html?buildTypeId=KotlinTools_Exposed_Build&guest=1)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.jetbrains.exposed/exposed-core/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.jetbrains.exposed/exposed-core)
[![GitHub License](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](https://www.apache.org/licenses/LICENSE-2.0)

## Welcome to **Exposed**, an ORM framework for [Kotlin](https://github.com/JetBrains/kotlin).

Exposed is a lightweight SQL library on top of JDBC driver for Kotlin language.
Exposed has two flavors of database access: typesafe SQL wrapping DSL and lightweight Data Access Objects (DAO).

With Exposed you can have two levels of databases Access. You would like to use exposed because the database access includes wrapping DSL and a lightweight data access object. Also, our official mascot is Cuttlefish, which is well known for its outstanding mimicry ability that enables it to blend seamlessly in any environment. 
Similar to our mascot, Exposed can be used to mimic a variety of database engines and help you build applications without dependencies on any specific database engine and switch between them with very little or no changes.

## Supported Databases

-   ![Postgres](https://img.shields.io/badge/postgres-%23316192.svg?style=for-the-badge&logo=postgresql&logoColor=white) (Also, PostgreSQL using the [pgjdbc-ng](https://github.com/impossibl/pgjdbc-ng) JDBC driver)
-   ![MySQL](https://img.shields.io/badge/mysql-%2300f.svg?style=for-the-badge&logo=mysql&logoColor=white)
-   ![MariaDB](https://img.shields.io/badge/MariaDB-003545?style=for-the-badge&logo=mariadb&logoColor=white)
-   ![SQLite](https://img.shields.io/badge/sqlite-%2307405e.svg?style=for-the-badge&logo=sqlite&logoColor=white)
-   H2 (versions 2.x; 1.x version is deprecated and will be removed in future releases)
-   [Oracle](ORACLE.md)
-   [SQL Server](SQLServer.md)

## Links

Currently, Exposed is available for **maven/gradle builds**. Check the [Maven Central](https://search.maven.org/search?q=g:org.jetbrains.exposed) and read ([Getting started](https://github.com/JetBrains/Exposed/wiki/Getting-Started#download)) to get an insight on setting up Exposed.
<br><br>
For more information visit the links below:

-   [Wiki](https://github.com/JetBrains/Exposed/wiki) with examples and docs
-   [Roadmap](ROADMAP.md) to see what's coming next
-   [Change log](ChangeLog.md) of improvements and bug fixes
-   [Slack Channel](https://kotlinlang.slack.com/archives/C0CG7E0A1)
-   [Issue Tracker](https://youtrack.jetbrains.com/issues/EXPOSED)
<br><br>

## Filing issues

Please note that we are moving away from GitHub Issues for reporting of bugs and features. Please log any new requests on [YouTrack](https://youtrack.jetbrains.com/issues/EXPOSED). 

## Community

Do you have questions? Feel free to [request an invitation](https://surveys.jetbrains.com/s3/kotlin-slack-sign-up) for the [kotlinlang slack](https://kotlinlang.slack.com/) and join the project conversation at our [#exposed](https://kotlinlang.slack.com/archives/C0CG7E0A1) channel.
<br><br>

## Pull requests

We actively welcome your pull requests. However, linking your work to an existing issue is preferred.

-   Fork the repo and create your branch from main.
-   Name your branch something that is descriptive to the work you are doing. i.e. adds-new-thing.
-   If you've added code that should be tested, add tests and ensure the test suite passes.
-   Make sure you address any lint warnings.
-   If you make the existing code better, please let us know in your PR description.

## Examples

### SQL DSL

```kotlin
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.transaction

object Users : Table() {
    val id: Column<String> = varchar("id", 10)
    val name: Column<String> = varchar("name", length = 50)
    val cityId: Column<Int?> = (integer("city_id") references Cities.id).nullable()

    override val primaryKey = PrimaryKey(id, name = "PK_User_ID") // name is optional here
}

object Cities : Table() {
    val id: Column<Int> = integer("id").autoIncrement()
    val name: Column<String> = varchar("name", 50)

    override val primaryKey = PrimaryKey(id, name = "PK_Cities_ID")
}

fun main() {
    Database.connect("jdbc:h2:mem:test", driver = "org.h2.Driver", user = "root", password = "")

    transaction {
        addLogger(StdOutSqlLogger)

        SchemaUtils.create(Cities, Users)

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
        println("pragueName = $pragueName")

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

        Users.update({ Users.id eq "alex" }) {
            it[name] = "Alexey"
        }

        Users.deleteWhere{ Users.name like "%thing" }

        println("All cities:")

        for (city in Cities.selectAll()) {
            println("${city[Cities.id]}: ${city[Cities.name]}")
        }

        println("Manual join:")
        
        (Users innerJoin Cities)
            .slice(Users.name, Cities.name)
            .select {
                (Users.id.eq("andrey") or Users.name.eq("Sergey")) and
                    Users.id.eq("sergey") and Users.cityId.eq(Cities.id)
            }.forEach { 
                println("${it[Users.name]} lives in ${it[Cities.name]}") 
            }

        println("Join with foreign key:")

        (Users innerJoin Cities)
            .slice(Users.name, Users.cityId, Cities.name)
            .select { Cities.name.eq("St. Petersburg") or Users.cityId.isNull() }
            .forEach { 
                if (it[Users.cityId] != null) { 
                    println("${it[Users.name]} lives in ${it[Cities.name]}") 
                } 
                else { 
                    println("${it[Users.name]} lives nowhere") 
                } 
            }

        println("Functions and group by:")

        ((Cities innerJoin Users)
            .slice(Cities.name, Users.id.count())
            .selectAll()
            .groupBy(Cities.name)
            ).forEach {
                val cityName = it[Cities.name]
                val userCount = it[Users.id.count()]

                if (userCount > 0) {
                    println("$userCount user(s) live(s) in $cityName")
                } else {
                    println("Nobody lives in $cityName")
                }
            }

        SchemaUtils.drop(Users, Cities)
    }
}

```

Generated SQL:

```sql
    SQL: CREATE TABLE IF NOT EXISTS Cities (id INT AUTO_INCREMENT, name VARCHAR(50) NOT NULL, CONSTRAINT PK_Cities_ID PRIMARY KEY (id))
    SQL: CREATE TABLE IF NOT EXISTS Users (id VARCHAR(10), name VARCHAR(50) NOT NULL, city_id INT NULL, CONSTRAINT PK_User_ID PRIMARY KEY (id), CONSTRAINT FK_Users_city_id__ID FOREIGN KEY (city_id) REFERENCES Cities(id) ON DELETE RESTRICT ON UPDATE RESTRICT)
    SQL: INSERT INTO Cities (name) VALUES ('St. Petersburg')
    SQL: INSERT INTO Cities (name) VALUES ('Munich')
    SQL: INSERT INTO Cities (name) VALUES (SUBSTRING(TRIM('   Prague   '), 1, 2))
    SQL: SELECT Cities.id, Cities.name FROM Cities WHERE Cities.id = 3
    pragueName = Pr
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
    3: Pr
    Manual join:
    SQL: SELECT Users.name, Cities.name FROM Users INNER JOIN Cities ON Cities.id = Users.city_id WHERE ((Users.id = 'andrey') or (Users.name = 'Sergey')) and (Users.id = 'sergey') and (Users.city_id = Cities.id)
    Sergey lives in Munich
    Join with foreign key:
    SQL: SELECT Users.name, Users.city_id, Cities.name FROM Users INNER JOIN Cities ON Cities.id = Users.city_id WHERE (Cities.name = 'St. Petersburg') or (Users.city_id IS NULL)
    Andrey lives in St. Petersburg
    Functions and group by:
    SQL: SELECT Cities.name, COUNT(Users.id) FROM Cities INNER JOIN Users ON Cities.id = Users.city_id GROUP BY Cities.name
    1 user(s) live(s) in St. Petersburg
    2 user(s) live(s) in Munich
    SQL: DROP TABLE IF EXISTS Users
    SQL: DROP TABLE IF EXISTS Cities
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

        SchemaUtils.create(Cities, Users)

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

        println("Cities: ${City.all().joinToString { it.name }}")
        println("Users in ${stPete.name}: ${stPete.users.joinToString { it.name }}")
        println("Adults: ${User.find { Users.age greaterEq 18 }.joinToString { it.name }}")
    }
}
```

Generated SQL:

```sql
    SQL: CREATE TABLE IF NOT EXISTS Cities (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(50) NOT NULL)
    SQL: CREATE TABLE IF NOT EXISTS Users (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(50) NOT NULL, city INT NOT NULL, age INT NOT NULL, CONSTRAINT FK_Users_city__ID FOREIGN KEY (city) REFERENCES Cities(id) ON DELETE RESTRICT ON UPDATE RESTRICT)
    SQL: CREATE INDEX Users_name ON Users (name)
    SQL: INSERT INTO Cities (name) VALUES ('St. Petersburg')
    SQL: INSERT INTO Cities (name) VALUES ('Munich')
    SQL: SELECT Cities.id, Cities.name FROM Cities
    Cities: St. Petersburg, Munich
    SQL: INSERT INTO Users (name, city, age) VALUES ('a', 1, 5)
    SQL: INSERT INTO Users (name, city, age) VALUES ('b', 1, 27)
    SQL: INSERT INTO Users (name, city, age) VALUES ('c', 2, 42)
    SQL: SELECT Users.id, Users.name, Users.city, Users.age FROM Users WHERE Users.city = 1
    Users in St. Petersburg: a, b
    SQL: SELECT Users.id, Users.name, Users.city, Users.age FROM Users WHERE Users.age >= 18
    Adults: b, c
```

## Contributing
Please see the [contribution guide](https://github.com/JetBrains/Exposed/blob/master/docs/CONTRIBUTING.md) before contributing.

By contributing to the Exposed project, you agree that your contributions will be licensed under [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).

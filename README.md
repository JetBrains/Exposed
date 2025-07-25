<div align="center">

  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="./docs/logo-dark.png" width="315">
    <img alt="Exposed logo" src="./docs/logo-light.png" width="315">
  </picture>

</div>
<br><br>

<div align="center">

[![JetBrains team project](https://jb.gg/badges/team.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![Slack Channel](https://img.shields.io/badge/chat-exposed-yellow.svg?logo=slack)](https://kotlinlang.slack.com/messages/exposed/)
[![TC Build status](https://exposed.teamcity.com/app/rest/builds/buildType:id:Exposed_Build/statusIcon.svg)](https://exposed.teamcity.com/viewType.html?buildTypeId=Exposed_Build&guest=1)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.jetbrains.exposed/exposed-core/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.jetbrains.exposed/exposed-core)
[![GitHub License](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](https://www.apache.org/licenses/LICENSE-2.0)

</div>

## Welcome to **Exposed**, an ORM framework for [Kotlin](https://github.com/JetBrains/kotlin).

[Exposed](https://www.jetbrains.com/exposed/) is a lightweight SQL library on top of a database connectivity driver for the Kotlin programming language,
with support for both JDBC and R2DBC (since version 1.0.0-*) drivers. 
It offers two approaches for database access: a typesafe SQL-wrapping Domain-Specific Language (DSL) and a lightweight Data Access Object (DAO) API.

Our official mascot is the cuttlefish, which is well-known for its outstanding mimicry ability that enables it to blend seamlessly into any environment.
Similar to our mascot, Exposed can be used to mimic a variety of database engines, which helps you to build applications without dependencies on any specific database engine and to switch between them with very little or no changes.

## Supported Databases

- H2 (versions 2.x; 1.x version is deprecated and will be removed in future releases)
- [![MariaDB](https://img.shields.io/badge/MariaDB-003545?style=for-the-badge&logo=mariadb&logoColor=white)](https://github.com/mariadb-corporation/mariadb-connector-j)
- [![MySQL](https://img.shields.io/badge/mysql-4479A1.svg?style=for-the-badge&logo=mysql&logoColor=white)](https://github.com/mysql/mysql-connector-j)
- [![Oracle](https://img.shields.io/badge/Oracle-F80000?style=for-the-badge&logo=oracle&logoColor=white)](https://www.oracle.com/ca-en/database/technologies/appdev/jdbc-downloads.html)
- [![Postgres](https://img.shields.io/badge/postgres-%23316192.svg?style=for-the-badge&logo=postgresql&logoColor=white)](https://jdbc.postgresql.org/)
  (Also, PostgreSQL using the [pgjdbc-ng](https://impossibl.github.io/pgjdbc-ng/) JDBC driver)
- [![MicrosoftSQLServer](https://img.shields.io/badge/Microsoft%20SQL%20Server-CC2927?style=for-the-badge&logo=microsoft%20sql%20server&logoColor=white)](https://github.com/microsoft/mssql-jdbc)
- [![SQLite](https://img.shields.io/badge/sqlite-%2307405e.svg?style=for-the-badge&logo=sqlite&logoColor=white)](https://github.com/xerial/sqlite-jdbc)

## Dependencies

Releases of Exposed are available in the [Maven Central repository](https://search.maven.org/search?q=org.jetbrains.exposed).
For details on how to configure this repository and how to add Exposed dependencies to an existing Gradle/Maven project,
see the full [guide on modules](https://www.jetbrains.com/help/exposed/exposed-modules.html).

### Exposed modules

`Exposed` consists of the following core modules:

| Module          | Function                                                                                                                                                         |
|-----------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `exposed-core`  | Provides the foundational components and abstractions needed to work with databases in a type-safe manner and includes the Domain-Specific Language (DSL) API    |
| `exposed-dao`   | (Optional) Allows you to work with the Data Access Object (DAO) API. <br> It is only compatible with `exposed-jdbc` and does not work with `exposed-r2dbc`.</br> |
| `exposed-jdbc`  | Provides support for Java Database Connectivity (JDBC) with a transport-level implementation based on the Java JDBC API                                          |
| `exposed-r2dbc` | Provides support for Reactive Relational Database Connectivity (R2DBC)                                                                                           |

As well as the following extension modules:

| Module                        | Function                                                                                                                                                                        |
|-------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `exposed-crypt`               | Provides additional column types to store encrypted data in the database and encode/decode it on the client-side                                                                |
| `exposed-java-time`           | Date-time extensions based on the [Java 8 Time API](https://docs.oracle.com/javase/8/docs/api/java/time/package-summary.html)                                                   |
| `exposed-jodatime`            | Date-time extensions based on the [Joda-Time](https://www.joda.org/joda-time/) library                                                                                          |
| `exposed-json`                | JSON and JSONB data type extensions                                                                                                                                             |
| `exposed-kotlin-datetime`     | Date-time extensions based on the [`kotlinx-datetime`](https://kotlinlang.org/api/kotlinx-datetime/) library                                                                    |
| `exposed-migration`           | Provides utilities to support database schema migrations                                                                                                                        |
| `exposed-money`               | Extensions to support [`MonetaryAmount`](https://javamoney.github.io/apidocs/java.money/javax/money/MonetaryAmount.html) from the [JavaMoney API](https://javamoney.github.io/) |
| `exposed-spring-boot-starter` | A starter for [Spring Boot](https://spring.io/projects/spring-boot) to utilize Exposed as the ORM instead of [Hibernate](https://hibernate.org/)                                |
| `spring-transaction`          | Transaction manager that builds on top of Spring's standard transaction workflow                                                                                                |

## Samples using Exposed

Follow the [Getting Started with DSL tutorial](https://www.jetbrains.com/help/exposed/getting-started-with-exposed.html) for a quick start or check out the [samples](samples/README.md) for more in-depth projects.

## Documentation

For complete documentation, samples, and tutorials, see the following links:

-   [Documentation](https://www.jetbrains.com/help/exposed/home.html)
-   [Migration Guide](https://www.jetbrains.com/help/exposed/migration-guide-1-0-0.html)
-   [Breaking changes](https://www.jetbrains.com/help/exposed/breaking-changes.html)

## Reporting Issues / Support

We encourage your feedback in any form, such as feature requests, bug reports, documentation updates, and questions.
Please use [our issue tracker](https://youtrack.jetbrains.com/issues/EXPOSED) to report any issues or to log new requests.
While issues are visible publicly, either creating a new issue or commenting on an existing one does require logging in to YouTrack.

Have questions or want to contribute to the discussion? Join us in the [#exposed](https://kotlinlang.slack.com/messages/exposed/) channel on the [Kotlin Slack](https://kotlinlang.slack.com/).
If you're not a member yet, you can [request an invitation](https://surveys.jetbrains.com/s3/kotlin-slack-sign-up).

## Contributing

We actively welcome your pull requests and encourage you to link your work to an [existing issue](https://youtrack.jetbrains.com/issues/EXPOSED).


See the full [contribution guide](https://www.jetbrains.com/help/exposed/contributing.html) for more details.

By contributing to the Exposed project, you agree that your contributions will be licensed under [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).
<br><br>

## Examples

### SQL DSL

```kotlin
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.like
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object Cities : Table() {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 50)

    override val primaryKey = PrimaryKey(id)
}

object Users : Table() {
    val id = varchar("id", 10)
    val name = varchar("name", length = 50)
    val cityId = integer("city_id").references(Cities.id).nullable()

    override val primaryKey = PrimaryKey(id, name = "PK_User_ID")
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

        val pragueName = Cities
            .selectAll()
            .where { Cities.id eq pragueId }
            .single()[Cities.name]
        println("pragueName = $pragueName")

        Users.insert {
            it[id] = "andrey"
            it[name] = "Andrey"
            it[cityId] = saintPetersburgId
        }

        Users.insert {
            it[id] = "sergey"
            it[name] = "Sergey"
            it[cityId] = munichId
        }

        Users.insert {
            it[id] = "eugene"
            it[name] = "Eugene"
            it[cityId] = munichId
        }

        Users.insert {
            it[id] = "alex"
            it[name] = "Alex"
            it[cityId] = null
        }

        Users.insert {
            it[id] = "smth"
            it[name] = "Something"
            it[cityId] = null
        }

        Users.update(where = { Users.id eq "alex" }) {
            it[name] = "Alexey"
        }

        Users.deleteWhere { Users.name like "%thing" }

        println("All cities:")

        Cities
            .selectAll()
            .forEach { result ->
                println("${result[Cities.id]}: ${result[Cities.name]}")
            }

        println("Manual join:")

        (Users innerJoin Cities)
            .select(Users.name, Cities.name)
            .where {
                (Users.id.eq("andrey") or Users.name.eq("Sergey")) and
                    Users.id.eq("sergey") and Users.cityId.eq(Cities.id)
            }.forEach { result ->
                println("${result[Users.name]} lives in ${result[Cities.name]}")
            }

        println("Join with foreign key:")

        (Users innerJoin Cities)
            .select(Users.name, Users.cityId, Cities.name)
            .where { Cities.name.eq("St. Petersburg") or Users.cityId.isNull() }
            .forEach { result ->
                if (result[Users.cityId] != null) {
                    println("${result[Users.name]} lives in ${result[Cities.name]}")
                } else {
                    println("${result[Users.name]} lives nowhere")
                }
            }

        println("Functions and group by:")

        (Cities innerJoin Users)
            .select(Cities.name, Users.id.count())
            .groupBy(Cities.name)
            .forEach { result ->
                val cityName = result[Cities.name]
                val userCount = result[Users.id.count()]

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
    SQL: CREATE TABLE IF NOT EXISTS CITIES (ID INT AUTO_INCREMENT PRIMARY KEY, "name" VARCHAR(50) NOT NULL)
    SQL: CREATE TABLE IF NOT EXISTS USERS (ID VARCHAR(10), "name" VARCHAR(50) NOT NULL, CITY_ID INT NULL, CONSTRAINT PK_User_ID PRIMARY KEY (ID), CONSTRAINT FK_USERS_CITY_ID__ID FOREIGN KEY (CITY_ID) REFERENCES CITIES(ID) ON DELETE RESTRICT ON UPDATE RESTRICT)
    SQL: INSERT INTO CITIES ("name") VALUES ('St. Petersburg')
    SQL: INSERT INTO CITIES ("name") VALUES ('Munich')
    SQL: INSERT INTO CITIES ("name") VALUES (SUBSTRING(TRIM('   Prague   '), 1, 2))
    SQL: SELECT CITIES.ID, CITIES."name" FROM CITIES WHERE CITIES.ID = 3
    pragueName = Pr
    SQL: INSERT INTO USERS (ID, "name", CITY_ID) VALUES ('andrey', 'Andrey', 1)
    SQL: INSERT INTO USERS (ID, "name", CITY_ID) VALUES ('sergey', 'Sergey', 2)
    SQL: INSERT INTO USERS (ID, "name", CITY_ID) VALUES ('eugene', 'Eugene', 2)
    SQL: INSERT INTO USERS (ID, "name", CITY_ID) VALUES ('alex', 'Alex', NULL)
    SQL: INSERT INTO USERS (ID, "name", CITY_ID) VALUES ('smth', 'Something', NULL)
    SQL: UPDATE USERS SET "name"='Alexey' WHERE USERS.ID = 'alex'
    SQL: DELETE FROM USERS WHERE USERS."name" LIKE '%thing'
    All cities:
    SQL: SELECT CITIES.ID, CITIES."name" FROM CITIES
    1: St. Petersburg
    2: Munich
    3: Pr
    Manual join:
    SQL: SELECT USERS."name", CITIES."name" FROM USERS INNER JOIN CITIES ON CITIES.ID = USERS.CITY_ID WHERE ((USERS.ID = 'andrey') OR (USERS."name" = 'Sergey')) AND (USERS.ID = 'sergey') AND (USERS.CITY_ID = CITIES.ID)
    Sergey lives in Munich
    Join with foreign key:
    SQL: SELECT USERS."name", USERS.CITY_ID, CITIES."name" FROM USERS INNER JOIN CITIES ON CITIES.ID = USERS.CITY_ID WHERE (CITIES."name" = 'St. Petersburg') OR (USERS.CITY_ID IS NULL)
    Andrey lives in St. Petersburg
    Functions and group by:
    SQL: SELECT CITIES."name", COUNT(USERS.ID) FROM CITIES INNER JOIN USERS ON CITIES.ID = USERS.CITY_ID GROUP BY CITIES."name"
    2 user(s) live(s) in Munich
    1 user(s) live(s) in St. Petersburg
    SQL: DROP TABLE IF EXISTS USERS
    SQL: DROP TABLE IF EXISTS CITIES
```

### DAO

```kotlin
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.dao.id.*
import org.jetbrains.exposed.v1.dao.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object Cities: IntIdTable() {
    val name = varchar("name", 50)
}

object Users : IntIdTable() {
    val name = varchar("name", length = 50).index()
    val city = reference("city", Cities)
    val age = integer("age")
}

class City(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<City>(Cities)

    var name by Cities.name
    val users by User referrersOn Users.city
}

class User(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<User>(Users)

    var name by Users.name
    var city by City referencedOn Users.city
    var age by Users.age
}

fun main() {
    Database.connect("jdbc:h2:mem:test", driver = "org.h2.Driver", user = "root", password = "")

    transaction {
        addLogger(StdOutSqlLogger)

        val saintPetersburg = City.new {
            name = "St. Petersburg"
        }

        val munich = City.new {
            name = "Munich"
        }

        User.new {
            name = "Andrey"
            city = saintPetersburg
            age = 5
        }

        User.new {
            name = "Sergey"
            city = saintPetersburg
            age = 27
        }

        User.new {
            name = "Eugene"
            city = munich
            age = 42
        }

        val alex = User.new {
            name = "alex"
            city = munich
            age = 11
        }

        alex.name = "Alexey"

        println("Cities: ${City.all().joinToString { it.name }}")

        println("Users in ${saintPetersburg.name}: ${saintPetersburg.users.joinToString { it.name }}")

        println("Adults: ${User.find { Users.age greaterEq 18 }.joinToString { it.name }}")

        SchemaUtils.drop(Users, Cities)
    }
}
```

Generated SQL:

```sql
    SQL: CREATE TABLE IF NOT EXISTS CITIES (ID INT AUTO_INCREMENT PRIMARY KEY, "name" VARCHAR(50) NOT NULL)
    SQL: CREATE TABLE IF NOT EXISTS USERS (ID INT AUTO_INCREMENT PRIMARY KEY, "name" VARCHAR(50) NOT NULL, CITY INT NOT NULL, AGE INT NOT NULL, CONSTRAINT FK_USERS_CITY__ID FOREIGN KEY (CITY) REFERENCES CITIES(ID) ON DELETE RESTRICT ON UPDATE RESTRICT)
    SQL: CREATE INDEX USERS_NAME ON USERS ("name")
    SQL: INSERT INTO CITIES ("name") VALUES ('St. Petersburg')
    SQL: INSERT INTO CITIES ("name") VALUES ('Munich')
    SQL: SELECT CITIES.ID, CITIES."name" FROM CITIES
    Cities: St. Petersburg, Munich
    SQL: INSERT INTO USERS ("name", CITY, AGE) VALUES ('Andrey', 1, 5)
    SQL: INSERT INTO USERS ("name", CITY, AGE) VALUES ('Sergey', 1, 27)
    SQL: INSERT INTO USERS ("name", CITY, AGE) VALUES ('Eugene', 2, 42)
    SQL: INSERT INTO USERS ("name", CITY, AGE) VALUES ('Alexey', 2, 11)
    SQL: SELECT USERS.ID, USERS."name", USERS.CITY, USERS.AGE FROM USERS WHERE USERS.CITY = 1
    Users in St. Petersburg: Andrey, Sergey
    SQL: SELECT USERS.ID, USERS."name", USERS.CITY, USERS.AGE FROM USERS WHERE USERS.AGE >= 18
    Adults: Sergey, Eugene
    SQL: DROP TABLE IF EXISTS USERS
    SQL: DROP TABLE IF EXISTS CITIES
```

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

Exposed is a lightweight SQL library on top of a JDBC driver for the Kotlin language.
Exposed has two flavors of database access: typesafe SQL wrapping DSL and lightweight Data Access Objects (DAO).

With Exposed, you have two options for database access: wrapping DSL and a lightweight DAO. Our official mascot is the cuttlefish, which is well-known for its outstanding mimicry ability that enables it to blend seamlessly into any environment.
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

### Maven Central configuration

Releases of Exposed are available in the Maven Central repository. You can declare this repository in your build script as follows:

#### Gradle Groovy and Kotlin DSL

**Warning:** You might need to set your Kotlin JVM target to 8, and when using Spring to 17, in order for this to work properly:

```kotlin
repositories {
    // Versions after 0.30.1
    // Versions before 0.30.1 is unavailable for now
    mavenCentral()
}
```

#### Maven

The Maven Central repository is enabled by default for Maven users.

### Exposed modules

`Exposed` consists of the following modules:

* exposed-core - base module, which contains both DSL api along with mapping
* exposed-crypt - provides additional column types to store encrypted data in DB and encode/decode it on client-side
* exposed-dao - DAO api
* exposed-java-time - date-time extensions based on Java8 Time API
* exposed-jdbc - transport level implementation based on Java JDBC API
* exposed-jodatime - date-time extensions based on JodaTime library
* exposed-json - JSON and JSONB data type extensions
* exposed-kotlin-datetime - date-time extensions based on kotlinx-datetime
* exposed-money - extensions to support MonetaryAmount from "javax.money:money-api"
* exposed-spring-boot-starter - a starter for [Spring Boot](https://spring.io/projects/spring-boot) to utilize Exposed as the ORM instead
  of [Hibernate](https://hibernate.org/)

```xml

<dependencies>
    <dependency>
        <groupId>org.jetbrains.exposed</groupId>
        <artifactId>exposed-core</artifactId>
        <version>0.56.0</version>
    </dependency>
    <dependency>
        <groupId>org.jetbrains.exposed</groupId>
        <artifactId>exposed-crypt</artifactId>
        <version>0.56.0</version>
    </dependency>
    <dependency>
        <groupId>org.jetbrains.exposed</groupId>
        <artifactId>exposed-dao</artifactId>
        <version>0.56.0</version>
    </dependency>
    <dependency>
        <groupId>org.jetbrains.exposed</groupId>
        <artifactId>exposed-java-time</artifactId>
        <version>0.56.0</version>
    </dependency>
    <dependency>
        <groupId>org.jetbrains.exposed</groupId>
        <artifactId>exposed-jdbc</artifactId>
        <version>0.56.0</version>
    </dependency>
    <dependency>
        <groupId>org.jetbrains.exposed</groupId>
        <artifactId>exposed-jodatime</artifactId>
        <version>0.56.0</version>
    </dependency>
    <dependency>
        <groupId>org.jetbrains.exposed</groupId>
        <artifactId>exposed-json</artifactId>
        <version>0.56.0</version>
    </dependency>
    <dependency>
        <groupId>org.jetbrains.exposed</groupId>
        <artifactId>exposed-kotlin-datetime</artifactId>
        <version>0.56.0</version>
    </dependency>
    <dependency>
        <groupId>org.jetbrains.exposed</groupId>
        <artifactId>exposed-money</artifactId>
        <version>0.56.0</version>
    </dependency>
    <dependency>
        <groupId>org.jetbrains.exposed</groupId>
        <artifactId>exposed-spring-boot-starter</artifactId>
        <version>0.56.0</version>
    </dependency>
</dependencies>

```

#### Gradle Groovy

```groovy
dependencies {
    implementation 'org.jetbrains.exposed:exposed-core:0.56.0'
    implementation 'org.jetbrains.exposed:exposed-crypt:0.56.0'
    implementation 'org.jetbrains.exposed:exposed-dao:0.56.0'
    implementation 'org.jetbrains.exposed:exposed-jdbc:0.56.0'
    
    implementation 'org.jetbrains.exposed:exposed-jodatime:0.56.0'
    // or
    implementation 'org.jetbrains.exposed:exposed-java-time:0.56.0'
    // or
    implementation 'org.jetbrains.exposed:exposed-kotlin-datetime:0.56.0'
    
    implementation 'org.jetbrains.exposed:exposed-json:0.56.0'
    implementation 'org.jetbrains.exposed:exposed-money:0.56.0'
    implementation 'org.jetbrains.exposed:exposed-spring-boot-starter:0.56.0'
}
```

#### Gradle Kotlin DSL

In `build.gradle.kts`:

```kotlin
val exposedVersion: String by project
dependencies {
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-crypt:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    
    implementation("org.jetbrains.exposed:exposed-jodatime:$exposedVersion")
    // or
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    // or
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:$exposedVersion")
    
    implementation("org.jetbrains.exposed:exposed-json:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-money:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-spring-boot-starter:$exposedVersion")
}
```

and in `gradle.properties`

```
exposedVersion=0.56.0
```

## Samples

Check out the [samples](samples/README.md) for a quick start.

## Links

Currently, Exposed is available for **maven/gradle builds**. Check the [Maven Central](https://search.maven.org/search?q=g:org.jetbrains.exposed) and read [Getting Started](https://jetbrains.github.io/Exposed/getting-started-with-exposed.html) to get an insight on setting up Exposed.
<br><br>
For more information visit the links below:

-   [Documentation](https://jetbrains.github.io/Exposed/home.html) with examples and docs
-   [Contributing to Exposed](#contributing)
-   [Migration Guide](https://jetbrains.github.io/Exposed/migration-guide.html)
-   [Breaking changes](https://jetbrains.github.io/Exposed/breaking-changes.html) and any migration details
-   [Slack Channel](https://kotlinlang.slack.com/messages/exposed/)
-   [Issue Tracker](https://youtrack.jetbrains.com/issues/EXPOSED)
<br><br>

## Filing issues

Please note that we are moving away from GitHub Issues for reporting of bugs and features. Please log any new requests on [YouTrack](https://youtrack.jetbrains.com/issues/EXPOSED). You must be logged in to view and log issues, otherwise you will be met with a 404.

## Community

Do you have questions? Feel free to [request an invitation](https://surveys.jetbrains.com/s3/kotlin-slack-sign-up) for the [kotlinlang slack](https://kotlinlang.slack.com/) and join the project conversation at our [#exposed](https://kotlinlang.slack.com/messages/exposed/) channel.
<br><br>

## Pull requests

We actively welcome your pull requests. However, linking your work to an existing issue is preferred.

-   Fork the repo and create your branch from main.
-   Name your branch something that is descriptive to the work you are doing. i.e. adds-new-thing.
-   If you've added code that should be tested, add tests and ensure the test suite passes.
-   Make sure you address any lint warnings.
-   If you make the existing code better, please let us know in your PR description.

See the [contribution guidelines](https://jetbrains.github.io/Exposed/contributing.html) for more details.

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

        val pragueName = Cities.selectAll().where { Cities.id eq pragueId }.single()[Cities.name]
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
            .select(Users.name, Cities.name)
            .where {
                (Users.id.eq("andrey") or Users.name.eq("Sergey")) and
                    Users.id.eq("sergey") and Users.cityId.eq(Cities.id)
            }.forEach { 
                println("${it[Users.name]} lives in ${it[Cities.name]}") 
            }

        println("Join with foreign key:")

        (Users innerJoin Cities)
            .select(Users.name, Users.cityId, Cities.name)
            .where { Cities.name.eq("St. Petersburg") or Users.cityId.isNull() }
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
            .select(Cities.name, Users.id.count())
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
Please see the [contribution guide](https://jetbrains.github.io/Exposed/contributing.html) before contributing.

By contributing to the Exposed project, you agree that your contributions will be licensed under [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).

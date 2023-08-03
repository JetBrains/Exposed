# Getting Started

## Adding the Exposed dependency

<tabs>
    <tab title="Maven">
        <code-block lang="xml">
<![CDATA[
<repositories>
    <repository>
        <id>mavenCentral</id>
        <name>mavenCentral</name>
        <url>https://repo1.maven.org/maven2/</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
      <groupId>org.jetbrains.exposed</groupId>
      <artifactId>exposed-core</artifactId>
      <version>0.42.0</version>
    </dependency>
    <dependency>
      <groupId>org.jetbrains.exposed</groupId>
      <artifactId>exposed-dao</artifactId>
      <version>0.42.0</version>
    </dependency>
    <dependency>
      <groupId>org.jetbrains.exposed</groupId>
      <artifactId>exposed-jdbc</artifactId>
      <version>0.42.0</version>
    </dependency>
</dependencies>
]]>
</code-block>
    </tab>
    <tab title="Gradle Kotlin Script">
        <code-block lang="kotlin">
<![CDATA[
val exposedVersion: String = "0.42.0"

dependencies {
implementation("org.jetbrains.exposed:exposed-core", exposedVersion)
implementation("org.jetbrains.exposed:exposed-dao", exposedVersion)
implementation("org.jetbrains.exposed:exposed-jdbc", exposedVersion)
}
]]>
</code-block>
    </tab>
</tabs>

- Note: There are other modules. Detailed information is located in [Modules Documentation](Modules-Documentation.md) section.

## Starting a transaction

Every database access using Exposed is started by obtaining a connection and creating a transaction.

To get a connection:

```kotlin
Database.connect("jdbc:h2:mem:test", driver = "org.h2.Driver")
```

It is also possible to provide `javax.sql.DataSource` for advanced behaviors such as connection pooling:

```kotlin
Database.connect(dataSource)
```

More details in [Databases](Databases.md)

After obtaining a connection, all SQL statements should be placed inside a transaction:

```kotlin
transaction {
    // Statements here
}
```

To see the actual database calls, add a logger:

```kotlin
transaction {
    // print SQL to stdout
    addLogger(StdOutSqlLogger)
}
```

## Access Layers

Exposed comes in two flavors: DSL and DAO.

- DSL stands for Domain-Specific Language, and for Exposed it means type-safe syntax similar to SQL statements to access a database. For more
  information, see [Deep Dive into DSL](Deep-Dive-into-DSL.md).

- DAO means Data Access Object, and it allows treating the data from database as entities and performing CRUD operations. For more information, see
  [Deep Dive into DAO](Deep-Dive-into-DAO.md).

To get an idea of the difference, compare the following code samples and corresponding SQL outputs:

<tabs>
<tab title="DSL">
<code-block lang="kotlin">
<![CDATA[
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

// a table Cities with integer IDs and three fields:
// String name, Int population, and Enum country
object Cities : IntIdTable() {
val name = varchar("name", 50).default("Unknown")
val population = integer("population").nullable()
val country = customEnumeration(
"country",
"ENUM('RUSSIA', 'CHINA', 'USA')",
{ Country.values()[it as Int] },
{ it.name }
).nullable()
override val primaryKey = PrimaryKey(id, name = "Cities_ID")
}

enum class Country {
RUSSIA, CHINA, USA
}

fun init() {
Database.connect(
"jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
driver = "org.h2.Driver"
)

    transaction {
        // print sql to std-out
        addLogger(StdOutSqlLogger)

        // create table Cities
        SchemaUtils.create(Cities)

        // insert new city and return id to nyId
        val nyId = Cities.insert {
            it[name] = "New York"
            it[country] = Country.USA
            it[population] = 1000
        } get Cities.id

        // insert new city and return id to moscId
        val mosId = Cities.insertAndGetId {
            it[name] = "Moscow"
            it[country] = Country.RUSSIA
            it[population] = 500
        }

        // insert new city and return id to stPetId
        val stPetId = Cities.insertAndGetId {
            it[name] = "St. Petersburg"
            it[country] = Country.RUSSIA
            it[population] = 300
        }
    }

}

fun main() {
init()
}
]]>
</code-block>
</tab>
<tab title="DAO">
<code-block lang="kotlin">
<![CDATA[
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.transaction


// a table Cities with integer IDs
object Cities : IntIdTable() {
val name = varchar("name", 50).default("Unknown")
val population: Column<Int> = integer("population").uniqueIndex()
val country = Cities.customEnumeration(
"country",
"ENUM('RUSSIA', 'CHINA', 'USA', 'UNKNOWN')",
{ Country.values()[it as Int] },
{ it.name }
).default(Country.UNKNOWN)
override val primaryKey = PrimaryKey(id, name = "Cities_ID")
}
class City(id: EntityID<Int>) : IntEntity(id) {
companion object : IntEntityClass<City>(Cities)

    var name by Cities.name
    var country by Cities.country
    var population by Cities.population

}

enum class Country {
RUSSIA, CHINA, USA, UNKNOWN
}

fun init() {
Database.connect(
"jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
driver = "org.h2.Driver"
)

    transaction {
        // print sql calls to stdout
        addLogger(StdOutSqlLogger)

        // create a table Cities
        SchemaUtils.create(Cities)

        // insert a new city
        val ny = City.new {
            name = "New York"
            country = Country.USA
            population = 1000
        }

        // insert a new city
        val mos = City.new {
            name = "Moscow"
            country = Country.RUSSIA
            population = 500
        }

        // insert a new city
        val stPet = City.new {
            name = "St. Petersburg"
            country = Country.RUSSIA
            population = 300
        }
    }

}

fun main() {
init()
}
]]>
</code-block>
</tab>
</tabs>

SQL output:

<code-block lang="sql">
SQL: SELECT VALUE FROM INFORMATION_SCHEMA.SETTINGS WHERE NAME = 'MODE'
SQL: CREATE TABLE IF NOT EXISTS CITIES (
    ID INT AUTO_INCREMENT,
    "NAME" VARCHAR(50) DEFAULT 'Unknown' NOT NULL,
    POPULATION INT NULL,
    COUNTRY ENUM('RUSSIA', 'CHINA', 'USA') NULL,
    CONSTRAINT Cities_ID PRIMARY KEY (ID)
)
SQL: INSERT INTO CITIES (COUNTRY, "NAME", POPULATION) VALUES ('USA', 'New York', 1000)
SQL: INSERT INTO CITIES (COUNTRY, "NAME", POPULATION) VALUES ('RUSSIA', 'Moscow', 500)
SQL: INSERT INTO CITIES (COUNTRY, "NAME", POPULATION) VALUES ('RUSSIA', 'St. Petersburg', 300)
</code-block>

More on [DSL](Deep-Dive-into-DSL.md) and [DAO](Deep-Dive-into-DAO.md).

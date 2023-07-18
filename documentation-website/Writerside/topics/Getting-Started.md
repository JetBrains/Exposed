# Getting Started

## Adding Dependency

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
      <version>0.40.1</version>
    </dependency>
    <dependency>
      <groupId>org.jetbrains.exposed</groupId>
      <artifactId>exposed-dao</artifactId>
      <version>0.40.1</version>
    </dependency>
    <dependency>
      <groupId>org.jetbrains.exposed</groupId>
      <artifactId>exposed-jdbc</artifactId>
      <version>0.40.1</version>
    </dependency>
</dependencies>
]]>
        </code-block>
    </tab>
    <tab title="Gradle Kotlin Script">
        <code-block lang="kotlin">
<![CDATA[
val exposedVersion: String = "0.40.1"

dependencies {
  implementation("org.jetbrains.exposed:exposed-core", exposedVersion)
  implementation("org.jetbrains.exposed:exposed-dao", exposedVersion)
  implementation("org.jetbrains.exposed:exposed-jdbc", exposedVersion)
}
]]>
        </code-block>
    </tab>
</tabs>

- Note: There are another modules. Detailed information located in [Modules Documentation](Modules-Documentation.md) section.

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

More details on [Database and DataSource](Database-and-DataSource.md)

After obtaining a connection all SQL statements should be placed inside a transaction:

```kotlin
transaction {
  // Statements here
}
```

To see the actual DB calls, add a logger:

```kotlin
transaction {
  // print sql to std-out
  addLogger(StdOutSqlLogger)
}
```

### DSL & DAO

Exposed comes in two flavors: DSL (Domain Specific Language) and DAO (Data Access Object).  
On a high level, DSL means type-safe syntax that is similar to SQL whereas DAO means doing CRUD operations on entities.  
Observe the below examples and head on to the specific section of each API for more details.

### Your first Exposed DSL

```kotlin


fun main(args: Array<String>) {
  //an example connection to H2 DB
  Database.connect("jdbc:h2:mem:test", driver = "org.h2.Driver")

  transaction {
    // print sql to std-out
    addLogger(StdOutSqlLogger)

    SchemaUtils.create (Cities)

    // insert new city. SQL: INSERT INTO Cities (name) VALUES ('St. Petersburg')
    val stPeteId = Cities.insert {
      it[name] = "St. Petersburg"
    } get Cities.id

    // 'select *' SQL: SELECT Cities.id, Cities.name FROM Cities
    println("Cities: ${Cities.selectAll()}")
  }
}

object Cities: IntIdTable() {
    val name = varchar("name", 50)
}

```

More on [DSL API](DSL-API.md)

### Your first Exposed DAO

```kotlin


fun main(args: Array<String>) {
  //an example connection to H2 DB
  Database.connect("jdbc:h2:mem:test", driver = "org.h2.Driver")

  transaction {
    // print sql to std-out
    addLogger(StdOutSqlLogger)

    SchemaUtils.create (Cities)

    // insert new city. SQL: INSERT INTO Cities (name) VALUES ('St. Petersburg')
    val stPete = City.new {
            name = "St. Petersburg"
    }

    // 'select *' SQL: SELECT Cities.id, Cities.name FROM Cities
    println("Cities: ${City.all()}")
  }
}

object Cities: IntIdTable() {
    val name = varchar("name", 50)
}

class City(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<City>(Cities)

    var name by Cities.name
}
```

More information: [DAO API](DAO-API.md)

Read Next [Database and DataSource](Database-and-DataSource.md)

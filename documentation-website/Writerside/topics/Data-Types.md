# Data Types

Exposed supports the following data types in the table definition:
* `integer` - translates to DB `INT`
* `short` - translates to DB `SMALLINT`
* `long` - `BIGINT`
* `float` - `FLOAT`
* `decimal` - `DECIMAL` with scale and precision
* `bool` - `BOOLEAN`
* `char` - `CHAR`
* `varchar` - `VARCHAR` with length
* `text` - `TEXT`
* `enumeration` - `INT` ordinal value
* `enumerationByName` - `VARCHAR`
* `customEnumeration` - see [additional section](#how-to-use-database-enum-types)
* `blob` - `BLOB`
* `binary` - `VARBINARY` with length
* `uuid` - `BINARY(16)`
* `reference` - a foreign key

The `exposed-java-time` extension (`org.jetbrains.exposed:exposed-java-time:$exposed_version`) provides additional types:

* `date` - `DATETIME`
* `time` - `TIME`
* `datetime` - `DATETIME`
* `timestamp` - `TIMESTAMP`
* `duration` - `DURATION`

<note>
Some types are different for specific DB dialect.
</note>

The `exposed-json` extension (`org.jetbrains.exposed:exposed-json:$exposed_version`) provides additional types 
(see [how to use](#how-to-use-json-and-jsonb-types)):

* `json` - `JSON`
* `jsonb` - `JSONB`

<note>
Databases store JSON values either in text or binary format, so Exposed provides two types to account for any potential 
differences, if they exist, for example:

- **PostgreSQL**: `json()` maps to `JSON`, while `jsonb()` maps to `JSONB`.
- **SQLite**: No native JSON type, so `json()` maps to TEXT, while `jsonb()` throws.
- **MySQL**: JSON type only supports binary format, so `json()` and `jsonb()` both map to JSON.
- **Oracle**: Exposed does not currently support the JSON binary format of Oracle 21c; only text format `json()` can be used.
</note>

## How to use database ENUM types
Some of the databases (e.g. MySQL, PostgreSQL, H2) support explicit ENUM types. Because keeping such columns in sync with 
Kotlin enumerations using only JDBC metadata could be a huge challenge, Exposed doesn't provide a possibility to manage 
such columns in an automatic way, but that doesn't mean that you can't use such column types.
You have two options to work with ENUM database types:
1. Use existing ENUM column from your tables
2. Create column from Exposed by providing raw definition SQL
   In both cases, you should use `customEnumeration` function (available since version 0.10.3)

As a jdbc-driver can provide/expect specific classes for Enum type, you must provide from/to transformation functions for 
them when defining a `customEnumeration`.

For such enum `private enum class Foo { Bar, Baz }`, you can use the provided code for your database:

**H2**
```Kotlin
val existingEnumColumn = customEnumeration("enumColumn", { Foo.values()[it as Int] }, { it.name })
val newEnumColumn = customEnumeration("enumColumn", "ENUM('Bar', 'Baz')", { Foo.values()[it as Int] }, { it.name })
```

**MySQL**
```Kotlin
val existingEnumColumn = customEnumeration("enumColumn", { value -> Foo.valueOf(value as String) }, { it.name })
val newEnumColumn = customEnumeration("enumColumn", "ENUM('Bar', 'Baz')", { value -> Foo.valueOf(value as String) }, { it.name })
```

**PostgreSQL**

PostgreSQL requires that ENUM is defined as a separate type, so you have to create it before creating your table. 
Also, PostgreSQL JDBC driver returns PGobject instances for such values. The full working sample is provided below:
```Kotlin
class PGEnum<T : Enum<T>>(enumTypeName: String, enumValue: T?) : PGobject() {
    init {
        value = enumValue?.name
        type = enumTypeName
    }
}

object EnumTable : Table() {
    val enumColumn = customEnumeration("enumColumn", "FooEnum", {value -> Foo.valueOf(value as String)}, { PGEnum("FooEnum", it) }
}
...
transaction {
   exec("CREATE TYPE FooEnum AS ENUM ('Bar', 'Baz');")
   SchemaUtils.create(EnumTable)
   ...
}
```

## How to use Json and JsonB types

Add the following dependencies to your `build.gradle.kts`:
```kotlin
val exposedVersion: String by project

dependencies {
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-json:$exposedVersion")
}
```

Exposed works together with [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) to support 
`@Serializable` classes and JSON serialization/deserialization:
```kotlin
@Serializable
data class Project(val name: String, val language: String, val active: Boolean)

val format = Json { prettyPrint = true }

object Team : Table("team") {
    val groupId = varchar("group_id", 32)
    val project = json<Project>("project", format) // equivalent to json("project", format, Project.serializer())
}

transaction {
    val mainProject = Project("Main", "Java", true)
    Team.insert {
        it[groupId] = "A"
        it[project] = mainProject
    }
    Team.update({ Team.groupId eq "A" }) {
        it[project] = mainProject.copy(language = "Kotlin")
    }

    Team.selectAll().map { "Team ${it[Team.groupId]} -> ${it[Team.project]}" }.forEach { println(it) }
    // Team A -> Project(name=Main, language=Kotlin, active=true)
}
```

Both column types also support custom serializer and deserializer arguments, using the form:
```kotlin
fun <T : Any> json(name: String, serialize: (T) -> String, deserialize: (String) -> T): Column<T>
```

### Json Functions

JSON path strings can be used to extract values (either as JSON or as a scalar value) at a specific field/key:
```kotlin
val projectName = Team.project.extract<String>("name")
val languageIsKotlin = Team.project.extract<String>("language").lowerCase() eq "kotlin"
Team.slice(projectName).select { languageIsKotlin }.map { it[projectName] }
```

<note>
Databases that support a path context root `$` will have this value appended to the generated SQL path expression 
by default, so it is not necessary to include it in the provided argument String. In the above example, if MySQL is being 
used, the provided path arguments should be `.name` and `.language` respectively.
</note>

The JSON functions `exists()` and `contains()` are currently supported as well:
```kotlin
val hasActiveStatus = Team.project.exists(".active")
val activeProjects = Team.select { hasActiveStatus }.count()

// Depending on the database, filter paths can be provided instead, as well as optional arguments
// PostgreSQL example
val mainId = "Main"
val hasMainProject = Team.project.exists(".name ? (@ == \$main)", optional = "{\"main\":\"$mainId\"}")
val mainProjects = Team.select { hasMainProject }.map { it[Team.groupId] }

val usesKotlin = Team.project.contains("{\"language\":\"Kotlin\"}")
val kotlinTeams = Team.select { usesKotlin }.count()

// Depending on the database, an optional path can be provided too
// MySQL example
val usesKotlin = Team.project.contains("\"Kotlin\"", ".language")
val kotlinTeams = Team.select { usesKotlin }.count()
```

### Json Arrays

JSON columns also accept JSON arrays as input values. For example, using the serializable data class `Project` from the 
example above, the following details some ways to create such a column:

```kotlin
object TeamProjects : Table("team_projects") {
    val memberIds = json<IntArray>("member_ids", Json.Default)
    val projects = json<Array<Project>>("projects", Json.Default)
    // equivalent to:
    // @OptIn(ExperimentalSerializationApi::class) json("projects", Json.Default, ArraySerializer(Project.serializer()))
}

transaction {
    TeamProjects.insert {
        it[memberIds] = intArrayOf(1, 2, 3)
        it[projects] = arrayOf(
            Project("A", "Kotlin", true),
            Project("B", "Java", true)
        )
    }
    // generates SQL
    // INSERT INTO team_projects (member_ids, projects) VALUES ([1,2,3], [{"name":"A","language":"Kotlin","active":true},{"name":"B","language":"Java","active":true}])
}
```

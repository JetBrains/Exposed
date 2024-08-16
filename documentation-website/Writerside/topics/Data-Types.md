<show-structure for="chapter,procedure" depth="3"/>

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
* `array` - `ARRAY`

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

You have two options to work with ENUM database types and you should use `customEnumeration()` (available since version 0.10.3) in both cases:
1. Use an existing ENUM column from your table. In this case, the `sql` parameter in `customEnumeration()` can be left as `null`.
2. Create a new ENUM column using Exposed by providing the raw definition SQL to the `sql` parameter in `customEnumeration()`.

As a JDBC driver can provide/expect specific classes for ENUM types, you must also provide from/to transformation functions for 
them when defining a `customEnumeration`.

For a class like `enum class Foo { BAR, BAZ }`, you can use the provided code below for your specific database:

### MySQL, H2
```Kotlin
val existingEnumColumn = customEnumeration("enumColumn", { value -> Foo.valueOf(value as String) }, { it.name })
val newEnumColumn = customEnumeration("enumColumn", "ENUM('BAR', 'BAZ')", { value -> Foo.valueOf(value as String) }, { it.name })
```

### PostgreSQL

PostgreSQL requires that ENUM is defined as a separate type, so you have to create it before creating your table. 
Also, the PostgreSQL JDBC driver returns `PGobject` instances for such values, so a `PGobject` with its type manually set to the ENUM type needs to be used for the `toDb` parameter.
The full working sample is provided below:
```Kotlin
class PGEnum<T : Enum<T>>(enumTypeName: String, enumValue: T?) : PGobject() {
    init {
        value = enumValue?.name
        type = enumTypeName
    }
}

object EnumTable : Table() {
    val enumColumn = customEnumeration("enumColumn", "FooEnum", { value -> Foo.valueOf(value as String) }, { PGEnum("FooEnum", it) })
}

transaction {
   exec("CREATE TYPE FooEnum AS ENUM ('BAR', 'BAZ');")
   SchemaUtils.create(EnumTable)
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

Exposed works together with the JSON serialization/deserialization library of your choice by allowing column definitions
that accept generic serializer and deserializer arguments:
```kotlin
fun <T : Any> json(name: String, serialize: (T) -> String, deserialize: (String) -> T): Column<T>

fun <T : Any> jsonb(name: String, serialize: (T) -> String, deserialize: (String) -> T): Column<T>
```

Here's an example that leverages [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) to support
`@Serializable` classes. It uses a simpler form of `json()` that relies on the library's `KSerializer` interface:
```kotlin
@Serializable
data class Project(val name: String, val language: String, val active: Boolean)

val format = Json { prettyPrint = true }

object Teams : Table("team") {
    val groupId = varchar("group_id", 32)
    val project = json<Project>("project", format) // equivalent to json("project", format, Project.serializer())
}

transaction {
    val mainProject = Project("Main", "Java", true)
    Teams.insert {
        it[groupId] = "A"
        it[project] = mainProject
    }
    Teams.update({ Teams.groupId eq "A" }) {
        it[project] = mainProject.copy(language = "Kotlin")
    }

    Teams.selectAll().map { "Team ${it[Teams.groupId]} -> ${it[Teams.project]}" }.forEach { println(it) }
    // Team A -> Project(name=Main, language=Kotlin, active=true)
}
```

Here's how the same `Project` and `Teams` would be defined using [Jackson](https://github.com/FasterXML/jackson)
with the `jackson-module-kotlin` dependency and the full form of `json()`:
```kotlin
val mapper = jacksonObjectMapper()

data class Project(val name: String, val language: String, val active: Boolean)

object Teams : Table("team") {
    val groupId = varchar("group_id", 32)
    val project = json("project", { mapper.writeValueAsString(it) }, { mapper.readValue<Project>(it) })
}
```

### Json Functions

JSON path strings can be used to extract values (either as JSON or as a scalar value) at a specific field/key:
```kotlin
val projectName = Teams.project.extract<String>("name")
val languageIsKotlin = Teams.project.extract<String>("language").lowerCase() eq "kotlin"
Teams.select(projectName).where { languageIsKotlin }.map { it[projectName] }
```

<note>
Databases that support a path context root <code>$</code> will have this value appended to the generated SQL path expression 
by default, so it is not necessary to include it in the provided argument String. In the above example, if MySQL is being 
used, the provided path arguments should be <code>.name</code> and <code>.language</code> respectively.
</note>

The JSON functions `exists()` and `contains()` are currently supported as well:
```kotlin
val hasActiveStatus = Teams.project.exists(".active")
val activeProjects = Teams.selectAll().where { hasActiveStatus }.count()

// Depending on the database, filter paths can be provided instead, as well as optional arguments
// PostgreSQL example
val mainId = "Main"
val hasMainProject = Teams.project.exists(".name ? (@ == \$main)", optional = "{\"main\":\"$mainId\"}")
val mainProjects = Teams.selectAll().where { hasMainProject }.map { it[Teams.groupId] }

val usesKotlin = Teams.project.contains("{\"language\":\"Kotlin\"}")
val kotlinTeams = Teams.selectAll().where { usesKotlin }.count()

// Depending on the database, an optional path can be provided too
// MySQL example
val usesKotlin = Teams.project.contains("\"Kotlin\"", ".language")
val kotlinTeams = Teams.selectAll().where { usesKotlin }.count()
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

## How to use Array types

PostgreSQL and H2 databases support the explicit ARRAY data type.

Exposed currently only supports columns defined as one-dimensional arrays, with the stored contents being any out-of-the-box or custom data type.
If the contents are of a type with a supported `ColumnType` in the `exposed-core` module, the column can be simply defined with that type:
```kotlin
object Teams : Table("teams") {
    val memberIds = array<UUID>("member_ids")
    val memberNames = array<String>("member_names")
    val budgets = array<Double>("budgets")
}
```

If more control is needed over the base content type, or if the latter is user-defined or from a non-core module, the explicit type should be provided to the function:
```kotlin
object Teams : Table("teams") {
    val memberIds = array<UUID>("member_ids")
    val memberNames = array<String>("member_names", VarCharColumnType(colLength = 32))
    val deadlines = array<LocalDate>("deadlines", KotlinLocalDateColumnType()).nullable()
    val budgets = array<Double>("budgets")
    val expenses = array<Double?>("expenses", DoubleColumnType()).default(emptyList())
}
```
This will prevent an exception being thrown if Exposed cannot find an associated column mapping for the defined type.
Null array contents are allowed, and the explicit column type should be provided for these columns as well.

An array column accepts inserts and retrieves stored array contents as a Kotlin `List`:
```kotlin
Teams.insert {
    it[memberIds] = List(5) { UUID.randomUUID() }
    it[memberNames] = List(5) { i -> "Member ${'A' + i}" }
    it[budgets] = listOf(9999.0)
}
```

### Array Functions

A single element in a stored array can be accessed using the index reference `get()` operator:
```kotlin
val firstMember = Teams.memberIds[1]
Teams
    .select(firstMember)
    .where { Teams.expenses[1] greater Teams.budgets[1] }
```
<note>
Both PostgreSQL and H2 use a one-based indexing convention, so the first element is retrieved by using index 1.
</note>

A new subarray can also be accessed by using `slice()`, which takes a lower and upper bound (inclusive):
```kotlin
Teams.select(Teams.deadlines.slice(1, 3))
```
Both arguments for these bounds are optional if using PostgreSQL.

An array column can also be used as an argument for the `ANY` and `ALL` SQL operators, either by providing the entire column or a new array expression via `slice()`:
```kotlin
Teams
    .selectAll()
    .where { Teams.budgets[1] lessEq allFrom(Teams.expenses) }

Teams
    .selectAll()
    .where { stringParam("Member A") eq anyFrom(Teams.memberNames.slice(1, 4)) }
```

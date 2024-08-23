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

## Custom Data Types

If a database-specific data type is not immediately supported by Exposed, any existing and open column type class can be extended or
a custom `ColumnType` class can be implemented to achieve the same functionality.

The following examples describe different ways to customize a column type, register a column with the custom type,
and then start using it in transactions.

### Ltree type

PostgreSQL provides a data type, `ltree`, to represent hierarchical tree-like data.

The hierarchy labels are stored as strings, so the existing `StringColumnType` class be extended with a few overrides:
```kotlin
import org.postgresql.util.PGobject

class LTreeColumnType : StringColumnType() {
    override fun sqlType(): String = "LTREE"

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        val parameterValue: PGobject? = value?.let {
            PGobject().apply {
                type = sqlType()
                this.value = value as? String
            }
        }
        super.setParameter(stmt, index, parameterValue)
    }
}
```

> When setting an object in a prepared statement with JDBC, any unknown data type without a JDBC mapping is set as a varying character string.
> To avoid a casting exception due to PostgreSQL's stricter type system, the type of the set parameter should be manually declared,
> by using a `PGobject` in `setParamater()`, as shown in the example above.
{style="note"}

A table extension function can then be added to register a new column with this type:
```kotlin
fun Table.ltree(name: String): Column<String> = registerColumn(name, LTreeColumnType())

object TestTable : Table("test_table") {
    val path = ltree("path")

    init {
        index(customIndexName = "path_gist_idx", indexType = "GIST", columns = arrayOf(path))
        index(customIndexName = "path_idx", indexType = "BTREE", columns = arrayOf(path))
    }
}
```

> To use the `ltree` data type, the extension must first be enabled in the database by running `exec("CREATE EXTENSION ltree;")`.
{style="note"}

String values representing hierarchy labels can then be inserted and queried from the `path` column.
The following block shows an update of all records that have a stored `path` either equal to or a descendant of the path `Top.Science`,
by setting a subpath of the first 2 labels as the updated value:
```kotlin
transaction {
    TestTable.update(
        where = { TestTable.path isDescendantOrEq "Top.Science" }
    ) {
        it[path] = path.subltree(0, 2)
    }
}

fun <T : String?> Expression<T>.subltree(start: Int, end: Int) =
    CustomStringFunction("SUBLTREE", this, intParam(start), intParam(end))

infix fun <T : String?> ExpressionWithColumnType<T>.isDescendantOrEq(other: T) =
    IsDescendantOrEqOp(this, wrap(other))

class IsDescendantOrEqOp<T : String?>(
    left: Expression<T>,
    right: Expression<T>
) : ComparisonOp(left, right, "<@")
```

### Year type

MySQL and MariaDB provide a data type, `YEAR`, for 1-byte storage of year values in the range of 1901 to 2155.

This example assumes that the column accepts string input values, but a numerical format is also possible, in which case
`IntegerColumnType` could be extended instead:
```kotlin
class YearColumnType : StringColumnType(), IDateColumnType {
    override fun sqlType(): String = "YEAR"

    override val hasTimePart: Boolean = false

    override fun valueFromDB(value: Any): String = when (value) {
        is java.sql.Date -> value.toString().substringBefore('-')
        else -> error("Retrieved unexpected value of type ${value::class.simpleName}")
    }
}

fun Table.year(name: String): Column<String> = registerColumn(name, YearColumnType())
```

The `IDateColumnType` interface is implemented to ensure that any default expressions are handled appropriately. For example,
a new object `CurrentYear` can be added as a default to avoid issues with the strict column typing:
```kotlin
object CurrentYear : Function<String>(YearColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder { +"CURRENT_DATE" }
    }
}

object TestTable : Table("test_table") {
    val established = year("established").defaultExpression(CurrentYear)
}
```

String values of different formats (depending on the enabled `sql_mode`) can then be inserted and queried from the `year` column:
```kotlin
transaction {
    // disable strict mode to allow truncation of full date strings
    exec("SET sql_mode=''")
    
    val yearData = listOf("1901", "2000", "2023-08-22", "2155")
    TestTable.batchInsert(yearData) { year ->
        this[TestTable.established] = year
    }

    TestTable
        .selectAll()
        .where { TestTable.established less CurrentYear }
        .toList()
}
```

### Range type

PostgreSQL provides multiple range data types of different subtypes.

If more than one range subtype needs to be used, a base `RangeColumnType` class could be first introduced with the minimum common logic:
```kotlin
import org.postgresql.util.PGobject

abstract class RangeColumnType<T : Comparable<T>, R : ClosedRange<T>>(
    val subType: ColumnType<T>,
) : ColumnType<R>() {
    abstract fun List<String>.toRange(): R

    override fun nonNullValueToString(value: R): String {
        return "[${value.start},${value.endInclusive}]"
    }

    override fun nonNullValueAsDefaultString(value: R): String {
        return "'${nonNullValueToString(value)}'"
    }

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        val parameterValue: PGobject? = value?.let {
            PGobject().apply {
                type = sqlType()
                this.value = nonNullValueToString(it as R)
            }
        }
        super.setParameter(stmt, index, parameterValue)
    }

    override fun valueFromDB(value: Any): R? = when (value) {
        is PGobject -> value.value?.let {
            val components = it.trim('[', ')').split(',')
            components.toRange()
        }
        else -> error("Retrieved unexpected value of type ${value::class.simpleName}")
    }
}
```

A class for the type `int4range` that accepts `IntRange` values could then be implemented:
```kotlin
class IntRangeColumnType : RangeColumnType<Int, IntRange>(IntegerColumnType()) {
    override fun sqlType(): String = "INT4RANGE"

    override fun List<String>.toRange(): IntRange {
        return IntRange(first().toInt(), last().toInt() - 1)
    }
}

fun Table.intRange(name: String): Column<IntRange> = registerColumn(name, IntRangeColumnType())
```

If a custom Kotlin implementation for a `DateRange` is set up (using `Iterable` and `ClosedRange`),
then a class for the type `daterange` can also be added. This implementation would require a dependency on `exposed-kotlin-datetime`:
```kotlin
class DateRangeColumnType : RangeColumnType<LocalDate, DateRange>(KotlinLocalDateColumnType()) {
    override fun sqlType(): String = "DATERANGE"

    override fun List<String>.toRange(): DateRange {
        val endInclusive = LocalDate.parse(last()).minus(1, DateTimeUnit.DAY)
        return DateRange(LocalDate.parse(first()), endInclusive)
    }
}

fun Table.dateRange(name: String): Column<DateRange> = registerColumn(name, DateRangeColumnType())
```

These new column types can be used in a table definition:
```kotlin
object TestTable : Table("test_table") {
    val amounts = intRange("amounts").default(1..10)
    val holidays = dateRange("holidays")
}
```

With the addition of some custom functions, the stored data can then be queried to return the upper bound of the date range
for all records that have an integer range within the specified bounds:
```kotlin
transaction {
    val holidayEnd = TestTable.holidays.upperBound()
    TestTable
        .select(holidayEnd)
        .where { TestTable.amounts isContainedBy 0..100 }
        .toList()
}

fun <T : Comparable<T>, CR : ClosedRange<T>, R : CR?> ExpressionWithColumnType<R>.upperBound()
    = CustomFunction("UPPER", (columnType as RangeColumnType<T, CR>).subType, this)

infix fun <R : ClosedRange<*>?> ExpressionWithColumnType<R>.isContainedBy(other: R) =
    RangeIsContainedOp(this, wrap(other))

class RangeIsContainedOp<R : ClosedRange<*>?>(
    left: Expression<R>,
    right: Expression<R>
) : ComparisonOp(left, right, "<@")
```

### Set type

MySQL and MariaDB provide a data type, `SET`, for strings that can have zero or more values from a defined list of permitted values.
This could be useful, for example, when storing a list of Kotlin enum constants.

To use this type, a new `ColumnType` could be implemented with all the necessary overrides. This example instead takes advantage of
the existing logic in `StringColumnType` as the base for database storage, then uses a custom `ColumnTransformer` to achieve the final
transformation between a set of enum constants and a string:
```kotlin
class SetColumnType<T : Enum<T>>(
    private val enumClass: KClass<T>
) : StringColumnType() {
    // uses reflection to retrieve elements of the enum class
    private val enumConstants by lazy {
        enumClass.java.enumConstants?.map { it.name } ?: emptyList()
    }

    override fun sqlType(): String = enumConstants
        .takeUnless { it.isEmpty() }
        ?.let { "SET(${it.joinToString { e -> "'$e'" }})" }
        ?: error("SET column must be defined with a list of permitted values")
}

inline fun <reified T : Enum<T>> Table.set(name: String): Column<String> =
    registerColumn(name, SetColumnType(T::class))

class EnumListColumnType<T : Enum<T>>(
    private val enumClass: KClass<T>
) : ColumnTransformer<String, List<T>> {
    private val enumConstants by lazy {
        enumClass.java.enumConstants?.associateBy { it.name } ?: emptyMap()
    }

    override fun unwrap(value: List<T>): String {
        return value.joinToString(separator = ",") { it.name }
    }

    override fun wrap(value: String): List<T> = value
        .takeUnless { it.isEmpty() }?.let {
            it.split(',').map { e ->
                enumConstants[e]
                    ?: error("$it can't be associated with any value from ${enumClass.qualifiedName}")
            }
        }
        ?: emptyList()
}
```

> See [column transformations](Deep-Dive-into-DSL.md#column-transformation) for more details about `ColumnTransformer`.
{style="note"}

The new column type and transformer can then be used in a table definition:
```kotlin
enum class Vowel { A, E, I, O, U }

object TestTable : Table("test_table") {
    val vowel: Column<List<Vowel>> = set<Vowel>("vowel")
        .transform(EnumListColumnType(Vowel::class))
        .default(listOf(Vowel.A, Vowel.E))
}
```

Lists of enum constants can then be inserted and queried from the `set` column. The following block shows a query for all records that
have `Vowel.O` stored at any position in the `set` column string:
```kotlin
transaction {
    TestTable.insert { it[vowel] = listOf(Vowel.U, Vowel.E) }
    TestTable.insert { it[vowel] = emptyList() }
    TestTable.insert { it[vowel] = Vowel.entries }

    TestTable
        .selectAll()
        .where { TestTable.vowel.findInSet(Vowel.O) greater 0 }
        .toList()
}

fun <T : Enum<T>> Expression<List<T>>.findInSet(enum: T) =
    CustomFunction("FIND_IN_SET", IntegerColumnType(), stringParam(enum.name), this)
```

### Hstore type

PostgreSQL provides a data type, `hstore`, to store key-value data pairs in a single text string.

The existing `StringColumnType` class can be extended with a few overrides:
```kotlin
import org.postgresql.util.PGobject

class HStoreColumnType : TextColumnType() {
    override fun sqlType(): String = "HSTORE"

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        val parameterValue: PGobject? = value?.let {
            PGobject().apply {
                type = sqlType()
                this.value = value as? String
            }
        }
        super.setParameter(stmt, index, parameterValue)
    }
}
```

A table extension function can then be added to register a new column with this type.
This example assumes that the input values will be of type `Map<String, String>`, so `transform()` is used on the string column to handle parsing:
```kotlin
fun Table.hstore(name: String): Column<String> = registerColumn(name, HStoreColumnType())

object TestTable : Table("test_table") {
    val bookDetails = hstore("book_details").transform(
        wrap = {
            it.trim('{', '}').split(", ")
                .associate { pair ->
                    pair.substringBefore("=") to pair.substringAfter("=")
                }
        },
        unwrap = {
            it.entries.joinToString(separator = ",") { (k, v) ->
                "\"$k\"=>\"$v\""
            }
        }
    )
}
```

> See [column transformations](Deep-Dive-into-DSL.md#column-transformation) for more details about `transform()`.
{style="note"}

> To use the `hstore` data type, the extension must first be enabled in the database by running `exec("CREATE EXTENSION hstore;")`.
{style="note"}

Map values representing key-value pairs of strings can then be inserted and queried from the `bookDetails` column.
The following block queries the value associated with the `title` key from all `bookDetails` records:
```kotlin
transaction {
    TestTable.insert {
        it[bookDetails] = mapOf(
            "title" to "Kotlin in Action",
            "edition" to "2"
        )
    }

    val bookTitle = TestTable.bookDetails.getValue("title")
    TestTable
        .select(bookTitle)
        .toList()
}

fun <T : Map<String, String>> Expression<T>.getValue(key: String) =
    CustomOperator("->", TextColumnType(), this, stringParam(key))
```

### Citext type

PostgreSQL provides a data type, `citext`, that represents a case-insensitive string type.

The existing `StringColumnType` class can be extended with a few overrides:
```kotlin
import org.postgresql.util.PGobject

class CitextColumnType(
    colLength: Int
) : VarCharColumnType(colLength) {
    override fun sqlType(): String = "CITEXT"

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        val parameterValue: PGobject? = value?.let {
            PGobject().apply {
                type = sqlType()
                this.value = value as? String
            }
        }
        super.setParameter(stmt, index, parameterValue)
    }
}
```

A table extension function can then be added to register a new column with this type:
```kotlin
fun Table.citext(name: String, length: Int): Column<String> =
    registerColumn(name, CitextColumnType(length))

object TestTable : Table("test_table") {
    val firstName = citext("first_name", 32)
}
```

> To use the `citext` data type, the extension must first be enabled in the database by running `exec("CREATE EXTENSION citext;")`.
{style="note"}

String values can then be inserted and queried from the `firstName` column in a case-insensitive manner:
```kotlin
transaction {
    val allNames = listOf("Anna", "Anya", "Agna")
    TestTable.batchInsert(allNames) { name ->
        this[TestTable.firstName] = name
    }

    TestTable
        .selectAll()
        .where { TestTable.firstName like "an%" }
        .toList()
}
```

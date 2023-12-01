# Deep Dive into DSL

## Overview

The DSL (Domain-Specific Language) API of Exposed is similar to actual SQL statements, but with the type safety that Kotlin offers.

A database table is represented by an `object` inherited from `org.jetbrains.exposed.sql.Table` like this:

```kotlin
object StarWarsFilms : Table() {
    val id: Column<Int> = integer("id").autoIncrement()
    val sequelId: Column<Int> = integer("sequel_id").uniqueIndex()
    val name: Column<String> = varchar("name", 50)
    val director: Column<String> = varchar("director", 50)
    override val primaryKey = PrimaryKey(id, name = "PK_StarWarsFilms_Id") // PK_StarWarsFilms_Id is optional here
}
```

Tables that contains `Int` id with the name `id` can be declared like this:

```kotlin
object StarWarsFilms : IntIdTable() {
    val sequelId: Column<Int> = integer("sequel_id").uniqueIndex()
    val name: Column<String> = varchar("name", 50)
    val director: Column<String> = varchar("director", 50)
}
``` 

## CRUD operations

CRUD stands for Create Read Update Delete, which are four basic operations for a database to support. This section shows how to perform SQL CRUD operations
using Kotlin DSL.

### Create

To create a new table row, you use the `insert` query. Exposed provides several functions to insert rows into a table:

* `insert` adds a new row. If the same row already exists in the table, it throws an exception.
    ```kotlin
    // SQL: INSERT INTO CITIES (COUNTRY, "NAME", POPULATION)
    // VALUES ('RUSSIA', 'St. Petersburg', 300)
    Cities.insert {
    it[name] = "St. Petersburg"
    it[country] = Country.RUSSIA
    it[population] = 500
    }
    ```
* `insertAndGetId` adds a new row and returns its ID. If the same row already exists in the table, it throws an exception. Works only with IntIdTable() tables.
    ```kotlin
    // SQL: INSERT INTO CITIES (COUNTRY, "NAME", POPULATION)
    // VALUES ('RUSSIA', 'St. Petersburg', 300)
    val id = Cities.insertAndGetId {
        it[name] = "St. Petersburg"
        it[country] = Country.RUSSIA
        it[population] = 500
    }
    ```
* `insertIgnore` adds a new row. If the same row already exists in the table, it ignores it and doesn't throw an exception. This function is supported only for MySQL, 
  PostgreSQL, and SQLite. 
    ```kotlin
    // SQL: INSERT IGNORE INTO CITIES (COUNTRY, "NAME", POPULATION)
    // VALUES ('RUSSIA', 'St. Petersburg', 300)
    Cities.insertIgnore {
        it[name] = "St. Petersburg"
        it[country] = Country.RUSSIA
        it[population] = 500
    }
    ```
* `insertIgnoreAndGetId` adds a new row and returns its ID. If the same row already exists in the table, it ignores it and doesn't throw an exception. This function
  is supported only for MySQL, PostgreSQL, and SQLite. Works only with `IntIdTable()` tables.
    ```kotlin
    // SQL: INSERT IGNORE INTO CITIES (COUNTRY, "NAME", POPULATION)
    // VALUES ('RUSSIA', 'St. Petersburg', 300)
    val id = Cities.insertIgnoreAndGetId {
        it[name] = "St. Petersburg"
        it[country] = Country.RUSSIA
        it[population] = 500
    }
    ```

```kotlin
val id = StarWarsFilms.insertAndGetId {
    it[name] = "The Last Jedi"
    it[sequelId] = 8
    it[director] = "Rian Johnson"
}
```

Some databases return a count of the number of rows inserted, updated, or deleted by the CRUD operation.
For `insert()`, `upsert()`, and `replace()`, this value can be accessed using the statement class property, `insertedCount`:

```kotlin
val insertStatement = StarWarsFilms.insertIgnore {
  it[name] = "The Last Jedi"
  it[sequelId] = 8
  it[director] = "Rian Johnson"
}
val rowCount: Int = insertStatement.insertedCount
```

### Read

```kotlin
val query: Query = StarWarsFilms.select { StarWarsFilms.sequelId eq 8 }
```

`Query` inherit `Iterable` so it is possible to traverse it with map/foreach etc'. For example:

```kotlin
StarWarsFilms.select { StarWarsFilms.sequelId eq 8 }.forEach {
    println(it[StarWarsFilms.name])
}
```

There is `slice` function which allows you to select specific columns or/and expressions.

```kotlin
val filmAndDirector = StarWarsFilms.slice(StarWarsFilms.name, StarWarsFilms.director).selectAll().map {
    it[StarWarsFilms.name] to it[StarWarsFilms.director]
}
```

If you want to select only distinct value then use `withDistinct()` function:

```kotlin
val directors = StarWarsFilms.slice(StarWarsFilms.director).select { StarWarsFilms.sequelId less 5 }.withDistinct().map {
    it[StarWarsFilms.director]
}
```

### Update

```kotlin
StarWarsFilms.update({ StarWarsFilms.sequelId eq 8 }) {
    it[StarWarsFilms.name] = "Episode VIII – The Last Jedi"
}
```

If you want to update column value with some expression like increment use `update` function or setter:

```kotlin
StarWarsFilms.update({ StarWarsFilms.sequelId eq 8 }) {
    with(SqlExpressionBuilder) {
        it.update(StarWarsFilms.sequelId, StarWarsFilms.sequelId + 1)
        // or 
        it[StarWarsFilms.sequelId] = StarWarsFilms.sequelId + 1
    }
} 
```

If you want to know the number of updated rows, this value is also returned by the `update()` function:

```kotlin
val updatedCount = StarWarsFilms.update({ StarWarsFilms.name like "Episode%" }) {
    it[StarWarsFilms.director] = StarWarsFilms.director.upperCase()
}
```

### Delete

```kotlin
StarWarsFilms.deleteWhere { StarWarsFilms.sequelId eq 8 }
```

Delete functions also return a count of the number of deleted rows, as for Update above.

## Where expression

Query expression (where) expects a boolean operator (ie: `Op<Boolean>`).  
Allowed conditions are:

```
eq - (==)
neq - (!=)
isNull()
isNotNull()
less - (<)
lessEq - (<=)
greater - (>)
greaterEq - (>=)
like - (=~)
notLike - (!~)
exists
notExists
regexp
inList
notInList
between
match (MySQL MATCH AGAINST)
isDistinctFrom (null-safe equality comparison)
isNotDistinctFrom (null-safe equality comparison)
```

Allowed logical conditions are:

```
not
and
or
andIfNotNull
orIfNotNull
compoundAnd()
compoundOr()
```

## Conditional where

It is a rather common case to have a query with a `where` clause that depends on some other code's conditions. Moreover, independent or nested conditions could 
make it more complicated to prepare such `where` clauses.
Let's imagine that we have a form on a website where a user can optionally filter "Star Wars" films by a director and/or a sequel.
In Exposed version before 0.8.1 you had to code it like:

```Kotlin 
val condition = when {
    directorName!=null && sequelId!=null ->
        Op.build { StarWarsFilms.director eq directorName and (StarWarsFilms.sequelId eq sequelId) }
    directorName!=null ->
        Op.build { StarWarsFilms.director eq directorName }
    sequelId!=null ->
        Op.build { StarWarsFilms.sequelId eq sequelId }
    else -> null
}
val query = condition?.let { StarWarsFilms.select(condition) } ?: StarWarsFilms.selectAll()
```

or

```Kotlin
val query = when {
    directorName!=null && sequelId!=null ->
        StarWarsFilms.select { StarWarsFilms.director eq directorName and (StarWarsFilms.sequelId eq sequelId) }
    directorName!=null ->
        StarWarsFilms.select { StarWarsFilms.director eq directorName }
    sequelId!=null ->
        StarWarsFilms.select { StarWarsFilms.sequelId eq sequelId }
    else -> StarWarsFilms.selectAll()
}
```

This is a very primitive example, but you should get the main idea about the problem.
Now let's try to write the same query in a more simple way (`andWhere` function available since 0.10.5):

```Kotlin
val query = StarWarsFilms.selectAll()
directorName?.let {
    query.andWhere { StarWarsFilms.director eq it }
}
sequelId?.let {
    query.andWhere { StarWarsFilms.sequelId eq it }
}
```

But what if we want to conditionally select from another table and join it only when a condition is true?
You have to use `adjustColumnSet` and `adjustSlice` functions (available since 0.8.1) which allows to extend and modify `join` and `slice` parts of a query (see kdoc
on that functions):

```Kotlin
actorName?.let {
    query.adjustColumnSet { innerJoin(Actors, { StarWarsFilms.sequelId }, { Actors.sequelId }) }
        .adjustSlice { slice(fields + Actors.columns) }
        .andWhere { Actors.name eq actorName }
}
```

### Check for a match in a pattern

```kotlin
StarWarsFilms.select { StarWarsFilms.name like "The %" }
```

`notLike` is also available to check for expressions that do not match the provided pattern.

To perform a pattern match that supports regular expressions, use `regexp` instead:

```kotlin
StarWarsFilms.select { StarWarsFilms.name regexp "^The(\\s\\w+){2}\$" }
```

### Check for a match in a range

```kotlin
StarWarsFilms.select { StarWarsFilms.sequelId.between(4, 6) }
```

The `between` operator returns `true` if the expression is between the lower and upper range values (inclusive). 
Date and time values are also supported as arguments.

### Check for a match in a collection

```kotlin
StarWarsFilms.select { StarWarsFilms.sequelId inList listOf(6, 4) }
```

`inList` also accepts multiple expressions to check for equality, either as a `Pair` or a `Triple`:

```kotlin
val topRated = listOf(5 to "Empire Strikes Back", 4 to "A New Hope")
StarWarsFilms.select {
    StarWarsFilms.sequelId to StarWarsFilms.name inList topRated
}
```

`notInList` is available to check for expressions that are not equal to any elements in the provided collection.

## Count

`count()` is a method of `Query` that is used like in the example below:

```kotlin
val count = StarWarsFilms.select { StarWarsFilms.sequelId eq 8 }.count()
```

## Order-by

Order-by accepts a list of columns mapped to boolean indicates if sorting should be ascending or descending.
Example:

```kotlin
StarWarsFilms.selectAll().orderBy(StarWarsFilms.sequelId to SortOrder.ASC)
```

## Group-by

In group-by, define fields and their functions (such as `count`) by the `slice()` method.

```kotlin
StarWarsFilms
    .slice(StarWarsFilms.sequelId.count(), StarWarsFilms.director)
    .selectAll()
    .groupBy(StarWarsFilms.director)
```

Available functions are:

```
count
sum
max
min
average
...
``` 

## Limit

You can use limit function to prevent loading large data sets or use it for pagination with second `offset` parameter.

```kotlin
// Take 2 films after the first one.
StarWarsFilms.select { StarWarsFilms.sequelId eq Actors.sequelId }.limit(2, offset = 1)
```

## Join

For the join examples below, consider the following tables:

```kotlin
object StarWarsFilms : IntIdTable() {
    val sequelId: Column<Int> = integer("sequel_id").uniqueIndex()
    val name: Column<String> = varchar("name", 50)
    val director: Column<String> = varchar("director", 50)
}
object Actors : IntIdTable() {
    val sequelId: Column<Int> = integer("sequel_id").uniqueIndex()
    val name: Column<String> = varchar("name", 50)
}
object Roles : Table() {
    val sequelId: Column<Int> = integer("sequel_id")
    val actorId: Column<EntityID<Int>> = reference("actor_id", Actors)
    val characterName: Column<String> = varchar("name", 50)
}
```

Join to count how many actors star in each movie:

```kotlin
Actors.join(StarWarsFilms, JoinType.INNER, onColumn = Actors.sequelId, otherColumn = StarWarsFilms.sequelId)
    .slice(Actors.name.count(), StarWarsFilms.name)
    .selectAll()
    .groupBy(StarWarsFilms.name)
``` 

Instead of specifying `onColumn` and `otherColumn`, `additionalConstraint` can be used (and allows specifying
other types of join conditions).

```kotlin
Actors.join(StarWarsFilms, JoinType.INNER, additionalConstraint = { StarWarsFilms.sequelId eq Actors.sequelId })
    .slice(Actors.name.count(), StarWarsFilms.name)
    .selectAll()
    .groupBy(StarWarsFilms.name)
```

When joining on a foreign key, the more concise `innerJoin` can be used:

```kotlin
(Actors innerJoin Roles)
    .slice(Roles.characterName.count(), Actors.name)
    .selectAll()
    .groupBy(Actors.name)
    .toList()
```

This is equivalent to the following:

```kotlin
Actors.join(Roles, JoinType.INNER, onColumn = Actors.id, otherColumn = Roles.actorId)
    .slice(Roles.characterName.count(), Actors.name)
    .selectAll()
    .groupBy(Actors.name)
    .toList()
```

## Union

You can combine the results of multiple queries using `.union(...)`.
Per the SQL specification, the queries must have the same number of columns, and not be marked for update.
Subqueries may be combined when supported by the database.

```kotlin
val lucasDirectedQuery = StarWarsFilms.slice(StarWarsFilms.name).select { StarWarsFilms.director eq "George Lucas" }
val abramsDirectedQuery = StarWarsFilms.slice(StarWarsFilms.name).select { StarWarsFilms.director eq "J.J. Abrams" }
val filmNames = lucasDirectedQuery.union(abramsDirectedQuery).map { it[StarWarsFilms.name] }
```

Only unique rows are returned by default. Duplicates may be returned using `.unionAll()`.

```kotlin
val lucasDirectedQuery = StarWarsFilms.slice(StarWarsFilms.name).select { StarWarsFilms.director eq "George Lucas" }
val originalTrilogyQuery = StarWarsFilms.slice(StarWarsFilms.name).select { StarWarsFilms.sequelId inList (3..5) }
val filmNames = lucasDirectedQuery.unionAll(originalTrilogyQuery).map { it[StarWarsFilms.name] }
```

## Alias

Aliases allow preventing ambiguity between field names and table names.
Use the aliased var instead of original one:

```Kotlin
val filmTable1 = StarWarsFilms.alias("ft1")
filmTable1.selectAll() // can be used in joins etc'
```

Also, aliases allow you to use the same table in a join multiple times:

```Kotlin
val sequelTable = StarWarsFilms.alias("sql")
val originalAndSequelNames = StarWarsFilms
    .innerJoin(sequelTable, { StarWarsFilms.sequelId }, { sequelTable[StarWarsFilms.id] })
    .slice(StarWarsFilms.name, sequelTable[StarWarsFilms.name])
    .selectAll()
    .map { it[StarWarsFilms.name] to it[sequelTable[StarWarsFilms.name]] }
```

And they can be used when selecting from sub-queries:

```kotlin
val starWarsFilms = StarWarsFilms
    .slice(StarWarsFilms.id, StarWarsFilms.name)
    .selectAll()
    .alias("swf")
val id = starWarsFilms[StarWarsFilms.id]
val name = starWarsFilms[StarWarsFilms.name]
starWarsFilms
    .slice(id, name)
    .selectAll()
    .map { it[id] to it[name] }
```

## Schema

You can create a schema or drop an existing one:

```Kotlin
val schema = Schema("my_schema") // my_schema is the schema name.
// Creates a Schema
SchemaUtils.createSchema(schema)
// Drops a Schema
SchemaUtils.dropSchema(schema)
```

Also, you can specify the schema owner like this (some databases require the explicit owner) :

```Kotlin
val schema = Schema("my_schema", authorization = "owner")
```

If you have many schemas and you want to set a default one, you can use:

```Kotlin
SchemaUtils.setSchema(schema)
```

## Sequence

If you want to use Sequence, Exposed allows you to:

### Define a Sequence

```Kotlin
val myseq = Sequence("my_sequence") // my_sequence is the sequence name.
```

Several parameters can be specified to control the properties of the sequence:

```Kotlin
private val myseq = Sequence(
    name = "my_sequence",
    startWith = 4,
    incrementBy = 2,
    minValue = 1,
    maxValue = 10,
    cycle = true,
    cache = 20
)
```

### Create and Drop a Sequence

```Kotlin
// Creates a sequence
SchemaUtils.createSequence(myseq)
// Drops a sequence
SchemaUtils.dropSequence(myseq)
```

### Use the NextVal function

You can use the nextVal function like this:

```Kotlin
val nextVal = myseq.nextVal()
val id = StarWarsFilms.insertAndGetId {
    it[id] = nextVal
    it[name] = "The Last Jedi"
    it[sequelId] = 8
    it[director] = "Rian Johnson"
}
```

```Kotlin
val firstValue = StarWarsFilms.slice(nextVal).selectAll().single()[nextVal]
```

## Batch Insert

Batch Insert allow mapping a list of entities into DB raws in one sql statement. It is more efficient than inserting one by one as it initiates only one statement.
Here is an example that uses a simple list:

```kotlin
val cityNames = listOf("Paris", "Moscow", "Helsinki")
val allCitiesID = cities.batchInsert(cityNames) { name ->
    this[cities.name] = name
}
```

Here is an example that uses a list of data class instances:

```kotlin
data class SWFilmData(val sequelId: Int, val name: String, val director: String)

transaction {
    // ...
    val films = listOf(
        SWFilmData(5, "The Empire Strikes Back", "Irvin Kershner"),
        SWFilmData(4, "A New Hope", "George Lucas"),
        SWFilmData(7, "The Force Awakens", "JJ Abrams")
    )

    StarWarsFilms.batchInsert(films) { (id, name, director) ->
        this[StarWarsFilms.sequelId] = id
        this[StarWarsFilms.name] = name
        this[StarWarsFilms.director] = director
    }

    StarWarsFilms.selectAll().count() // 3
}
```

*NOTE:* The `batchInsert` function will still create multiple `INSERT` statements when interacting with your database. You most likely want to couple this with
the `rewriteBatchedInserts=true` (or `rewriteBatchedStatements=true`) option of your relevant JDBC driver, which will convert those into a single bulkInsert.
You can find the documentation for this option for MySQL [here](https://dev.mysql.com/doc/connector-j/5.1/en/connector-j-reference-configuration-properties.html) and
PostgreSQL [here](https://jdbc.postgresql.org/documentation/use/).

If you don't need to get the newly generated values (example: auto incremented ID), set the `shouldReturnGeneratedValues` parameter to false, this increases the
performance of batch inserts by batching them in chunks, instead of always waiting for the database to synchronize the newly inserted object state.

If you want to check if the `rewriteBatchedInserts` + `batchInsert` is working correctly, check how to enable JDBC logging for your driver because Exposed will always
show the non-rewritten multiple inserts. You can find the documentation for how to enable logging in
PostgreSQL [here](https://jdbc.postgresql.org/documentation/logging/).

## Insert From Select

If you want to use `INSERT INTO ... SELECT ` SQL clause try Exposed analog `Table.insert(Query)`.

```kotlin
val substring = users.name.substring(1, 2)
cities.insert(users.slice(substring).selectAll().orderBy(users.id).limit(2))
```

By default it will try to insert into all non auto-increment `Table` columns in order they defined in Table instance. If you want to specify columns or change the
order, provide list of columns as second parameter:

```kotlin
val userCount = users.selectAll().count()
users.insert(users.slice(stringParam("Foo"), Random().castTo<String>(VarCharColumnType()).substring(1, 10)).selectAll(), columns = listOf(users.name, users.id))
```

## Insert Or Ignore

If supported by your specific database, `insertIgnore()` allows insert statements to be executed without throwing any 
ignorable errors. This may be useful, for example, when insertion conflicts are possible:
```kotlin
StarWarsFilms.insert {
    it[sequelId] = 8  // column pre-defined with a unique index
    it[name] = "The Last Jedi"
    it[director] = "Rian Johnson"
}
// If insert() was used, this would throw a constraint violation exception
// Instead, this new row is ignored and discarded
StarWarsFilms.insertIgnore {
    it[sequelId] = 8
    it[name] = "The Rise of Skywalker"
    it[director] = "JJ Abrams"
}
```

## Insert Or Update

Insert or update (Upsert) is a database operation that either inserts a new row or updates an existing row if a duplicate 
constraint already exists. The supported functionality of `upsert()` is dependent on the specific database being used.
For example, MySQL's `INSERT ... ON DUPLICATE KEY UPDATE` statement automatically assesses the primary key and unique indices 
for a duplicate value, so using the function in Exposed would look like this:
```kotlin
// inserts a new row
StarWarsFilms.upsert {
    it[sequelId] = 9  // column pre-defined with a unique index
    it[name] = "The Rise of Skywalker"
    it[director] = "Rian Johnson"
}
// updates existing row with the correct [director]
StarWarsFilms.upsert {
    it[sequelId] = 9
    it[name] = "The Rise of Skywalker"
    it[director] = "JJ Abrams"
}
```

If none of the optional arguments are provided to `upsert()`, the statements in the `body` block will be used for both the insert and update parts of the operation. 
This means that, for example, if a table mapping has columns with default values and these columns are omitted from the `body` block, the default values will be 
used for insertion as well as for the update operation. If the update operation should differ from the insert operation, then `onUpdate` should be provided an 
argument with the specific columns to update, as seen in the example below.

Using another example, PostgreSQL allows more control over which key constraint columns to check for conflict, whether different 
values should be used for an update, and whether the update statement should have a `WHERE` clause:
```kotlin
val incrementSequelId = listOf(StarWarsFilms.sequelId to StarWarsFilms.sequelId.plus(1))
StarWarsFilms.upsert(
    StarWarsFilms.sequelId,
    onUpdate = incrementSequelId,
    where = { StarWarsFilms.director like stringLiteral("JJ%") }
) {
    it[sequelId] = 9
    it[name] = "The Rise of Skywalker"
    it[director] = "JJ Abrams"
}
```
If a specific database supports user-defined key columns and none are provided, the table's primary key is used. If there 
is no defined primary key, the first unique index is used. If there are no unique indices, each database handles this case 
differently, so it is strongly advised that keys are defined to avoid unexpected results.

<note>
Databases that do not support a specific Insert or Update command implement the standard `MERGE INTO ... USING` statement with aliases and a derived table column list. 
These include Oracle, SQL Server, and H2 compatibility modes (except for MySQL mode). 
Any columns defined as key constraints (to be used in the `ON` clause) must be included in the statement block to avoid throwing an error.
</note>

## Replace

SQLite, MySQL, and MariaDB (as well as the H2 compatibility modes of the latter 2 databases) support a `REPLACE` statement that acts in a similar manner 
to an `INSERT OR UPDATE` statement. The only difference is that, if an insertion would violate a unique constraint, the existing row is deleted (not updated) 
before the new row is inserted.

```kotlin
object StarWarsFilms : Table() {
    val sequelId: Column<Int> = integer("sequel_id").uniqueIndex()
    val releaseYear: Column<Int> = integer("release_year")
    val name: Column<String> = varchar("name", 50)
    val director: Column<String> = varchar("director", 50)
    val rating: Column<Double> = double("rating").default(10.0)

    override val primaryKey = PrimaryKey(sequelId, releaseYear)
}

transaction {
    // ...
    // inserts a new row with default rating
    StarWarsFilms.replace {
        it[sequelId] = 9
        it[releaseYear] = 2019
        it[name] = "The Rise of Skywalker"
        it[director] = "JJ Abrams"
    }
    // deletes existing row and inserts new row with set [rating]
    StarWarsFilms.replace {
        it[sequelId] = 9
        it[releaseYear] = 2019
        it[name] = "The Rise of Skywalker"
        it[director] = "JJ Abrams"
        it[rating] = 5.2
    }
}
```

Unlike Insert or Update, none of the supporting databases allows a `WHERE` clause. 
Also, the constraints used to assess a violation are limited to the primary key and unique indexes, so there is no parameter for a custom key set.

The values specified in the statement block will be used for the insert statement and any omitted columns are set to their default values, if applicable.

<note>
In the example above, if the original row was inserted with a user-defined `rating`, then `replace()` was executed with a block that omitted the `rating` column, 
the newly inserted row would store the default rating value. This is because the old row was completely deleted first.
</note>

# Frequently Asked Questions 

### What is the difference between Squash and Exposed?

[Ilya Ryzhenkov](https://github.com/orangy/) ([Squash](https://github.com/orangy/squash) maintainer) answers:

"Squash is an attempt to refactor Exposed (long time ago) to fix DSL issues, extensibility on dialect side, support
graph fetching and avoid TLS-stored transactions. Unfortunately, I didn’t have enough time to finish the work, but I 
still hope to return to it some day. We were talking with Exposed maintainer [@tapac](https://github.com/tapac/) at
the time about coordinating efforts and eventually joining forces. Note that Squash is not an “official” JetBrains
Kotlin SQL library, but rather a side project of mine."

### Can I use multiple database connections?

Yes. You can use multiple database connections by passing the database reference to the `transaction()` function.
For more details and examples, see [](Transactions.md#working-with-multiple-databases).

### What data types are supported?

Exposed supports a variety of data types, including [basic data types](Numeric-Boolean-String-Types.topic),
[date and time](Date-and-time-types.topic), [arrays](Array-types.topic), [binary data](Binary-types.topic),
[enumeration](Enumeration-types.topic), and [](JSON-And-JSONB-types.topic). You can also extend and create new
[custom data types](Custom-data-types.topic) to fit your specific needs.

### How do I get a plain SQL query which will be executed?

To get the SQL representation of a query without executing it, use the
[`prepareSQL()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/-abstract-query/prepare-s-q-l.html)
method:

```kotlin
val plainSQL = StarWarsFilmsTable.selectAll()
    .where{ StarWarsFilmsTable.sequelId eq ActorsTable.sequelId }
    .prepareSQL(QueryBuilder(false))
```
In this example `QueryBuiler` is used with `false` to return a non-parameterized string.

### Is it possible to update a field relative to current field value?

Yes. You can achieve this by using the `.update()` function with the `SqlExpressionBuilder`. For more information, see
[how to update a record](DSL-CRUD-operations.topic#update-record).

### How can I add another type of database?

To add another type of database that is not currently supported by Exposed, implement the
[`DatabaseDialect`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql.vendors/-database-dialect/index.html)
interface and register it with
[`Database.registerDialect()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/-database/-companion/register-dialect.html).

If the implementation adds a lot of value, consider [contributing](Contributing.md) it to Exposed.

### Is it possible to create tables with cyclic (circular) reference?

Yes, it is. To define such tables, you can use the `reference()` or `optReference()` functions to establish foreign key 
relationships between tables. For more information, see the [](DAO-Relationships.topic) topic.

### How can I implement nested queries?

You can implement nested queries by using the `alias()` function to create subqueries and join them with other tables
or queries. For more information, see the [alias](DSL-Querying-data.topic#alias) documentation.

### How can I use SAVEPOINT?
You can use `SAVEPOINT` by executing raw SQL statements within a transaction.

### How do I prepare query like `SELECT * FROM table WHERE (x,y) IN ((1, 2), (3, 4), (5, 6))`?
Exposed does not natively support tuple-based `IN` clauses. To use such query, you can create a custom function to 
handle tuple comparisons.

### Where can I find snapshot builds of Exposed?

You could use a `jitpack.io` service for that.

Add `jitpack.io` to `repositories`:
```
repositories {
    maven { url 'https://jitpack.io' }
}
```
Then add an Exposed dependency as in the following way:

```
dependencies {
    implementation 'com.github.JetBrains:Exposed:-SNAPSHOT'
}
```

### How can I create a custom column type?

You can implement a custom column type using the [`IColumnType`](https://github.com/JetBrains/Exposed/blob/76a671e57a0105d6aed79e256c088690bd4a56b6/exposed-core/src/main/kotlin/org/jetbrains/exposed/sql/ColumnType.kt#L25) interface
and [`registerColumn()`](https://github.com/JetBrains/Exposed/blob/76a671e57a0105d6aed79e256c088690bd4a56b6/exposed-core/src/main/kotlin/org/jetbrains/exposed/sql/Table.kt#L387)
to register it to a table. For more information, refer to the [custom data types](Custom-data-types.topic) documentation.

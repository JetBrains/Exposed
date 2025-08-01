# Frequently Asked Questions

### What is Exposed?

Exposed is a Kotlin-based SQL library that combines a DSL for building queries, Object-Relational Mapping (ORM) 
features, and a DAO framework for managing entities. It allows developers to write type-safe queries and interact 
with a database using Kotlin's expressive and concise syntax.
For a more detailed description, see the [about](About.topic) section.

### Can I use multiple database connections?

Yes. You can use multiple database connections by passing the database reference to the `transaction()` function.
For more details and examples, see [](Transactions.md#working-with-multiple-databases).

### What data types are supported?

Exposed supports a variety of data types, including [basic data types](Numeric-Boolean-String-Types.topic),
[date and time](Date-and-time-types.topic), [arrays](Array-types.topic), [binary data](Binary-types.topic),
[enumeration](Enumeration-types.topic), and [](JSON-And-JSONB-types.topic). You can also extend and create new
[custom data types](Custom-data-types.topic) to fit your specific needs.

### How can I create a custom column type?

You can implement a custom column type using the
[`IColumnType`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.core/-i-column-type/index.html)
interface and
[`registerColumn()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.core/-table/register-column.html)
to register it to a table. For more information, refer to the [custom data types](Custom-data-types.topic) documentation.

### Is it possible to generate SQL without a database connection?

No, Exposed requires a database connection to generate SQL.
SQL generation depends on the database dialect and transaction context, both of which are determined by the active 
database connection. Since Exposed adapts queries dynamically based on the underlying database, a connection is
necessary even if the query is never executed.

### How do I get a plain SQL query which will be executed?

You can use `Statement.prepareSQL()`, and potentially `buildStatement()`. For more details, see [](DSL-Statement-Builder.md).

### Is it possible to update a field relative to current field value?

Yes. You can achieve this by using the `.update()` function with the `SqlExpressionBuilder`. For more information, see
[how to update a record](DSL-CRUD-operations.topic#update-record).

### How do I prepare query like `SELECT * FROM table WHERE (x,y) IN ((1, 2), (3, 4), (5, 6))`?

Exposed provides the
[`inList()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.core/-i-sql-expression-builder/in-list.html)
function that works with pairs of columns. For more details, see
[](DSL-Querying-data.topic#collection-condition-pairs-or-triples).

### How can I convert a DSL query result to a DAO entity?

To convert the result of a DSL query into an entity, you can use the DAO's
[`wrapRow()`](https://jetbrains.github.io/Exposed/api/exposed-dao/org.jetbrains.exposed.v1.dao/-entity-class/wrap-row.html)
function, which allows you to wrap a row into a DAO entity.

### How can I implement nested queries?

You can implement nested queries by using the `alias()` function to create subqueries and join them with other tables
or queries. For more information, see the [alias](DSL-Querying-data.topic#alias) documentation.

### Is it possible to create tables with cyclic (circular) reference?

Yes, it is. To define such tables, you can use the `reference()` or `optReference()` functions to establish foreign key 
relationships between tables. For more information, see the [](DAO-Relationships.topic) topic.

### How can I use a savepoint?

You can set a savepoint through the `ExposedConnection.setSavepoint()` method within a transaction. For more details,
see [](Transactions.md#using-savepoints).

### Is it possible to use a low-level JDBC connection directly with Exposed?

Yes, by accessing the raw connection wrapped by a transaction block's `connection` property:

```Kotlin
transaction {
    val lowLevelCx = connection.connection as java.sql.Connection

    val stmt = lowLevelCx.prepareStatement("INSERT INTO TEST_TABLE (AMOUNT) VALUES (?)")
    stmt.setInt(1, 99)
    stmt.addBatch()
    stmt.setInt(1, 100)
    stmt.addBatch()
    stmt.executeBatch()

    val query = lowLevelCx.createStatement()
    val result = query.executeQuery("SELECT COUNT(*) FROM TEST_TABLE")
    result.next()
    val count = result.getInt(1)
    println(count) // 2
}
```

### How can I add another type of database?

To add another type of database that is not currently supported by Exposed, implement the
[`DatabaseDialect`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.core.vendors/-database-dialect/index.html)
interface and register it with
[`Database.registerDialect()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.core/-database/-companion/register-dialect.html).

If the implementation adds a lot of value, consider [contributing](Contributing.md) it to Exposed.

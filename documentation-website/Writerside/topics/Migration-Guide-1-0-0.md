<show-structure for="chapter,procedure" depth="3"/>

# Migrating from 0.61.0 to 1.0.0

This guide provides instructions on how to migrate from Exposed version 0.61.0 to the version 1.0.0,
which primarily provides additional R2DBC support on top of the existing JDBC support.

## Import versioning and package renaming

### Updated imports

All dependencies have been updated to follow the import path pattern of `org.jetbrains.exposed.v1.packageName.*`.

This means that imports from `exposed-core`, for example, which were previously located under `org.jetbrains.exposed.sql.*`,
are now located under `org.jetbrains.exposed.v1.core.*`. The table below shows example changes:

| 0.61.0                                        | 1.0.0-beta-1                                    |
|-----------------------------------------------|-------------------------------------------------|
| `org.jetbrains.exposed.sql.Table`             | `org.jetbrains.exposed.v1.core.Table`           |
| `org.jetbrains.exposed.sql.AbstractQuery`     | `org.jetbrains.exposed.v1.core.AbstractQuery`   |
| `org.jetbrains.exposed.sql.Expression`        | `org.jetbrains.exposed.v1.core.Expression`      |
| `org.jetbrains.exposed.dao.id.EntityID`       | `org.jetbrains.exposed.v1.core.dao.id.EntityID` |
| `org.jetbrains.exposed.dao.IntEntity`         | `org.jetbrains.exposed.v1.dao.IntEntity`        |
| `org.jetbrains.exposed.sql.javatime.datetime` | `org.jetbrains.exposed.v1.javatime.datetime`    |
| `org.jetbrains.exposed.sql.json.json`         | `org.jetbrains.exposed.v1.json.json`            |

### Moved imports

The major design change for allowing R2DBC functionality involved extracting some of the classes and interfaces
from `exposed-core` and moving them to `exposed-jdbc`, so that new R2DBC variants could also be created in `exposed-r2dbc`.
The table below shows example changes:

| 0.61.0                                                      | 1.0.0-beta-1                                                    |
|-------------------------------------------------------------|-----------------------------------------------------------------|
| `org.jetbrains.exposed.sql.Database`                        | `org.jetbrains.exposed.v1.jdbc.Database`                        |
| `org.jetbrains.exposed.sql.SchemaUtils`                     | `org.jetbrains.exposed.v1.jdbc.SchemaUtils`                     |
| `org.jetbrains.exposed.sql.Query`                           | `org.jetbrains.exposed.v1.jdbc.Query`                           |
| `org.jetbrains.exposed.sql.transactions.TransactionManager` | `org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager` |

Additionally, top-level query and statement functions that required an R2DBC variant have been moved out of `exposed-core`.
This also applies to certain class methods that perform metadata query checks, namely the `exists()` method from the classes
`Table`, `Schema`, and `Sequence`. The table below shows example changes:

| 0.61.0                                | 1.0.0-beta-1                              |
|---------------------------------------|-------------------------------------------|
| `org.jetbrains.exposed.sql.select`    | `org.jetbrains.exposed.v1.jdbc.select`    |
| `org.jetbrains.exposed.sql.selectAll` | `org.jetbrains.exposed.v1.jdbc.selectAll` |
| `org.jetbrains.exposed.sql.andWhere`  | `org.jetbrains.exposed.v1.jdbc.andWhere`  |
| `org.jetbrains.exposed.sql.exists`    | `org.jetbrains.exposed.v1.jdbc.exists`    |
| `org.jetbrains.exposed.sql.insert`    | `org.jetbrains.exposed.v1.jdbc.insert`    |
| `org.jetbrains.exposed.sql.update`    | `org.jetbrains.exposed.v1.jdbc.update`    |

### IDE auto-import assistance

These changes to the import paths will present as multiple unresolved errors in the IDE and may be tedious to resolve and add manually.

If IntelliJ IDEA is being used, a shortcut to resolving these import errors may be to rely on the [automatic addition](https://www.jetbrains.com/help/idea/creating-and-optimizing-imports.html#automatically-add-import-statements)
of import statements by temporarily enabling 'Add unambiguous imports' in `Settings | Editor | General | Auto Import`.
With that option checked, the deletion of any unresolved import statements should trigger the automatic addition of the correct paths,
which can then be confirmed manually.

### Implicit imports and naming conflicts

Prior to version 1.0.0, it was possible to create custom extension functions and class methods with identical names to existing
query and statement functions, like `selectAll()` and `insert()`. This is still possible with version 1.0.0; however, due to
the new import paths of these Exposed functions, the use of wildcard imports may lead to unexpected invocation behavior
if such custom functions are also being used. It is recommended to explicitly import these custom functions in the event
that renaming is not a feasible option.

## Transactions

The class `Transaction` remains in `exposed-core` but it is now abstract and all its driver-specific properties and methods
are now accessible from the new open classes `JdbcTransaction` and `R2dbcTransaction`.
The following shows some examples of the ownership changes:

| 0.61.0                    | 1.0.0-beta-1                       |
|---------------------------|------------------------------------|
| `Transaction.connection`  | `JdbcTransaction.connection`       |
| `Transaction.db`          | `JdbcTransaction.db`               |
| `Transaction.exec()`      | `JdbcTransaction.exec()`           |
| `Transaction.rollback()`  | `JdbcTransaction.rollback()`       |

### Custom functions

Any custom transaction-scoped extension functions will most likely require the receiver to be changed from `Transaction`
to `JdbcTransaction`, as may any functions that used to accept a `Transaction` as an argument:

<compare first-title="0.61.0" second-title="1.0.0-beta-1">

```kotlin
import org.jetbrains.exposed.sql.Transaction

fun Transaction.getVersionString(): String {
    val alias = "VERSION"
    val sql = "SELECT H2VERSION() AS $alias"
    val result = exec(sql) {
        it.next()
        it.getString(alias)
    }
    return result ?: ""
}
```

```kotlin
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction

fun JdbcTransaction.getVersionString(): String {
    val alias = "VERSION"
    val sql = "SELECT H2VERSION() AS $alias"
    val result = exec(sql) {
        it.next()
        it.getString(alias)
    }
    return result ?: ""
}
```

</compare>

### Transaction managers

The `TransactionManager` interface has undergone a similar redesign, except that the interface remaining in `exposed-core`
has been renamed to `TransactionManagerApi`. The latter only holds the properties and methods common to both JDBC and R2DBC drivers.

With version 1.0.0, it is still possible to call the companion object methods on `TransactionManager` because new implementations
have been introduced to both `exposed-jdbc` and `exposed-r2dbc`. The return type of some of these methods may have changed to
reflect the exact `Transaction` implementation:

<compare first-title="0.61.0" second-title="1.0.0-beta-1">

```kotlin
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.TransactionManager

val tx1: Transaction? = TransactionManager.currentOrNull()
val tx2: Transaction = TransactionManager.current()
```

```kotlin
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

val tx1: R2dbcTransaction? = TransactionManager.currentOrNull()
val tx2: R2dbcTransaction = TransactionManager.current()
```

</compare>

### JDBC `suspend` functions deprecated

The original top-level suspend transaction functions, namely `newSuspendedTransaction()`, `withSuspendTransaction()`, and
`suspendedTransactionAsync()`, have been moved out of `exposed-core` and into `exposed-jdbc`. They have also been deprecated.

To properly open a suspend transaction block, these functions should be replaced with `suspendTransaction()` and
`suspendTransactionAsync()` from `exposed-r2dbc`.

Please leave a comment on [YouTrack](https://youtrack.jetbrains.com/issue/EXPOSED-74/Add-R2DBC-Support)
with a use case if you believe these methods should remain available for blocking JDBC connections.

## Statement builders and executables

The abstract class `Statement` remains in `exposed-core`, along with all its original implementations, like `InsertStatement`.
But from version 1.0.0, these statement classes no longer hold any logic pertaining to their specific execution in the database
nor store any knowledge of the transaction they are created in. These classes are now only responsible for SQL syntax building
and parameter binding.

The original extracted logic is now owned by a newly introduced interface, `BlockingExecutable`, in `exposed-jdbc`.
The R2DBC variant is `SuspendExecutable` in `exposed-r2dbc`. Each core statement implementation now has an associated
executable implementation, like `InsertBlockingExecutable`, which stores the former in its `statement` property.
The following shows some examples of the ownership changes:

| 0.61.0                                                       | 1.0.0-beta-1                                                              |
|--------------------------------------------------------------|---------------------------------------------------------------------------|
| `Statement.execute()`                                        | `BlockingExecutable.execute()`                                            |
| `Statement.prepared()`                                       | `BlockingExecutable.prepared()`                                           |
| `with(Statement) { PreparedStatementApi.executeInternal() }` | `with(BlockingExecutable) { JdbcPreparedStatementApi.executeInternal() }` |
| `Statement.isAlwaysBatch`                                    | `BlockingExecutable.isAlwaysBatch`                                        |

If a statement implementation originally held a protected `transaction` property, this is now also owned by the executable implementation.

### Custom statements

This separation of responsibility means that any custom `Statement` implementation will now require an associated `BlockingExecutable`
implementation to be sent to the database. The `BlockingExecutable` may be custom or an existing class may provide sufficient
execution logic, as shown in the following example:

<compare first-title="0.61.0" second-title="1.0.0-beta-1">

```kotlin
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.BatchInsertStatement
import org.jetbrains.exposed.sql.transactions.transaction

class BatchInsertOnConflictDoNothing(
    table: Table,
) : BatchInsertStatement(table) {
    override fun prepareSQL(
        transaction: Transaction,
        prepared: Boolean
    ) = buildString {
        val insertStatement = super.prepareSQL(transaction, prepared)
        append(insertStatement)
        append(" ON CONFLICT (id) DO NOTHING")
    }

    // optional custom execute logic
}

transaction {
    val insertedCount: Int? = BatchInsertOnConflictDoNothing(
        TableA
    ).run {
        addBatch()
        // set column values using this[columnName] = value

        addBatch()
        // set column values using this[columnName] = value

        execute(this@transaction)
    }
}
```

```kotlin
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.statements.BatchInsertStatement
import org.jetbrains.exposed.v1.jdbc.statements.BatchInsertBlockingExecutable
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class BatchInsertOnConflictDoNothing(
    table: Table,
) : BatchInsertStatement(table) {
    override fun prepareSQL(
        transaction: Transaction,
        prepared: Boolean
    ) = buildString {
        val insertStatement = super.prepareSQL(transaction, prepared)
        append(insertStatement)
        append(" ON CONFLICT (id) DO NOTHING")
    }

    // optional custom execute logic -> create custom Executable
}

transaction {
    val executable = BatchInsertBlockingExecutable(
        statement = BatchInsertOnConflictDoNothing(TableA)
    )
    val insertedCount: Int? = executable.run {
        statement.addBatch()
        // set column values using statement[column] = value

        statement.addBatch()
        // set column values using statement[column] = value

        execute(this@transaction)
    }
}
```

</compare>

### `exec()` parameter type changed

Prior to version 1.0.0, it was possible to create a `Statement` instance, using a built-in or custom implementation,
and send it to the database by passing it as an argument to `exec()`.

In version 1.0.0, since statements are no longer responsible for execution, the same `exec()` method only accepts a
`BlockingExecutable` as its argument:

<compare first-title="0.61.0" second-title="1.0.0-beta-1">

```kotlin
import org.jetbrains.exposed.sql.statements.DeleteStatement
import org.jetbrains.exposed.sql.transactions.transaction

transaction {
    val delete = DeleteStatement(
        targetsSet = TableA,
        where = null
    )
    val result = exec(delete) {
        // do something with deleted row count
    }
}
```

```kotlin
import org.jetbrains.exposed.v1.core.statements.DeleteStatement
import org.jetbrains.exposed.v1.jdbc.statements.DeleteBlockingExecutable
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

transaction {
    val deleteStmt = DeleteStatement(
        targetsSet = TableA,
        where = null
    )
    val delete = DeleteBlockingExecutable(
        statement = deleteStmt
    )
    val result = exec(delete) {
        // do something with deleted row count
    }
}
```

</compare>

This signature change does not affect the method's use with a `Query` argument, since the latter implements `BlockingExecutable` directly.

### `ReturningStatement` return type changed

Prior to version 1.0.0, table extension functions that returned values for specified columns, like `insertReturning()` and 
`updateReturning()`, returned a value of type `ReturningStatement`.
This return type ensured that such statements would be sent to the database only at the moment a terminal operation
attempted to iterate over the results.

In version 1.0.0, since all execution logic has been removed from the core `ReturningStatement`, these functions instead return
a value of type `ReturningBlockingExecutable`. Other than this return type change, the return value can be iterated over in
the same manner as previously.

### `DeleteStatement` companion methods deprecated

Prior to version 1.0.0, the companion object of `DeleteStatement` provided methods `all()` and `where()` as alternatives
to calling `Table.deleteAll()` or `Table.deleteWhere()`.

Following version 1.0.0's removal of statement execution logic from
`exposed-core`, these companion methods have now been deprecated. It is recommended to use the table extension functions
directly or combine a `DeleteStatement` constructor with `DeleteBlockingExecutable`.

### `insert()` parameter type changed

The type of the `body` parameter lambda block for `insert()` has been changed to take `UpdateBuilder` as its argument,
instead of `InsertStatement` directly. This applies to its variants as well, like `insertIgnore()` and `insertAndGetId()`.

## Queries

While the `AbstractQuery` class remains in `exposed-core`, its `Query` implementation is now in `exposed-jdbc`, with the
R2DBC variant located in `exposed-r2dbc.` This restructuring was necessary to allow for the required differences in the
underlying implementations of each class, with the JDBC `Query` ultimately implementing `Iterable`, and the R2DBC `Query`
implementing `Flow`.

### `CommentPosition` ownership changed

Certain `Query` properties, like `where` and `having` (as well as their associated adjustment methods), have been pulled
down from the subclass into superclass `AbstractQuery` so that they remain common to both drivers.

This also includes the`comments` property and its related enum class `CommentPosition`,
which is now only accessible from `AbstractQuery`:

<compare first-title="0.61.0" second-title="1.0.0-beta-1">

```kotlin
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.selectAll

val queryWithHint = TableA
    .selectAll()
    .comment(
        content = "+ MAX_EXECUTION_TIME(1000) ",
        position = Query.CommentPosition.AFTER_SELECT
    )
```

```kotlin
import org.jetbrains.exposed.v1.core.AbstractQuery
import org.jetbrains.exposed.v1.jdbc.selectAll

val queryWithHint = TableA
    .selectAll()
    .comment(
        content = "+ MAX_EXECUTION_TIME(1000) ",
        position = AbstractQuery.CommentPosition.AFTER_SELECT
    )
```

</compare>

## Result wrappers

Two new `exposed-core` interfaces, `ResultApi` and `RowApi`, have been introduced to represent commonalities between the
results of query execution with JDBC (`java.sql.ResultSet`) and with R2DBC (`io.r2dbc.spi.Result`).
These are both implemented by new driver-specific wrapper classes, `JdbcResult` and `R2dbcResult`.

### `readObject()` parameter type changed {id = read-object}

Since driver-specific results, like `java.sql.ResultSet`, are no longer supported in `exposed-core`, they are instead wrapped
by common interfaces, like `RowApi`.

The `IColumnType` interface has a method `readObject()` for performing any special read and/or conversion logic when
accessing an object at a specific index in the result.
The signature of this method has changed to use `RowApi` instead of `ResultSet`, which still allows access via `getObject()`:

<compare first-title="0.61.0" second-title="1.0.0-beta-1">

```kotlin
import org.jetbrains.exposed.sql.TextColumnType
import java.sql.ResultSet

class ShortTextColumnType : TextColumnType() {
    override fun readObject(
        rs: ResultSet,
        index: Int
    ): Any? {
        return rs
            .getString(index)
            .take(MAX_CHARS)
    }

    companion object {
        private const val MAX_CHARS = 128
    }
}
```

```kotlin
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.statements.api.RowApi

class ShortTextColumnType : TextColumnType() {
    override fun readObject(
        rs: RowApi,
        index: Int
    ): Any? {
        return rs
            .getObject(index, java.lang.String::class.java)
            ?.take(MAX_CHARS)
    }

    companion object {
        private const val MAX_CHARS = 128
    }
}
```

</compare>

### `ResultRow.create()` parameter type changed

As mentioned in the section on [`readObject()`](#read-object), all usage of `java.sql.ResultSet` has been removed from
`exposed-core`. The companion method to create an Exposed `ResultRow` directly from the result of a query or statement
execution now accepts a `RowApi` as an argument.

### `execute()` return type changed

Calling `execute()` directly on a `Query` will no longer return a `ResultSet`. It will instead return a `ResultApi`,
which must be cast if needing to access the original wrapped result type:

<compare first-title="0.61.0" second-title="1.0.0-beta-1">

```kotlin
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.ResultSet

transaction {
    val result: ResultSet? = TableA
        .selectAll()
        .where { TableA.amount greater 100 }
        .execute(this)
}
```

```kotlin
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.statements.jdbc.JdbcResult
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.ResultSet

transaction {
    val result: ResultSet? = (TableA
        .selectAll()
        .where { TableA.amount greater 100 }
        .execute(this) as? JdbcResult)
        ?.result
}
```

</compare>

### `StatementResult.Object` property type changed

The `Object` type from the `exposed-core` sealed class `StatementResult` no longer has a property of type `java.sql.ResultSet`.
Its property is now of type `ResultApi`.

## `DatabaseDialect` and `VendorDialect`

Any method from the `exposed-core` interface `DatabaseDialect` that required driver-specific metadata queries has been extracted.
If moved, the method is now accessible from the new abstract class `DatabaseDialectMetadata` found in `exposed-jdbc`.
This also applies to such methods and properties from the core `VendorDialect` implementations.
The following shows some examples of the ownership changes:

| 0.61.0                                              | 1.0.0-beta-1                                                |
|-----------------------------------------------------|-------------------------------------------------------------|
| `DatabaseDialect.allTablesNames()`                  | `DatabaseDialectMetadata.allTablesNames()`                  |
| `DatabaseDialect.tableColumns()`                    | `DatabaseDialectMetadata.tableColumns()`                    |
| `DatabaseDialect.catalog()`                         | `DatabaseDialectMetadata.catalog()`                         |
| `DatabaseDialect.existingIndices()`                 | `DatabaseDialectMetadata.existingIndices()`                 |

<note>
Corresponding ownership changes for the underlying metadata query functions originally called by the above <code>DatabaseDialect</code> methods
have also been introduced. This means that the abstract class <code>ExposedDatabaseMetadata</code> in <code>exposed-core</code> additionally has
had some of its methods extracted to the driver-specific implementations in <code>exposed-jdbc</code> and <code>exposed-r2dbc</code>.
</note>

These methods would have previously been most commonly invoked on the top-level property `currentDialect`.
To follow a similar patter, a related property, `currentDialectMetadata`, has been added to replace the original call:

<compare first-title="0.61.0" second-title="1.0.0-beta-1">

```kotlin
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.vendors.currentDialect

transaction {
    val tableKeys = currentDialect.existingPrimaryKeys(TableA)[TableA]
    if (TableA.tableName in currentDialect.allTablesNames()) {
        // do something
    }
}
```

```kotlin
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.vendors.currentDialectMetadata

transaction {
    val tableKeys = currentDialectMetadata.existingPrimaryKeys(TableA)[TableA]
    if (TableA.tableName in currentDialectMetadata.allTablesNames()) {
        // do something
    }
}
```

</compare>

### Custom dialects

In the same way as how there are database-specific implementations for further extensibility in `exposed-core`, like `H2Dialect`,
the new class also comes with open implementations for metadata extensions, like `H2DialectMetadata`. These new classes should
be extended to hold any custom overrides for the metadata methods that have been moved.

Any custom database dialect implementation can be registered as before via `Database.registerDialect()`. With version 1.0.0,
an additional call to `Database.registerDialectMetadata()` should be used to ensure that the newly associated metadata implementation
is also registered.

### `resolveRefOptionFromJdbc()` removed

Given the original intention behind this method, `DatabaseDialect.resolveRefOptionFromJdbc()` has been removed and replaced
by a driver-agnostic variant `resolveReferenceOption()` found under `org.jetbrains.exposed.v1.core.statements.api.ExposedDatabaseMetadata`.
Driver-specific variants have been introduced both in `exposed-jdbc` and `exposed-r2dbc`.

### Property `ENABLE_UPDATE_DELETE_LIMIT` removed

This property in the companion object of `SQLiteDialect()` has been removed and replaced entirely by
`DatabaseDialectMetadata.supportsLimitWithUpdateOrDelete()`.

## Additional core class restructuring

Some other public classes have been restructured to remove any driver-specific logic from `exposed-core`.

### `Database`

A new abstract class, `DatabaseApi`, has been added to `exposed-core` to hold all properties and methods relevant to both
JDBC and R2DBC databases and their future connections. The original `Database` class and its companion object have been moved
to `exposed-jdbc`, and a new class `R2dbcDatabase` has been introduced to `exposed-r2dbc`.

### `PreparedStatementApi`

The `PreparedStatementApi` interface no longer holds methods that perform any statement execution logic.

With version 1.0.0, these are now only accessible from the new driver-specific interface implementation,
`JdbcPreparedStatementApi`. The following shows some examples of the ownership changes:

| 0.61.0                                       | 1.0.0-beta-1                                 |
|----------------------------------------------|----------------------------------------------|
| `PreparedStatementApi.executeQuery()`        | `JdbcPreparedStatementApi.executeQuery()`    |
| `PreparedStatementApi.executeUpdate()`       | `JdbcPreparedStatementApi.executeUpdate()`   |
| `PreparedStatementApi.addBatch()`            | `JdbcPreparedStatementApi.addBatch()`        |
| `PreparedStatementApi.cancel()`              | `JdbcPreparedStatementApi.cancel()`          |

#### `set()` deprecated

The original `operator fun set(index: Int, value: Any)` has been deprecated. It should be replaced by a new variant
that accepts a third argument for the column type associated with the value being bound to the statement.
This new `set()` method will require an override if the interface is implemented directly.

#### `setArray()` deprecated

The original `setArray(index: Int, type: String, array: Array<*>)` has been deprecated. It should be replaced by a new variant
that accepts the actual `ArrayColumnType` associated with the array value being bound to the statement as the second argument,
instead of a string representation of the type. This new `setArray()` method will require an override if the interface is
implemented directly.

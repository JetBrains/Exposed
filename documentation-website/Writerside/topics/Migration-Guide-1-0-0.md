<show-structure for="chapter,procedure" depth="3"/>

# Migrating from 0.61.0 to 1.0.0

This guide provides instructions on how to migrate from Exposed version 0.61.0 to the version 1.0.0.
Version 1.0.0 introduces R2DBC support on top of the existing JDBC support. Most of the changes in this release were made
to accommodate reactive database access while preserving existing functionality.

## Import versioning and package renaming

### Updated imports

All dependencies have been updated to follow the import path pattern of `org.jetbrains.exposed.v1.packageName.*`.
This introduces two key changes in package naming: unique prefixes for every module and artifact, and an additional `v1` prefix.

The unique prefix on every module makes it easier to differentiate which dependency a particular class, function,
or other element comes from. This becomes increasingly important as the number of packages grows. The unique `v1` prefix
for the whole version helps users who have transient dependencies to the `0.x` version of Exposed.

This means that imports from `exposed-core`, for example, which were previously located under `org.jetbrains.exposed.sql.*`,
are now located under `org.jetbrains.exposed.v1.core.*`. The table below shows example changes:

| 0.61.0                                        | 1.0.0                                           |
|-----------------------------------------------|-------------------------------------------------|
| `org.jetbrains.exposed.sql.Table`             | `org.jetbrains.exposed.v1.core.Table`           |
| `org.jetbrains.exposed.sql.AbstractQuery`     | `org.jetbrains.exposed.v1.core.AbstractQuery`   |
| `org.jetbrains.exposed.sql.Expression`        | `org.jetbrains.exposed.v1.core.Expression`      |
| `org.jetbrains.exposed.dao.id.EntityID`       | `org.jetbrains.exposed.v1.core.dao.id.EntityID` |
| `org.jetbrains.exposed.dao.IntEntity`         | `org.jetbrains.exposed.v1.dao.IntEntity`        |
| `org.jetbrains.exposed.sql.javatime.datetime` | `org.jetbrains.exposed.v1.javatime.datetime`    |
| `org.jetbrains.exposed.sql.json.json`         | `org.jetbrains.exposed.v1.json.json`            |

Check [Breaking Changes - 1.0.0-beta-1](https://www.jetbrains.com/help/exposed/breaking-changes.html#1-0-0-beta-1) for more details about the changes in imports.  

### Moved imports

The major design change for allowing R2DBC functionality involved extracting some of the classes and interfaces
from `exposed-core` and moving them to `exposed-jdbc`, so that new R2DBC variants could also be created in `exposed-r2dbc`.
The table below shows example changes:

| 0.61.0                                                      | 1.0.0                                                           |
|-------------------------------------------------------------|-----------------------------------------------------------------|
| `org.jetbrains.exposed.sql.Database`                        | `org.jetbrains.exposed.v1.jdbc.Database`                        |
| `org.jetbrains.exposed.sql.SchemaUtils`                     | `org.jetbrains.exposed.v1.jdbc.SchemaUtils`                     |
| `org.jetbrains.exposed.sql.Query`                           | `org.jetbrains.exposed.v1.jdbc.Query`                           |
| `org.jetbrains.exposed.sql.transactions.TransactionManager` | `org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager` |

Additionally, top-level query and statement functions that required an R2DBC variant have been moved out of `exposed-core`.
This also applies to certain class methods that perform metadata query checks, namely the `exists()` method from the classes
`Table`, `Schema`, and `Sequence`. The table below shows example changes:

| 0.61.0                                | 1.0.0                                     |
|---------------------------------------|-------------------------------------------|
| `org.jetbrains.exposed.sql.select`    | `org.jetbrains.exposed.v1.jdbc.select`    |
| `org.jetbrains.exposed.sql.selectAll` | `org.jetbrains.exposed.v1.jdbc.selectAll` |
| `org.jetbrains.exposed.sql.andWhere`  | `org.jetbrains.exposed.v1.jdbc.andWhere`  |
| `org.jetbrains.exposed.sql.exists`    | `org.jetbrains.exposed.v1.jdbc.exists`    |
| `org.jetbrains.exposed.sql.insert`    | `org.jetbrains.exposed.v1.jdbc.insert`    |
| `org.jetbrains.exposed.sql.update`    | `org.jetbrains.exposed.v1.jdbc.update`    |

<note>
This means that a project with a dependency on <code>exposed-spring-boot-starter</code>, for example, will now most likely require an
additional dependency on <code>exposed-jdbc</code>.
</note>

### `SqlExpressionBuilder` method imports {id = sql-expression-builder-imports}

The interface `ISqlExpressionBuilder` (and all its methods) has been deprecated, along with its implementation objects, 
`SqlExpressionBuilder` and `UpsertSqlExpressionBuilder`. All methods previously restricted to this interface should now
be replaced with their new equivalent top-level functions. This will require the addition of new import statements if
`org.jetbrains.exposed.v1.core.*` is not already present:

<compare first-title="0.61.0" second-title="1.0.0">

```kotlin
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.selectAll
    
val amountIsLow = TableA.amount less 10
TableA
    .selectAll()
    .where(amountIsLow)
```

```kotlin
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.selectAll

val amountIsLow = TableA.amount less 10
TableA
    .selectAll()
    .where(amountIsLow)
```

</compare>

<compare first-title="0.61.0" second-title="1.0.0">

```kotlin
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
    
val isValid = TableA.value.isNotNull() and
    (TableA.amount greaterEq 10)
TableA
    .selectAll()
    .where(isValid)
```

```kotlin
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.selectAll

val isValid = TableA.value.isNotNull() and
    (TableA.amount greaterEq 10)
TableA
    .selectAll()
    .where(isValid)
```

</compare>

This means that it is no longer necessary to use a scope function with `SqlExpressionBuilder` as the receiver,
so builder methods like `Op.build()` and `Expression.build()` have also been deprecated:

<compare first-title="0.61.0" second-title="1.0.0">

```kotlin
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.concat
    
val calculatedAmount = Expression.build {
    (TableA.amount * 2) - 10
}

val detailsInvalid = Op.build {
    TableA.details like "% - N/A"
}

val newDetails = with(SqlExpressionBuilder) {
    Case()
        .When(TableA.amount eq 0, TableA.details + " - S/O")
        .When(TableA.warranty.isNull(), TableA.details + " - N/A")
        .Else(TableA.details)
}.alias("nl")

TableA.update {
    it[TableA.details] = concat(TableA.details.upperCase(), stringLiteral(" - UPDATED"))
    it.update(TableA.amount) { TableA.amount plus 3 }
}
```

```kotlin
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.update

val calculatedAmount = (TableA.amount * 2) - 10

val detailsInvalid = TableA.details like "% - N/A"

val newDetails = Case()
    .When(TableA.amount eq 0, TableA.details + " - S/O")
    .When(TableA.warranty.isNull(), TableA.details + " - N/A")
    .Else(TableA.details)
    .alias("nl")

TableA.update {
    it[TableA.details] = concat(TableA.details.upperCase(), stringLiteral(" - UPDATED"))
    it[TableA.amount] = TableA.amount plus 3
}
```

</compare>

<note>
Any higher-order function with a parameter that accepts <code>SqlExpressionBuilder</code> as the receiver (or argument)
has also changed to no longer use this object, meaning that import statements may need to be added.
See the <a href="#sql-expression-builder-lambda">section below</a> for more details.
</note>

### IDE auto-import assistance

The above changes to the import paths will present as multiple unresolved errors in your IDE and may be tedious to resolve and add manually.

In IntelliJ IDEA, a shortcut to resolving these import errors may be to rely on the [automatic addition](https://www.jetbrains.com/help/idea/creating-and-optimizing-imports.html#automatically-add-import-statements)
of import statements by temporarily enabling the **Add unambiguous imports** option in **Settings | Editor | General | Auto Import**.
With that option checked, the deletion of any unresolved import statements should trigger the automatic addition of the correct paths,
which can then be confirmed manually.

### Implicit imports and naming conflicts

Prior to version 1.0.0, it was possible to create custom extension functions and class methods with identical names to existing
query and statement functions, like `selectAll()` and `insert()`. This is still possible with version 1.0.0. However, due to
the import paths of these Exposed functions, using wildcard imports may lead to unexpected invocation behavior
if such custom functions are also being used. It is recommended to explicitly import these custom functions in the event
that renaming is not a feasible option.

## Kotlin version

Version 1.0.0 uses Kotlin 2.2, compared to 0.61.0, which used Kotlin 2.0.

### Datetime minimum requirement

As part of the core refactoring, the 3 available datetime artifacts have been restructured to have their column type classes
depend on common abstract classes in `exposed-core`. See [datetime class refactorings](#datetime-column-type-refactor)
for full details if you rely on any of these dependencies.

This new core datetime API relies on [`kotlinx.datetime` features](https://github.com/Kotlin/kotlinx-datetime?tab=readme-ov-file#using-in-your-projects)
that are only compatible with `kotlin-stdlib` 2.1.20 or higher. Attempting to build with an Exposed datetime artifact using an older
Kotlin version may lead to `NoClassDefFoundError` and will require bumping the Kotlin version.

## Migration dependencies

Prior to version 1.0.0, `MigrationUtils` was available with a dependency on the `exposed-migration` artifact.
In order to enable the use of its utility methods with both JDBC and R2DBC support, this artifact is now replaced by
`exposed-migration-core` and driver-specific artifacts have been introduced (with suffixes such as `-jdbc` and `-r2dbc`):

<compare first-title="0.61.0" second-title="1.0.0">

```kotlin
dependencies {
    // ...
    implementation("org.jetbrains.exposed:exposed-migration:0.61.0")
}
```

```kotlin
dependencies {
    // ...
    implementation("org.jetbrains.exposed:exposed-migration-core:1.0.0")
    implementation("org.jetbrains.exposed:exposed-migration-jdbc:1.0.0")
}
```

</compare>

This means that the import path of `MigrationUtils` has also been updated to follow the pattern of the other [package changes](#updated-imports):

<compare first-title="0.61.0" second-title="1.0.0">

```kotlin
import org.jetbrains.exposed.sql.transactions.transaction

transaction {
    MigrationUtils.statementsRequiredForDatabaseMigration(
        TableA,
        withLogs = false
    )
}
```

```kotlin
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils

transaction {
    MigrationUtils.statementsRequiredForDatabaseMigration(
        TableA,
        withLogs = false
    )
}
```

</compare>

## Transactions

The class `Transaction` remains in `exposed-core` but it is now abstract and all its driver-specific properties and methods
are now accessible from the new open classes `JdbcTransaction` and `R2dbcTransaction`.
The following shows some examples of the ownership changes:

| 0.61.0                    | 1.0.0                        |
|---------------------------|------------------------------|
| `Transaction.connection`  | `JdbcTransaction.connection` |
| `Transaction.db`          | `JdbcTransaction.db`         |
| `Transaction.exec()`      | `JdbcTransaction.exec()`     |
| `Transaction.rollback()`  | `JdbcTransaction.rollback()` |

### Custom functions

Any custom transaction-scoped extension functions will most likely require the receiver to be changed from `Transaction`
to `JdbcTransaction`, as may any functions that used to accept a `Transaction` as an argument:

<compare first-title="0.61.0" second-title="1.0.0">

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

### Transaction `id` renamed

The property `Transaction.id` has been renamed to `Transaction.transactionId` to avoid common naming collisions with users' code.

### `addLogger()`

Prior to version 1.0.0, `.addLogger()` was available as an extension function on the original `Transaction` class,
requiring an import statement for usage.

In version 1.0.0, `addLogger()` remains available, but as a method of the new base `Transaction` class, with final method overrides
in the respective `JdbcTransaction` and `R2dbcTransaction` classes:

<compare first-title="0.61.0" second-title="1.0.0">

```kotlin
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.transaction

transaction { // this: Transaction
    addLogger(StdOutSqlLogger)
}
```

```kotlin
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

transaction { // this: JdbcTransaction
    addLogger(StdOutSqlLogger)
}
```

</compare>

### `transaction()` signature changed

The order of declared parameters for the functions `transaction()` and `inTopLevelTransaction()` has changed, with the
`db` parameter of type `Database` now being the first parameter.

The `transactionIsolation` parameter has additionally been
provided a default argument based on the value set by the database's transaction manager, which defaults to the value
configured on `Database.connect()`.

The default argument for the `readOnly` parameter is no longer `false`, but instead
also defaults to the value derived from the database's transaction manager configuration.

<compare first-title="0.61.0" second-title="1.0.0">

```kotlin
import org.jetbrains.exposed.sql.transactions.*

inTopLevelTransaction(
    Connection.TRANSACTION_SERIALIZABLE
) { }

transaction(
    db.transactionManager.defaultIsolationLevel,
    db = db
) { }
```

```kotlin
import org.jetbrains.exposed.v1.jdbc.transactions.*

inTopLevelTransaction(
    transactionIsolation = Connection.TRANSACTION_SERIALIZABLE
) { }

transaction(db) { }
```

</compare>

### Transaction managers

The `TransactionManager` interface has undergone a similar redesign, except that the interface remaining in `exposed-core`
has been renamed to `TransactionManagerApi`. This interface contains only the properties and methods common to both JDBC and R2DBC drivers.

With version 1.0.0, you can still call companion object methods on `TransactionManager` because new implementations
have been introduced to both `exposed-jdbc` and `exposed-r2dbc`. The return type of some of these methods may have changed to
reflect the exact `Transaction` implementation:

<compare first-title="0.61.0" second-title="1.0.0">

```kotlin
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.TransactionManager

val tx1: Transaction? = TransactionManager.currentOrNull()
val tx2: Transaction = TransactionManager.current()
```

```kotlin
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

val tx1: JdbcTransaction? = TransactionManager.currentOrNull()
val tx2: JdbcTransaction = TransactionManager.current()
```

</compare>

#### Driver-specific transaction manager interfaces

To enable users to create custom transaction managers for JDBC or R2DBC operations, new interfaces have been introduced:
* [`JdbcTransactionManager`](https://jetbrains.github.io/Exposed/api/exposed-jdbc/org.jetbrains.exposed.v1.jdbc.transactions/-jdbc-transaction-manager/index.html) in `exposed-jdbc`
* [`R2dbcTransactionManager`](https://jetbrains.github.io/Exposed/api/exposed-r2dbc/org.jetbrains.exposed.v1.r2dbc.transactions/-r2dbc-transaction-manager/index.html) in `exposed-r2dbc`

Both interfaces extend `TransactionManagerApi` and require implementation of a `db` property that returns the associated database instance (`Database` or `R2dbcDatabase`).

The concrete `TransactionManager` class in each module now implements the respective interface. This means that several API signatures have changed to use the typed managers instead of `TransactionManagerApi`:

<compare first-title="0.61.0" second-title="1.0.0">

```kotlin
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager

// JDBC
val manager: TransactionManager =
    Database.connect("jdbc:h2:mem:test", "org.h2.Driver")
        .transactionManager

Database.connect(
    url = "jdbc:h2:mem:test",
    driver = "org.h2.Driver",
    manager = { db: Database ->
        object : TransactionManager {
            // defaultIsolationLevel, defaultReadOnly, ...
            
            override fun newTransaction(
                isolation: Int,
                readOnly: Boolean,
                outerTransaction: Transaction?
            ): Transaction { /* ... */ }
    
            override fun currentOrNull(): Transaction? { /* ... */ }
    
            override fun bindTransactionToThread(transaction: Transaction?) { /* ... */ }
        }
    }
)

TransactionManager.registerManager(database, manager)

// Getting manager
val tm: TransactionManager? =
    TransactionManager.managerFor(database)
```

```kotlin
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.JdbcTransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

// JDBC
val manager: JdbcTransactionManager =
    Database.connect("jdbc:h2:mem:test", "org.h2.Driver")
        .transactionManager

Database.connect(
    url = "jdbc:h2:mem:test",
    driver = "org.h2.Driver",
    manager = { db: Database ->
        object : JdbcTransactionManager {
            override val db = db

            fun newTransaction(
                isolation: Int = defaultIsolationLevel,
                readOnly: Boolean = defaultReadOnly,
                outerTransaction: JdbcTransaction? = null
            ): JdbcTransaction { /* ... */ }
        }
    }
)

TransactionManager.registerManager(database, manager)

// Getting manager
val tm: JdbcTransactionManager =
    TransactionManager.managerFor(database)
```

</compare>

The default argument for the connection `manager` parameter is no longer `ThreadLocalTransactionManager`, which has now been removed. The newly implemented
[`TransactionManager`](https://jetbrains.github.io/Exposed/api/exposed-jdbc/org.jetbrains.exposed.v1.jdbc.transactions/-transaction-manager/index.html)
is now passed as the default argument instead.

Additionally, the `currentOrNull()` method has been removed from the `TransactionManagerApi` interface. Instead, it is now available as:
* An extension function on `JdbcTransactionManager` and `R2dbcTransactionManager`
* A static method on the `TransactionManager` companion object in both modules

If you were calling `currentOrNull()` on a manager instance, replace it with either the static method or cast your manager to the appropriate typed interface:

<compare first-title="0.61.0" second-title="1.0.0">

```kotlin
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManagerApi

val manager: TransactionManagerApi =
    Database.connect(...).transactionManager
val tx = manager.currentOrNull()
```

```kotlin
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.JdbcTransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.currentOrNull

val manager: JdbcTransactionManager =
    Database.connect(...).transactionManager
val tx = manager.currentOrNull()
```

</compare>

<compare first-title="0.61.0" second-title="1.0.0 (Static Method)">

```kotlin
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManagerApi

val manager: TransactionManagerApi =
    Database.connect(...).transactionManager
val tx = manager.currentOrNull()
```

```kotlin
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

Database.connect(...)

val tx = TransactionManager.currentOrNull()
```

</compare>

#### Transaction management changes

As part of the redesign, the underlying database transaction management logic and switching of active transactions has
been changed to no longer rely on `ThreadLocal`. This means that the property `TransactionManager.threadLocal`, as well as
the methods `TransactionManagerApi.bindTransactionToThread()` and `TransactionManager.resetCurrent()` have all been removed.

The association between a `Database` instance and its `TransactionManager` has also been streamlined to ensure a stricter,
more reliable relationship. For this reason, the following changes have been made:
* The signature of `TransactionManager.managerFor()` has changed to accept only a non-nullable `database` argument
  and its return type is now a non-nullable `JdbcTransactionManager` (or `R2dbcTransactionManager` for R2DBC).
* The top-level property `transactionManager` no longer accepts a nullable `Database` as its receiver.
* `TransactionManager.isInitialized()` has also been removed.

Prior to version 1.0.0, `TransactionManager.defaultDatabase` was used to retrieve the `Database` instance that had been
set as the default to use for all transactions or, if a default was not set, the last instance created. With 1.0.0,
this functionality has been split for simplicity:
* `TransactionManager.defaultDatabase`: To get and set the default database to use when opening a transaction.
  This value will remain null unless explicitly set at some point.
* `TransactionManager.primaryDatabase`: To get the database that will be used when opening the next transaction.
  This value will either be retrieved from `defaultDatabase` or the last `Database` instance registered, if any.

Prior to version 1.0.0, the property `TransactionManager.manager` resolved its value from thread local and returned an
uninitialized stand-in manager if none was found. With 1.0.0, the manager instance retrieved is resolved based on the
current active transaction, if any; otherwise it gets the manager of the default set database or the last registered
database instance. If no manager can be resolved, this property now throws an exception.

### JDBC `suspend` functions deprecated

The original top-level suspend transaction functions, namely `newSuspendedTransaction()`, `withSuspendTransaction()`, and
`suspendedTransactionAsync()`, have been moved out of `exposed-core` and into `exposed-jdbc`. They have also been deprecated
in favor of either switching to R2DBC operations or using newly introduced JDBC functions, `suspendTransaction()` and `inTopLevelSuspendTransaction()`.

These new functions should be used if you want to continue using a blocking JDBC driver, but with the ability to call suspend functions
alongside your database transaction operations. The behavior of these functions aligns fully with the behavior of the standard
`transaction()` functions, for example where it pertains to nesting logic and exception handling. Unlike the original suspend
transaction functions, `suspendTransaction()` does not accept a `CoroutineContext` argument. Instead, a coroutine builder
function, like `withContext()` or `async()` should be used to wrap the `suspendTransaction()` block.

> To properly run asynchronous transactions using reactive drivers,
> `suspendTransaction()` overloads are now available from the `exposed-r2dbc` dependency.
>
{style="tip"}

## Statement builders and executables

The abstract class `Statement` remains in `exposed-core`, along with all its original implementations, like `InsertStatement`.
But from version 1.0.0, these statement classes no longer hold any logic pertaining to their specific execution in the database
nor store any knowledge of the transaction they are created in. These classes are now only responsible for SQL syntax building
and parameter binding.

The original extracted logic is now owned by a newly introduced interface, `BlockingExecutable`, in `exposed-jdbc`.
The R2DBC variant is `SuspendExecutable` in `exposed-r2dbc`. Each core statement implementation now has an associated
executable implementation, like `InsertBlockingExecutable`, which stores the former in its `statement` property.
The following shows some examples of the ownership changes:

| 0.61.0                                                       | 1.0.0                                                                     |
|--------------------------------------------------------------|---------------------------------------------------------------------------|
| `Statement.execute()`                                        | `BlockingExecutable.execute()`                                            |
| `Statement.prepared()`                                       | `BlockingExecutable.prepared()`                                           |
| `with(Statement) { PreparedStatementApi.executeInternal() }` | `with(BlockingExecutable) { JdbcPreparedStatementApi.executeInternal() }` |
| `Statement.isAlwaysBatch`                                    | `BlockingExecutable.isAlwaysBatch`                                        |

If a statement implementation originally held a protected `transaction` property, this is now also owned by the executable implementation.

### Custom statements

This separation of responsibility means that any custom `Statement` implementation now requires an associated `BlockingExecutable`
implementation to be sent to the database. This `BlockingExecutable` can be custom, or you can use an existing class if it provides sufficient
execution logic, as shown in the following example:

<compare first-title="0.61.0" second-title="1.0.0">

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
import org.jetbrains.exposed.v1.jdbc.statements.toExecutable
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
    val executable = BatchInsertOnConflictDoNothing(TableA).toExecutable()
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

<note>
The appropriate executable class instance can be resolved for you by calling <code>.toExecutable()</code> (
<a href="https://jetbrains.github.io/Exposed/api/exposed-jdbc/org.jetbrains.exposed.v1.jdbc.statements/to-executable.html">JDBC</a>,
<a href="https://jetbrains.github.io/Exposed/api/exposed-r2dbc/org.jetbrains.exposed.v1.r2dbc.statements/to-executable.html">R2DBC</a>,
), as long as the custom statement extends an existing subclass from the Exposed API. Alternatively, the custom statement can
be passed as an argument to the known executable class constructor.
</note>

### `exec()` parameter type changed

Prior to version 1.0.0, it was possible to create a `Statement` instance, using a built-in or custom implementation,
and send it to the database by passing it as an argument to `exec()`.

In version 1.0.0, since statements are no longer responsible for execution, the same `exec()` method only accepts a
`BlockingExecutable` as its argument:

<compare first-title="0.61.0" second-title="1.0.0">

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
import org.jetbrains.exposed.v1.jdbc.statements.toExecutable
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

transaction {
    val deleteStmt = DeleteStatement(
        targetsSet = TableA,
        where = null
    )
    val delete = deleteStmt.toExecutable()
    val result = exec(delete) {
        // do something with deleted row count
    }
}
```

</compare>

<note>
The appropriate executable class instance can be resolved for you by calling <code>.toExecutable()</code> (
<a href="https://jetbrains.github.io/Exposed/api/exposed-jdbc/org.jetbrains.exposed.v1.jdbc.statements/to-executable.html">JDBC</a>,
<a href="https://jetbrains.github.io/Exposed/api/exposed-r2dbc/org.jetbrains.exposed.v1.r2dbc.statements/to-executable.html">R2DBC</a>).
Alternatively, the statement can be passed as an argument directly to the <code>DeleteBlockingExecutable</code> constructor.
</note>

This signature change does not affect the method's use with a `Query` argument, as `Query` implements `BlockingExecutable` directly.
But changes to how Exposed processes query results does affect the type of the lambda block's argument.

Prior to version 1.0.0, the `java.sql.ResultSet` retrieved from the database was passed directly as the argument.
In version 1.0.0, this result is wrapped by a common `ResultApi` object and requires casting to a `JdbcResult`
if you want to process the underlying `ResultSet` directly using the original `exec()`:

<compare first-title="0.61.0" second-title="1.0.0">

```kotlin
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

transaction {
    val query = TablA
        .select(TableA.amount)
        .where { TableA.amount greater 100 }

    val result = exec(query) {
        val amounts = mutableListOf<Int>()
        while (it.next()) {
            amounts += it.getInt("amount") % 10
        }
        amounts
    }
}
```

```kotlin
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.statements.jdbc.JdbcResult
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

transaction {
    val query = TablA
        .select(TableA.amount)
        .where { TableA.amount greater 100 }

    val result = exec(query) {
        val rs = (it as JdbcResult).result
        val amounts = mutableListOf<Int>()
        while (rs.next()) {
            amounts += rs.getInt("amount") % 10
        }
        amounts
    }
}
```

</compare>

You can avoid this casting and continue to use the original pre-version 1.0.0 code in your lambda by replacing `exec()` with
`execQuery()`.
The latter automatically performs the necessary casting and unwrapping under-the-hood, so you can continue to use `ResultSet` directly as before:

```kotlin
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.statements.jdbc.JdbcResult
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

transaction {
    val query = TablA
        .select(TableA.amount)
        .where { TableA.amount greater 100 }

    val result = execQuery(query) {
        val amounts = mutableListOf<Int>()
        while (it.next()) {
            amounts += it.getInt("amount") % 10
        }
        amounts
    }
}
```

<note>
It is also recommended to use <code>execQuery()</code> if the statement originally passed to <code>exec()</code>
was obtained via <code>explain()</code> or any DML functions that return results, for example, <code>.insertReturning()</code>.
</note>

See [result wrappers](#result-wrappers) for more details behind the changes made to how Exposed handles query results.

### `ReturningStatement` return type changed

Prior to version 1.0.0, table extension functions that returned values for specified columns, like `insertReturning()` and 
`updateReturning()`, returned a value of type `ReturningStatement`.
This return type ensured that such statements would be sent to the database only at the moment a terminal operation
attempted to iterate over the results.

In version 1.0.0, since all execution logic has been removed from the core `ReturningStatement`, these functions instead return
a value of type `ReturningBlockingExecutable`. Other than this return type change, the return value can be iterated over in
the same way as before.

### `DeleteStatement` companion methods deprecated

Prior to version 1.0.0, the companion object of `DeleteStatement` provided the `.all()` and `.where()` methods as alternatives
to calling `Table.deleteAll()` or `Table.deleteWhere()`.

Following version 1.0.0's removal of statement execution logic from
`exposed-core`, these companion methods have now been deprecated. It is recommended to use the table extension functions
directly or combine a `DeleteStatement` constructor with `DeleteBlockingExecutable`.

### `SqlExpressionBuilder` lambda blocks {id = sql-expression-builder-lambda}

As mentioned in the [imports section above](#sql-expression-builder-imports), `SqlExpressionBuilder` object has been deprecated.

Prior to version 1.0.0, many higher-order functions used this object as a parameter receiver to allow access to its methods
without needing import statements for every single method used. Since all these object methods have been replaced with
top-level functions, such receivers are now redundant and have been removed.

All expression builder methods passed to these function parameters will now be unresolved unless either a statement such as
`import org.jetbrains.exposed.v1.core.*` is already present, or the prompted import statements are added:

<compare first-title="0.61.0" second-title="1.0.0">

```kotlin
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.longLiteral
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

TableA.update(
    where = { TableA.details like "%S/O" }
) {
    it.update(TableA.amount) { TableA.amount plus 3 }
}

TableA
    .selectAll()
    .where {
        TableA.details eq "N/A" and TableA.warranty.isNull()
    }
    .groupBy(TableA.label)
    .having {
        TableA.label.count() greaterEq longLiteral(1)
    }
```

```kotlin
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.longLiteral
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

TableA.update(
    where = { TableA.details like "%S/O" }
) {
    it.update(TableA.amount) { TableA.amount plus 3 }
}

TableA
    .selectAll()
    .where {
        TableA.details eq "N/A" and TableA.warranty.isNull()
    }
    .groupBy(TableA.label)
    .having {
        TableA.label.count() greaterEq longLiteral(1)
    }
```

</compare>

### `BaseBatchInsertStatement` removed

The abstract class `BaseBatchInsertStatement` has been removed. All its elements have been consolidated into and replaced
by the single open class `BatchInsertStatement`. Any custom extensions of `BaseBatchInsertStatement` can be safely replaced
by extending `BatchInsertStatement` instead.

## Queries

While the `AbstractQuery` class remains in `exposed-core`, its `Query` implementation is now in `exposed-jdbc`, with the
R2DBC variant located in `exposed-r2dbc.` This restructuring was necessary to allow for the required differences in the
underlying implementations of each class, with the JDBC `Query` ultimately implementing `Iterable`, and the R2DBC `Query`
implementing `Flow`.

### `CommentPosition` ownership changed

Certain `Query` properties, like `where` and `having` (as well as their associated adjustment methods), have been moved
down from the subclass into superclass `AbstractQuery` so that they remain common to both drivers.

This also includes the`comments` property and its related enum class `CommentPosition`,
which is now only accessible from `AbstractQuery`:

<compare first-title="0.61.0" second-title="1.0.0">

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

### `Case` property `value` removed

Prior to version 1.0.0, the `Case` class had a `value` property, against which chained conditions could potentially be compared,
as long as the expression stored in the property was a boolean type.

This class has now been split into a general `Case` class with no such property,
and a separate `ValueCase` class that has a `value` property of type `ExpressionWithColumnType`.
These represent expression builder instances to facilitate the following syntax, respectively:
`CASE WHEN <condition> THEN <result> END` and `CASE <value0> WHEN <value1> THEN <result> END`.

Any use of the `case(value)` function to start a value-based CASE expression remains unchanged (except for updated import statements),
as long as the passed argument is of type `ExpressionWithColumnType`.
Any direct use of the `Case(value)` constructor must be replaced with the `ValueCase(value)` constructor
or the `case(value)` function:

<compare first-title="0.61.0" second-title="1.0.0">

```kotlin
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentDate

val statusCase = Case(Books.isOverdue)
    .When(Books.dueDate less CurrentDate, booleanLiteral(true))
    .Else(booleanLiteral(false))
    .alias("status_valid")
```

```kotlin
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.datetime.CurrentDate

val statusCase = ValueCase(Books.isOverdue)
    .When(Books.dueDate less CurrentDate, booleanLiteral(true))
    .Else(booleanLiteral(false))
    .alias("status_valid")
```

</compare>

## Result wrappers

Two new `exposed-core` interfaces, `ResultApi` and `RowApi`, have been introduced to represent commonalities between the
results of query execution with JDBC (`java.sql.ResultSet`) and with R2DBC (`io.r2dbc.spi.Result`).
These are both implemented by new driver-specific wrapper classes, `JdbcResult` and `R2dbcResult`.

### `readObject()` parameter type changed {id = read-object}

Since driver-specific results, like `java.sql.ResultSet`, are no longer supported in `exposed-core`, they are instead wrapped
by common interfaces, like `RowApi`.

The `IColumnType` interface has a method `readObject()` for performing any special read or conversion logic when
accessing an object at a specific index in the result.
The signature of this method has changed to use `RowApi` instead of `ResultSet`, which still allows access to either the
underlying JDBC `ResultSet` or R2DBC `Row` via `getObject()`:

<compare first-title="0.61.0" second-title="1.0.0">

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

Instead of switching to `getObject()` as above, the original code called on a `ResultSet` could still be used by accessing
the underlying wrapped result via `RowApi.origin`:

<compare first-title="0.61.0" second-title="1.0.0">

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
        return rs.origin
            .getString(index)
            .take(MAX_CHARS)
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

Calling `execute()` directly on a `Query` no longer returns a `ResultSet`. It instead returns a `ResultApi`,
which must be cast if you need to access the original wrapped result type:

<compare first-title="0.61.0" second-title="1.0.0">

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

## Column types

### UUID column type classes refactored {id = uuid-column-type-refactor}

Version 1.0.0 comes with support for storing `kotlin.uuid.Uuid` values in binary columns via the new `UuidColumnType` class.
Built-in support for DAO API elements are also available by using `UuidTable` with `UuidEntity` and `UuidEntityClass`. The
existing `Table.uuid()` method will now only accept values of the type `kotlin.uuid.Uuid`.

In order to avoid name clashes and unresolved errors, the original classes for storing `java.util.UUID` values have all been
renamed to include the prefix "Java". To continue passing `java.util.UUID` values, your Exposed objects should be renamed
and your registered columns should be switched to `javaUUID()`:

| 0.61.0                                      | 1.0.0                                                |
|---------------------------------------------|------------------------------------------------------|
| `org.jetbrains.exposed.sql.UUIDColumnType`  | `org.jetbrains.exposed.v1.core.JavaUUIDColumnType`   |
| `org.jetbrains.exposed.dao.id.UUIDTable`    | `org.jetbrains.exposed.v1.core.dao.id.JavaUUIDTable` |
| `org.jetbrains.exposed.dao.UUIDEntity`      | `org.jetbrains.exposed.v1.dao.JavaUUIDEntity`        |
| `org.jetbrains.exposed.dao.UUIDEntityClass` | `org.jetbrains.exposed.v1.dao.JavaUUIDEntityClass`   |

While `UuidColumnType` and `JavaUUIDColumnType` accept different UUID types on the client-side, they both map to the same
data type on the database-side. This means that keeping the use of `Table.uuid()` (or switching to the new `UuidTable`)
will not trigger the generation of any migration DDL statements.

Assuming the following table and entity objects with version 0.61.0:

```kotlin
import org.jetbrains.exposed.dao.id.*
import org.jetbrains.exposed.dao.*
import java.util.UUID

object TestTable : UUIDTable("tester") {
    val secondaryId = uuid("secondary_id").uniqueIndex()
}

class TestEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<TestEntity>(TestTable)

    var secondaryId by TestTable.secondaryId
}
```

The following changes should be made depending on which client-side type is required:

<compare first-title="kotlin.uuid.Uuid" second-title="java.util.UUID">

```kotlin
import org.jetbrains.exposed.v1.core.dao.id.*
import org.jetbrains.exposed.v1.dao.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object TestTable : UuidTable("tester") {
    @OptIn(ExperimentalUuidApi::class)
    val secondaryId = uuid("secondary_id").uniqueIndex()
}

@OptIn(ExperimentalUuidApi::class)
class TestEntity(id: EntityID<Uuid>) : UuidEntity(id) {
    companion object : UuidEntityClass<TestEntity>(TestTable)

    var secondaryId by TestTable.secondaryId
}
```

```kotlin
import org.jetbrains.exposed.v1.core.dao.id.*
import org.jetbrains.exposed.v1.dao.*
import java.util.UUID

object TestTable : JavaUUIDTable("tester") {
    val secondaryId = javaUUID("secondary_id").uniqueIndex()
}

class TestEntity(id: EntityID<UUID>) : JavaUUIDEntity(id) {
    companion object : JavaUUIDEntityClass<TestEntity>(TestTable)

    var secondaryId by TestTable.secondaryId
}
```

</compare>

### H2 DATETIME data type

Starting from [H2 version 2.4.240](https://github.com/h2database/h2database/releases/tag/version-2.4.240), `DATETIME(9)` data type is
[no longer accepted](https://github.com/h2database/h2database/issues/4285) unless using certain compatibility modes. This was
the data type that `datetime()` columns were mapped to for the following H2 modes: Regular, MySQL, and MariaDB.

Moving forward, `datetime()` columns for these modes will instead map to `TIMESTAMP(9)` for any `Database` instance using version 2.4.240 or higher.
Older versions of H2 retain the original data type mapping.

### `exposed-kotlin-datetime` `timestamp()` mapped type

Prior to version 1.0.0, `KotlinInstantColumnType` and `Table.timestamp()` (from `exposed-kotlin-datetime`)
were mapped to accept `kotlinx.datetime.Instant` values. These now only accept `kotlin.time.Instant` values.
This also applies to `CurrentTimestamp`, `CustomTimeStampFunction`, and any provided functions that had the old
`kotlinx.datetime.Instant` as a type parameter.

If `kotlinx.datetime.Instant` is still a requirement, all usages must be replaced with their deprecated variants, prefixed
with 'X'. For example, `XKotlinInstantColumnType`, `Table.xTimestamp()`, `XCurrentTimestamp`, and `XCustomTimeStampFunction`.
Function overloads that use `kotlinx.datetime.Instant` as a type parameter are still available, but deprecated.

### Datetime column type classes refactored {id = datetime-column-type-refactor}

The datetime artifacts have been refactored to extend new  abstract column type classes from `exposed-core`. All original column
type classes in `exposed-java-time` and `exposed-kotlin-datetime` remain unchanged, except that they now extend these base
core classes.

#### Updated classes in `exposed-jodatime`

In the `exposed-jodatime` artifact, most of the original classes have been split and/or renamed to reflect the new superclasses
being extended. This primarily affects direct usage of these classes, such as in custom functions.
The table below shows the class changes:

| 0.61.0                                                               | 1.0.0                                                               |
|----------------------------------------------------------------------|---------------------------------------------------------------------|
| `org.jetbrains.exposed.sql.jodatime.DateColumnType(time=false)`      | `org.jetbrains.exposed.v1.jodatime.JodaLocalDateColumnType`         |
| `org.jetbrains.exposed.sql.jodatime.DateColumnType(time=true)`       | `org.jetbrains.exposed.v1.jodatime.JodaLocalDateTimeColumnType`     |
| `org.jetbrains.exposed.sql.jodatime.LocalTimeColumnType`             | `org.jetbrains.exposed.v1.jodatime.JodaLocalTimeColumnType`         |
| `org.jetbrains.exposed.sql.jodatime.DateTimeWithTimeZoneColumnType ` | `org.jetbrains.exposed.v1.jodatime.DateTimeWithTimeZoneColumnType ` |

## `DatabaseDialect` and `VendorDialect`

Any method from the `exposed-core` interface `DatabaseDialect` that required driver-specific metadata queries has been extracted.
If moved, the method is now accessible from the new abstract class `DatabaseDialectMetadata` found in `exposed-jdbc`.
This also applies to such methods and properties from the core `VendorDialect` implementations.
The following shows some examples of the ownership changes:

| 0.61.0                                              | 1.0.0                                       |
|-----------------------------------------------------|---------------------------------------------|
| `DatabaseDialect.allTablesNames()`                  | `DatabaseDialectMetadata.allTablesNames()`  |
| `DatabaseDialect.tableColumns()`                    | `DatabaseDialectMetadata.tableColumns()`    |
| `DatabaseDialect.catalog()`                         | `DatabaseDialectMetadata.catalog()`         |
| `DatabaseDialect.existingIndices()`                 | `DatabaseDialectMetadata.existingIndices()` |

<note>
Corresponding ownership changes for the underlying metadata query functions originally called by the above <code>DatabaseDialect</code> methods
have also been introduced. This means that the abstract class <code>ExposedDatabaseMetadata</code> in <code>exposed-core</code> additionally has
had some of its methods extracted to the driver-specific implementations in <code>exposed-jdbc</code> and <code>exposed-r2dbc</code>.
</note>

These methods would have previously been most commonly invoked on the top-level property `currentDialect`.
To follow a similar pattern, a related property, `currentDialectMetadata`, has been added to replace the original call:

<compare first-title="0.61.0" second-title="1.0.0">

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

### H2 version 1.x.x

Support for H2 versions earlier than 2.0.202 (namely 1.4.200 and earlier) has now been fully phased out. In addition,
`H2Dialect.H2MajorVersion.One` is now deprecated and `H2Dialect`-specific properties, like `majorVersion` and `isSecondVersion`,
now throw an exception if H2 version 1.x.x is detected.

Moving forward, new features will no longer be tested on H2 version 1.x.x, so support for those versions will not be guaranteed.
Depending on the built-in support from these older H2 versions, Exposed API may still mostly be compatible,
but may now throw syntax or unsupported exceptions when generating certain SQL clauses.

### Custom dialects

In the same way as how there are database-specific implementations for further extensibility in `exposed-core`, like `H2Dialect`,
the new class also comes with open implementations for metadata extensions, like `H2DialectMetadata`. These new classes should
be extended to hold any custom overrides for the metadata methods that have been moved.

Any custom database dialect implementation can be registered as before via `Database.registerDialect()`. With version 1.0.0,
an additional call to `Database.registerDialectMetadata()` should be used to ensure that the newly associated metadata implementation
is also registered.

### `areEquivalentColumnTypes()` deprecated

`DatabaseDialect.areEquivalentColumnTypes()` has been deprecated in favor of a driver-agnostic variant `areEquivalentColumnTypes()`
found under `org.jetbrains.exposed.v1.core.statements.api.ExposedDatabaseMetadata`.
Driver-specific variants have been introduced both in `exposed-jdbc` and `exposed-r2dbc` and can be accessed directly via
calls to `currentDialectMetadata.areEquivalentColumnTypes()`.

### `resolveRefOptionFromJdbc()` removed

Given the original intention behind this method, `DatabaseDialect.resolveRefOptionFromJdbc()` has been removed and replaced
by a driver-agnostic variant `resolveReferenceOption()` found under `org.jetbrains.exposed.v1.core.statements.api.ExposedDatabaseMetadata`.
Driver-specific variants have been introduced both in `exposed-jdbc` and `exposed-r2dbc`.

### Property `supportsSelectForUpdate` deprecated

`DatabaseDialect.supportsSelectForUpdate` has been deprecated in favor of driver-specific `DatabaseApi` properties implemented
in `exposed-jdbc` and `exposed-r2dbc`. Once a database has been registered for connections, this property can instead
be accessed directly from it, for example, by using a call to `TransactionManager.current().db.supportsSelectForUpdate`.

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

| 0.61.0                                       | 1.0.0                                      |
|----------------------------------------------|--------------------------------------------|
| `PreparedStatementApi.executeQuery()`        | `JdbcPreparedStatementApi.executeQuery()`  |
| `PreparedStatementApi.executeUpdate()`       | `JdbcPreparedStatementApi.executeUpdate()` |
| `PreparedStatementApi.addBatch()`            | `JdbcPreparedStatementApi.addBatch()`      |
| `PreparedStatementApi.cancel()`              | `JdbcPreparedStatementApi.cancel()`        |

#### `set()` deprecated

The original `operator fun set(index: Int, value: Any)` has been deprecated. It should be replaced by a new variant
that accepts a third argument for the column type associated with the value being bound to the statement.
This new `set()` method will require an override if the interface is implemented directly.

#### `setArray()` deprecated

The original `setArray(index: Int, type: String, array: Array<*>)` has been deprecated. It should be replaced by a new variant
that accepts the actual `ArrayColumnType` associated with the array value being bound to the statement as the second argument,
instead of a string representation of the type. This new `setArray()` method will require an override if the interface is
implemented directly.

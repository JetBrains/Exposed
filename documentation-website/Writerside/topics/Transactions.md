<show-structure for="chapter,procedure" depth="2"/>

# Working with Transactions

CRUD operations in Exposed must be called from within a _transaction._ Transactions encapsulate a set of DSL operations.

## Create a transaction

To create and execute a transaction with default parameters, simply pass a function block to the
[`transaction()`](https://jetbrains.github.io/Exposed/api/exposed-jdbc/org.jetbrains.exposed.v1.jdbc.transactions/transaction.html)
function:

```kotlin
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

transaction {
    // DSL/DAO operations go here
}
```

Transactions are executed synchronously on the current thread. This means they might block other parts of your 
application if not managed carefully.

If you need to execute a transaction asynchronously or within a coroutine, use a 
[suspend-based transaction](#suspend-based-transaction) instead.

## Suspend-based transaction

Use [`suspendTansaction()`](https://jetbrains.github.io/Exposed/api/exposed-r2dbc/org.jetbrains.exposed.v1.r2dbc.transactions/suspend-transaction.html)
from `exposed-r2dbc` to perform non-blocking operations in coroutine-based applications:

```kotlin
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

suspendTransaction {
    // DSL/DAO operations go here
}
```

For compatibility with JDBC drivers, a [`suspendTansaction()`](https://jetbrains.github.io/Exposed/api/exposed-jdbc/org.jetbrains.exposed.v1.jdbc.transactions/suspend-transaction.html)
is also available to call suspend functions alongside blocking database operations.

The behavior of both these functions match that of `transaction()`, but with their `statement` parameter accepting suspend functions.
To pass additional context to either `suspendTansaction()`, wrap it in a coroutine builder function, like
[`withContext()`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/with-context.html)
or [`async()`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/async.html).


## Accessing returned values

Although you can modify variables from your code within the transaction block, it also supports returning a value
directly, enabling immutability.

In the following example, `jamesList` is a `List<ResultRow>` containing `UsersTable` data:

<tabs group="connectivity">
    <tab id="jdbc-connect" title="JDBC" group-key="jdbc">
        <code-block lang="kotlin"
                    src="exposed-databases-jdbc/src/main/kotlin/org/example/App.kt"
                    include-symbol="jamesList" />
    </tab>
    <tab id="r2dbc-connect" title="R2DBC" group-key="r2dbc">
        <code-block lang="kotlin"
                    src="exposed-databases-r2dbc/src/main/kotlin/org/example/App.kt"
                    include-symbol="jamesList" />
    </tab>
</tabs>



>`Blob` and `text` fields won't be available outside a transaction if you don't load them directly. For `text`
>fields you can also use the `eagerLoading` param when defining the Table to make the text fields available outside the
>transaction.
{style="note"}

```kotlin
// without eagerLoading
val idsAndContent = transaction {
   Documents.selectAll().limit(10).map { it[Documents.id] to it[Documents.content] }
}

// with eagerLoading for text fields
object Documents : Table() {
  //...
  val content = text("content", eagerLoading = true)
}

val documentsWithContent = transaction {
   Documents.selectAll().limit(10)
}
```

## Working with multiple databases


If you want to work with different databases, you have to store the database reference returned by the `.connect()` 
function and provide it to the transaction function as the first parameter. 

The transaction block without parameters will work with the latest connected database.

```kotlin
val db1 = connect("jdbc:h2:mem:db1;DB_CLOSE_DELAY=-1;", "org.h2.Driver", "root", "")
val db2 = connect("jdbc:h2:mem:db2;DB_CLOSE_DELAY=-1;", "org.h2.Driver", "root", "")
transaction(db1) {
   //...
   val result = transaction(db2) {
      Table1.selectAll().where { }.map { it[Table1.name] }
   }
   
   val count = Table2.selectAll().where { Table2.name inList result }.count()
}
```

Entities `stick` to a transaction that was used to load that entity. That means that all
changes persist to the same database and what cross-database references are prohibited and will throw exceptions.

## Setting a default database

To set the default database explicitly, use the `TransactionManager.defaultDatabase` property:

```kotlin
val db = Database.connect()
TransactionManager.defaultDatabase = db
```

Retrieving this `defaultDatabase` property will return the set value, or `null` if no value was provided.

A transaction block without parameters uses the default database or the latest _connected_ database.
To retrieve and check the `Database` instance that would be used by a transaction block in this case, get the
`TransactionManager.primaryDatabase` property.

## Using nested transactions

By default, a nested transaction block shares the transaction resources of its parent transaction block. This
means that any changes within the nested transaction affect the outer transaction. If a rollback occurs inside the 
nested block, it will roll back changes in the parent transaction as well:

```kotlin
val db = Database.connect()
db.useNestedTransactions = false // Default setting

transaction {
    println("Transaction # ${this.id}") // Transaction # 1
    FooTable.insert{ it[id] = 1 }
    println(FooTable.selectAll().count()) // 1
    
    transaction {
        println("Transaction # ${this.id}") // Transaction # 1
        FooTable.insert{ it[id] = 2 }
        println(FooTable.selectAll().count()) // 2
    
        rollback() 
    }

    println(FooTable.selectAll().count()) // 0
}
```

### Independent nested transactions

To allow nested transactions to function independently, set the `useNestedTransactions` property on the `Database`
instance to `true`:

```kotlin
val db = Database.connect(
    // connection parameters
)
db.useNestedTransactions = true

transaction {
    println("Transaction # ${this.id}") // Transaction # 1
    FooTable.insert{ it[id] = 1 }
    println(FooTable.selectAll().count()) // 1
    
    transaction {
        println("Transaction # ${this.id}") // Transaction # 2
        FooTable.insert{ it[id] = 2 }
        println(FooTable.selectAll().count()) // 2
    
        rollback() 
    }

    println(FooTable.selectAll().count()) // 1
}
```
With this, any rollback or exception inside a nested transaction affects only that block, without rolling back the
outer transaction.

Exposed achieves this by using SQL `SAVEPOINT` to mark transaction states at the beginning of each transaction
block, releasing them on exit.

> Using `SAVEPOINT` may affect performance. For more details, refer to your database documentation.

<note>
<code>suspendTransaction()</code> from <code>exposed-jdbc</code> uses the same nesting behavior logic as <code>transaction()</code>
detailed above in this section.
</note>

## Using savepoints

To roll back to a specific point without affecting the entire transaction, you can set a savepoint through the
transaction's `connection` property.

The `connection` property provides access to an
[`ExposedConnection`](https://jetbrains.github.io/Exposed/api/exposed-jdbc/org.jetbrains.exposed.v1.jdbc.statements.api/-exposed-connection/index.html)
or an [`R2dbcExposedConnection`](https://jetbrains.github.io/Exposed/api/exposed-r2dbc/org.jetbrains.exposed.v1.r2dbc.statements.api/-r2dbc-exposed-connection/index.html),
which acts as a wrapper around the underlying JDBC or R2DBC connection respectively.

To manually create a savepoint within a transaction, use the `.setSavepoint()` method:

```Kotlin
```
{src="exposed-transactions/src/main/kotlin/org/example/examples/SavepointExample.kt" include-lines="39,41-50"}


## Advanced parameters and usage

For specific functionality, transactions can be created with the additional
parameters: `db`, `transactionIsolation`, `readOnly`, `maxAttempts`, and `queryTimeout`:

<tabs group="connectivity">
    <tab id="transaction" title="JDBC" group-key="jdbc">
        <code-block lang="kotlin">
        transaction(
            db = h2Db,
            transactionIsolation = Connection.TRANSACTION_READ_COMMITTED,
            readOnly = true,
        ) {
            maxAttempts = 5
            queryTimeout = 5
            // DSL/DAO operations go here
        }
        </code-block>
    </tab>
    <tab id="suspend-transaction" title="R2DBC" group-key="r2dbc">
        <code-block lang="kotlin">
        suspendTransaction(
            db = h2Db,
            transactionIsolation = IsolationLevel.READ_COMMITTED,
            readOnly = true,
        ) {
            maxAttempts = 5
            queryTimeout = 5
            // DSL/DAO operations go here
        }
        </code-block>
    </tab>
</tabs>

### `db`

The `db` parameter is optional and is used to select the database where the transaction should be
settled. This is useful when [working with multiple databases](#working-with-multiple-databases).

### `transactionIsolation`

The `transactionIsolation` parameter specifies what is supposed to happen when
multiple transactions execute concurrently on the database. This value is sent to the database where it is taken 
into account. By default, it is set to use the value provided to the database's transaction manager configuration.

The allowed values for JDBC connections are defined in `java.sql.Connection` and
for R2DBC connections in `io.r2dbc.spi.IsolationLevel`.

<tabs group="connectivity">
<tab id="jdbc-transaction-isolation" title="JDBC" group-key="jdbc">

`TRANSACTION_NONE`
: Transactions are not supported.

`TRANSACTION_READ_UNCOMMITTED`
: Allows uncommitted changes from one transaction to affect
a read in another transaction (a "dirty read").

`TRANSACTION_READ_COMMITTED` (default, except for MySql and SQLite)
: This setting prevents dirty reads from occurring, but still allows non-repeatable
reads to occur. A _non-repeatable read_ is when a transaction ("Transaction A") reads a row from the database, another
transaction ("Transaction B") changes the row, and Transaction A reads the row again, resulting in an inconsistency.

`TRANSACTION_REPEATABLE_READ` (default for MySql)
: Prevents both dirty and non-repeatable
reads, but still allows for phantom reads. A _phantom read_ is when a transaction ("Transaction A") selects a list of
rows through a `WHERE` clause, another transaction ("Transaction B") performs an `INSERT` or `DELETE` with a row that
satisfies Transaction A's `WHERE` clause, and Transaction A selects using the same WHERE clause again, resulting in an
inconsistency.

`TRANSACTION_SERIALIZABLE` (default for SQLite)
: Prevents dirty reads, non-repeatable reads, and phantom reads.

{type="wide"}

</tab>
<tab id="r2dbc-transaction-isolation" title="R2DBC" group-key="r2dbc">

`READ_UNCOMMITTED`
: Allows uncommitted changes from one transaction to affect
a read in another transaction (a "dirty read").

`READ_COMMITTED` (default, except for MySql)
: This setting prevents dirty reads from occurring, but still allows non-repeatable
reads to occur. A _non-repeatable read_ is when a transaction ("Transaction A") reads a row from the database, another
transaction ("Transaction B") changes the row, and Transaction A reads the row again, resulting in an inconsistency.

`REPEATABLE_READ` (default for MySql)
: Prevents both dirty and non-repeatable
reads, but still allows for phantom reads. A _phantom read_ is when a transaction ("Transaction A") selects a list of
rows through a `WHERE` clause, another transaction ("Transaction B") performs an `INSERT` or `DELETE` with a row that
satisfies Transaction A's `WHERE` clause, and Transaction A selects using the same WHERE clause again, resulting in an
inconsistency.

`SERIALIZABLE`
: Prevents dirty reads, non-repeatable reads, and phantom reads.

{type="wide"}

</tab>
</tabs>

### `readOnly`

The `readOnly` parameter indicates whether any database connection used by the transaction is in read-only mode. By default,
it is set to use the value provided to the database's transaction manager configuration. This value is not directly used by Exposed,
but is relayed to the database.

### `maxAttempts`

Use the `maxAttempts` property to set the maximum number of attempts to perform a transaction block.

If this value is set to `1` and an `SQLException` occurs inside the transaction block, the exception will be thrown
without performing a retry.

If it is set to a value greater than 1, `minRetryDelay` and `maxRetryDelay` can also be set in the
transaction block to indicate the minimum and maximum number of milliseconds to wait before retrying.

If not set, any default value provided in
[`DatabaseConfig`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.core/-database-config/index.html)
will be used instead:

```kotlin
val db = Database.connect(
    datasource = datasource,
    databaseConfig = DatabaseConfig {
        defaultMaxAttempts = 3
    }
)

// property set in transaction block overrides default DatabaseConfig
transaction(db = db) {
    maxAttempts = 25
    // operation that may need multiple attempts
}
```

### `queryTimeout`

Use `queryTimeout` to set the number of seconds to wait
for each statement in the block to execute before timing out:

```kotlin
transaction {
    queryTimeout = 3
    try {
        // operation that may run for more than 3 seconds
    } catch (cause: ExposedSQLException) {
        // logic to perform if execution timed out
    }
}
```

This value is not directly managed by Exposed, but is relayed to the JDBC or R2DBC driver.

>Some drivers may not support implementing this limit. For more information, refer to the relevant driver documentation.
{style="note"}

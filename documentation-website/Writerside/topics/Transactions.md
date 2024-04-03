# Transactions

## Overview

CRUD operations in Exposed must be called from within a _transaction._ Transactions encapsulate a set of DSL operations.
To create and execute a transaction with default parameters, simply pass a function block to the `transaction` function:

```kotlin
transaction {
    // DSL/DAO operations go here
}
```

Transactions are executed synchronously on the current thread, so they _will block_ other parts of your application! If
you need to execute a transaction asynchronously, consider running it on a separate `Thread`.

### Accessing returned values

Although you can modify variables from your code within the transaction block, `transaction` supports returning a value
directly, enabling immutability:

```kotlin
val jamesList = transaction {
    Users.selectAll().where { Users.firstName eq "James" }.toList()
}
// jamesList is now a List<ResultRow> containing Users data
```

*Note:* `Blob` and `text` fields won't be available outside a transaction if you don't load them directly. For `text`
fields you can also use the `eagerLoading` param when defining the Table to make the text fields available outside the
transaction.

```kotlin
// without eagerLoading
val idsAndContent = transaction {
   Documents.selectAll().limit(10).map { it[Documents.id] to it[Documents.content] }
}

// with eagerLoading for text fields
object Documents : Table() {
  ...
  val content = text("content", eagerLoading = true)
}

val documentsWithContent = transaction {
   Documents.selectAll().limit(10)
}
```

### Working with multiple databases

_This functionality supported since 0.10.1 version_

When you want to work with different databases then you have to store database reference returned
by `Database.connect()` and provide it to `transaction` function as first parameter.

```kotlin
val db1 = connect("jdbc:h2:mem:db1;DB_CLOSE_DELAY=-1;", "org.h2.Driver", "root", "")
val db2 = connect("jdbc:h2:mem:db2;DB_CLOSE_DELAY=-1;", "org.h2.Driver", "root", "")
transaction(db1) {
   ...
   val result = transaction(db2) {
      Table1.selectAll().where { }.map { it[Table1.name] }
   }
   
   val count = Table2.selectAll().where { Table2.name inList result }.count()
}
```

Entities (see [DAO API](Deep-Dive-into-DAO.md) page) `stick` to a transaction that was used to load that entity. That means that all
changes persist to the same database and what cross-database references are prohibited and will throw exceptions.

### Setting default database

`transaction` block without parameters will use the default database.
As before 0.10.1 this will be the latest _connected_ database.
It is also possible to set the default database explicitly.

```kotlin
val db = Database.connect()
TransactionManager.defaultDatabase = db
```

### Using nested transactions

By default, a nested `transaction` block shares the transaction resources of its parent `transaction` block, so any
effect on the child affects the parent:

```kotlin
val db = Database.connect()
db.useNestedTransactions = false // set by default

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

Since Exposed 0.16.1 it is possible to use nested transactions as separate transactions by
setting `useNestedTransactions = true` on the desired `Database` instance.

After that any exception or rollback operation that happens within a `transaction` block will not roll back the whole
transaction but only the code inside the current `transaction`.
Exposed uses SQL `SAVEPOINT` functionality to mark the current transaction at the beginning of a `transaction` block and
release it on exit.

Using savepoint could affect performance, so please read the documentation on your DBMS for more details.

```kotlin
val db = Database.connect()
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

### Working with Coroutines

In the modern world, non-blocking and asynchronous code is popular.
Kotlin has [Coroutines](https://kotlinlang.org/docs/reference/coroutines-overview.html), which provide an imperative way to
write asynchronous code.
Most Kotlin frameworks (like [Ktor](https://ktor.io/docs)) have built-in support for
coroutines, while Exposed is mostly blocking.

Why?

Because Exposed interacts with databases using JDBC API, which was designed in an era of blocking APIs. Additionally,
Exposed stores some values in thread-local variables while coroutines could (and will) be executed in different threads.

Starting from Exposed 0.15.1,
bridge functions are available that give you a safe way to interact with Exposed within `suspend`
blocks: `newSuspendedTransaction()` and `Transaction.withSuspendTransaction()`.
These have the same parameters as a
blocking `transaction()` but allow you to provide a `CoroutineContext` argument that explicitly specifies
the `CoroutineDispatcher` in which the function will be executed.
If context is not provided your code will be executed
within the current `CoroutineContext`.

Here is an example that uses these three types of transactions:

```kotlin
transaction {
    println("Transaction # ${this.id}") // Transaction # 1
    SchemaUtils.create(FooTable) // Table will be created on a current thread

    runBlocking {
        newSuspendedTransaction(Dispatchers.Default) {
            println("Transaction # ${this.id}") // Transaction # 2
            FooTable.insert { it[id] = 1 }  // This insert will be executed in one of the Default dispatcher threads

            withSuspendTransaction {
                println("Transaction # ${this.id}") // Transaction # 2
                // This select also will be executed on some thread from Default dispatcher using the same transaction as its parent
                FooTable.selectAll().where { FooTable.id eq 1 }.single()[FooTable.id]
            }
        }
    }

    transaction {
        println("Transaction # ${this.id}") // Transaction # 1
    }

    runBlocking {
        val result = newSuspendedTransaction(Dispatchers.IO) {
            println("Transaction # ${this.id}") // Transaction # 3
            FooTable.selectAll().where { FooTable.id eq 1 }.single()[FooTable.id] // This select will be executed on some thread from IO dispatcher
        }
        println("Result: $result") // Result: 1
    }

    SchemaUtils.drop(Testing)
}
```  

Please note that such code remains blocking (as it still uses JDBC) and you should not try to share a transaction
between multiple threads as it may lead to undefined behavior.

If you desire to execute some code asynchronously and use the result later, take a look
at `suspendedTransactionAsync()`:

```kotlin
runBlocking {
    val launchResult = suspendedTransactionAsync(Dispatchers.IO) {
        FooTable.insert { it[id] = 2 }

        FooTable.selectAll().where { FooTable.id eq 2 }.singleOrNull()?.getOrNull(FooTable.id)
    }
    
    println("Async result: " + (launchResult.await() ?: -1)) // Async result: 2
}
```

This function will accept the same parameters as `newSuspendedTransaction()` above but returns its future result as an
implementation of `Deferred`, which you can `await` on to achieve your result.

**Note: ** `newSuspendedTransaction` and `suspendedTransactionAsync` are always executed in a new transaction to prevent
concurrency issues when query execution order could be changed by the `CoroutineDispatcher`. This means that nesting
these suspend transactions may not result in the same behavior as nested `transaction`s (when `useNestedTransactions = false`), as was shown in [the previous section](#using-nested-transactions).

### Advanced parameters and usage

For specific functionality, transactions can be created with the additional
parameters: `transactionIsolation`, `readOnly`, and `db`:

```kotlin
transaction (Connection.TRANSACTION_SERIALIZABLE, true, db = db) {
    // DSL/DAO operations go here
}
```

**Transaction Isolation: ** This parameter, defined in the SQL standard, specifies what is supposed to happen when
multiple transactions execute concurrently on the database. This value does NOT affect Exposed operation directly, but
is sent to the database, where it is expected to be obeyed. Allowable values are defined in `java.sql.Connection` and
are as follows:

* **TRANSACTION_NONE**: Transactions are not supported.
* **TRANSACTION_READ_UNCOMMITTED**: The most lenient setting. Allows uncommitted changes from one transaction to affect
  a read in another transaction (a "dirty read").
* **TRANSACTION_READ_COMMITTED**: This setting prevents dirty reads from occurring, but still allows non-repeatable
  reads to occur. A _non-repeatable read_ is when a transaction ("Transaction A") reads a row from the database, another
  transaction ("Transaction B") changes the row, and Transaction A reads the row again, resulting in an inconsistency.
* **TRANSACTION_REPEATABLE_READ**: The default setting for Exposed transactions. Prevents both dirty and non-repeatable
  reads, but still allows for phantom reads. A _phantom read_ is when a transaction ("Transaction A") selects a list of
  rows through a `WHERE` clause, another transaction ("Transaction B") performs an `INSERT` or `DELETE` with a row that
  satisfies Transaction A's `WHERE` clause, and Transaction A selects using the same WHERE clause again, resulting in an
  inconsistency.
* **TRANSACTION_SERIALIZABLE**: The strictest setting. Prevents dirty reads, non-repeatable reads, and phantom reads.

**readOnly: ** This parameter indicates whether any database connection used by the transaction is in read-only mode, and
is set to `false` by default. Much like with `transactionIsolation`, this value is not directly used by Exposed, but is
simply relayed to the database.

**db:** This parameter is optional and is used to select the database where the transaction should be
settled ([see the section above](#working-with-multiple-databases)).

**Transaction Repetition Attempts**

Transactions also provide a property, `repetitionAttempts`, which sets the number of retries that should be made if an
SQLException occurs inside the `transaction` block. If this property is not set, any default value provided
in `DatabaseConfig` will be used instead:

```kotlin
val db = Database.connect(
    datasource = datasource,
    databaseConfig = DatabaseConfig {
        defaultRepetitionAttempts = 3
    }
)

// property set in transaction block overrides default DatabaseConfig
transaction(db = db) {
    repetitionAttempts = 25
    // operation that may need multiple attempts
}
```

If this property is set to a value greater than 0, `minRepetitionDelay` and `maxRepetitionDelay` can also be set in the
transaction block to indicate the minimum and maximum number of milliseconds to wait before retrying.

**Transaction Query Timeout**

Another advanced property available in a transaction block is `queryTimeout`. This sets the number of seconds to wait
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

**Note** As is the case for `transactionIsolation` and `readOnly` properties, this value is not directly managed by
Exposed, but is simply relayed to the JDBC driver. Some drivers may not support implementing this limit.

### Statement Interceptors

DSL operations within a transaction create SQL statements, on which commands like *Execute*, *Commit*, and *Rollback*
are issued. Exposed provides
the `StatementInterceptor` [interface](https://github.com/JetBrains/Exposed/blob/main/exposed-core/src/main/kotlin/org/jetbrains/exposed/sql/statements/StatementInterceptor.kt)
that allows you to implement your own logic before and after these specific steps in a statement's lifecycle.

`registerInterceptor()` and `unregisterInterceptor()` can be used to enable and disable a custom interceptor in a single
transaction.

To use a custom interceptor that acts on all transactions, extend the `GlobalStatementInterceptor` class instead.
Exposed uses the Java SPI ServiceLoader to discover and load any implementations of this class. In this situation, a new
file should be created in the *resources* folder
named: `META-INF/services/org.jetbrains.exposed.sql.statements.GlobalStatementInterceptor`. The contents of this file
should be the fully qualified class names of all custom implementations.
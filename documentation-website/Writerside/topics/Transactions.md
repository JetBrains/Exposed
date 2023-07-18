# Transactions

## Overview

CRUD operations in Exposed must be called from within a _transaction._ Transactions encapsulate a set of DSL operations. To create and execute a transaction with default parameters, simply pass a function block to the `transaction` function:
```kotlin
transaction {
    // DSL/DAO operations go here
}
```
Transactions are executed synchronously on the current thread, so they _will block_ other parts of your application! If you need to execute a transaction asynchronously, consider running it on a separate `Thread`.

### Accessing returned values

Although you can modify variables from your code within the transaction block, `transaction` supports returning a value directly, enabling immutability:

```kotlin
val jamesList = transaction {
    Users.select { Users.firstName eq "James" }.toList()
}
// jamesList is now a List<ResultRow> containing Users data
```
*Note:* `Blob` and `text` fields won't be available outside of a transaction if you don't load them directly. For `text` fields you can also use the `eagerLoading` param when defining the Table to make the text fields available outside of the transaction.
```kotlin
// without eagerLoading
val idsAndContent = transaction {
   Documents.selectAll().limit(10).map { it[Documents.id] to it[Documents.content] }
}

// with eagerLoading for text fields
object Documents : Table() {
  val content = text("content", eagerLoading = true)
}

val documentsWithContent = transaction {
   Documents.selectAll().limit(10)
}
```

### Working with a multiple databases

When you want to work with different databases then you have to store database reference returned by `Database.connect()` and provide it to `transaction` function as first parameter.
```kotlin
val db1 = connect("jdbc:h2:mem:db1;DB_CLOSE_DELAY=-1;", "org.h2.Driver", "root", "")
val db2 = connect("jdbc:h2:mem:db2;DB_CLOSE_DELAY=-1;", "org.h2.Driver", "root", "")
transaction(db1) {
   val result = transaction(db2) {
      Table1.select{ }.map { it[Table1.name] }
   }
   
   val count = Table2.select { Table2.name inList result }.count()
}
```

Entities (see [DAO](DAO-API.md) page) `stick` to a transaction that was used to load that entity. That means that all changes persist to the same database and what cross-database references are prohibited and will throw exceptions.

### Setting default database
`transaction` block without parameters will use the default database.
As before 0.10.1 this will be the latest _connected_ database.
It is also possible to set the default database explicitly.
```kotlin
val db = Database.connect()
TransactionManager.defaultDatabase = db
```

### Using nested transactions
Since Exposed 0.16.1 it is possible to use nested transactions. To enable this feature you should set `useNestedTransactions` on desire `Database` instance to `true`.

After that any exception that happens within `transaction` block will not rollback the whole transaction but only the code inside current `transaction`.
Exposed uses SQL `SAVEPOINT` functionality to mark current transaction at the begining of `transaction` block and release it on exit from it.

Using savepoint could affect performance, so please read documentation on DBMS you use for more details.

```kotlin
val db = Database.connect()
db.useNestedTransactions = true

transaction {
    FooTable.insert{ it[id] = 1 }
    
    var idToInsert = 0
    transaction { // nested transaction
        idToInsert++
        // On the first insert it will fail with unique constraint exception and will rollback to the `nested transaction` and then insert a new record with id = 2
        FooTable.insert{ it[id] = idToInsert } 
    }
}
```

### Working with Coroutines
In the modern world non-blocking and asynchronous code is popular. Kotlin has [Coroutines](https://kotlinlang.org/docs/reference/coroutines-overview.html) that give you an imperative way of asynchronous code writing. Most of Kotlin frameworks (like [ktor](https://ktor.io)) have built-in support for Coroutines while Exposed is mostly blocking.

Why?

Because Exposed uses JDBC-api to interact with databases that was designed in an era of blocking apis. What's more, Exposed store some values in thread-local variables while Coroutines could (and will) be executed in different threads.

Since Exposed 0.15.1 there are bridge functions that will give you a safe way to interact with Exposed within `suspend` blocks: `newSuspendedTransaction/Transaction.suspendedTransaction` have same parameters as a blocking `transaction` function but will allow you to provide `CoroutineDispatcher` in which function will be executed. If context is not provided your code will be executed within current `coroutineContext`.

Sample usage looks like:
```kotlin
runBlocking {
    transaction {    
        SchemaUtils.create(FooTable) // Table will be created on a current thread
    
        newSuspendedTransaction(Dispatchers.Default) {
            FooTable.insert { it[id] = 1 } // This insert will be executed in one of Default dispatcher threads
    
            suspendedTransaction {
                val id = FooTable.select { FooTable.id eq 1 }.single()()[FooTable.id] // This select also will be executed on some thread from Default dispatcher using the same transaction
            }
        }
    
        val result = newSuspendedTransaction(Dispatchers.IO) {
            FooTable.select { FooTable.id eq 1 }.single()[H2Tests.Testing.id] // This select will be executed on some thread from IO dispatcher using the same transaction
        }
    }
}

```  

Please note what such code remains blocking (as it still uses JDBC) and you should not try to share a transaction between multiple threads as it will lead to undefined behaviour.

If you desire to execute some code asynchronously and use the result later in your code take a look at `suspendedTransactionAsync` function.

```kotlin
val launchResult = suspendedTransactionAsync(Dispatchers.IO, db = db) {
    FooTable.insert{}

    FooTable.select { FooTable.id eq 1 }.singleOrNull()?.getOrNull(Testing.id)
}

println("Result: " + (launchResult.await() ?: -1))

```

This function will accept the same parameters as `newSuspendedTransaction` above but returns `Deferred` which you could `await` on to achieve your result.

`suspendedTransactionAsync` is always executed in new transaction to prevent concurrency issues when queries execution order could be changed by `CoroutineDispatcher`.

### Advanced parameters and usage

For specific functionality, transactions can be created with the additional parameters: `transactionIsolation`, `repetitionAttempts` and `db`:

```kotlin
transaction (Connection.TRANSACTION_SERIALIZABLE, 2) {
    // DSL/DAO operations go here
}
```
**Transaction Isolation:** This parameter, defined in the SQL standard, specifies what is supposed to happen when multiple transactions execute concurrently on the database. This value does NOT affect Exposed operation directly, but is sent to the database, where it is expected to be obeyed. Allowable values are defined in `java.sql.Connection` and are as follows:
* **TRANSACTION_NONE**: Transactions are not supported.
* **TRANSACTION_READ_UNCOMMITTED**: The most lenient setting. Allows uncommitted changes from one transaction to affect a read in another transaction (a "dirty read").
* **TRANSACTION_READ_COMMITTED**: This setting prevents dirty reads from occurring, but still allows non-repeatable reads to occur. A _non-repeatable read_ is when a transaction ("Transaction A") reads a row from the database, another transaction ("Transaction B") changes the row, and Transaction A reads the row again, resulting in an inconsistency.
* **TRANSACTION_REPEATABLE_READ**: The default setting for Exposed transactions. Prevents both dirty and non-repeatable reads, but still allows for phantom reads. A _phantom read_ is when a transaction ("Transaction A") selects a list of rows through a `WHERE` clause, another transaction ("Transaction B") performs an `INSERT` or `DELETE` with a row that satisfies Transaction A's `WHERE` clause, and Transaction A selects using the same WHERE clause again, resulting in an inconsistency.
* **TRANSACTION_SERIALIZABLE**: The strictest setting. Prevents dirty reads, non-repeatable reads, and phantom reads.

**db** parameter is optional and used to select database where transaction should be settled (see section above).

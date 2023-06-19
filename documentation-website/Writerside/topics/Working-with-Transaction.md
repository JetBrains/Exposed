# Working with Transaction

## Overview

CRUD operations in Exposed must be called from within a _transaction._ Transactions encapsulate a set of DSL operations. To create and execute a
transaction with default parameters, simply pass a function block to the `transaction` function:

```kotlin
transaction {
    // DSL/DAO operations go here
}
```

Transactions are executed synchronously on the current thread, so they _will block_ other parts of your application! If you need to execute a
transaction asynchronously, consider running it on a [separate thread](Asynchronous-Support.md).

### Accessing returned values

Although you can modify variables from your code within the `transaction` block, `transaction` supports returning a value directly, enabling
immutability:

```kotlin
val jamesList = transaction {
    Users.select { Users.firstName eq "James" }.toList()
}
// jamesList is now a List<ResultRow> containing Users data
```

<note>
`Blob` and `text` fields won't be available outside of a transaction if you don't load them directly. For `text` fields you can also use
the `eagerLoading` param when defining the Table to make the text fields available outside of the transaction.
</note>

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

### Working with multiple databases

If you want to work with different databases, you have to store the database reference returned by `Database.connect()` and provide it
to `transaction` function as the first parameter. The `transaction` block without parameters will work with the latest connected database.

```kotlin
val db1 = connect("jdbc:h2:mem:db1;DB_CLOSE_DELAY=-1;", "org.h2.Driver", "root", "")
val db2 = connect("jdbc:h2:mem:db2;DB_CLOSE_DELAY=-1;", "org.h2.Driver", "root", "")
transaction(db1) {
    val result = transaction(db2) {
        Table1.select { }.map { it[Table1.name] }
    }

    val count = Table2.select { Table2.name inList result }.count()
}
```

Entities (see [DAO](Deep-Dive-into-DAO.md) page) _stick_ to the transaction that was used to load that entity. That means that all changes persist to the same
database and cross-database references are prohibited and will throw exceptions.

### Setting default database

The `transaction` block without parameters will use the default database.
Before 0.10.1, this will be the latest _connected_ database.
It is also possible to set the default database explicitly.

```kotlin
val db = Database.connect()
TransactionManager.defaultDatabase = db
```

### Using nested transactions

Since Exposed 0.16.1, it is possible to use nested transactions. To enable this feature, you should set `useNestedTransactions` on the desired `Database`
instance to `true`.

After that, any exception that happens within the `transaction` block will not rollback the whole transaction but only the code inside the
current `transaction`.
Exposed uses SQL `SAVEPOINT` functionality to mark the current transaction at the beginning of the `transaction` block and release it on exit from it.

Using `SAVEPOINT` could affect performance, so please read the documentation of the DBMS you use for more details.

```kotlin
val db = Database.connect()
db.useNestedTransactions = true

transaction {
    FooTable.insert { it[id] = 1 }

    var idToInsert = 0
    transaction { // nested transaction
        idToInsert ++
        // On the first insert it will fail with unique constraint exception and will rollback to the `nested transaction` and then insert a new record with id = 2
        FooTable.insert { it[id] = idToInsert }
    }
}
```

### Advanced parameters and usage

For specific functionality, transactions can be created with additional parameters: `transactionIsolation` and `db`.
The following properties can be set inside the `transaction` lambda:

* `repetitionAttempts`: The number of retries that will be made inside this `transaction` block if SQLException happens
* `minRepetitionDelay`: The minimum number of milliseconds to wait before retrying this `transaction` if SQLException happens
* `maxRepetitionDelay`: The maximum number of milliseconds to wait before retrying this `transaction` if SQLException happens

```kotlin
transaction(Connection.TRANSACTION_SERIALIZABLE, db) {
    repetitionAttempts = 2
    // DSL/DAO operations go here
}
```

**Transaction Isolation**: This parameter, defined in the SQL standard, specifies what is supposed to happen when multiple transactions execute
concurrently on the database. This value does NOT affect Exposed operations directly, but is sent to the database, where it is expected to be obeyed.
Allowable values are defined in `java.sql.Connection` and are as follows:

* **TRANSACTION_NONE**: Transactions are not supported.
* **TRANSACTION_READ_UNCOMMITTED**: The most lenient setting. Allows uncommitted changes from one transaction to affect a read in another
  transaction (a "dirty read").
* **TRANSACTION_READ_COMMITTED**: This setting prevents dirty reads from occurring, but still allows non-repeatable reads to occur. A _non-repeatable
  read_ is when a transaction ("Transaction A") reads a row from the database, another transaction ("Transaction B") changes the row, and Transaction
  A reads the row again, resulting in an inconsistency.
* **TRANSACTION_REPEATABLE_READ**: The default setting for Exposed transactions. Prevents both dirty and non-repeatable reads, but still allows for
  phantom reads. A _phantom read_ is when a transaction ("Transaction A") selects a list of rows through a `WHERE` clause, another transaction ("
  Transaction B") performs an `INSERT` or `DELETE` with a row that satisfies Transaction A's `WHERE` clause, and Transaction A selects using the same
  WHERE clause again, resulting in an inconsistency.
* **TRANSACTION_SERIALIZABLE**: The strictest setting. Prevents dirty reads, non-repeatable reads, and phantom reads.

**db** parameter is optional and used to select the database where the transaction should be settled (see section above).

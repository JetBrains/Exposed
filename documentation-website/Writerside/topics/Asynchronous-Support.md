# Asynchronous Support

## Working with Coroutines

In the modern world, non-blocking and asynchronous code is popular. Kotlin
has [Coroutines](https://kotlinlang.org/docs/reference/coroutines-overview.html) that give you an imperative way of writing asynchronous code. Most
Kotlin frameworks (like [ktor](https://ktor.io)) have built-in support for Coroutines while Exposed is mostly blocking. Why? Because Exposed uses JDBC API to interact
with databases that was designed in an era of blocking APIs. Moreover, Exposed stores some values in
thread-local variables while coroutines could (and will) be executed in different threads.

Since Exposed 0.15.1, bridge functions are available that give you a safe way to interact with Exposed within `suspend`
blocks: `newSuspendedTransaction()` and `Transaction.withSuspendTransaction()`. These have the same parameters as a blocking `transaction` function but allow you to
provide a `CoroutineContext` argument that explicitly specifies the `CoroutineDispatcher` in which the function will be executed. 
If context is not provided, your code will be executed within the current `CoroutineContext`.

Here is an example that uses these 3 types of transactions:

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

Please note that such code remains blocking (as it still uses JDBC) and you should not try to share a transaction between multiple threads as it will
lead to undefined behaviour.

If you want to execute some code asynchronously and use the result later, take a look at `suspendedTransactionAsync()`:

```kotlin
runBlocking {
    val launchResult = suspendedTransactionAsync(Dispatchers.IO) {
        FooTable.insert { it[id] = 2 }

        FooTable.selectAll().where { FooTable.id eq 2 }.singleOrNull()?.getOrNull(FooTable.id)
    }

    println("Async result: " + (launchResult.await() ?: -1)) // Async result: 2
}
```

This function will accept the same parameters as `newSuspendedTransaction()` above, but returns its future result as an implementation of `Deferred`, 
which you could call `await` on to achieve your result.

<note>
<code>newSuspendedTransaction()</code> and <code>suspendedTransactionAsync()</code> are always executed in a new transaction to prevent concurrency issues when query 
execution order could be changed by <code>CoroutineDispatcher</code>. 
This means that nesting these suspend transactions may not result in the same behavior as nested <code>transaction</code>s (when <code>useNestedTransactions = false</code>).
</note>

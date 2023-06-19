# Asynchronous Support

## Working with Coroutines

In the modern world, non-blocking and asynchronous code is popular. Kotlin
has [Coroutines](https://kotlinlang.org/docs/reference/coroutines-overview.html) that give you an imperative way of writing asynchronous code. Most
Kotlin frameworks (like [ktor](https://ktor.io)) have built-in support for Coroutines while Exposed is mostly blocking. Why? Because Exposed uses JDBC API to interact
with databases that was designed in an era of blocking APIs. Moreover, Exposed stores some values in
thread-local variables while coroutines could (and will) be executed in different threads.

Since Exposed 0.15.1, there are bridge functions that will give you a safe way to interact with Exposed within `suspend`
blocks: `newSuspendedTransaction/Transaction.withSuspendTransaction` have the same parameters as a blocking `transaction` function but will allow you to
provide a `CoroutineDispatcher` in which the function will be executed. If context is not provided, your code will be executed within the current `coroutineContext`.

Sample usage looks like:

```kotlin
runBlocking {
    transaction {
        SchemaUtils.create(FooTable) // Table will be created on a current thread

        newSuspendedTransaction(Dispatchers.Default) {
            FooTable.insert { it[id] = 1 } // This insert will be executed in one of Default dispatcher threads

            withSuspendTransaction {
                val id = FooTable.select { FooTable.id eq 1 }
                    .single()()[FooTable.id] // This select also will be executed on some thread from Default dispatcher using the same transaction
            }
        }

        val result = newSuspendedTransaction(Dispatchers.IO) {
            FooTable.select { FooTable.id eq 1 }
                .single()[H2Tests.Testing.id] // This select will be executed on some thread from IO dispatcher using the same transaction
        }
    }
}

```  

Please note that such code remains blocking (as it still uses JDBC) and you should not try to share a transaction between multiple threads as it will
lead to undefined behaviour.

If you want to execute some code asynchronously and use the result later in your code, take a look at `suspendedTransactionAsync` function.

```kotlin
val launchResult = suspendedTransactionAsync(Dispatchers.IO, db = db) {
    FooTable.insert {}

    FooTable.select { FooTable.id eq 1 }.singleOrNull()?.getOrNull(Testing.id)
}

println("Result: " + (launchResult.await() ?: - 1))

```

This function will accept the same parameters as `newSuspendedTransaction` above, but returns `Deferred` which you could call `await` on to achieve your
result.

`suspendedTransactionAsync` is always executed in a new transaction to prevent concurrency issues when queries execution order could be changed
by `CoroutineDispatcher`.

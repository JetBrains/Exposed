<show-structure for="chapter,procedure" depth="2"/>

# Working with SQL Strings

Using an SQL string to perform a database operation is possible from inside a transaction block with 
[`.exec()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/-transaction/exec.html). 
This function accepts and executes a `String` value argument, which may be useful when specific database commands are required:

<code-block lang="kotlin"
            src="exposed-transactions/src/main/kotlin/org/example/examples/ExecExamples.kt"
            include-lines="26-28"/>

## Transforming results

The SQL string sent to the database may return a result, in which case a transformation block can be optionally provided. 
The following example retrieves a single result for the current database version:

<code-block lang="kotlin"
            src="exposed-transactions/src/main/kotlin/org/example/examples/ExecExamples.kt"
            include-lines="30-33"/>

This example iterates over the result and returns a collection of database schema information:

<code-block lang="kotlin"
            src="exposed-transactions/src/main/kotlin/org/example/examples/ExecExamples.kt"
            include-lines="36-45"/>

Alternatively, a convenience extension function could be created to use SQL strings directly, for example:

<code-block lang="kotlin"
            src="exposed-transactions/src/main/kotlin/org/example/examples/ExecAndMapFunction.kt"/>

This function could then be called in the following way:

<code-block lang="kotlin"
            src="exposed-transactions/src/main/kotlin/org/example/examples/ExecExamples.kt"
            include-lines="70-72"/>

## Parameterized statements

SQL strings can be parameterized by substituting values with a `?` placeholder in the string and providing associated column types for each parameter argument:

<code-block lang="kotlin"
            src="exposed-transactions/src/main/kotlin/org/example/examples/ExecExamples.kt"
            include-lines="81-90"/>

## Explicit statement types

By default, `.exec()` uses the first keyword of an SQL string to determine how the string should be executed and whether 
results are expected to be returned by the database. The function attempts to find a match between this keyword and 
one of the [`StatementType`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql.statements/-statement-type/index.html) 
enum constants.

An argument can always be passed to the parameter `explicitStatementType` to avoid searching for a match and risking unexpected behavior:

<code-block lang="kotlin"
            src="exposed-transactions/src/main/kotlin/org/example/examples/ExecExamples.kt"
            include-lines="48-51"/>

Of all the defined `StatementType`s, only four prompt the function to execute the statement in such a way that results are 
expected to be returned. These types are:
* `StatementType.SELECT`
* `StatementType.EXEC`
* `StatementType.SHOW`
* `StatementType.PRAGMA`

All other types expect only an affected row count to be returned. This means that an argument can be provided to `explicitStatementType` 
in order to override default behavior.

For example, an SQL string that starts with `EXPLAIN ` would default to `StatementType.OTHER` because no match would be found. 
This would cause the `.exec()` to fail because this type of operation prompts the database to return an execution plan for a query. 
The `.exec()` would only succeed if a statement type override that expects a result, like `StatementType.EXEC`, is provided instead:

<code-block lang="kotlin"
            src="exposed-transactions/src/main/kotlin/org/example/examples/ExecExamples.kt"
            include-lines="99-110"/>

## Multiple SQL strings

Some databases allow multiple SQL strings of different operation types to be executed together in a single prepared statement, 
which can be enabled by choosing `StatementType.MULTI`.

The following example uses a MySQL database to perform an insert operation immediately followed by a query that returns 
the `id` column value of the last inserted row:

<code-block lang="kotlin"
            src="exposed-transactions/src/main/kotlin/org/example/examples/ExecMySQLExamples.kt"
            include-lines="26-40"/>

<note>
The exact result (and quantity of results) returned by multiple grouped statements executed together varies and is dependent on 
the database being used.

Some databases also require specific connection parameters to enable these operations. For example, 
MySQL requires adding <code>allowMultiQueries=true</code> to the connection url string.
</note>

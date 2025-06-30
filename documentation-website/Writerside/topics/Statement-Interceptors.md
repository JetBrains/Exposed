<show-structure for="chapter,procedure" depth="2"/>

# Statement Interceptors

DSL operations within a transaction create SQL statements, on which commands like *Execute*, *Commit*, and *Rollback*
are issued. Exposed provides
the [`StatementInterceptor`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql.statements/-statement-interceptor/index.html)
interface that allows you to implement your own logic before and after these specific steps in a statement's lifecycle.

`registerInterceptor()` and `unregisterInterceptor()` can be used to enable and disable a custom interceptor in a single
transaction.

To use a custom interceptor that acts on all transactions, implement the
[`GlobalStatementInterceptor`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql.statements/-global-statement-interceptor/index.html)
interface instead. Exposed uses the Java SPI ServiceLoader to discover and load any implementations of this interface.
In this situation, a new file should be created in the *resources* folder named:
```
META-INF/services/org.jetbrains.exposed.v1.core.statements.GlobalStatementInterceptor
```
The contents of this file should be the fully qualified class names of all custom implementations.

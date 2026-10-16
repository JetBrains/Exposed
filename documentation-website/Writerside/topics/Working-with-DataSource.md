<show-structure for="chapter,procedure" depth="2"/>

# Working with DataSources

<primary-label ref="jdbc"/>

It is also possible to provide a `javax.sql.DataSource` to the `Database.connect()` function. This allows you to use more advanced features like
connection pooling, and lets you set configuration options like maximum number of connections, connection timeouts, etc.

```kotlin
val db = Database.connect(dataSource)
```

### Example with HikariCP

To use a JDBC connection pool like [HikariCP](https://github.com/brettwooldridge/HikariCP), first set up a `HikariConfig` class.
This example uses the MySQL JDBC driver (see the official reference for [MySQL configuration](https://github.com/brettwooldridge/HikariCP/wiki/MySQL-Configuration) details):
```kotlin
val config = HikariConfig().apply {
    jdbcUrl = "jdbc:mysql://localhost/dbname"
    driverClassName = "com.mysql.cj.jdbc.Driver"
    username = "username"
    password = "password"
    maximumPoolSize = 6
    // as of version 0.46.0, if these options are set here, they do not need to be duplicated in DatabaseConfig
    isReadOnly = false
    transactionIsolation = "TRANSACTION_SERIALIZABLE"
}

// Gradle
implementation "mysql:mysql-connector-j:8.0.33"
implementation "com.zaxxer:HikariCP:4.0.3"
```
Then instantiate a `HikariDataSource` with this configuration class and provide it to `Database.connect()`:
```kotlin
val dataSource = HikariDataSource(config)

Database.connect(
    datasource = dataSource,
    databaseConfig = DatabaseConfig {
        // set other parameters here
    }
)
```

>Since version 0.46.0, when configured directly in the `HikariConfig` class,
>values like `transactionIsolation` and `isReadOnly` will be used by Exposed when creating transactions. 
>If they are duplicated or new values are set in `DatabaseConfig`,
>the latter will be treated as an override in the same way
>that setting these parameters on an individual transaction block overrides the default settings.
>It is therefore recommended to not set these values in `DatabaseConfig`
>unless the intention is for the new value to override the Hikari settings.
{style="note"}

With the built-in JDBC transaction manager and its default connection setup, `transaction()` and `suspendTransaction()` inherit DataSource settings
when the corresponding options are omitted. Explicit transaction options take precedence over explicitly configured
manager or `DatabaseConfig` defaults, which in turn take precedence over DataSource settings.
For example, `DatabaseConfig { defaultReadOnly = false }` overrides a read-only DataSource,
while `DatabaseConfig { }` inherits its read-only setting.

Inherited settings are resolved when the transaction first acquires its connection. Before that,
the transaction's `transactionIsolation` and `readOnly` properties expose explicit options or the manager defaults for unspecified options;
after successful connection initialization, they expose the resolved transaction settings.
Reading these properties does not acquire a connection.

<show-structure for="chapter,procedure" depth="2"/>

# Working with DataSources

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
implementation "mysql:mysql-connector-java:8.0.33"
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

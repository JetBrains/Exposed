# Working with DataSource

It is also possible to provide a `javax.sql.DataSource` to the `Database.connect` function. This allows you to use more advanced features like
connection pooling, and lets you set configuration options like maximum number of connections, connection timeouts, etc.

```kotlin
val db = Database.connect(dataSource)
```

## MariaDB/MySQL with latest JDBC driver + Hikari pooling

Add dependency:

```kotlin
implementation("mysql:mysql-connector-java:8.0.19")
implementation("com.zaxxer:HikariCP:3.4.2")
```

Connect to database:

```kotlin
val config = HikariConfig().apply {
    jdbcUrl = "jdbc:mysql://localhost/dbname"
    driverClassName = "com.mysql.cj.jdbc.Driver"
    username = "user"
    password = "password"
    maximumPoolSize = 10
}
val dataSource = HikariDataSource(config)
Database.connect(dataSource)
```

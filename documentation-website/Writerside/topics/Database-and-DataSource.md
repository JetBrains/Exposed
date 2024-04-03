# Working with Database and DataSource
Every database access using Exposed is started by obtaining a connection and creating a transaction.  
First of all, you have to tell Exposed how to connect to a database by using the `Database.connect` function.
It won't create a real database connection but will only provide a descriptor for future usage.

A real connection will be instantiated later by calling the `transaction` lambda
(see [Transactions](Transactions.md) for more details).

Use the following to get a Database instance by simply providing connection parameters:
```kotlin
val db = Database.connect("jdbc:h2:mem:test", driver = "org.h2.Driver")
```
It is also possible to provide `javax.sql.DataSource` for advanced behaviors such as connection pooling (see the [HikariCP section](#hikaricp)):
```kotlin
val db = Database.connect(dataSource)
```
<note>Executing this code more than once per database will create leaks in your application, hence it is recommended to store it for later use:
<code-block lang="kotlin">
object DbSettings {
   val db by lazy { 
       Database.connect(/* setup connection */)
   }
}
</code-block>
</note>
## DataSource
* PostgresSQL
```kotlin
Database.connect("jdbc:postgresql://localhost:12346/test", driver = "org.postgresql.Driver", 
                 user = "root", password = "your_pwd")  
// Gradle
implementation("org.postgresql:postgresql:42.7.1")  
```
* PostgresSQL using the pgjdbc-ng JDBC driver
```kotlin
Database.connect("jdbc:pgsql://localhost:12346/test", driver = "com.impossibl.postgres.jdbc.PGDriver", 
                 user = "root", password = "your_pwd")  
// Gradle
implementation("com.impossibl.pgjdbc-ng:pgjdbc-ng:0.8.9")  
```
* MySQL
```kotlin
Database.connect("jdbc:mysql://localhost:3306/test", driver = "com.mysql.cj.jdbc.Driver", 
                 user = "root", password = "your_pwd")  
// Gradle
implementation("mysql:mysql-connector-java:8.0.33")
```
* MariaDB
```kotlin
Database.connect("jdbc:mariadb://localhost:3306/test", driver = "org.mariadb.jdbc.Driver", 
                 user = "root", password = "your_pwd")  
// Gradle
implementation("org.mariadb.jdbc:mariadb-java-client:3.3.1")
```
* Oracle
```kotlin
Database.connect("jdbc:oracle:thin:@//localhost:1521/test", driver = "oracle.jdbc.OracleDriver", 
                 user = "root", password = "your_pwd")  
// Gradle
// Oracle jdbc-driver should be obtained from Oracle maven repo: https://blogs.oracle.com/dev2dev/get-oracle-jdbc-drivers-and-ucp-from-oracle-maven-repository-without-ides
```
* SQLite
```kotlin
// In file
Database.connect("jdbc:sqlite:/data/data.db", "org.sqlite.JDBC")  
// In memory
Database.connect("jdbc:sqlite:file:test?mode=memory&cache=shared", "org.sqlite.JDBC")  
// For both: set SQLite compatible isolation level, see 
// https://github.com/JetBrains/Exposed/wiki/FAQ
TransactionManager.manager.defaultIsolationLevel = 
    Connection.TRANSACTION_SERIALIZABLE
    // or Connection.TRANSACTION_READ_UNCOMMITTED
// Gradle
implementation("org.xerial:sqlite-jdbc:3.44.1.0")  
```  
* H2
```kotlin
// Database in file, needs full path or relative path starting with ./
Database.connect("jdbc:h2:./myh2file", "org.h2.Driver")
// In memory
Database.connect("jdbc:h2:mem:regular", "org.h2.Driver")  
// In memory / keep alive between connections/transactions
Database.connect("jdbc:h2:mem:regular;DB_CLOSE_DELAY=-1;", "org.h2.Driver")  
// Gradle
implementation("com.h2database:h2:2.2.224")  
```  
* SQL Server
```kotlin
Database.connect("jdbc:sqlserver://localhost:32768;databaseName=test", "com.microsoft.sqlserver.jdbc.SQLServerDriver", 
                 user = "root", password = "your_pwd")  
// Gradle
implementation("com.microsoft.sqlserver:mssql-jdbc:9.4.1.jre8")  
```

### HikariCP
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

<note>
Since version 0.46.0, when configured directly in the `HikariConfig` class,
values like `transactionIsolation` and `isReadOnly` will be used by Exposed when creating transactions.

If they are duplicated or new values are set in `DatabaseConfig`,
the latter will be treated as an override in the same way
that setting these parameters on an individual transaction block overrides the default settings.

It is therefore recommended to not set these values in `DatabaseConfig`
unless the intention is for the new value to override the Hikari settings.
</note>
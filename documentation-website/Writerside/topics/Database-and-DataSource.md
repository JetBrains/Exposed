# Database and DataSource

## Working with Database and DataSource
Every database access using Exposed is starting by obtaining a connection and creating a transaction.  
First of all, you have to tell Exposed how to connect to a database by using `Database.connect` function.
It won't create a real database connection but only provide a descriptor for future usage.

A real connection will be instantiated later by calling `transaction` lambda (see [Transaction](Transactions.md) for more details).

To get a Database instance by simple providing connection parameters:
```kotlin
val db = Database.connect("jdbc:h2:mem:test", driver = "org.h2.Driver")
```
It is also possible to provide `javax.sql.DataSource` for advanced behaviors such as connection pooling:
```kotlin
val db = Database.connect(dataSource)
```
* Note: Starting Exposed 0.10 executing this code more than once per db will create leaks in your application, hence it is recommended to store it for later use.
  For example:
```kotlin
object DbSettings {
   val db by lazy { 
       Database.connect(/* setup connection */)
   }
}
```
## DataSource


###  PostgreSQL
Add dependency:
```kotlin
implementation("org.postgresql:postgresql:42.2.2")  
```

Connect to database:
```kotlin
Database.connect(
    "jdbc:postgresql://localhost:12346/test",
    driver = "org.postgresql.Driver", 
    user = "user", 
    password = "password"
)  
```

### PostgreSQL using the pgjdbc-ng JDBC driver

Add dependency:
```kotlin
implementation("com.impossibl.pgjdbc-ng", "pgjdbc-ng", "0.8.3")  
```

Connect to database:
```kotlin
Database.connect(
    "jdbc:pgsql://localhost:12346/test",
    driver = "com.impossibl.postgres.jdbc.PGDriver", 
    user = "user",
    password = "password"
)  
```
### MySQL/MariaDB

Add dependency:
```kotlin
implementation("mysql:mysql-connector-java:8.0.2")
```

Connect to database:
```kotlin
Database.connect(
    "jdbc:mysql://localhost:3306/test",
    driver = "com.mysql.cj.jdbc.Driver",
    user = "user",
    password = "password"
)  
```

### MySQL/MariaDB with latest JDBC driver + Hikari pooling

Add dependency:
```kotlin
implementation("mysql:mysql-connector-java:8.0.19")
implementation("com.zaxxer:HikariCP:3.4.2")
```

Connect to database:
```kotlin
val config = HikariConfig().apply {
    jdbcUrl         = "jdbc:mysql://localhost/dbname"
    driverClassName = "com.mysql.cj.jdbc.Driver"
    username        = "user"
    password        = "password"
    maximumPoolSize = 10
}
val dataSource = HikariDataSource(config)
Database.connect(dataSource)
```

### Oracle

Add Dependency:
```kotlin
implementation("com.oracle.database.jdbc:ojdbc8:12.2.0.1")
```

Then connect to database:
```kotlin
Database.connect(
    "jdbc:oracle:thin:@//localhost:1521/test", 
    driver = "oracle.jdbc.OracleDriver", 
    user = "user",
    password = "password"
)  
```

### SQLite

In order to use SQLite, you need to add the dependency to the SQLite JDBC driver.
```kotlin
implementation("org.xerial:sqlite-jdbc:3.30.1")  
```

Then connect to file-database:
```kotlin
Database.connect("jdbc:sqlite:/data/data.db", "org.sqlite.JDBC")  
```

Or in-memory database:
```kotlin
Database.connect("jdbc:sqlite:file:test?mode=memory&cache=shared", "org.sqlite.JDBC")  
```  

For both: set SQLite compatible isolation level: [FAQ](Frequently-Asked-Questions.md).
```kotlin
TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
                                                // or Connection.TRANSACTION_READ_UNCOMMITTED
```

### H2

In order to use H2, you need to add the dependency to the H2 driver:
```kotlin
implementation("com.h2database:h2:2.1.214")
```

Then connect to database in file:
```kotlin
Database.connect("jdbc:h2:./myh2file", "org.h2.Driver")
```

Or in memory:
```kotlin
Database.connect("jdbc:h2:mem:regular", "org.h2.Driver")  
```  

By default, H2 closes the database when the last connection is closed. If you want to keep the database open, you can use the DB_CLOSE_DELAY=-1 option:
```kotlin
Database.connect("jdbc:h2:mem:regular;DB_CLOSE_DELAY=-1;", "org.h2.Driver")
```

### MSSQL Server

Add dependency:
```kotlin
implementation("com.microsoft.sqlserver:mssql-jdbc:6.4.0.jre7")  
```

Connect to database:
```kotlin
Database.connect(
    "jdbc:sqlserver://localhost:32768;databaseName=test",
    "com.microsoft.sqlserver.jdbc.SQLServerDriver", 
    user = "user",
    password = "password"
)  
```

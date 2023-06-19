# Working with Database

In Exposed, the `Database` class represents a database instance, and encapsulates the necessary connection
details and configuration required to interact with a specific database.

## Connecting to a Database

To connect to a database using `Database`, you need to provide the appropriate JDBC driver and connection URL. Here's an example of how to establish a
connection:

```kotlin
val db = Database.connect("jdbc:h2:mem:test", driver = "org.h2.Driver")
```

The `Database.connect` function tells Exposed _how_ to connect to a database, but won't _create_ a database connection. It only provides a
descriptor for future usage. A connection will be created later by calling the `transaction` lambda (see [Transaction](Working-with-Transaction.md) for more
details).

<note>
Starting from Exposed 0.10, executing this code more than once per database will create leaks in your application; hence, it is recommended to
  store it for later use.
</note>

Creating a database only when it is accessed for the first time can be done like this:

```kotlin
object DbSettings {
    val db by lazy {
        Database.connect(/* setup connection */)
    }
}
```

### H2

In order to use H2, you need to add the H2 driver dependency:

```kotlin
implementation("com.h2database:h2:2.1.214")
```

Then connect to a database:

```kotlin
Database.connect("jdbc:h2:./myh2file", "org.h2.Driver")
```

Or in-memory database:

```kotlin
Database.connect("jdbc:h2:mem:regular", "org.h2.Driver")  
```  

By default, H2 closes the database when the last connection is closed. If you want to keep the database open, you can use the `DB_CLOSE_DELAY=-1`
option:

```kotlin
Database.connect("jdbc:h2:mem:regular;DB_CLOSE_DELAY=-1;", "org.h2.Driver")
```

### MariaDB/MySQL

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

### Oracle

Add dependency:

```kotlin
implementation("com.oracle.database.jdbc:ojdbc8:12.2.0.1")
```

Connect to database:

```kotlin
Database.connect(
    "jdbc:oracle:thin:@//localhost:1521/test",
    driver = "oracle.jdbc.OracleDriver",
    user = "user",
    password = "password"
)  
```

### PostgreSQL

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

### SQL Server

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

### SQLite

Add the dependency:

```kotlin
implementation("org.xerial:sqlite-jdbc:3.30.1")  
```

Connect to database:

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

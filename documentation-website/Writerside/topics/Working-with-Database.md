<show-structure for="chapter,procedure" depth="2"/>

# Working with Databases

In Exposed, the `Database` class represents a database instance, and encapsulates the necessary connection
details and configuration required to interact with a specific database.

## Connecting to a Database

Every database access using Exposed is started by obtaining a connection and creating a transaction.

First of all, you have to tell Exposed how to connect to a database by using the `Database.connect` function.
It won't create a real database connection but will only provide a descriptor for future usage.

By default, Exposed using `ServiceLoader` to get `DatabaseConnectionAutoRegistration`.
It can be modified when calling `Database.connect` method by providing `connectionAutoRegistration` in parameter list.

A real connection will be instantiated later by calling the `transaction` lambda
(see [Transactions](Transactions.md) for more details).

Use the following to get a Database instance by simply providing connection parameters:

```kotlin
val db = Database.connect("jdbc:h2:mem:test", driver = "org.h2.Driver")
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

### H2

In order to use H2, you need to add the H2 driver dependency:

```kotlin
implementation("com.h2database:h2:2.2.224")
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

### MariaDB

Add dependency:

```kotlin
implementation("org.mariadb.jdbc:mariadb-java-client:3.3.1")
```
Connect to database:

```kotlin
Database.connect("jdbc:mariadb://localhost:3306/test",
    driver = "org.mariadb.jdbc.Driver",
    user = "root",
    password = "your_pwd"
)
```

### MySQL

Add dependency:

```kotlin
implementation("mysql:mysql-connector-java:8.0.33")
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
implementation("org.postgresql:postgresql:42.7.1")  
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
implementation("com.impossibl.pgjdbc-ng", "pgjdbc-ng", "0.8.9")  
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
implementation("com.microsoft.sqlserver:mssql-jdbc:9.4.1.jre8")
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
implementation("org.xerial:sqlite-jdbc:3.44.1.0")
```

Connect to database:

```kotlin
Database.connect("jdbc:sqlite:/data/data.db", "org.sqlite.JDBC")  
```

Or in-memory database:

```kotlin
Database.connect("jdbc:sqlite:file:test?mode=memory&cache=shared", "org.sqlite.JDBC")  
```  

Set SQLite compatible [isolation level](https://www.sqlite.org/isolation.html):

```kotlin
TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
// or Connection.TRANSACTION_READ_UNCOMMITTED
```

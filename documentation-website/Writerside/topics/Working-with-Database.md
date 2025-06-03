<show-structure for="chapter,procedure" depth="2"/>

# Working with Databases

In Exposed, the [`Database`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/-database/index.html)
and [`R2DBCDatabase`]() classes represent a database instance, and encapsulates the necessary connection details and configuration required to
interact with a specific database.

## Connecting to a Database

Every database access in Exposed begins by establishing a connection and creating a transaction.

To connect to a database, you first need to tell Exposed of the connection details. You have two options:

- Use [`Database.connect()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/-database/-companion/connect.html) for traditional JDBC-based access.
- Use [`R2DBCDatabase.connect()`]() for reactive, non-blocking access with R2DBC.

These functions do not immediately establish a connection. Instead, they provide a descriptor for future usage. An
actual connection is only established when a [transaction](Transactions.md) is initiated.

To get a database instance using simple connection parameters, use the following approach:

<tabs group="connectivity">
    <tab id="jdbc-connect" title="JDBC" group-key="jdbc">
        <code-block lang="kotlin"
                    src="exposed-databases-jdbc/src/main/kotlin/org/example/Databases.kt"
                    include-symbol="h2db" />
    </tab>
    <tab id="r2dbc-connect" title="R2DBC" group-key="r2dbc">
        <code-block lang="kotlin"
                    src="exposed-databases-r2dbc/src/main/kotlin/org/example/R2DBCDatabases.kt"
                    include-symbol="h2db" />
    </tab>
</tabs>

<note>Executing this code more than once per database will create leaks in your application, hence it is recommended to store it for later use:
<code-block lang="kotlin"
            src="exposed-databases-jdbc/src/main/kotlin/org/example/Databases.kt"
            include-symbol="DbSettings"/>
</note>

<note>
    By default, Exposed uses a <code>ServiceLoader</code> to get an implementation of the
    <a href="https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/-database-connection-auto-registration/index.html">
        <code>DatabaseConnectionAutoRegistration</code>
    </a>
    interface that represents a connection accessed by the <code>Database</code> instance.
    This can be modified when calling the <code>Database.connect()</code> method by providing an argument to <code>connectionAutoRegistration</code>
    in the parameter list.
</note>

### H2

In order to use H2, you need to add the H2 driver dependency:

<tabs group="connectivity">
    <tab id="jdbc-h2-db" title="JDBC" group-key="jdbc">
        <code-block lang="kotlin">
            implementation("com.h2database:h2:%h2_db_version%")
        </code-block>
    </tab>
    <tab id="r2dbc-h2-db" title="R2DBC" group-key="r2dbc">
        <code-block lang="kotlin">
            implementation("io.r2dbc:r2dbc-h2:%mariadb_r2dbc%")
        </code-block>
    </tab>
</tabs>

Then connect to a database:

<tabs group="connectivity">
    <tab id="jdbc-h2-driver-connect" title="JDBC" group-key="jdbc">
        <code-block lang="kotlin"
                    src="exposed-databases-jdbc/src/main/kotlin/org/example/Databases.kt"
                    include-symbol="h2dbFromFile" />
    </tab>
    <tab id="r2dbc-h2-driver-connect" title="R2DBC" group-key="r2dbc">
        <code-block lang="kotlin"
                    src="exposed-databases-r2dbc/src/main/kotlin/org/example/R2DBCDatabases.kt" 
                    include-symbol="h2dbFromFile" />
        </tab>
</tabs>

Or in-memory database:

<tabs group="connectivity">
    <tab id="jdbc-h2-db-in-memory" title="JDBC" group-key="jdbc">
        <code-block lang="kotlin"
                    src="exposed-databases-jdbc/src/main/kotlin/org/example/Databases.kt"
                    include-symbol="h2db" />
    </tab>
    <tab id="r2dbc-h2-db-in-memory" title="R2DBC" group-key="r2dbc">
        <code-block lang="kotlin"
                    src="exposed-databases-r2dbc/src/main/kotlin/org/example/R2DBCDatabases.kt"
                    include-symbol="h2db" />
    </tab>
</tabs>

By default, H2 closes the database when the last connection is closed. If you want to keep the database open, you can use the `DB_CLOSE_DELAY=-1`
option:

```kotlin
Database.connect("jdbc:h2:mem:regular;DB_CLOSE_DELAY=-1;", "org.h2.Driver")
```

### MariaDB

Add the required dependency:

<tabs group="connectivity">
    <tab id="jdbc-maria-db" title="JDBC" group-key="jdbc">
        <code-block lang="kotlin">
            implementation("org.mariadb.jdbc:mariadb-java-client:%mariadb%")
        </code-block>
    </tab>
    <tab id="r2dbc-maria-db" title="R2DBC" group-key="r2dbc">
        <code-block lang="kotlin">
            implementation("org.mariadb:r2dbc-mariadb:%mariadb_r2dbc%")
        </code-block>
    </tab>
</tabs>

Connect to a database:

<tabs group="connectivity">
    <tab id="jdbc-maria-db-connect" title="JDBC" group-key="jdbc">
        <code-block lang="kotlin"
                    src="exposed-databases-jdbc/src/main/kotlin/org/example/Databases.kt"
                    include-symbol="mariadb" />
    </tab>
    <tab id="r2dbc-maria-db-connect" title="R2DBC" group-key="r2dbc">
        <code-block lang="kotlin"
                    src="exposed-databases-r2dbc/src/main/kotlin/org/example/R2DBCDatabases.kt"
                    include-symbol="mariadb" />
    </tab>
</tabs>

### MySQL

Add the required dependency:

<tabs group="connectivity">
    <tab id="jdbc-mysql" title="JDBC" group-key="jdbc">
        <code-block lang="kotlin">
            implementation("mysql:mysql-connector-java:%mysql%")
        </code-block>
    </tab>
    <tab id="r2dbc-mysql" title="R2DBC" group-key="r2dbc">
        <code-block lang="kotlin">
            implementation("io.asyncer:r2dbc-mysql:%mysql_r2dbc%")
        </code-block>
    </tab>
</tabs>

Connect to a database:

<tabs group="connectivity">
    <tab id="jdbc-mysql-connect" title="JDBC" group-key="jdbc">
        <code-block lang="kotlin"
                    src="exposed-databases-jdbc/src/main/kotlin/org/example/Databases.kt"
                    include-symbol="mysqldb" />
    </tab>
    <tab id="r2dbc-mysql-connect" title="R2DBC" group-key="r2dbc">
        <code-block lang="kotlin"
                    src="exposed-databases-r2dbc/src/main/kotlin/org/example/R2DBCDatabases.kt"
                    include-symbol="mysqldb" />
    </tab>
</tabs>

### Oracle

Add the required dependency:

<tabs group="connectivity">
    <tab id="jdbc-oracle" title="JDBC" group-key="jdbc">
        <code-block lang="kotlin">
            implementation("com.oracle.database.jdbc:ojdbc8:%oracle%")
        </code-block>
    </tab>
    <tab id="r2dbc-oracle" title="R2DBC" group-key="r2dbc">
        <code-block lang="kotlin">
            implementation("com.oracle.database.r2dbc:oracle-r2dbc:%oracle_r2dbc%")
        </code-block>
    </tab>
</tabs>

Connect to a database:

<tabs group="connectivity">
    <tab id="jdbc-oracle-connect" title="JDBC" group-key="jdbc">
        <code-block lang="kotlin"
                    src="exposed-databases-jdbc/src/main/kotlin/org/example/Databases.kt"
                    include-symbol="oracledb" />
    </tab>
    <tab id="r2dbc-oracle-connect" title="R2DBC" group-key="r2dbc">
        <code-block lang="kotlin"
                    src="exposed-databases-r2dbc/src/main/kotlin/org/example/R2DBCDatabases.kt"
                    include-symbol="oracledb" />
    </tab>
</tabs>

### PostgreSQL

Add the required dependency:

<tabs group="connectivity">
    <tab id="jdbc-postgresql" title="JDBC" group-key="jdbc">
        <code-block lang="kotlin">
            implementation("org.postgresql:postgresql:%postgresql%")
        </code-block>
    </tab>
    <tab id="r2dbc-postgresql" title="R2DBC" group-key="r2dbc">
        <code-block lang="kotlin">
            implementation("org.postgresql:r2dbc-postgresql:%postgresql_r2dbc%")
        </code-block>
    </tab>
</tabs>

Connect to a database:

<tabs group="connectivity">
    <tab id="jdbc-postgresql-connect" title="JDBC" group-key="jdbc">
        <code-block lang="kotlin"
                    src="exposed-databases-jdbc/src/main/kotlin/org/example/Databases.kt"
                    include-symbol="postgresqldb" />
    </tab>
    <tab id="r2dbc-postgresql-connect" title="R2DBC" group-key="r2dbc">
        <code-block lang="kotlin"
                    src="exposed-databases-r2dbc/src/main/kotlin/org/example/R2DBCDatabases.kt"
                    include-symbol="postgresqldb" />
    </tab>
</tabs>

### PostgreSQL using the pgjdbc-ng JDBC driver

Add the required dependency:

```kotlin
implementation("com.impossibl.pgjdbc-ng:pgjdbc-ng:%postgreNG%")
```

Connect to a database:

```kotlin
```
{src="exposed-databases-jdbc/src/main/kotlin/org/example/Databases.kt" include-symbol="postgresqldbNG"}

### SQL Server

Add the required dependency:

<tabs group="connectivity">
    <tab id="jdbc-sqlserver" title="JDBC" group-key="jdbc">
        <code-block lang="kotlin">
            implementation("com.microsoft.sqlserver:mssql-jdbc:%sqlserver%")
        </code-block>
    </tab>
    <tab id="r2dbc-sqlserver" title="R2DBC" group-key="r2dbc">
        <code-block lang="kotlin">
            implementation("io.r2dbc:r2dbc-mssql:%sqlserver_r2dbc%")
        </code-block>
    </tab>
</tabs>

Connect to a database:

<tabs group="connectivity">
    <tab id="jdbc-sqlserver-connect" title="JDBC" group-key="jdbc">
        <code-block lang="kotlin"
                    src="exposed-databases-jdbc/src/main/kotlin/org/example/Databases.kt"
                    include-symbol="sqlserverdb" />
    </tab>
    <tab id="r2dbc-sqlserver-connect" title="R2DBC" group-key="r2dbc">
        <code-block lang="kotlin"
                    src="exposed-databases-r2dbc/src/main/kotlin/org/example/R2DBCDatabases.kt"
                    include-symbol="sqlserverdb" />
    </tab>
</tabs>

### SQLite

Add the required dependency:

```kotlin
implementation("org.xerial:sqlite-jdbc:%sqlite%")
```

Connect to a database:

```kotlin
Database.connect("jdbc:sqlite:/data/data.db", "org.sqlite.JDBC")  
```

Or an in-memory database:

```kotlin
Database.connect("jdbc:sqlite:file:test?mode=memory&cache=shared", "org.sqlite.JDBC")  
```  

Set SQLite compatible [isolation level](https://www.sqlite.org/isolation.html):

```kotlin
TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
// or Connection.TRANSACTION_READ_UNCOMMITTED
```

# Exposed DSL API examples

A Gradle application that shows how to work with Exposed DSL API.
The files are referenced in the DSL's [CRUD operations](../../topics/DSL-CRUD-operations.topic),
[Querying Data](../../topics/DSL-Querying-data.topic), [Joining tables](../../topics/DSL-Joining-tables.topic),
and [Table types](../../topics/DSL-Table-Types.topic) topics.
 
## Prerequisites

The project contains examples that run against H2, SQLite, and MySQL databases. While H2 and SQLite make use of 
in-memory storage, in order to run queries against MySQL, you must first install MySQL and create a local database.

To learn how to install MySQL, see the [installation guide](https://dev.mysql.com/doc/refman/8.4/en/installing.html).

## Database configuration

All database connections are configured within the `App.kt` file located in `src/main/kotlin/org/example/`.
You might want to adjust the MySQL database configuration to match your local setup.

```kotlin
val mysqlDb = Database.connect(
        "jdbc:mysql://localhost:3306/test",
        driver = "com.mysql.cj.jdbc.Driver",
        user = "root",
        password = "password"
    )
```

## Build

To build the application, in a terminal window navigate to the `snippets` folder and run the following command:

```shell
./gradlew :exposed-dsl:build
```

## Run

To run the application, in a terminal window navigate to the `snippets` folder and run the following command:

```shell
./gradlew :exposed-dsl:run
```

This will run queries to create new tables and run all functions in the `/examples` folder.
To only run a specific example, modify the `App.kt` file and re-run the project.

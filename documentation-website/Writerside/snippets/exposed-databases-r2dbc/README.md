# Exposed R2DBC databases

A Gradle application that shows how to define and connect to an R2DBCDatabase in Exposed.
The files are referenced in the [Working with Database](../../topics/Working-with-Database.md) topic.

## Configure

Navigate to the `R2DBCDatabases.kt` file and ensure that the database connection credentials match
your setup. For example:

```Kotlin
val postgresqldb = R2dbcDatabase.connect(
    url = "r2dbc:postgresql://db:5432/test",
    databaseConfig = {
        connectionFactoryOptions {
            option(ConnectionFactoryOptions.USER, "user")
            option(ConnectionFactoryOptions.PASSWORD, "password")
        }
    }
)
```

## Build

To build the application, run the following terminal command in the `snippets` folder:

```shell
./gradlew :exposed-databases-r2dbc:build
```

## Run

To run the application, in a terminal window navigate to the `snippets` folder and run the following command:

```shell
./gradlew :exposed-databases-r2dbc:run
```

To only run a specific example, modify the `App.kt` file and re-run the project.

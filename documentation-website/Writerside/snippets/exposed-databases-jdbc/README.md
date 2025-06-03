# Exposed JDBC databases

A Gradle application that shows how to define and connect to a Database in Exposed.
The files are referenced in the [Working with Database](../../topics/Working-with-Database.md) topic.

## Configure

Navigate to the `Databases.kt` file and ensure that the database connection credentials match
your setup. For example:

```Kotlin
    val postgreSQL = Database.connect(
        "jdbc:postgresql://localhost:5432/postgres",
        driver = "org.postgresql.Driver",
        user = "user",
        password = "password"
    )
```

## Build

To build the application, run the following terminal command in the `snippets` folder:

```shell
./gradlew :exposed-databases-jdbc:build
```

## Run

To run the application, in a terminal window navigate to the `snippets` folder and run the following command:

```shell
./gradlew :exposed-databases-jdbc:run
```

To only run a specific example, modify the `App.kt` file and re-run the project.

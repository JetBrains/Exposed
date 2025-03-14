# Exposed data types examples

A Gradle application that shows what data types are available in Exposed and how they are used.
The files are referenced in the Schema's Data types topics.

## Configure

Navigate to the `App.kt` file and ensure that the Database connection credentials match your setup. For example:

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
./gradlew :exposed-data-types:build
```

## Run

To run the application, in a terminal window navigate to the `snippets` folder and run the following command:

```shell
./gradlew :exposed-data-types:run
```

This will run queries to create new tables and run all functions in the `/examples` folder.
To only run a specific example, modify the `App.kt` file and re-run the project.

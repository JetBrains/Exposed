# Exposed SQL Functions examples

A Gradle application that shows how to work with built-in and custom SQL functions using Exposed API.
The files are referenced in the Schema's [SQL Functions](../../topics/SQL-Functions.md) topic.

## Build

To build the application, in a terminal window navigate to the `snippets` folder and run the following command:

```shell
./gradlew :exposed-sql-functions:build
```

## Run

To run the application, in a terminal window navigate to the `snippets` folder and run the following command:

```shell
./gradlew :exposed-sql-functions:run
```

This will run queries to create new tables and run all functions in the `/examples` folder.
To only run a specific example, modify the `App.kt` file and re-run the project.

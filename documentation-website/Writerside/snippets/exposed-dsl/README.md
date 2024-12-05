# Exposed DSL API examples

A Gradle application that shows how to work with Exposed DSL API.
The files are referenced in the DSL's [CRUD operations](../../topics/DSL-CRUD-operations.topic) and
[Table types](../../topics/DSL-Table-Types.topic) topics.

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

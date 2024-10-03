# Exposed DAO API examples

A Gradle application that shows how to work with Exposed DAO API.
The files are referenced in the DAO's [CRUD operations](../../topics/DAO-CRUD-Operations.topic),
[Table types](../../topics/DAO-Table-Types.topic) and [Entity definition](../../topics/DAO-Entity-definition.topic)
topics.

## Build

To build the application, in a terminal window navigate to the `snippets` folder and run the following command:

```shell
./gradlew :exposed-dao:build
```

## Run

To run the application, in a terminal window navigate to the `snippets` folder and run the following command:

```shell
./gradlew :exposed-dao:run
```

This will run queries to create new tables and run all functions in the `/examples` folder.
To only run a specific example, modify the `App.kt` file and re-run the project.

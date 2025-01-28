# Exposed DAO Relationships examples

A Gradle application that shows how to create relationships between entities with Exposed DAO API.
The files are referenced in the DAO's [Relationships](../../topics/DAO-Relationships.topic)
topic.

## Build

To build the application, in a terminal window navigate to the `snippets` folder and run the following command:

```shell
./gradlew :exposed-dao-relationships:build
```

## Run

To run the application, in a terminal window navigate to the `snippets` folder and run the following command:

```shell
./gradlew :exposed-dao-relationships:run
```

This will run queries to create new tables and run all functions in the `/examples` folder.
To only run a specific example, modify the `App.kt` file and re-run the project.

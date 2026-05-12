# Exposed Gradle Plugin

A Gradle application that shows how to generate a migration script using the Exposed Gradle plugin extension for migrations.
The files are referenced in the [Exposed Gradle plugin](../../topics/Exposed-gradle-plugin.md) topic.

## Build

To build the application, in a terminal window navigate to the `snippets` folder and run the following command:

```shell
./gradlew :exposed-gradle-plugin:build
```

## Generate a migration script

To generate the migration script, in a terminal window navigate to the `snippets` folder and run the following command:

```shell
./gradlew :exposed-gradle-plugin:generateMigrations
```

## Run

To run the application, in a terminal window navigate to the `snippets` folder and run the following command:

```shell
./gradlew :exposed-gradle-plugin:run
```

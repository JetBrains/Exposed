# Exposed Maven Plugin

A Maven project that shows how to generate a migration script using the Exposed Maven plugin extension for migrations.
The files are referenced in the [Exposed Maven plugin](../../topics/Exposed-maven-plugin.md) topic.

## Build

To build the project, in a terminal window navigate to the `exposed-maven-plugin` folder and run the following command:

```shell
mvn compile
```

## Generate a migration script

To generate the migration script, in a terminal window navigate to the `exposed-maven-plugin` folder and run the following command:

```shell
mvn exposed:generate-migrations
```

## Run

To run the project, in a terminal window navigate to the `exposed-maven-plugin` folder and run the following command:

```shell
mvn compile exec:java
```

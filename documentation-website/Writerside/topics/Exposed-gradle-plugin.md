<show-structure for="chapter,procedure" depth="2"/>

# Exposed Gradle plugin

<tldr>
    <p>
        <b>Required dependencies</b>: <code>org.jetbrains.exposed.plugin</code>
    </p>
    <p>
        <b>Code example</b>: <a href="https://github.com/JetBrains/Exposed/tree/main/documentation-website/Writerside/snippets/exposed-gradle-plugin">exposed-gradle-plugin</a>
    </p>
</tldr>

The Exposed Gradle plugin provides build-time tooling for working with Exposed-based database schemas.

Its primary feature is [generating SQL migration scripts](#generate-migration-scripts) by comparing Exposed
table definitions with an existing database schema.

## Requirements

* Kotlin 2.2 or later
* Gradle 8.14 or later ([Gradle installation guide](https://docs.gradle.org/current/userguide/installation.html))
* JVM 11 or later
* [Docker](https://www.docker.com/) (only required when [using `Testcontainers`](#use-testcontainers))

## Installation

To install the plugin, add it to the `plugins` block in your Gradle build script:

```kotlin
plugins {
  id("org.jetbrains.exposed.plugin") version "%exposed_version%"
}
```

## Generate migration scripts

To generate migration scripts based on the difference between your existing database schema and your Exposed table definitions,
use the `generateMigrations` task:

```bash
./gradlew generateMigrations
```

Generated files are written to the [configured output directory](#file-directory).

### Integration with the build lifecycle

You can configure migration generation to run automatically during the Gradle build lifecycle.

For example, you can generate migrations before the `build` or the `processResources` tasks:

```kotlin
// Generate migration scripts before the build task
tasks.named("build") {
    dependsOn("generateMigrations")
}

// Generate migration scripts before the processResources task
tasks.named("processResources") {
    dependsOn("generateMigrations")
}
```

## Configuration

Configure the plugin using the `exposed.migrations` block in your
<path>build.gradle.kts</path> file.

At minimum, configure the following properties:

* `tablesPackage` as the package name where Exposed table definitions are located.
* A database configuration or a `Testcontainers` configuration.

### Configure a database connection

To configure a database connection, set the `databaseUrl`, `databaseUser`, and `databasePassword` properties:

```kotlin
exposed {
    migrations {
        tablesPackage.set("com.example.db.tables")
        databaseUrl.set("jdbc:postgresql://localhost:5432/mydb")
        databaseUser.set("postgres")
        databasePassword.set("password")
    }
}
```

### Configure `Testcontainers` {id="testcontainers-config"}

To configure a `Testcontainers` connection, set the `testContainersImageName` property:

```kotlin
exposed {
    migrations {
        tablesPackage.set("com.example.db.tables")
        testContainersImageName.set("postgres:latest")
    }
}
```
> For more details and supported database container images, see [](#use-testcontainers).
> 
{style="tip"}

> When `testContainersImageName` is configured, the plugin uses `Testcontainers` instead of a direct database
> connection for schema generation.
>
{style="note"}

## Additional configuration

Optionally, you can configure the following properties for additional control over migration generation and file naming:

<deflist type="medium">
<def>
<title><code>classpath</code></title>
Classpath scanned for Exposed table definitions.

Defaults to the project's runtime classpath.
</def>
<def id="file-directory">
<title><code>fileDirectory</code></title>

Directory where migration scripts are stored.

Defaults to `"src/main/resources/db/migration"`.

</def>
<def>
<title><code>filePrefix</code></title>

Prefix used for migration script names.

Defaults to `"V"`.
</def>
<def>
<title><code>fileVersionFormat</code></title>

Version format used for migration script names. For supported values, see [version formats](#version-formats).

Defaults to a timestamp in the `yyyyMMddHHmmss` format.
</def>
<def>
<title><code>fileSeparator</code></title>

Separator used in migration script names.

Defaults to `"__"`.
</def>
<def>
<title><code>useUpperCaseDescription</code></title>

Whether the descriptive part of migration script names is converted to uppercase.

Defaults to `true`.
</def>
<def>
<title><code>fileExtension</code></title>

File extension used for migration scripts.

Defaults to `".sql"`.
</def>
</deflist>

Example:

```kotlin
exposed {
    migrations {
        // ...
        classpath = sourceSets.main.get().runtimeClasspath
        fileDirectory.set(layout.projectDirectory.dir("src/main/resources/db/migration"))
        filePrefix.set("V")
        fileVersionFormat.set(VersionFormat.TIMESTAMP_ONLY)
        fileSeparator.set("__")
        useUpperCaseDescription.set(true)
        fileExtension.set(".sql")
    }
}
```

## Version formats

The plugin supports the following `VersionFormat` values:
<deflist>
<def>
<title><code>TIMESTAMP_ONLY</code></title>
Include only the timestamp.

Example: `V20260417195521__CREATE_TABLE_USERS.sql`
</def>
<def>
<title><code>TIMESTAMP_WITHOUT_SECONDS</code></title>
Include only the timestamp without seconds.

Example: `V202604171955__CREATE_TABLE_USERS.sql`
</def>
<def>
<title><code>MAJOR_TIMESTAMP</code></title>
Include the major version and the timestamp.

Example: `V3_20260417195521__CREATE_TABLE_USERS.sql`
</def>
<def>
<title><code>MAJOR_TIMESTAMP_WITHOUT_SECONDS</code></title>
Include the major version and the timestamp without seconds.

Example: `V3_202604171955__CREATE_TABLE_USERS.sql`
</def>
<def>
<title><code>MAJOR_MINOR</code></title>
Include the major and the minor version.

Example: `V3_1__CREATE_TABLE_USERS.sql`
</def>
<def>
<title><code>MAJOR_ONLY</code></title>
Include the major version only.

Example: `V3__CREATE_TABLE_USERS.sql`
</def>
</deflist>

For version formats that include a major version, the plugin scans the configured `fileDirectory` to determine the
next available version. If the directory is empty, or if no compatible migration files are found, numbering starts at 1.

## File naming

By default, migration scripts use the following naming pattern:

```text
<prefix><version><separator><description><extension>
```

For example:

```text
V20260417195521__CREATE_TABLE_USERS.sql
```

The generated description (`CREATE_TABLE_USERS`) is derived from the generated SQL statement and
typically follows this format:

```text
<OPERATION>_<OBJECT>_<IDENTIFIER>_<EXTRA>
```

When a migration contains multiple SQL statements, the description is usually derived from the first significant
statement.

* A migration that creates two related tables typically uses the description of the first `CREATE TABLE` statement.
* If a sequence must be created before a table, the generated description still prefers the `CREATE TABLE` statement
  instead of `CREATE SEQUENCE`.

If the plugin cannot derive a standard description, it falls back to a generic name, such as `CUSTOM_STATEMENT_12345`.

### Override the generated filename

You can override the generated filename by passing the `--filename` argument to the `generateMigrations` task:

```shell
./gradlew generateMigrations --filename=V0__initialize_schema.sql
```

> When `--filename` is specified, the plugin generates a single migration script containing all migration statements,
> even if the schema diff affects multiple tables.
> 
{style="note"}

## Using `Testcontainers` {id="use-testcontainers"}

[`Testcontainers`](https://java.testcontainers.org/) is a Java library that lets you run temporary
[Docker](https://www.docker.com/) containers during tests or build tasks.
You can use `Testcontainers` to start a disposable database instance automatically while generating migration scripts.

> Docker must be installed and running to use `Testcontainers`.
>
{style="note"}

### `Testcontainers` workflow

When using `Testcontainers`, the Exposed Gradle plugin performs the following steps:

1. Starts a database container.
2. Applies existing migration scripts using [Flyway](https://documentation.red-gate.com/flyway).
3. Compares the resulting database schema with your Exposed table definitions.
4. Generates new migration scripts.
5. Stops the container.

If the configured migration directory contains existing migration scripts, the plugin applies them using Flyway before
generating new migrations.

This ensures that newly generated migration scripts are based on the latest schema state, including changes introduced
by previous migrations.

### Supported databases

The plugin supports the following database container images:

| Database   | Container images                                                                              |
|------------|-----------------------------------------------------------------------------------------------|
| MySQL      | `mysql`, `mysql:latest`, or other tags                                                        |
| MariaDB    | `mariadb`, `mariadb:latest` , or other tags                                                   |
| PostgreSQL | `postgres`, `postgres:latest` , or other tags                                                 |
| SQL Server | `mcr.microsoft.com/mssql/server`, `mcr.microsoft.com/mssql/server:2025-latest`, or other tags |
| Oracle     | Images starting with `container-registry.oracle.com/`,`gvenzl/oracle-` or `oracle/`           |




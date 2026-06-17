<show-structure for="chapter,procedure" depth="2"/>

# Exposed Maven plugin

<tldr>
    <p>
        <b>Required dependencies</b>: <code>org.jetbrains.exposed.plugin:exposed-maven-plugin</code>
    </p>
    <p>
        <b>Code example</b>: <a href="https://github.com/JetBrains/Exposed/tree/main/documentation-website/Writerside/snippets/exposed-maven-plugin">exposed-maven-plugin</a>
    </p>
</tldr>

The Exposed Maven plugin provides build-time tooling for working with Exposed-based database schemas in Maven projects.

Its primary feature is [generating SQL migration scripts](#generate-migration-scripts) by comparing Exposed
table definitions with an existing database schema.

## Requirements

* Kotlin 2.2 or later
* Maven 3.9 or later
* JVM 11 or later
* [Docker](https://www.docker.com/) (only required when [using `Testcontainers`](#use-testcontainers))

## Installation

To install the plugin, add it to the `<build><plugins>` section of your `pom.xml`:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.jetbrains.exposed.plugin</groupId>
            <artifactId>exposed-maven-plugin</artifactId>
            <version>%exposed_version%</version>
        </plugin>
    </plugins>
</build>
```

## Generate migration scripts

To generate migration scripts based on the difference between your existing database schema and your Exposed table
definitions, invoke the `generate-migration` goal:

```bash
mvn exposed:generate-migration
```

Generated files are written to the [configured output directory](#file-directory).

### Integration with the build lifecycle

You can bind migration generation to a Maven lifecycle phase by adding an `<executions>` block to the plugin
configuration. For example, to generate migrations during the `process-classes` phase:

```xml
<plugin>
    <groupId>org.jetbrains.exposed.plugin</groupId>
    <artifactId>exposed-maven-plugin</artifactId>
    <version>%exposed_version%</version>
    <executions>
        <execution>
            <id>generate-migration</id>
            <phase>process-classes</phase>
            <goals>
                <goal>generate-migration</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

## Configuration

Configure the plugin using the `<configuration>` block inside the plugin entry in your `pom.xml`.

At minimum, configure the following parameters:

* `tablesPackage` as the package name where Exposed table definitions are located.
* A database configuration or a `Testcontainers` configuration.

### Configure a database connection

To configure a database connection, set the `databaseUrl`, `databaseUser`, and `databasePassword` parameters:

```xml
<plugin>
    <groupId>org.jetbrains.exposed.plugin</groupId>
    <artifactId>exposed-maven-plugin</artifactId>
    <version>%exposed_version%</version>
    <configuration>
        <tablesPackage>com.example.db.tables</tablesPackage>
        <databaseUrl>jdbc:postgresql://localhost:5432/mydb</databaseUrl>
        <databaseUser>postgres</databaseUser>
        <databasePassword>password</databasePassword>
    </configuration>
</plugin>
```

### Configure `Testcontainers` {id="testcontainers-config"}

To configure a `Testcontainers` connection, set the `testContainersImageName` parameter:

```xml
<plugin>
    <groupId>org.jetbrains.exposed.plugin</groupId>
    <artifactId>exposed-maven-plugin</artifactId>
    <version>%exposed_version%</version>
    <configuration>
        <tablesPackage>com.example.db.tables</tablesPackage>
        <testContainersImageName>postgres:latest</testContainersImageName>
    </configuration>
</plugin>
```

> For more details and supported database container images, see [](#use-testcontainers).
>
{style="tip"}

> When `testContainersImageName` is configured, the plugin uses `Testcontainers` instead of a direct database
> connection for schema generation.
>
{style="note"}

### Override parameters from the command line

Every plugin parameter can be overridden on the command line using the matching `exposed.<name>` system property:

```bash
mvn exposed:generate-migration \
    -Dexposed.tablesPackage=com.example.db.tables \
    -Dexposed.databaseUrl=jdbc:postgresql://localhost:5432/mydb \
    -Dexposed.databaseUser=postgres \
    -Dexposed.databasePassword=password
```

## Additional configuration

Optionally, you can configure the following parameters for additional control over migration generation and file naming:

<deflist type="medium">
<def id="file-directory">
<title><code>fileDirectory</code></title>

Directory where migration scripts are stored.

Defaults to `src/main/resources/db/migration` under the project base directory.

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

```xml
<plugin>
    <groupId>org.jetbrains.exposed.plugin</groupId>
    <artifactId>exposed-maven-plugin</artifactId>
    <version>%exposed_version%</version>
    <configuration>
        <tablesPackage>com.example.db.tables</tablesPackage>
        <databaseUrl>jdbc:postgresql://localhost:5432/mydb</databaseUrl>
        <databaseUser>postgres</databaseUser>
        <databasePassword>password</databasePassword>

        <fileDirectory>${project.basedir}/src/main/resources/db/migration</fileDirectory>
        <filePrefix>V</filePrefix>
        <fileVersionFormat>TIMESTAMP_ONLY</fileVersionFormat>
        <fileSeparator>__</fileSeparator>
        <useUpperCaseDescription>true</useUpperCaseDescription>
        <fileExtension>.sql</fileExtension>
    </configuration>
</plugin>
```

## Version formats

The plugin supports the following `fileVersionFormat` values:

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

You can override the generated filename by passing the `exposed.filename` system property to the `generate-migration`
goal:

```bash
mvn exposed:generate-migration -Dexposed.filename=V0__initialize_schema.sql
```

> When `exposed.filename` is specified, the plugin generates a single migration script containing all migration
> statements, even if the schema diff affects multiple tables.
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

When using `Testcontainers`, the Exposed Maven plugin performs the following steps:

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

## Next steps

The Exposed Maven plugin generates migration scripts, but it does not apply them to your database automatically.

After generating migration scripts, review and apply them using your existing database migration workflow. For example,
you can:

* Apply migrations using tools such as [Flyway](https://www.red-gate.com/products/flyway/) or [Liquibase](https://www.liquibase.com/liquibase-secure).
* Execute scripts manually using your database client.
* Run scripts from the [IntelliJ IDEA Database tool window](https://www.jetbrains.com/help/idea/database-tool-window.html).
* Integrate migration execution into your CI/CD pipeline.

After applying the generated scripts, your database schema should match your current Exposed table definitions.

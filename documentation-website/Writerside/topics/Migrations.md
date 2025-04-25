<show-structure for="chapter,procedure" depth="2"/>

# Migrations

<tldr>
    <p>
        <b>Required dependencies</b>: <code>org.jetbrains.exposed:exposed-migrations</code>
    </p>
    <p>
        <b>Code example</b>: <a href="https://github.com/JetBrains/Exposed/tree/main/documentation-website/Writerside/snippets/exposed-migrations">exposed-migrations</a>
    </p>
</tldr>

Managing database schema changes is a critical part of application development. Exposed offers several tools to assist with schema migrations, allowing you to
evolve your database alongside your codebase.

While Exposed provides basic migration support through [`SchemaUtils`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/-schema-utils/index.html),
the [`MigrationUtils`](https://jetbrains.github.io/Exposed/api/exposed-migration/[root]/-migration-utils/index.html) methods from the `exposed-migration` package
provide a more structured and production-ready way to manage schema changes. They allow you to [inspect differences](#aligning-the-database-schema) between the current
database state and your defined table schema, and to generate or apply migration scripts accordingly.

## Adding dependencies

To use the methods provided by `MigrationUtils`, include the `exposed-migrations` artifact in your build script:

```Kotlin
implementation("org.jetbrains.exposed:exposed-migrations:%exposed_version%")
```

## Aligning the database schema

When you need to bring your database schema in line with your current Exposed table definitions, you have two options:

1. [Generate only missing column statements](#generate-missing-column-statements)
2. [Generate all required statements for database migration](#generate-all-required-statements)

### Generate missing column statements

If you only need the SQL statements that create any columns that are missing from the existing
tables in the database, use the
[`SchemaUtils.addMissingColumnsStatements()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/-schema-utils/add-missing-columns-statements.html)
function:

```Kotlin
```
{src="exposed-migrations/src/main/kotlin/org/example/App.kt" include-symbol="missingColStatements"}

<tip>
    <snippet id="sqlite-limitation-note">
        Some databases, like SQLite, only support <code>ALTER TABLE ADD COLUMN</code> under strict conditions. As a result, adding certain types of columns might fail
        silently or behave unexpectedly. Refer to the relevant database documentation for limitations.
    </snippet>
</tip>

### Generate all required statements

To compare your live database schema against your current Exposed table definitions and generate all statements
required to align the two, use the
[`MigrationUtils.statementsRequiredForDatabaseMigration()`](https://jetbrains.github.io/Exposed/api/exposed-migration/[root]/-migration-utils/statements-required-for-database-migration.html)
function:

```Kotlin
```
{src="exposed-migrations/src/main/kotlin/org/example/App.kt" include-symbol="statements"}

These statements may include `CREATE`, `ALTER`, and `DROP` operations—including potentially destructive actions like `DROP COLUMN` or `DELETE`, so review them
carefully before execution.

#### PostgreSQL

When running on PostgreSQL, the function also checks for inconsistencies between table definitions and sequences (especially those tied to `SERIAL` columns
on `IdTable`).

Sequences manually created with `CREATE SEQUENCE` and not linked to a table are ignored.

No `DROP` statements are generated for such sequences.

## Generating migration scripts

To generate a migration script based on schema differences between your database and the current Exposed model, use the
[`MigrationUtils.generateMigrationScript()`](https://jetbrains.github.io/Exposed/api/exposed-migration/[root]/-migration-utils/generate-migration-script.html)
function:

```Kotlin
```
{src="exposed-migrations/src/main/kotlin/org/example/GenerateMigrationScript.kt" include-lines="35-39"}

This method allows you to see what the migration script will look like before applying the migration. If a migration script with the same name already exists,
its content will be overwritten.

## Legacy columns cleanup

As your schema evolves, it's common to remove or rename columns in your table definitions. However, old columns may still exist in the database unless
explicitly dropped.

The [`MigrationUtils.dropUnmappedColumnsStatements()`](https://jetbrains.github.io/Exposed/api/exposed-migration/[root]/-migration-utils/drop-unmapped-columns-statements.html)
function helps identify columns that are no longer present in your current table definitions and returns the SQL statements to remove them:

```Kotlin
```
{src="exposed-migrations/src/main/kotlin/org/example/App.kt" include-symbol="dropStatements"}


## Logging

By default, each method provided by `MigrationUtils` logs descriptions and the execution time of each intermediate step. These logs are emitted at the `INFO` 
level and can be disabled by setting `withLogs` to `false`:

```Kotlin
```
{src="exposed-migrations/src/main/kotlin/org/example/App.kt" include-lines="57-60"}

## Limitations

While Exposed’s migration tools are powerful, there are some limitations:

- You must still implement and manage your own migration flow.
- No automatic migration application is provided — scripts must be executed manually or integrated into your deployment process.
- Some database-specific behaviors, such as SQLite’s limited `ALTER TABLE` support, may lead to partial or failed migrations if not reviewed.
- Destructive operations like `DROP COLUMN` or `DROP SEQUENCE` can be included — caution is advised.

We recommend that you always manually review generated diffs or scripts before applying them to a live database.

### Feature requests

Currently, Exposed does not offer a Maven plugin or Liquibase integration — share your interest to help shape future support:

- [Upvote or comment on the Maven plugin feature request](https://youtrack.jetbrains.com/issue/EXPOSED-758/Create-a-migration-plugin-for-Maven-build-tool)
- [Join the discussion for Liquibase extension support](https://youtrack.jetbrains.com/issue/EXPOSED-757/Allow-use-of-migration-plugin-with-Liquibase)

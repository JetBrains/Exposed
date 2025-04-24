<show-structure for="chapter,procedure" depth="2"/>

# Migrations

Managing database schema changes is a critical part of application development. Exposed offers several tools to assist with schema migrations, allowing you to
evolve your database alongside your codebase.

## Minimal migration with SchemaUtils

The [`SchemaUtils.addMissingColumnsStatements()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/-schema-utils/add-missing-columns-statements.html)
function returns the SQL statements that create any columns defined in tables that are missing from the existing
tables in the database:

```Kotlin
```
{src="exposed-migrations/src/main/kotlin/org/example/App.kt" include-symbol="missingColStatements"}

<tip>
    <snippet id="sqlite-limitation-note">
        Some databases, like SQLite, only support <code>ALTER TABLE ADD COLUMN</code> under strict conditions. As a result, adding certain types of columns might fail
        silently or behave unexpectedly. Refer to the relevant database documentation for limitations.
    </snippet>
</tip>

While Exposed provides basic migration support through `SchemaUtils`, the `exposed-migration` package provides a more structured and production-ready approach to
handling schema changes.

## Custom migration with MigrationUtils

The `exposed-migration` package allows you to inspect differences between the current database state and your defined table schema, and to generate or apply
migration scripts accordingly.

### Add dependencies

To use the tools provided by `exposed-migrations`, include the following artifact in your build script:

```Kotlin
implementation("org.jetbrains.exposed:exposed-migrations:%exposed_version%")
```

### Aligning the database schema

When you need to bring your database schema in line with your current Exposed table definitions, use the
[`.statementsRequiredForDatabaseMigration()`](https://jetbrains.github.io/Exposed/api/exposed-migration/[root]/-migration-utils/statements-required-for-database-migration.html)
function:

```Kotlin
```
{src="exposed-migrations/src/main/kotlin/org/example/App.kt" include-symbol="statements"}

This function compares your live database schema against your current Exposed table definitions and generates only the statements required to align the two.

These statements may include `CREATE`, `ALTER`, and `DROP` operations—including potentially destructive actions like `DROP COLUMN` or `DELETE`, so review them
carefully before execution.

#### PostgreSQL

When running on PostgreSQL, the function also checks for inconsistencies between table definitions and sequences (especially those tied to `SERIAL` columns
on `IdTable`).

Sequences manually created with `CREATE SEQUENCE` and not linked to a table are ignored.

No `DROP` statements are generated for such sequences.

<tip>
    <include from="Migrations.md" element-id="sqlite-limitation-note"></include>
</tip>

### Generate migration scripts

To generate a migration script based on schema differences between your database and the current Exposed model, use the
[`.generateMigrationScript()`](https://jetbrains.github.io/Exposed/api/exposed-migration/[root]/-migration-utils/generate-migration-script.html)
function:

```Kotlin
```
{src="exposed-migrations/src/main/kotlin/org/example/GenerateMigrationScript.kt" include-lines="35-39"}

This method allows you to see what the migration script will look like before applying the migration.

If a migration script with the same name already exists, its content will be overwritten.

### Clean up legacy columns

As your schema evolves, it's common to remove or rename columns in your Exposed definitions. However, old columns may still exist in the database unless
explicitly dropped.

The [`.dropUnmappedColumnsStatements()`](https://jetbrains.github.io/Exposed/api/exposed-migration/[root]/-migration-utils/drop-unmapped-columns-statements.html)
function helps identify columns that are no longer present in your current table definitions and returns the SQL statements to remove them:

```Kotlin
```
{src="exposed-migrations/src/main/kotlin/org/example/App.kt" include-symbol="dropStatements"}


## Logging

By default, each method provided by `exposed-migration` logs descriptions and the execution time of each intermediate step. These logs are emitted at the `INFO` 
level and can be disabled by setting `withLogs` to `false`:

```Kotlin
```
{src="exposed-migrations/src/main/kotlin/org/example/App.kt" include-lines="57-60"}

## Limitations

While Exposed’s migration tools are powerful, there are some limitations. Column renames, complex constraint changes, and type transformations are not yet supported.

We recommend that you always manually review generated diffs or scripts before applying them to a live database.

### Feature requests

Currently, Exposed does not offer a Maven plugin or Liquibase integration — share your interest to help shape future support:

- [Upvote or comment on the Maven plugin feature request](https://youtrack.jetbrains.com/issue/EXPOSED-758/Create-a-migration-plugin-for-Maven-build-tool)
- [Join the discussion for Liquibase extension support](https://youtrack.jetbrains.com/issue/EXPOSED-757/Allow-use-of-migration-plugin-with-Liquibase)

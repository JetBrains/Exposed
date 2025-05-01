<show-structure for="chapter,procedure" depth="2"/>

# Migrations

<tldr>
    <p>
        <b>Required dependencies</b>: <code>org.jetbrains.exposed:exposed-migration</code>
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

To use the methods provided by `MigrationUtils`, include the `exposed-migration` artifact in your build script:

```Kotlin
implementation("org.jetbrains.exposed:exposed-migration:%exposed_version%")
```

## Aligning the database schema

When you need to bring your database schema in line with your current Exposed table definitions, you have two options:

1. [Generate only missing column statements](#generate-missing-column-statements){summary="Use this method to get exact control over which part of the schema you want to align"}
2. [Generate all required statements for database migration](#generate-all-required-statements){summary="Use this method as a validation check before actual migration"}
3. [Generate a migration script](#generate-a-migration-script){summary="Use this method for a more hands-off approach that also allows you to control the content of the script before integrating your own migration "}

### Generate missing column statements

If you only need the SQL statements that create any columns that are missing from the existing
tables in the database, use the
[`SchemaUtils.addMissingColumnsStatements()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/-schema-utils/add-missing-columns-statements.html)
function:

```Kotlin
```
{src="exposed-migrations/src/main/kotlin/org/example/App.kt" include-symbol="missingColStatements"}

This function returns a collection of string SQL statements, ensuring that any column-associated constraints are aligned. As it adds missing columns, it
simultaneously adds any associated constraints such as primary keys, indexes, and foreign keys that may be absent.

> For database-specific constraints, see the [limitations](#limitations) section.

### Generate all required statements

To compare your live database schema against your current Exposed table definitions and generate all statements
required to align the two, use the
[`MigrationUtils.statementsRequiredForDatabaseMigration()`](https://jetbrains.github.io/Exposed/api/exposed-migration/[root]/-migration-utils/statements-required-for-database-migration.html)
function:

```Kotlin
```
{src="exposed-migrations/src/main/kotlin/org/example/App.kt" include-symbol="statements"}

The returned collection of string SQL statements may include `CREATE`, `ALTER`, and `DROP` operations—including potentially destructive actions like `DROP COLUMN`
or `DELETE`, so review them carefully before choosing to execute them.

> For database-specific constraints, see the [limitations](#limitations) section.

### Generate a migration script

To generate a migration script based on schema differences between your database and the current Exposed model, use the
[`MigrationUtils.generateMigrationScript()`](https://jetbrains.github.io/Exposed/api/exposed-migration/[root]/-migration-utils/generate-migration-script.html)
function:

```Kotlin
```
{src="exposed-migrations/src/main/kotlin/org/example/GenerateMigrationScript.kt" include-lines="35-39"}

This method allows you to see what the migration script will look like before applying the migration. If a migration script with the same name already exists,
its content will be overwritten.

## Validating the database schema

Before applying any migrations, it’s useful to validate that your Exposed schema definitions match the actual state of the database. While the primary use of
schema alignment methods is to generate SQL statements and migration scripts, these same methods can also serve as pre-checks—especially when used to detect
unexpected changes.

Exposed provides several low-level APIs that support schema validation and can be integrated into custom migration or deployment pipelines. These methods are also
used internally by Exposed to generate migration statements, but you can also use them for more precise checks.

### Check for existence of database object

To determine if a specific database object is already present, use one of the following methods:

- [`Table.exists()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/exists.html)
- [`Sequence.exists()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/-sequence/exists.html)
- [`Schema.exists()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/-schema/exists.html)

### Structural integrity checks

To evaluate whether a table has excessive indices or foreign keys, which might indicate schema drift or duplication, use one of the following `SchemaUtils` methods:

- [`SchemaUtils.checkExcessiveIndices()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/-schema-utils/check-excessive-indices.html)
- [`SchemaUtils.checkExcessiveForeignKeyConstraints()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/-schema-utils/check-excessive-foreign-key-constraints.html)

### Database metadata inspection

To retrieve metadata from the current dialect to compare with your defined Exposed schema, use one of the following `currentDialect` methods:

- [`currentDialect.tableColumns()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql.vendors/-database-dialect/table-columns.html)
- [`currentDialect.existingIndices()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql.vendors/-database-dialect/existing-indices.html)
- [`currentDialect.existingPrimaryKeys()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql.vendors/-database-dialect/existing-primary-keys.html)

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
- Automatic migration execution is not provided — scripts must be run manually or integrated into your deployment process. This limitation is already addressed in the 
[Gradle migration plugin feature request](#gradle-plugin).
> For an example of manual execution
> with Flyway, see the [`exposed-migrations` sample project](https://github.com/JetBrains/Exposed/tree/main/documentation-website/Writerside/snippets/exposed-migrations).
- Some database-specific behaviors, such as SQLite’s limited `ALTER TABLE` support, may lead to partial or failed migrations if not reviewed.
- Destructive operations like `DROP COLUMN` or `DROP SEQUENCE` can be included — caution is advised.

We recommend that you always manually review generated diffs or scripts before applying them to a live database.

### SQLite

SQLite has strict limitations around the `ALTER TABLE ADD COLUMN` statement. For example, it does not allow adding a new column without a 
default value under certain conditions. Since Exposed cannot account for all of SQLite’s specific constraints, it will still generate the expected SQL statement. 
It is up to you to review the generated SQL and avoid attempting migrations that are incompatible with SQLite’s rules. If such a statement is executed, it will
fail at runtime.

> For more information on this restriction, refer to the [SQLite documentation](https://www.sqlite.org/lang_altertable.html#alter_table_add_column). 

### PostgreSQL

When running on PostgreSQL, the functions to align the database schema also check for inconsistencies between table definitions and sequences (especially those tied
to `SERIAL` columns on `IdTable`).

Sequences manually created with `CREATE SEQUENCE` and not linked to a table are ignored. No `DROP` statements are generated for such sequences.

## Feature requests

### Gradle plugin

A Gradle plugin to simplify SQL migrations is in development. A proposed design for Flyway integration has been presented and is actively being implemented. To show
interest or get involved, see the [YouTrack issue for creating the migration Gradle plugin](https://youtrack.jetbrains.com/issue/EXPOSED-755/Create-a-migration-Gradle-plugin).

### Maven and Liquibase integration

Exposed does not currently offer a Maven plugin or Liquibase integration — share your interest to help shape future support:

- [Upvote or comment on the Maven plugin feature request](https://youtrack.jetbrains.com/issue/EXPOSED-758/Create-a-migration-plugin-for-Maven-build-tool)
- [Join the discussion for Liquibase extension support](https://youtrack.jetbrains.com/issue/EXPOSED-757/Allow-use-of-migration-plugin-with-Liquibase)

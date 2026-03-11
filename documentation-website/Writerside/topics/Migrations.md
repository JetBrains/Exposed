<show-structure for="chapter,procedure" depth="2"/>

# Migrations

<tldr>
    <p>
        <b>Required dependencies</b>: <code>org.jetbrains.exposed:exposed-migration-core</code> and
        <code>org.jetbrains.exposed:exposed-migration-jdbc</code> (JDBC) or 
        <code>org.jetbrains.exposed:exposed-migration-r2dbc</code> (R2DBC)
    </p>
    <include from="lib.topic" element-id="jdbc-supported"/>
    <include from="lib.topic" element-id="r2dbc-supported"/>
    <p>
        <b>Code example</b>: <a href="https://github.com/JetBrains/Exposed/tree/main/documentation-website/Writerside/snippets/exposed-migrations">exposed-migrations</a>
    </p>
</tldr>

Managing database schema changes is a critical part of application development. Exposed offers several tools to help with schema migrations, allowing you to
evolve your database alongside your codebase.

While Exposed provides basic migration support through `SchemaUtils`,
the `MigrationUtils` methods from either the `exposed-migration-jdbc` or `exposed-migration-r2dbc` packages
provide a more structured and production-ready way to manage schema changes. They allow you to [inspect differences](#aligning-the-database-schema) between the current
database state and your defined table schema and to generate or apply migration scripts accordingly.

## Adding dependencies

To use the methods provided by `MigrationUtils`, include the following dependencies in your build script:

* `exposed-migration-core`, containing core common functionality for database schema migrations.
* A dependency for migration support with either a JDBC or R2DBC driver.

<tabs group="connectivity">
   <tab id="jdbc-dependencies" title="JDBC" group-key="jdbc">
     <code-block lang="kotlin">
         implementation("org.jetbrains.exposed:exposed-migration-core:%exposed_version%")
         implementation("org.jetbrains.exposed:exposed-migration-jdbc:%exposed_version%")
     </code-block>
   </tab>
   <tab id="r2dbc-dependencies" title="R2DBC" group-key="r2dbc">
      <code-block lang="kotlin">
         implementation("org.jetbrains.exposed:exposed-migration-core:%exposed_version%")
         implementation("org.jetbrains.exposed:exposed-migration-r2dbc:%exposed_version%")
      </code-block>
    </tab>
</tabs>

<note>
Prior to version 1.0.0, <code>MigrationUtils</code> with JDBC support was available through a single dependency on
the artifact <code>exposed-migration</code>.
</note>

## Aligning the database schema

When you need to bring your database schema in line with your current Exposed table definitions, you have three options:

1. [Generate only missing column statements](#generate-missing-column-statements){summary="Use this method to get exact control over which part of the schema you want to align"}
2. [Generate all required statements for database migration](#generate-all-required-statements){summary="Use this method as a validation check before actual migration"}
3. [Generate a migration script](#generate-a-migration-script){summary="Use this method for a more hands-off approach that also allows you to control the content of the script before integrating your own migration "}

### Generate missing column statements

<tldr>
    <p>API references:
        <a href="https://jetbrains.github.io/Exposed/api/exposed-jdbc/org.jetbrains.exposed.v1.jdbc/-schema-utils/add-missing-columns-statements.html">
            <code>addMissingColumnsStatements</code> (JDBC)
        </a>,
        <a href="https://jetbrains.github.io/Exposed/api/exposed-r2dbc/org.jetbrains.exposed.v1.r2dbc/-schema-utils/add-missing-columns-statements.html">
            <code>addMissingColumnsStatements</code> (R2DBC)
        </a>
    </p>
</tldr>

If you only need the SQL statements that create any columns that are missing from the existing
tables in the database, use the `SchemaUtils.addMissingColumnsStatements()` function:

```Kotlin
```
{src="exposed-migrations/src/main/kotlin/org/example/App.kt" include-symbol="missingColStatements"}

This function returns a collection of string SQL statements, ensuring that any column-associated constraints are aligned. As it adds missing columns, it
simultaneously adds any associated constraints such as primary keys, indexes, and foreign keys that may be absent.

> For database-specific constraints, see the [limitations](#limitations) section.

### Generate all required statements

<tldr>
    <p>API references:
        <a href="https://jetbrains.github.io/Exposed/api/exposed-migration-jdbc/org.jetbrains.exposed.v1.migration.jdbc/-migration-utils/statements-required-for-database-migration.html">
            <code>statementsRequiredForDatabaseMigration</code> (JDBC)
        </a>,
        <a href="https://jetbrains.github.io/Exposed/api/exposed-migration-r2dbc/org.jetbrains.exposed.v1.migration.r2dbc/-migration-utils/statements-required-for-database-migration.html">
            <code>statementsRequiredForDatabaseMigration</code> (R2DBC)
        </a>
    </p>
</tldr>

To compare your live database schema against your current Exposed table definitions and generate all statements
required to align the two, use the `MigrationUtils.statementsRequiredForDatabaseMigration()` function:

```Kotlin
```
{src="exposed-migrations/src/main/kotlin/org/example/App.kt" include-symbol="statements"}

The returned collection of string SQL statements may include `CREATE`, `ALTER`, and `DROP` operations — including potentially destructive actions like `DROP COLUMN`
or `DELETE`, so review them carefully before choosing to execute them.

> For database-specific constraints, see the [limitations](#limitations) section.

### Generate a migration script

<tldr>
    <p>API references:
        <a href="https://jetbrains.github.io/Exposed/api/exposed-migration-jdbc/org.jetbrains.exposed.v1.migration.jdbc/-migration-utils/generate-migration-script.html">
            <code>generateMigrationScript</code> (JDBC)
        </a>,
        <a href="https://jetbrains.github.io/Exposed/api/exposed-migration-r2dbc/org.jetbrains.exposed.v1.migration.r2dbc/-migration-utils/generate-migration-script.html">
            <code>generateMigrationScript</code> (R2DBC)
        </a>
    </p>
</tldr>

To generate a migration script based on schema differences between your database and the current Exposed model, use the
`MigrationUtils.generateMigrationScript()` function:

```Kotlin
```
{src="exposed-migrations/src/main/kotlin/org/example/GenerateMigrationScript.kt" include-lines="36-40"}

This method allows you to see what the migration script will look like before applying the migration. If a migration script with the same name already exists,
its content will be overwritten.

## Validating the database schema

Before applying any migrations, it's useful to validate that your Exposed schema definitions match the actual state of the database. While the primary use of
schema alignment methods is to generate SQL statements and migration scripts, these same methods can also serve as pre-checks — especially when used to detect
unexpected changes.

Exposed provides several low-level APIs that support schema validation and can be integrated into custom migration or deployment pipelines. These methods are also
used internally by Exposed to generate migration statements, but you can also use them for more precise checks.

### Check for existence of a database object

<tldr>
    <p>API references:
        <a href="https://jetbrains.github.io/Exposed/api/exposed-jdbc/org.jetbrains.exposed.v1.jdbc/exists.html">
            <code>exists</code> (JDBC)
        </a>
        <a href="https://jetbrains.github.io/Exposed/api/exposed-r2dbc/org.jetbrains.exposed.v1.r2dbc/exists.html">
            <code>exists</code> (R2DBC)
        </a>
    </p>
</tldr>

To determine if a specific database object is already present, use the `.exists()` method on a `Table`, `Sequence`, or
`Schema`.

### Structural integrity checks

To evaluate whether a table has excessive indices or foreign keys, which might indicate schema drift or duplication, use one of the following `SchemaUtils` methods:

- `SchemaUtils.checkExcessiveIndices()` ([JDBC](https://jetbrains.github.io/Exposed/api/exposed-jdbc/org.jetbrains.exposed.v1.jdbc/-schema-utils/check-excessive-indices.html),
  [R2DBC](https://jetbrains.github.io/Exposed/api/exposed-r2dbc/org.jetbrains.exposed.v1.r2dbc/-schema-utils/check-excessive-indices.html))
- `SchemaUtils.checkExcessiveForeignKeyConstraints()` (
  [JDBC](https://jetbrains.github.io/Exposed/api/exposed-jdbc/org.jetbrains.exposed.v1.jdbc/-schema-utils/check-excessive-foreign-key-constraints.html),
  [R2DBC](https://jetbrains.github.io/Exposed/api/exposed-r2dbc/org.jetbrains.exposed.v1.r2dbc/-schema-utils/check-excessive-foreign-key-constraints.html))

### Database metadata inspection

To retrieve metadata from the current dialect to compare with your defined Exposed schema, use one of the following `currentDialectMetadata` methods:

- `currentDialectMetadata.tableColumns()` ([JDBC](https://jetbrains.github.io/Exposed/api/exposed-jdbc/org.jetbrains.exposed.v1.jdbc.vendors/-database-dialect-metadata/table-columns.html),
  [R2DBC](https://jetbrains.github.io/Exposed/api/exposed-r2dbc/org.jetbrains.exposed.v1.r2dbc.vendors/-database-dialect-metadata/table-columns.html))
- `currentDialectMetadata.existingIndices()` ([JDBC](https://jetbrains.github.io/Exposed/api/exposed-jdbc/org.jetbrains.exposed.v1.jdbc.vendors/-database-dialect-metadata/existing-indices.html),
  [R2DBC](https://jetbrains.github.io/Exposed/api/exposed-r2dbc/org.jetbrains.exposed.v1.r2dbc.vendors/-database-dialect-metadata/existing-indices.html))
- `currentDialectMetadata.existingPrimaryKeys()` ([JDBC](https://jetbrains.github.io/Exposed/api/exposed-jdbc/org.jetbrains.exposed.v1.jdbc.vendors/-database-dialect-metadata/existing-primary-keys.html),
  [R2DBC](https://jetbrains.github.io/Exposed/api/exposed-r2dbc/org.jetbrains.exposed.v1.r2dbc.vendors/-database-dialect-metadata/existing-primary-keys.html))

## Legacy columns cleanup

<tldr>
    <p>API references:
        <code>dropUnmappedColumnsStatements</code> (
        <a href="https://jetbrains.github.io/Exposed/api/exposed-migration-jdbc/org.jetbrains.exposed.v1.migration.jdbc/-migration-utils/drop-unmapped-columns-statements.html">
             JDBC
        </a>,
        <a href="https://jetbrains.github.io/Exposed/api/exposed-migration-r2dbc/org.jetbrains.exposed.v1.migration.r2dbc/-migration-utils/drop-unmapped-columns-statements.html">
            R2DBC
        </a>),
        <code>dropUnmappedIndices</code> (
        <a href="https://jetbrains.github.io/Exposed/api/exposed-migration-jdbc/org.jetbrains.exposed.v1.migration.jdbc/-migration-utils/drop-unmapped-indices.html">
             JDBC
        </a>,
        <a href="https://jetbrains.github.io/Exposed/api/exposed-migration-r2dbc/org.jetbrains.exposed.v1.migration.r2dbc/-migration-utils/drop-unmapped-indices.html">
            R2DBC
        </a>),
        <code>dropUnmappedSequences</code>(
        <a href="https://jetbrains.github.io/Exposed/api/exposed-migration-jdbc/org.jetbrains.exposed.v1.migration.jdbc/-migration-utils/drop-unmapped-sequences.html">
             JDBC
        </a>,
        <a href="https://jetbrains.github.io/Exposed/api/exposed-migration-r2dbc/org.jetbrains.exposed.v1.migration.r2dbc/-migration-utils/drop-unmapped-sequences.html">
            R2DBC
        </a>)
    </p>
</tldr>

As your schema evolves, it's common to remove or rename columns in your table definitions. However, old columns may still
exist in the database unless explicitly dropped.

The `MigrationUtils.dropUnmappedColumnsStatements()` function helps identify columns that are no longer present in your
current table definitions and returns the SQL statements to remove them:

```Kotlin
```
{src="exposed-migrations/src/main/kotlin/org/example/App.kt" include-symbol="dropStatements"}

For indices and sequences, you can use the `MigrationUtils.dropUnmappedIndices()` and
`MigrationUtils.dropUnmappedSequences()` methods.

## Logging

By default, each method provided by `MigrationUtils` logs descriptions and the execution time of each intermediate step. These logs are emitted at the `INFO` 
level and can be disabled by setting `withLogs` to `false`:

```Kotlin
```
{src="exposed-migrations/src/main/kotlin/org/example/App.kt" include-lines="57-60"}

## Limitations

While Exposed's migration tools are powerful, there are some limitations:

- You must still implement and manage your own migration flow.
- Automatic migration execution is not provided — scripts must be run manually or integrated into your deployment process. This limitation is already addressed in the 
[Gradle migration plugin feature request](#gradle-plugin).
> For an example of manual execution
> with Flyway, see the [`exposed-migrations` sample project](https://github.com/JetBrains/Exposed/tree/main/documentation-website/Writerside/snippets/exposed-migrations).
- Some database-specific behaviors, such as SQLite's limited `ALTER TABLE` support, may lead to partial or failed migrations if not reviewed.
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

### Constraint change detection

Any detected changes to table and column constraints generally result in the generation of `DROP` and `CREATE` / `ALTER` statement pairs.
The type of change that generates these migration statements depends on the type of constraint:

- [`ForeignKeyConstraint`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.core/-foreign-key-constraint/index.html) detects mismatches in name, update rule, or delete rule.
- [`Index`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.core/-index/index.html) detects mismatches in name, uniqueness, or columns involved. Differences in index type, index function, or filter conditions will not be detected.
- [`CheckConstraint`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.core/-check-constraint/index.html) detects mismatches in name only. Differences in the boolean expression or condition used by this constraint will not be detected.

### Column change detection

A table's column can have multiple defining properties that need to be evaluated by Exposed's migration tools.
Column changes are determined by [`ColumnDiff`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.core/-column-diff/index.html).

Column properties, such as nullability, autoincrement status, and comments,
are compared directly and result in appropriate migration statements.

The following column properties have limitations in how changes are detected:

* [Name](#column-name)
* [Default values](#default-values)
* [Type](#type)
* [Size and scale](#size-and-scale)

####  Name {id="column-name"}

Renaming a column typically results in a pair of statements that add the new column and drop the old one.

Changes to a name's case sensitivity are usually ignored unless the database does not auto-fold identifiers or the name
is quoted.

SQLite, for example, is a database for which an `ALTER... RENAME` statement will be specifically generated if a
difference in case sensitivity is found.

#### Default values {id="default-values"}

Only primitive default values are reliably detected. The detection of changes to default expressions or functions may
not be guaranteed.

Additionally, any column marked with [`.databaseGenerated()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.core/-table/database-generated.html)
will have its default values excluded from the check to ensure that potential database-side defaults are not incorrectly removed.

#### Type {id="type"}

Full support for column type changes is currently only available when using H2.

#### Size and scale {id="size-and-scale"}

Detection applies only to column types that support these values, such as `DECIMAL` and `CHAR`.

#### Custom column definitions

It is also possible to configure a column's definition on table creation by marking it with [`.withDefinition()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.core/-table/with-definition.html),
which accepts any combination of strings and expressions to append to the SQL column syntax.

However, these custom definitions are not used when comparing the Exposed table object with database metadata. For a 
more reliable migration workflow, prefer more definitive column methods whenever possible.

For example, if your database supports column comments on table creation, marking a table column using `.withDefinition("COMMENT '...'")` and then changing the
comment string value in the future will not trigger a migration statement. If you use the [`.comment("...")`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.core/-table/comment.html)
method instead, the string value will be properly compared with the comment retrieved from the database.

## Feature requests

### Gradle plugin

A Gradle plugin to simplify SQL migrations is in development. A proposed design for Flyway integration has been presented and is actively being implemented. To show
interest or get involved, see the [YouTrack issue for creating the migration Gradle plugin](https://youtrack.jetbrains.com/issue/EXPOSED-755/Create-a-migration-Gradle-plugin).

### Maven and Liquibase integration

Exposed does not currently offer a Maven plugin or Liquibase integration — share your interest to help shape future support:

- [Upvote or comment on the Maven plugin feature request](https://youtrack.jetbrains.com/issue/EXPOSED-758/Create-a-migration-plugin-for-Maven-build-tool)
- [Join the discussion for Liquibase extension support](https://youtrack.jetbrains.com/issue/EXPOSED-757/Allow-use-of-migration-plugin-with-Liquibase)

<show-structure for="chapter,procedure" depth="2"/>

# Generate table definitions

<tldr>
    <p>
        <b>Required dependencies</b>: <code>org.jetbrains.exposed.plugin</code>
    </p>
</tldr>

The Exposed Gradle plugin provides build-time tooling for working with Exposed-based database schemas.

This topic describes how to generate Exposed table definitions from the tables of an existing database schema. To
generate SQL migration scripts from Exposed table definitions instead, see [](generate-migration-scripts.md).

## Requirements

* Kotlin 2.2 or later
* Gradle 8.14 or later ([Gradle installation guide](https://docs.gradle.org/current/userguide/installation.html))
* JVM 11 or later

## Installation

To install the plugin, add it to the `plugins` block in your Gradle build script:

```kotlin
plugins {
  id("org.jetbrains.exposed.plugin") version "%exposed_version%"
}
```

## Generate table definitions

To generate Exposed table definitions from the tables of an existing database, use the `generateTables` task:

```bash
./gradlew generateTables
```

The task connects to the configured database, reads the metadata of every table in the connection's current schema
(or in the [configured schemas](#tables-schemas)), and writes one Kotlin source file per table to the
[configured source directory](#tables-file-directory), in the sub-directory that matches the
[configured package](#tables-package).

For example, the following table:

```sql
CREATE TABLE USERS (
    ID INT AUTO_INCREMENT PRIMARY KEY,
    EMAIL VARCHAR(320) NOT NULL,
    AGE INT NULL
);
CREATE UNIQUE INDEX USERS_EMAIL_UNIQUE ON USERS (EMAIL);
```

results in the following generated file:

```kotlin
package com.example.db.tables

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table

object Users : Table("USERS") {
    val id: Column<Int> = integer("ID").autoIncrement()

    val email: Column<String> = varchar("EMAIL", 320).uniqueIndex()

    val age: Column<Int?> = integer("AGE").nullable()

    override val primaryKey: Table.PrimaryKey = PrimaryKey(id)
}
```

> The task never overwrites an existing file. If a file with the name of a generated object (or the file passed with
> `--filename`) already exists in the output package, the task fails and names the file, whether or not that file
> declares the object. Move or rename the file, or set `objectNameSuffix`, and run the task again.
> Review the generated definitions and commit them to your project like any other source file.
>
{style="note"}

### Generate a single file

By default, each table is written to its own file, named after the generated object.
To write all table definitions to a single file instead, pass the `--filename` argument to the `generateTables` task:

```shell
./gradlew generateTables --filename=Schema.kt
```

## Configuration

Configure table generation using the `exposed.tables` block in your <path>build.gradle.kts</path> file.

At minimum, configure the following properties:

* `tablesPackage` (as the package name in which the generated table definitions are declared).
* A database configuration.

### Configure the table package name {id="tables-package"}

To configure the package in which the generated table definitions are declared, set the `tablesPackage` property.
Generated files are written to the matching sub-directory of the [configured source directory](#tables-file-directory):

```kotlin
exposed {
    tables {
        tablesPackage = "com.example.db.tables"
        // ...
    }
}
```

> If the same `tablesPackage` is configured for both the `tables` and the `migrations` blocks,
> the generated table definitions can be used directly as the input of the `generateMigrations` task.
>
{style="tip"}

### Configure a database connection

To configure the database whose current schema should be read, set the `databaseUrl`, `databaseUser`, and
`databasePassword` properties:

```kotlin
exposed {
    tables {
        tablesPackage = "com.example.db.tables"
        databaseUrl = "jdbc:postgresql://localhost:5432/mydb"
        databaseUser = "postgres"
        databasePassword = "password"
    }
}
```

## Additional configuration

Optionally, you can configure the following properties for additional control over which tables are read and how they
are generated:

<deflist type="medium">
<def id="tables-file-directory">
<title><code>fileDirectory</code></title>

Root source directory where generated files are written.

Defaults to `"src/main/kotlin"`.
</def>
<def id="tables-schemas">
<title><code>schemas</code></title>

Names or glob patterns of the database schemas in which tables are searched. In a pattern, `*` matches any sequence
of characters and `?` matches a single character; any other character, including `_`, matches literally.
Names are matched as given and in the database's default identifier case, so `sales*` matches `SALES` and
`SALES_ARCHIVE` on H2. System schemas, such as `INFORMATION_SCHEMA`, are never searched, so `listOf("*")` searches
every user schema.

Tables found outside the connection's current schema are generated with a schema-qualified table name and an object
name prefixed with the schema name, for example `object SalesOrders : Table("SALES.ORDERS")`.

Defaults to an empty list, which searches only the connection's current schema.
</def>
<def id="tables-object-name-suffix">
<title><code>objectNameSuffix</code></title>

Suffix appended to the name of every generated table object, and therefore to its file name.
For example, `"Table"` generates `object UsersTable : Table("USERS")` in <path>UsersTable.kt</path>
from a database table named `USERS`. The table name passed to the `Table` constructor is not affected.

Defaults to `""`.
</def>
<def id="tables-uuid-column-type">
<title><code>uuidColumnType</code></title>

Which Exposed column function a database `UUID` column is mapped to:
`UuidColumnType.KOTLIN` generates `uuid()` columns of `kotlin.uuid.Uuid`, and
`UuidColumnType.JAVA` generates `javaUUID()` columns of `java.util.UUID`.

Defaults to `UuidColumnType.KOTLIN`.
</def>
<def id="tables-date-time-column-type">
<title><code>dateTimeColumnType</code></title>

Which Exposed date/time module the functions for date/time columns are taken from:
`DateTimeColumnType.KOTLIN_DATETIME` (`exposed-kotlin-datetime`), `DateTimeColumnType.JAVA_TIME` (`exposed-java-time`),
or `DateTimeColumnType.JODA_TIME` (`exposed-jodatime`).

When not set, the module is detected on the [configured classpath](#tables-classpath). If none is found there,
`exposed-kotlin-datetime` is used; if several are found, the first in the order above is used. The choice is logged.
If the module the generated definitions use is not on the classpath, a warning names it, as for any other Exposed
module they depend on.
</def>
<def id="tables-classpath">
<title><code>classpath</code></title>

Classpath scanned for an Exposed date/time module when `dateTimeColumnType` is not set.

Defaults to the project's runtime classpath.
</def>
<def id="tables-column-mappings">
<title><code>columnMappings</code></title>

Exposed code to generate for particular columns in place of the generated mapping, see
[](#map-columns-by-configuration).

Defaults to an empty list.
</def>
<def id="tables-generate-id-tables">
<title><code>generateIdTables</code></title>

Whether tables with a primary key are generated as `IdTable` subclasses instead of plain `Table` objects, see
[](#generate-id-tables).

Defaults to `false`.
</def>
</deflist>

Example:

```kotlin
import org.jetbrains.exposed.v1.plugin.core.tables.DateTimeColumnType
import org.jetbrains.exposed.v1.plugin.core.tables.UuidColumnType

exposed {
    tables {
        tablesPackage = "com.example.db.tables"
        databaseUrl = "jdbc:h2:file:./data/exampledb"
        databaseUser = "sa"
        databasePassword = ""
        // optional
        fileDirectory = layout.projectDirectory.dir("src/main/kotlin")
        schemas = listOf("PUBLIC", "SALES*")
        objectNameSuffix = "Table"
        uuidColumnType = UuidColumnType.JAVA
        dateTimeColumnType = DateTimeColumnType.JAVA_TIME
    }
}
```

## Map columns by configuration

When a column's type is not supported, or the generated type is not the one you want (for example `datetime()` where
the table should use `timestamp()`, or `json<String>()` where the JSON maps to a class of your own), configure the code
to generate for that column in a `columnMappings` block:

```kotlin
exposed {
    tables {
        // ...
        columnMappings {
            column("EVENTS.CREATED_AT") { code = "timestamp(\"CREATED_AT\")" }
            column("EVENTS.PAYLOAD") {
                code = "json<Payload>(\"PAYLOAD\", Json.Default)"
                imports = listOf("com.example.Payload", "kotlinx.serialization.json.Json")
            }
            column("SALES.ORDERS.SPAN") {
                code = "registerColumn(\"SPAN\", DayIntervalColumnType())"
                imports = listOf("com.example.db.DayIntervalColumnType")
            }
        }
    }
}
```

The key is `TABLE.COLUMN`, or `SCHEMA.TABLE.COLUMN` for a table outside the connection's current schema. Each part is
matched as given and in the database's default identifier case, so `events.created_at` matches `EVENTS.CREATED_AT` on
H2; quote a part to match a name that is not in that case. A mapping that matches no column, or several, fails the task
before anything is written.

`code` is the column factory call, including the column name. It is pasted into the generated definition as it is,
and the property's type is left to Kotlin's inference (`val createdAt = timestamp("CREATED_AT")`). The modifiers that
the generator derives from the database, `.nullable()`, `.autoIncrement()`, `.index()`, `.uniqueIndex()` and
`.databaseGenerated()`, are still appended, so `code` must not contain them; a warning names a mapping that does. A
column whose default value comes from a sequence cannot have `.autoIncrement("sequence")` appended to mapped code, so
the task warns and leaves it to the mapping. A documentation comment on the property and a log line record that the
column was mapped.

`imports` lists the fully qualified names that `code` needs. When it is empty, the import of an Exposed extension
function that the plugin itself knows is inferred: the date/time functions of the
[configured date/time module](#tables-date-time-column-type) (`date`, `time`, `datetime`, `timestamp`,
`xTimestamp`, `timestampWithTimeZone`, `duration`), `json` and `jsonb`, and `javaUUID`. `Table` member functions such
as `uinteger()` or `enumerationByName()` need no import. The plugin does not compile the mapped code or verify the
imports; a mistake in either shows up when the generated file is compiled, at the mapped line. Two mappings in one
generated file that import the same simple name from different packages fail the task.

> Column mappings are the way to recover a column function that cannot be told from the database, such as
> `timestamp()`, `duration()`, `uinteger()`, `encryptedVarchar()` or `currency()`, which are stored as the same
> database types as `datetime()`, `long()`, `varchar()` and so on.
>
{style="tip"}

## Generate id tables

With `generateIdTables = true`, every table that has a primary key is generated as an `IdTable` subclass, so that
it can be used with Exposed's DAO API. The form depends on the key:

| Primary key | Generated object |
|-------------|------------------|
| One `INTEGER` or `BIGINT` identity column, or one backed by a sequence | `IntIdTable("USERS", "ID")` / `LongIdTable("ORDERS", "ID", "ORDERS_SEQ")`, with no `id` property or `primaryKey` in the body |
| One `UUID` column | `UuidTable("TOKENS", "ID")`, or `UUIDTable` with `uuidColumnType = JAVA` |
| One column of any other supported type, or one of the above that also carries an index, a default, a foreign key or a [column mapping](#map-columns-by-configuration) | `IdTable<T>("PRODUCTS")` with `override val id: Column<EntityID<T>> = varchar("SKU", 32).entityId()` and the `primaryKey` override; the property is always named `id` |
| Several columns | `CompositeIdTable("ORDER_ITEMS")` with each key column declared through `.entityId()` under its own property name, and the `primaryKey` override |
| None | a plain `Table`, as without the option |

A concrete subclass declares its primary key unnamed, so a custom key name is logged and not kept (Exposed's
migration utilities never compare primary key names). A table whose key includes a column that is not generated, or
a mapped key column whose Kotlin type cannot be told from the mapping, stays a plain `Table` with a warning.

Foreign keys to such a table's id are declared with `reference("USER_ID", Users)` or, for a nullable column,
`optReference("USER_ID", Users)`, whose property is a `Column<EntityID<Int>>` rather than a `Column<Int>`; a key to
any other column keeps the `.references(...)` form. A key column that is itself a reference to an id, as in a
one-to-one table, is declared as `override val id: Column<EntityID<Int>> = reference("USER_ID", Users)`.

## Supported column types

All built-in Exposed column types are supported: a column declared with a column function of `exposed-core`, of the
`exposed-json` module or of a date/time module is generated with that same function. The exceptions are the functions
that declare the same database type as another function, since the database cannot tell them apart. Such a column
comes back as the function listed on the right, and the original can be restored with a
[column mapping](#map-columns-by-configuration):

* `timestamp()` and `xTimestamp()` come back as `datetime()`, see [](#date-and-time-types).
* `duration()` comes back as `long()`.
* `ubyte()`, `ushort()`, `uinteger()` and `ulong()` come back as `short()`, `integer()`, `long()` and `decimal(20, 0)`
  (`long()` on PostgreSQL), since the unsigned types are stored in the next larger signed type.
* `mediumText()` and `largeText()` come back as `text()`.
* `enumeration()` and `enumerationByName()` come back as `integer()` and `varchar()`, see [](#enumeration-types).
* `jsonb()` comes back as `json()` on H2, see [](#json-types).
* `encryptedVarchar()` and `encryptedBinary()` of `exposed-crypt` come back as `varchar()` and `binary()`.
* `compositeMoney()` and `currency()` of `exposed-money` come back as `decimal()` and `varchar(3)`, see
  [](#composite-and-transformed-columns).

On PostgreSQL, `byte()` and `blob()` come back as `short()` and `binary()` for the same reason, see
[](#postgresql-specifics). Column types the database has but Exposed does not are omitted, see
[](#database-types-without-an-exposed-counterpart).

### Date and time types

Date/time functions are imported from the [configured date/time module](#tables-date-time-column-type).
A `TIMESTAMP` column is always generated with `datetime()`, because Exposed's `datetime()` and `timestamp()` declare
the same database type; a column defined with `timestamp()` therefore comes back as `datetime()`. Likewise, a column
defined with `duration()` is stored as a `BIGINT` and comes back as `long()`.

### JSON types

A `JSON` column is generated as `json<String>("NAME", { it }, { it })` from the `exposed-json` module, because the
database cannot say which Kotlin type the JSON maps to; a documentation comment on the property and a log line explain
this, so that the identity serializers can be replaced with `json<T>(name, jsonConfig)` once the type is known.
H2 has a single JSON format, so a column defined with `jsonb()` comes back as `json()` as well; on PostgreSQL, a `jsonb`
column comes back as `jsonb<String>()`. If the generated definitions use functions from an Exposed module that is not
on the [configured classpath](#tables-classpath), a warning names the module to add.

### Array types

A one-dimensional array column is generated with the `array()` overload that takes the element's column type
explicitly, for example `array("IDS", IntegerColumnType(), 10)` for `INTEGER ARRAY[10]` or
`array("DAYS", KotlinLocalDateColumnType())` for `DATE ARRAY`, so that the element type matches the database exactly.
The shorter `array<Int>("IDS", 10)` form is not generated, because it resolves the element type by reflection rather
than from the database (`array<String>` declares a `TEXT` element, for example). An unbounded `CHARACTER VARYING ARRAY`
is generated with a `TextColumnType()` element, like a scalar column of that type. Arrays of any other element type
listed above are supported; arrays of `JSON` or of unsupported types are omitted like any unsupported column. A
multi-dimensional array (PostgreSQL only) is generated with the `dimensions` overload and a nested `List` type,
for example `val grid: Column<List<List<Int>>> = array("grid", IntegerColumnType(), dimensions = 2)`.

### Enumeration types

An `ENUM('Bar', 'Baz')` column is generated as a `customEnumeration()` whose `sql` argument is the definition exactly as
the database reports it, together with an `internal enum class` nested in the table object whose constants are the
values verbatim (escaped with backticks where necessary). A documentation comment on both the property and the enum
class, and a log line, say so, so that the generated class can be reviewed or replaced with an existing enum. Columns
defined in Exposed with `enumeration()` or `enumerationByName()` are stored as `INTEGER` and `CHARACTER VARYING(n)`
and therefore come back as `integer()` and `varchar()`. A value that cannot be a JVM identifier even in backticks (one
containing `.`, `;`, `/`, `[`, `]`, `<`, `>`, `:`, `\` or a backtick) makes the column unsupported. On PostgreSQL, where
an enumeration is a named type (`CREATE TYPE mood AS ENUM (...)`), the type name is passed as the `sql` argument
(`customEnumeration("current_mood", "mood", ...)`) and the values are read from the type; the type must already exist
when the generated definition is used to create the table, since `customEnumeration()` does not create it.

### Vector types

A `VECTOR(n)` column (PostgreSQL with the pgvector extension) is generated as `vector("NAME", n)` of `FloatArray`, and a
vector declared without dimensions as `vector("NAME", null)`. pgvector's `halfvec`, `sparsevec` and `bit` vectors have no
Exposed counterpart and are omitted like any other unsupported type.

### Database types without an Exposed counterpart

Columns of a type that no Exposed column function declares, such as `TIME WITH TIME ZONE` or `INTERVAL`, are omitted
from the generated definition. A warning is logged and a `TODO` entry naming the column is added to the documentation
comment of the generated object, so that the column can be added manually, for example with a
[column mapping](#map-columns-by-configuration) to a custom column type.

### Composite and transformed columns

Composite columns such as `compositeMoney()` are not generated, because they leave no trace in the database:
the database only holds the ordinary columns they are built from (a `DECIMAL(p, s)` and a `VARCHAR(3)` for money), which
come back as `decimal()` and `varchar()`. The same applies to every other Kotlin-side view over stored columns, such as
`transform()` mappings: the generated definition reproduces what is stored, not how it was declared in Exposed.

## Generated definitions

In addition to the column types, the following properties of a table are reflected in the generated definitions:

* Nullable columns are generated with `.nullable()`.
* Auto-incremented (identity) columns are generated with `.autoIncrement()`.
* Integer and long columns whose default value is taken from a sequence (`DEFAULT NEXT VALUE FOR ...`) are generated
  with `.autoIncrement("sequence_name")`, so that the sequence is mapped in the same way as a column defined with
  that function. When the sequence's start, increment, bounds, cycling or cache differ from what that call creates,
  the sequence is passed with exactly the differing parameters instead, for example
  `.autoIncrement(Sequence("sequence_name", startWith = 100, cache = 10))`.

> On H2, a column defined in Exposed with `autoIncrement("sequence_name")` has no such default: Exposed advances the
> sequence in each `INSERT` statement instead. The link between the column and the sequence therefore cannot be
> recovered from the database. The task logs a warning for every sequence that no generated column references, so
> that the `autoIncrement("sequence_name")` call can be added manually. Without it, `generateMigrations` would
> propose dropping the sequence. Conversely, a column that does carry `DEFAULT NEXT VALUE FOR` (or, on PostgreSQL,
> `DEFAULT nextval(...)`) in the database keeps a default that the generated `autoIncrement(...)` definition does not
> declare, so `generateMigrations` reports an `ALTER COLUMN` for it even though the definition is right. On
> PostgreSQL, the sequence behind a `SERIAL` or identity column is owned by that column; such a column is generated as
> a plain `.autoIncrement()` and its sequence is neither generated nor reported.
>
{style="warning"}
* Primary keys are generated as `override val primaryKey: Table.PrimaryKey = PrimaryKey(...)`. A custom key name is
  preserved as `PrimaryKey(..., name = "...")`; names generated by the database and the `pk_<table>` name that Exposed
  assigns by default are omitted.
* Columns whose values are always computed by the database (`GENERATED ALWAYS AS (...)`) are generated with
  `.databaseGenerated()`, so that Exposed never writes to them, and their expression is kept as a documentation
  comment on the property. The expression itself is not reproduced in DDL; a warning is logged for each such column.
* Single-column indices are generated with `.index()` or `.uniqueIndex()`, and multi-column indices are generated in
  an `init` block. An index name is preserved as the first argument unless it is the `<table>_<columns>[_unique]` name
  that Exposed assigns by default. A `UNIQUE` constraint is generated as a `uniqueIndex()` under the constraint's
  name, which is what Exposed creates for one; a name that the database generated for an unnamed constraint is
  omitted.
* Column defaults are generated with `.default(...)` for integer, floating point, decimal, boolean, string, UUID,
  `DATE`, `TIME` and `TIMESTAMP` literals (the last three for the `kotlinx.datetime` and `java.time` modules) and for
  a value of a generated enumeration, and with `.defaultExpression(...)` for `CURRENT_TIMESTAMP` and `CURRENT_DATE`
  (`CurrentDateTime`, `CurrentTimestampWithTimeZone` and `CurrentDate` of the configured date/time module). Any other
  default, such as `RANDOM_UUID()`, `CURRENT_TIME`, or a binary, array or JSON literal, is left to the database: the
  column is generated with `.databaseGenerated()`, so that Exposed never writes it, and a warning names the default so
  that the call can be replaced with `.default(...)` or `.defaultExpression(...)` by hand. Defaults are never added
  to [mapped columns](#map-columns-by-configuration); a warning asks for them to be part of the mapping.

> A `TIMESTAMP` column is generated with `datetime()`, so its `CURRENT_TIMESTAMP` default becomes
> `defaultExpression(CurrentDateTime)`. If the column should be a `timestamp()` instead, map it to
> `timestamp("NAME").defaultExpression(CurrentTimestamp)`.
>
{style="note"}
* Single-column foreign keys are generated with `.references(Target.column)` on the referencing column, where
  `Target` is the generated object of the referenced table (or the property alone for a self-reference). A delete or
  update rule is declared with `onDelete` / `onUpdate` only when it differs from the rule Exposed assumes for an
  undeclared one (`RESTRICT` on H2); a custom constraint name is preserved as `fkName`, while database-generated
  names and the `fk_<table>_<columns>__<targetColumns>` name that Exposed assigns by default are omitted. The index
  that H2 creates on every foreign key column is not generated, since the database recreates it with the key. A key
  whose referenced table is not among the generated tables, or whose referenced column is not generated, is omitted
  with a warning and a `TODO` on the property. `references()` requires both columns to have the same Kotlin type,
  which the task cannot verify when either column is [mapped by configuration](#map-columns-by-configuration), so it
  warns in that case.
* Foreign keys spanning several columns are generated in the `init` block, after any multi-column indices, with the
  `foreignKey()` overload that pairs each referencing column with its referenced column:
  `foreignKey(partMaker to Parts.maker, partCode to Parts.code, onDelete = ReferenceOption.CASCADE, name = "...")`,
  with the same rules for the declared options and the name as single-column keys. A key that includes a column that
  was not generated, or whose target cannot be resolved, is reported with a `TODO` on the object. A single-column key
  that references a column of the same table declared later than the referencing column is declared in the `init`
  block too, because a `references()` call on the property would run before the referenced property is assigned.

> Tables whose references form a cycle (`ORDERS` referencing `USERS` and `USERS` referencing `ORDERS`) are generated
> with both references, but Kotlin objects that reference each other while initializing fail at first use, and
> Exposed has no deferred reference. The task warns, names the tables, and adds a `TODO` to each generated object in
> the cycle; remove one of the references by hand.
>
{style="warning"}

Table, column, and index names are generated exactly as the database reports them. A name that is not in the
database's default identifier case, for example a table created as `"reserved"` in H2, is generated pre-quoted
(`Table("\"reserved\"")`), so that Exposed uses it verbatim instead of folding its case.

Two tables that would be generated as the same object, for example `USERS` and a quoted `"users"`, make the task fail
before anything is written, because the generated code would not compile. If another Kotlin file in the output package
already declares an object extending `Table` with the name of a generated object, a warning names that file; set
`objectNameSuffix`, or move or rename the existing object, so that the package compiles.

Column comments and check constraints are not reflected in the generated definitions.

## PostgreSQL specifics {id="postgresql-specifics"}

Table generation on PostgreSQL uses the `postgresql` driver; the `pgjdbc-ng` driver is not supported. The generator
reads the declared type of every column from the catalog, since the driver reports only internal names such as `int4`
or `_text`, and maps it as [](#supported-column-types) describes. Differences from H2 worth knowing:

* `smallint` comes back as `short()` (Exposed declares `byte()` as `SMALLINT` on PostgreSQL), `bytea` as `binary()`
  (`blob()` is `bytea` too), `real` as `float()`, and `character(1)` as `char(name, 1)`, because PostgreSQL does not tell
  `CHAR` from `CHAR(1)`.
* `SERIAL`, `BIGSERIAL` and identity columns are generated as `.autoIncrement()`. Exposed creates such a column as
  `SERIAL`, so an identity column is reproduced with a different definition, which `generateMigrations` does not
  report as a difference.
* A column with `DEFAULT nextval('sequence')` on a sequence it does not own is generated as
  `.autoIncrement("sequence")`, with the sequence's parameters when they differ from the defaults, as on H2.
* Defaults are reported with a cast, for example `'hello'::text` or `'2024-01-31'::date`; the cast is removed before
  the default is generated. `now()` has no Exposed counterpart that round-trips and is left to the database with a
  warning, while `CURRENT_TIMESTAMP` becomes `CurrentDateTime`.
* Identifiers fold to lower case, so a table or column created with a quoted mixed-case name is generated pre-quoted.
  The names PostgreSQL assigns to unnamed constraints (`<table>_pkey`, `<table>_<columns>_key`,
  `<table>_<columns>_fkey`) are omitted like database-generated names on H2.
* An undeclared foreign key rule reads back as `NO ACTION`, which differs from the `RESTRICT` Exposed assumes, so a
  hand-written key without rules is generated with `onDelete = ReferenceOption.NO_ACTION, onUpdate = ReferenceOption.NO_ACTION`;
  an Exposed-created key, which declares `RESTRICT` explicitly, comes back without rules.
* Index types (`USING hash`) are not reflected; every index is generated as a default index.

> Exposed's migration utilities do not compare column types on PostgreSQL, only nullability, auto-increment, defaults,
> size and scale. A `generateMigrations` run that reports nothing therefore does not prove that every generated column
> type matches the database; review the generated definitions.
>
{style="note"}

## Next steps

The Exposed Gradle plugin generates table definitions as a starting point; it does not keep them in sync with the
database afterwards.

After generating table definitions, review them like any other source file and adjust what the database cannot
express. For example, you can:

* Replace the `json<String>()` serializers and the generated enum classes with the types your application uses.
* Add the column functions that leave no trace in the database, such as `timestamp()` or `duration()`, by hand or
  through [column mappings](#map-columns-by-configuration) on the next run.
* Add `autoIncrement("sequence_name")` to the columns of sequences the task reported as unreferenced.
* Configure the same `tablesPackage` in the `migrations` block and run `generateMigrations` to check that the
  definitions match the database, see [](generate-migration-scripts.md).

After reviewing the generated definitions, commit them to your project and maintain them in Kotlin from then on.

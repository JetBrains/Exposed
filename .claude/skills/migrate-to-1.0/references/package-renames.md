# Package renames

This file lists every package-level rename that the skill applies to Kotlin source files,
extracted from `Migration-Guide-1-0-0.md`. The skill loads this file during Phase 3 (Apply).

The skill replaces full FQNs in `import` statements via `Edit`. It does not parse Kotlin AST;
matching is purely textual on lines that start with `import org.jetbrains.exposed.`.

## Section 1 — Pure renames (always apply)

These renames apply unconditionally to any project. Source: migration guide section
"Updated imports".

| Old (0.61.0)                                  | New (1.0.0)                                     |
|-----------------------------------------------|-------------------------------------------------|
| `org.jetbrains.exposed.sql.Table`             | `org.jetbrains.exposed.v1.core.Table`           |
| `org.jetbrains.exposed.sql.AbstractQuery`     | `org.jetbrains.exposed.v1.core.AbstractQuery`   |
| `org.jetbrains.exposed.sql.Expression`        | `org.jetbrains.exposed.v1.core.Expression`      |
| `org.jetbrains.exposed.dao.id.EntityID`       | `org.jetbrains.exposed.v1.core.dao.id.EntityID` |
| `org.jetbrains.exposed.dao.IntEntity`         | `org.jetbrains.exposed.v1.dao.IntEntity`        |
| `org.jetbrains.exposed.sql.javatime.datetime` | `org.jetbrains.exposed.v1.javatime.datetime`    |
| `org.jetbrains.exposed.sql.json.json`         | `org.jetbrains.exposed.v1.json.json`            |
| `org.jetbrains.exposed.sql.Transaction`       | `org.jetbrains.exposed.v1.core.Transaction`     |
| `org.jetbrains.exposed.sql.TextColumnType`    | `org.jetbrains.exposed.v1.core.TextColumnType`  |
| `org.jetbrains.exposed.sql.and`               | `org.jetbrains.exposed.v1.core.and`             |
| `org.jetbrains.exposed.sql.count`             | `org.jetbrains.exposed.v1.core.count`           |
| `org.jetbrains.exposed.sql.eq`                | `org.jetbrains.exposed.v1.core.eq`              |
| `org.jetbrains.exposed.sql.greaterEq`         | `org.jetbrains.exposed.v1.core.greaterEq`       |
| `org.jetbrains.exposed.sql.isNull`            | `org.jetbrains.exposed.v1.core.isNull`          |
| `org.jetbrains.exposed.sql.isNotNull`         | `org.jetbrains.exposed.v1.core.isNotNull`       |
| `org.jetbrains.exposed.sql.less`              | `org.jetbrains.exposed.v1.core.less`            |
| `org.jetbrains.exposed.sql.like`              | `org.jetbrains.exposed.v1.core.like`            |
| `org.jetbrains.exposed.sql.longLiteral`       | `org.jetbrains.exposed.v1.core.longLiteral`     |
| `org.jetbrains.exposed.sql.plus`              | `org.jetbrains.exposed.v1.core.plus`            |
| `org.jetbrains.exposed.sql.statements.BatchInsertStatement` | `org.jetbrains.exposed.v1.core.statements.BatchInsertStatement` |
| `org.jetbrains.exposed.sql.statements.DeleteStatement`      | `org.jetbrains.exposed.v1.core.statements.DeleteStatement`      |
| `org.jetbrains.exposed.sql.statements.api.RowApi`           | `org.jetbrains.exposed.v1.core.statements.api.RowApi`           |
| `org.jetbrains.exposed.dao.id.IntIdTable`     | `org.jetbrains.exposed.v1.core.dao.id.IntIdTable` |
| `org.jetbrains.exposed.dao.id.LongIdTable`    | `org.jetbrains.exposed.v1.core.dao.id.LongIdTable` |
| `org.jetbrains.exposed.dao.id.CompositeIdTable` | `org.jetbrains.exposed.v1.core.dao.id.CompositeIdTable` |
| `org.jetbrains.exposed.dao.IntEntityClass`    | `org.jetbrains.exposed.v1.dao.IntEntityClass`   |
| `org.jetbrains.exposed.dao.LongEntity`        | `org.jetbrains.exposed.v1.dao.LongEntity`       |
| `org.jetbrains.exposed.dao.LongEntityClass`   | `org.jetbrains.exposed.v1.dao.LongEntityClass`  |
| `org.jetbrains.exposed.dao.CompositeEntity`   | `org.jetbrains.exposed.v1.dao.CompositeEntity`  |
| `org.jetbrains.exposed.dao.CompositeEntityClass` | `org.jetbrains.exposed.v1.dao.CompositeEntityClass` |
| `org.jetbrains.exposed.sql.kotlin.datetime.CurrentDate` | `org.jetbrains.exposed.v1.datetime.CurrentDate` |
| `org.jetbrains.exposed.sql.transactions.TransactionManagerApi` | `org.jetbrains.exposed.v1.core.transactions.TransactionManagerApi` |

**Wildcard imports also apply.** Treat each row as a prefix rule when the user has
`import org.jetbrains.exposed.sql.*` etc.: rewrite it to the corresponding `v1.core.*`
or `v1.dao.*` wildcard.

## Section 2 — JDBC-conditional moves (apply only if `usesJdbc == true`)

These imports moved from `exposed-core` to `exposed-jdbc`. They must NOT be rewritten in
files that are R2DBC-only. Source: migration guide section "Moved imports".

When a file matches both a wildcard rule and a specific rule for the same import, apply the more specific rule first — the wildcard exists only as a fallback.

| Old (0.61.0)                                                | New (1.0.0)                                                     |
|-------------------------------------------------------------|-----------------------------------------------------------------|
| `org.jetbrains.exposed.sql.Database`                        | `org.jetbrains.exposed.v1.jdbc.Database`                        |
| `org.jetbrains.exposed.sql.SchemaUtils`                     | `org.jetbrains.exposed.v1.jdbc.SchemaUtils`                     |
| `org.jetbrains.exposed.sql.Query`                           | `org.jetbrains.exposed.v1.jdbc.Query`                           |
| `org.jetbrains.exposed.sql.transactions.TransactionManager` | `org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager` |
| `org.jetbrains.exposed.sql.transactions.transaction`        | `org.jetbrains.exposed.v1.jdbc.transactions.transaction`        |
| `org.jetbrains.exposed.sql.select`                          | `org.jetbrains.exposed.v1.jdbc.select`                          |
| `org.jetbrains.exposed.sql.selectAll`                       | `org.jetbrains.exposed.v1.jdbc.selectAll`                       |
| `org.jetbrains.exposed.sql.andWhere`                        | `org.jetbrains.exposed.v1.jdbc.andWhere`                        |
| `org.jetbrains.exposed.sql.exists`                          | `org.jetbrains.exposed.v1.jdbc.exists`                          |
| `org.jetbrains.exposed.sql.insert`                          | `org.jetbrains.exposed.v1.jdbc.insert`                          |
| `org.jetbrains.exposed.sql.update`                          | `org.jetbrains.exposed.v1.jdbc.update`                          |
| `org.jetbrains.exposed.sql.transactions.*`                  | `org.jetbrains.exposed.v1.jdbc.transactions.*`                  |
| `org.jetbrains.exposed.sql.transactions.inTopLevelTransaction` | `org.jetbrains.exposed.v1.jdbc.transactions.inTopLevelTransaction` |
| `org.jetbrains.exposed.sql.vendors.currentDialect`          | `org.jetbrains.exposed.v1.jdbc.vendors.currentDialectMetadata`  |
| `org.jetbrains.exposed.sql.statements.toExecutable`         | `org.jetbrains.exposed.v1.jdbc.statements.toExecutable`         |
| `org.jetbrains.exposed.sql.statements.jdbc.JdbcResult`      | `org.jetbrains.exposed.v1.jdbc.statements.jdbc.JdbcResult`      |
| `org.jetbrains.exposed.sql.migration.MigrationUtils`        | `org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils`        |

**Per-file decision rule:** if a file imports `R2dbcDatabase` or
`org.jetbrains.exposed.v1.r2dbc.*` (or pre-1.0 R2DBC equivalent), skip JDBC-conditional
moves for that file even when `usesJdbc == true` at the project level.

## Section 3 — `SqlExpressionBuilder` lambda imports (mechanical rewrite)

The `SqlExpressionBuilder` qualifier is removed; methods are imported as top-level functions
from `org.jetbrains.exposed.v1.core`. Source: migration guide section
"`SqlExpressionBuilder` method imports".

For every line of the form
`import org.jetbrains.exposed.sql.SqlExpressionBuilder.<NAME>`,
rewrite to
`import org.jetbrains.exposed.v1.core.<NAME>`.

Known method names (extracted from the migration guide examples):

- `less`, `lessEq`, `greater`, `greaterEq`
- `eq`, `neq`
- `isNull`, `isNotNull`
- `like`, `notLike`
- `concat`, `plus`, `minus`, `times`, `div`, `rem`
- `and`, `or`, `not`
- `between`, `inList`, `notInList`

If a name is not in this list but appears as
`org.jetbrains.exposed.sql.SqlExpressionBuilder.<NAME>`, apply the same rewrite — the rule
is independent of which method it is.

## Section 4 — UUID column type rename

Source: migration guide section "Updated UUID type classes".

The original classes for storing `java.util.UUID` values have been moved to new `.java.*`
packages to avoid name shadowing with the new `kotlin.uuid.Uuid` variants. The new
`UuidColumnType`, `UuidTable`, `UuidEntity`, and `UuidEntityClass` classes accept
`kotlin.uuid.Uuid` instead.

| Old (0.61.0)                                      | New (1.0.0)                                                   |
|---------------------------------------------------|---------------------------------------------------------------|
| `org.jetbrains.exposed.sql.UUIDColumnType`        | `org.jetbrains.exposed.v1.core.java.UUIDColumnType`           |
| `org.jetbrains.exposed.dao.id.UUIDTable`          | `org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable`         |
| `org.jetbrains.exposed.dao.UUIDEntity`            | `org.jetbrains.exposed.v1.dao.java.UUIDEntity`                |
| `org.jetbrains.exposed.dao.UUIDEntityClass`       | `org.jetbrains.exposed.v1.dao.java.UUIDEntityClass`           |

Additionally, `Table.uuid()` now accepts `kotlin.uuid.Uuid`; to continue using
`java.util.UUID` values, replace calls to `uuid()` with `javaUUID()` and add:
`import org.jetbrains.exposed.v1.core.java.javaUUID`.

## Section 5 — Datetime column type rename

Source: migration guide section "Datetime column type classes refactored". Some
class names changed when the column-type hierarchy was unified.

The `exposed-java-time` and `exposed-kotlin-datetime` artifacts retain their original class
names (only the package prefix changes from `org.jetbrains.exposed.sql.*` to
`org.jetbrains.exposed.v1.*`). The `exposed-jodatime` artifact had more significant
class-level renames:

| Old (0.61.0)                                                          | New (1.0.0)                                                         |
|-----------------------------------------------------------------------|---------------------------------------------------------------------|
| `org.jetbrains.exposed.sql.jodatime.DateColumnType` (time=false)      | `org.jetbrains.exposed.v1.jodatime.JodaLocalDateColumnType`         |
| `org.jetbrains.exposed.sql.jodatime.DateColumnType` (time=true)       | `org.jetbrains.exposed.v1.jodatime.JodaLocalDateTimeColumnType`     |
| `org.jetbrains.exposed.sql.jodatime.LocalTimeColumnType`              | `org.jetbrains.exposed.v1.jodatime.JodaLocalTimeColumnType`         |
| `org.jetbrains.exposed.sql.jodatime.DateTimeWithTimeZoneColumnType`   | `org.jetbrains.exposed.v1.jodatime.DateTimeWithTimeZoneColumnType`  |

Note: `DateColumnType` in `exposed-jodatime` had a `time` boolean constructor parameter
that determined which class it maps to in 1.0.0. If the call site uses `DateColumnType(time=false)`
it becomes `JodaLocalDateColumnType`; `DateColumnType(time=true)` becomes
`JodaLocalDateTimeColumnType`. This split cannot be resolved purely from import lines — flag
it in the manual-review checklist when `org.jetbrains.exposed.sql.jodatime.DateColumnType`
is found.

## Section 6 — `addLogger` and `StdOutSqlLogger`

| Old (0.61.0)                                | New (1.0.0)                                      |
|---------------------------------------------|--------------------------------------------------|
| `org.jetbrains.exposed.sql.StdOutSqlLogger` | `org.jetbrains.exposed.v1.core.StdOutSqlLogger`  |

The free-function `import org.jetbrains.exposed.sql.addLogger` is removed entirely
(in 1.0.0 `addLogger` is a method on the new `Transaction` class). The skill should
delete the line, not rewrite it.

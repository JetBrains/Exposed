# Manual review

This file lists migration cases the skill detects but does NOT auto-apply. For each case,
the skill prints the file:line, what was detected, the migration-guide section, and a
short instruction.

## Detection patterns

The skill greps every Kotlin file under `src/` for these patterns. Any match becomes one
line in the manual-review summary. The skill does not edit these files except to apply
unrelated import renames.

| # | What to detect (substring or pattern)             | Migration-guide section                           | What the user must do                                                                                                  |
|---|---------------------------------------------------|---------------------------------------------------|------------------------------------------------------------------------------------------------------------------------|
| 1 | `fun Transaction.` (extension on the `Transaction` receiver) | "Transactions — Custom functions"      | Change the receiver to `JdbcTransaction` (or `R2dbcTransaction` for R2DBC code).                                       |
| 2 | `inTopLevelTransaction(` with a positional first arg that is `Connection.TRANSACTION_*` | "transaction() signature changed" | Convert the call to use the named arg `transactionIsolation = …`. The first positional arg slot is now `db: Database`. |
| 3 | `transaction(` with two-or-more positional args (any of which is a `transactionIsolation` value) | same as above                         | Same — use named args; `db` is now first.                                                                              |
| 4 | `: Statement<` or `class … : Statement(` (custom `Statement` subclass) | "Statement builders and executables — Custom statements" | The `StatementType` parameter type changed from `String` to a sealed class. Update accordingly.                        |
| 5 | `.exec(` followed by a positional `StatementType.X` arg | "exec() parameter type changed"                   | Pass the new sealed-class form.                                                                                        |
| 6 | `class … : VendorDialect(`, `class … : DatabaseDialect(` | "DatabaseDialect and VendorDialect — Custom dialects"   | Multiple property-type signatures changed. Read the section.                                                            |
| 7 | `.set(` or `.setArray(` on a `PreparedStatementApi` receiver | "PreparedStatementApi — set() removed", "setArray() removed" | These methods were removed. Use the new typed setters listed in the guide.                                              |
| 8 | `: BaseBatchInsertStatement` (custom subclass)    | "BaseBatchInsertStatement removed"                | The class was removed. Switch to `BatchInsertStatement` or another supported type.                                      |
| 9 | `CommentPosition.` reference                      | "CommentPosition ownership changed"               | The enum moved. Update the import; the qualifier-using-class changed.                                                  |
| 10 | `.value` access on a `Case` object                | "Case property value removed"                     | The property was removed; use the alternative documented in the section.                                                |
| 11 | `readObject(` override with a `String` parameter (custom `ColumnType`) | "readObject() parameter type changed"      | The parameter is now `Int` (column index). Update the override signature.                                              |
| 12 | `ResultRow.create(` call                          | "ResultRow.create() parameter type changed"       | The factory's parameter type changed. Inspect call site.                                                               |
| 13 | `StatementResult.Object.` reference               | "StatementResult.Object property type changed"    | Property type changed. Inspect the consumer.                                                                            |
| 14 | `.execute(` whose return value is bound to a previously-typed variable | "execute() return type changed" | Return type is now driver-specific. Adjust the variable type or call.                                                  |
| 15 | `areEquivalentColumnTypes(` reference             | "areEquivalentColumnTypes() deprecated"           | The function is deprecated; use the documented replacement.                                                            |
| 16 | `resolveRefOptionFromJdbc(` reference             | "resolveRefOptionFromJdbc() removed"              | Removed. See the section for the alternative.                                                                          |
| 17 | `supportsSelectForUpdate` property reference      | "supportsSelectForUpdate deprecated"              | Property is deprecated; use replacement.                                                                                |
| 18 | `ENABLE_UPDATE_DELETE_LIMIT` reference            | "ENABLE_UPDATE_DELETE_LIMIT removed"              | Removed entirely.                                                                                                       |

## Output format in the summary

For each match, the summary line uses:

```
⚠️  <file>:<line>  <category>
    detected: <short description>
    guide:    <migration-guide section>
    action:   <what to do>
```

Example:

```
⚠️  src/main/kotlin/com/example/Tx.kt:42  custom-transaction-extension
    detected: fun Transaction.getVersion()
    guide:    "Transactions — Custom functions"
    action:   change the receiver to `JdbcTransaction` (or `R2dbcTransaction` for R2DBC).
```

## What the skill must NOT do for these cases

- Do NOT rewrite the matched line, even partially. The risk of producing a half-broken edit
  is higher than the value of the partial fix.
- Do NOT add `// TODO` comments next to source lines (only to build-file lines per
  `build-files.md`). Source files get reported via the summary; the user reviews on a
  separate pass.
- Do NOT skip subsequent steps just because a manual-review match was found. The skill
  proceeds with mechanical changes in the same file.

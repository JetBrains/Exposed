# Module detection

This file describes how the skill determines which Exposed modules a user's project depends
on. The result is a small fact table the skill keeps in mind for the duration of one
invocation:

```
{
  "usesJdbc":            true | false,
  "usesR2dbc":           true | false,
  "usesDao":             true | false,
  "usesSpring":          true | false,    // spring-boot-starter
  "usesJson":            true | false,
  "usesKotlinDatetime":  true | false,
  "usesJodatime":        true | false,
  "usesJavatime":        true | false,
  "usesMigration":       true | false,
  "usesCrypt":           true | false,
  "usesMoney":           true | false
}
```

> `exposed-spring-boot4-starter` is deliberately **not** in the detection table. It only
> exists in Exposed 1.0+, so a project being migrated from 0.x cannot have it yet. The
> manual-review note in `build-files.md` advises the user about it as part of the
> migration target, not as something to detect in the source project.

## Detection — primary: build-file scan

Look in the user's build files for these artifact IDs. Files to scan, in order:

1. `gradle/libs.versions.toml`
2. `build.gradle.kts` (project root + any submodule)
3. `build.gradle` (Groovy variant)
4. `pom.xml`

For each file, search for the substrings below. Any match anywhere in the file flips
the corresponding flag to `true`.

| Artifact ID                     | Sets flag         |
|---------------------------------|-------------------|
| `exposed-jdbc`                  | usesJdbc          |
| `exposed-r2dbc`                 | usesR2dbc         |
| `exposed-dao`                   | usesDao           |
| `exposed-spring-boot-starter`   | usesSpring        |
| `exposed-json`                  | usesJson          |
| `exposed-kotlin-datetime`       | usesKotlinDatetime |
| `exposed-jodatime`              | usesJodatime      |
| `exposed-java-time`             | usesJavatime      |
| `exposed-migration` (or any `exposed-migration-*`) | usesMigration |
| `exposed-crypt`                 | usesCrypt         |
| `exposed-money`                 | usesMoney         |

Special rule: a project that depends on `exposed-core` but on neither `exposed-jdbc` nor
`exposed-r2dbc` is treated as `usesJdbc = true` (the 0.x default before the JDBC/R2DBC
split). The skill records this as a "build-file change required" finding so the user adds
the explicit `exposed-jdbc` dependency.

## Detection — fallback: source-code scan

If the build-file scan finds zero `exposed-*` artifacts (which can happen when the user
uses a custom convention plugin or `buildSrc` indirection), fall back to grepping all
Kotlin files under `src/` for these markers:

| Pattern                                        | Sets flag     |
|------------------------------------------------|---------------|
| `org.jetbrains.exposed.sql.transactions.transaction` | usesJdbc      |
| `R2dbcDatabase.connect`                              | usesR2dbc     |
| `: IntEntity\(`, `: LongEntity\(`, `: UUIDEntity\(` (regex) | usesDao    |
| `org.jetbrains.exposed.spring`                       | usesSpring    |

The fallback is best-effort — if it returns no flags, the skill exits Phase 1 with
"could not determine project flavor" and asks the user to confirm `usesJdbc` / `usesR2dbc`.

## Per-file rule

In addition to the project-level fact table, the skill makes a per-file decision when
applying JDBC-conditional moves (Section 2 of `package-renames.md`). For a given Kotlin
file:

- If the file already imports `org.jetbrains.exposed.v1.r2dbc.*` or any pre-1.0 R2DBC
  equivalent (`R2dbcDatabase`, etc.), skip JDBC-conditional moves *for that file* even
  if the project as a whole has `usesJdbc = true`.
- Otherwise apply JDBC-conditional moves to that file (if `usesJdbc` is true).

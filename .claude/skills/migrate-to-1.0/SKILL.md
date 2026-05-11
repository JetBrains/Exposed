---
name: migrate-to-1.0
description: "Migrates a Kotlin project from Exposed 0.61.0 to Exposed 1.0. Scans Kotlin sources and build files, applies the mechanical changes documented in the official 1.0 migration guide (package renames, JDBC module moves, SqlExpressionBuilder lambda → top-level imports, build dependency bumps), and produces a report of items that need manual review. Use when the user wants to upgrade an existing Exposed 0.61.0 project to 1.0; for older 0.x versions the skill emits a compatibility warning and proceeds only with user confirmation."
user_invocable: true
---

# Migrate to Exposed 1.0

Automates the **mechanical** parts of migrating a Kotlin project from Exposed **0.61.0**
to Exposed 1.0. Anything that requires human judgment is detected and reported in a
summary at the end so the user can address it manually using the migration guide.

**Supported starting version: 0.61.0** (the last pre-1.0 release). The official
Migration-Guide-1-0-0.md was written specifically for the 0.61.0 → 1.0.0 jump. If the
detected version is older (e.g. 0.41.x, 0.55.x), the skill warns and asks for explicit
confirmation before proceeding: the rules will still rewrite the package paths it
recognises, but APIs introduced between the detected version and 0.61.0 are out of
scope, and the user should upgrade to 0.61.0 first or expect a noisier manual-review
list.

The canonical source for what to migrate is the official guide:
<https://www.jetbrains.com/help/exposed/migration-guide-1-0-0.html>
(or the local `Migration-Guide-1-0-0.md` if available).

The reference files in `./references/` encode that guide as machine-applicable rules:

- `package-renames.md` — every import-level rename, organised by category (always-apply,
  JDBC-conditional, SqlExpressionBuilder, UUID, datetime, addLogger).
- `module-detection.md` — heuristics for the project fact table
  (`usesJdbc`, `usesR2dbc`, `usesDao`, …).
- `build-files.md` — Gradle KTS / Groovy / version-catalog / Maven edit patterns.
- `manual-review.md` — cases the skill must NOT auto-apply, with detection patterns.

Load each reference file only when the corresponding phase below needs it.

## Operating principles

- Operate on the user's current working tree. Do **not** create branches or commits.
- Never write outside the user's project root.
- Never run `./gradlew` or any other build command on the user's project.
- Idempotent at line level: every edit is a precise `Edit` (`old_string`/`new_string`),
  so re-running the skill after partial completion is safe.
- Cross-check: if a file was changed externally between Phase 1 and Phase 3 (an `Edit`
  fails because `old_string` doesn't match), skip the file with a "skipped — file changed"
  reason and continue.

## Phase 1 — Discover (read-only)

1. Determine whether migration is needed.

   The real signal is whether any source file uses the old `0.x` package paths
   (`org.jetbrains.exposed.sql.*`, `org.jetbrains.exposed.dao.*`,
   `org.jetbrains.exposed.r2dbc.sql.*`, …). Version coordinates in build files are a
   useful hint but don't always reflect reality — projects on a `1.0.0-beta-N` /
   `1.0.0-rc-N` release already use the `v1.*` namespace and don't need this skill,
   even though their coordinate isn't strictly `0.x`.

   a. **Source scan:** look for any `import org.jetbrains.exposed.sql.` or
      `import org.jetbrains.exposed.dao.` (and the R2DBC equivalents) in any `.kt`
      file under `src/`. Record file:line for use in the rename-scan step below.

   b. **Build-file scan:** look for `org.jetbrains.exposed:` coordinates in
      `gradle/libs.versions.toml`, every `build.gradle.kts` / `build.gradle`, and
      `pom.xml`. Record every coordinate version found.

   c. If (a) finds zero old-namespace imports AND (b) finds no pre-1.0 coordinate
      (everything is either `1.0.0` proper or higher, or no Exposed coordinate at all),
      **stop here** and report:
      ```
      No migration needed. Your project does not use the old `org.jetbrains.exposed.sql.*` /
      `.dao.*` namespace and the build files do not pin a pre-1.0 release. Projects on
      `1.0.0-beta-N` / `1.0.0-rc-N` already use the new `v1.*` namespace and don't need
      this skill.
      ```

   d. Otherwise, migration applies. Proceed with the version check below.

   **Version check.** The supported starting version is `0.61.0`. If the detected
   coordinate is something else under `0.x` (e.g. `0.41.x`, `0.55.x`), print the
   warning below and let the user decide whether to continue:
   ```
   ⚠️  Detected Exposed <X.Y.Z>, but this skill targets the 0.61.0 → 1.0.0 migration.
       Older 0.x versions may use APIs that were renamed or removed before 0.61.0;
       those changes are NOT covered by this skill. Recommended path:
         1. Upgrade your project to Exposed 0.61.0 first.
         2. Re-run this skill.
       Proceed anyway? The skill will rewrite recognised package paths, but expect a
       noisier "manual review" list and possibly missed changes.
   ```
   If the user declines, exit cleanly. If they confirm, continue but include the
   version-mismatch warning in the Phase 4 summary.

   Beta/RC of 1.0 with `sql.*` imports somehow still present (e.g. a half-finished
   manual migration) is also valid input: the source scan in (a) catches it and the
   skill proceeds without emitting the `0.61.0` warning, since the user is already
   targeting 1.0.0.

2. Build the project fact table per `references/module-detection.md`.

3. Enumerate every Kotlin file under `src/` (recursively). For each file, run two scans:
    - **Rename scan** — grep for any pattern in `references/package-renames.md` (sections
      1–6). Record file:line + which pattern hit.
    - **Manual-review scan** — grep for any pattern in `references/manual-review.md`.
      Record file:line + which case.

4. Print a Phase 1 summary to the user:
   ```
   Discovered:
     • Exposed version <X.Y.Z> in <build files>
     • Project flavor: usesJdbc=<bool>, usesR2dbc=<bool>, usesDao=<bool>, ...
     • <N> Kotlin files contain 0.x imports (<M> imports total)
     • <K> manual-review cases across <L> files
     • Build files to update: <list>
   ```

## Phase 2 — Plan (interactive checkpoint)

1. Print a one-paragraph plan to the user:
   ```
   Planned changes:
     1. Apply <category-1> rewrites to <N1> files.
     2. Apply <category-2> rewrites to <N2> files.
     3. Update <build-file> dependencies.
     4. Report <K> manual-review items at the end (no source edits).
   ```

2. **Stop and ask the user to confirm before any edits.** Wait for their response.

3. If the user says "skip <category>", remove that category from the plan and proceed.
   If the user declines, exit cleanly with no changes.

## Phase 3 — Apply (write phase)

For each Kotlin source file with rename hits, apply edits in this order:

a. **Section 1 (always-apply renames)** from `references/package-renames.md`. Each row
becomes one or more `Edit` calls (`old_string` = the 0.61.0 FQN; `new_string` = the
1.0.0 FQN).

b. **Section 2 (JDBC-conditional moves)** — only if the project's `usesJdbc` is true AND
the file does not contain any R2DBC import. Apply each row as in (a).

c. **Section 3 (`SqlExpressionBuilder` lambda imports)** — for every line of the form
`import org.jetbrains.exposed.sql.SqlExpressionBuilder.<NAME>`, rewrite to
`import org.jetbrains.exposed.v1.core.<NAME>`. The list of `<NAME>` values is in
`package-renames.md` Section 3, but the rule is independent of which method —
if the prefix matches, rewrite.

d. **Section 4 (UUID)**, **Section 5 (Datetime)**, **Section 6 (StdOutSqlLogger / addLogger)**
from `package-renames.md` — apply each row as in (a). For
`import org.jetbrains.exposed.sql.addLogger`, **delete the import line** entirely
instead of rewriting it (the function became a method).

If any `Edit` fails (`old_string` not unique or not present), skip just that edit, record
the reason, and continue with the rest of the file.

For build files (`build.gradle.kts`, `build.gradle`, `gradle/libs.versions.toml`,
`pom.xml`):

e. Apply the patterns in `references/build-files.md` for the matching flavor. For
ambiguous lines (a custom version variable that does not match the
`exposed`-name rule), append `// TODO: review for Exposed 1.0` (or `# TODO: …`
for `.toml`, or `<!-- TODO: ... -->` for XML) and add the line to the manual-review
summary.

The skill **never modifies** files matched by `manual-review.md` patterns beyond the
mechanical rename edits in (a)–(d). The matches themselves get reported in Phase 4.

## Phase 4 — Summarise

Print a markdown report to stdout:

````markdown
# Migration summary

## ✅ Files modified (<N> total)

### Package renames (Section 1)

- src/main/kotlin/com/example/Foo.kt — 4 imports
- src/main/kotlin/com/example/Bar.kt — 2 imports
  ...

### JDBC moves (Section 2)

- src/main/kotlin/com/example/Foo.kt — 3 imports
  ...

### SqlExpressionBuilder rewrites (Section 3)

- src/main/kotlin/com/example/Foo.kt — 1 import
  ...

### UUID / Datetime / Logger

- src/main/kotlin/com/example/Models.kt — 1 import

## ✅ Build files modified

```diff
--- build.gradle.kts
+++ build.gradle.kts
@@
-implementation("org.jetbrains.exposed:exposed-core:0.61.0")
+implementation("org.jetbrains.exposed:exposed-core:1.0.0")
+implementation("org.jetbrains.exposed:exposed-jdbc:1.0.0")
```

## ⚠️ Manual review needed (<K> items)

⚠️ src/main/kotlin/com/example/Tx.kt:42 custom-transaction-extension
detected: fun Transaction.getVersion()
guide:    "Transactions — Custom functions"
action:   change the receiver to `JdbcTransaction`.

⚠️ src/main/kotlin/com/example/Tx.kt:88 transaction-signature
detected: transaction(db.transactionManager.defaultIsolationLevel, db = db) { ... }
guide:    "transaction() signature changed"
action:   db is now the first positional parameter; use named args for the rest.

...

## ⏭️ Skipped files (<S> total)

- src/main/kotlin/com/example/Old.kt — old_string did not match (file may have been edited)

## 📋 Suggested next steps

1. Review the diff: `git diff`
2. Compile: `./gradlew compileKotlin` (or your project's build command)
3. Run tests: `./gradlew test`
4. Address each ⚠️ item using the official migration guide:
   https://www.jetbrains.com/help/exposed/migration-guide-1-0-0.html
5. When everything compiles and tests pass, commit:
   `git commit -am "Migrate to Exposed 1.0"`
````

The skill stops here. No further actions, no commits.

## Edge cases

- **Project uses both JDBC and R2DBC.** The per-file decision in Phase 3 (b) handles this:
  files importing R2DBC are not given JDBC-conditional moves.
- **`exposed-spring-boot-starter`.** The skill does NOT replace it with
  `exposed-spring-boot4-starter`. That's a Spring upgrade the user opts into. A note is
  added to the manual-review summary instead.
- **Custom Exposed-version variable.** If named to contain `exposed` (case-insensitive),
  bumped automatically. Otherwise reported for manual review.
- **No 0.x usage.** Exit cleanly in Phase 1 with "no migration needed."
- **Nothing left to do after rerun.** All `Edit` calls fail because `old_string` is
  already the new value. The summary reports zero modifications and zero skips.

# Build-file edit patterns

This file describes the mechanical edits the skill applies to a project's build
configuration. The skill loads this file during Phase 3 (Apply) after detecting which
build flavor the project uses.

## Goals of the build-file edit phase

For every recognized build-file flavor, the skill must:

1. Bump every Exposed coordinate from `0.x.y` to `1.0.0`.
2. Add `org.jetbrains.exposed:exposed-jdbc:1.0.0` if the project depends on `exposed-core`
   but neither `exposed-jdbc` nor `exposed-r2dbc` (as `module-detection.md` flagged).
3. Replace `exposed-migration` with `exposed-migration-core` AND `exposed-migration-jdbc`
   (or `-r2dbc`, depending on `usesJdbc`/`usesR2dbc`) — see migration guide section
   "Migration dependencies".
4. Leave `exposed-spring-boot-starter` alone (still valid for Spring 6 / Spring Boot 3).
   Do NOT auto-replace it with `exposed-spring-boot4-starter` — that's a Spring upgrade
   the user must opt in to. Add a note to the manual-review summary instead.

Anything ambiguous (custom version variables holding the version of >1 unrelated
artifact, externally generated build scripts, etc.) is left alone with a comment
`// TODO: review for Exposed 1.0` appended on the relevant line.

## Pattern A — Gradle Kotlin DSL (`build.gradle.kts`)

### A1. Direct version bump

Match (regex over a single line):
```
implementation\("org\.jetbrains\.exposed:(exposed-[a-z0-9-]+):0\.[^"]+"\)
```
Replace with:
```
implementation("org.jetbrains.exposed:$1:1.0.0")
```
Same for `api`, `compileOnly`, `runtimeOnly`, `testImplementation`, etc. — the rule is on
the artifact coordinate, not the configuration name.

### A2. Variable-bound version

Match a top-level `val` whose name contains `exposed` (case-insensitive) and whose value
is a `0.x.y` string:
```
val exposedVersion = "0.61.0"
```
Replace with:
```
val exposedVersion = "1.0.0"
```
If the variable name does not contain `exposed`, leave it alone and add the line to the
manual-review summary.

### A3. `exposed-migration` split

If a line matches:
```
implementation("org.jetbrains.exposed:exposed-migration:0.61.0")
```
Replace with two lines:
```
implementation("org.jetbrains.exposed:exposed-migration-core:1.0.0")
implementation("org.jetbrains.exposed:exposed-migration-jdbc:1.0.0")
```
(use `-r2dbc` instead of `-jdbc` if `usesJdbc == false && usesR2dbc == true`).

### A4. Add `exposed-jdbc` if missing

If `exposed-core` is present and neither `exposed-jdbc` nor `exposed-r2dbc` is, append
to the same `dependencies { ... }` block:
```
implementation("org.jetbrains.exposed:exposed-jdbc:1.0.0")
```

## Pattern B — Gradle Groovy DSL (`build.gradle`)

Same rules as A, but with single quotes and Groovy `implementation 'group:name:version'`
syntax. Replace double-quote forms `"..."` with the matching `'...'` form.

## Pattern C — Version catalog (`gradle/libs.versions.toml`)

### C1. Version bump in `[versions]`

Match:
```
exposed = "0.61.0"
```
Replace with:
```
exposed = "1.0.0"
```
Variable name may also be `exposed-version` or similar — use a case-insensitive
contains-`exposed` rule.

### C2. Add `exposed-jdbc` library entry if missing

If `exposed-core` exists in `[libraries]` and `exposed-jdbc` does not, append to
`[libraries]`:
```
exposed-jdbc = { group = "org.jetbrains.exposed", name = "exposed-jdbc", version.ref = "exposed" }
```
The user must add `implementation(libs.exposed.jdbc)` to their `build.gradle.kts` —
flag this in the manual-review summary because the catalog change alone doesn't add the
dependency to any module.

### C3. `exposed-migration` split

If a `exposed-migration = ...` line exists, replace with:
```
exposed-migration-core = { group = "org.jetbrains.exposed", name = "exposed-migration-core", version.ref = "exposed" }
exposed-migration-jdbc = { group = "org.jetbrains.exposed", name = "exposed-migration-jdbc", version.ref = "exposed" }
```
Flag the `libs.exposed.migration` consumer sites for manual update.

## Pattern D — Maven (`pom.xml`)

### D1. Version bump

Match every `<dependency>` block whose `<groupId>org.jetbrains.exposed</groupId>` and
`<version>0.x.y</version>`. Replace `<version>0.x.y</version>` with `<version>1.0.0</version>`.

### D2. Add `exposed-jdbc`

If `exposed-core` is present without `exposed-jdbc` or `exposed-r2dbc`, append a new
`<dependency>` block for `exposed-jdbc:1.0.0` inside `<dependencies>`.

### D3. `exposed-migration` split

If `exposed-migration` is present, replace its `<dependency>` block with two:
`exposed-migration-core` and `exposed-migration-jdbc`.

## What to flag for manual review

After the mechanical edits, add these to the manual-review summary if they apply:

- Any `exposed-spring-boot-starter` usage with a note: "If you also want to migrate to
  Spring Boot 4, replace this artifact with `exposed-spring-boot4-starter`. Otherwise
  leave as-is."
- Any version variable that did not match the `exposed`-named-variable rule.
- Any custom convention plugin / `buildSrc` indirection touched during detection but not
  edited.

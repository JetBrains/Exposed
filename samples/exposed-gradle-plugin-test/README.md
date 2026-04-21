# Exposed Gradle Plugin — Acceptance Test Sample

Manual full-acceptance test harness for the `exposed-gradle-plugin`.

Uses JDBC + H2 (file-based) in "production" mode (direct `databaseUrl`), not TestContainers.

## Structure

- `build.gradle.kts` — applies `org.jetbrains.exposed.plugin` and configures `exposed { migrations { ... } }`.
- `src/main/kotlin/com/example/tables/` — Exposed `Table` definitions the plugin scans.
- `src/main/resources/db/migration/` — generated migration scripts land here.
- `data/` — H2 database file acting as the "current schema" the plugin diffs against. **Gitignored.**
- `TEST_GUIDE.md` — step-by-step acceptance scenarios (start here after reading this file).

## Running against an unreleased build of the plugin

This sample references `org.jetbrains.exposed.plugin:1.2.0` from Maven Central. If you're running it against a pre-release build of Exposed (e.g., this branch), publish locally first and add `mavenLocal()` to the `pluginManagement` and `dependencyResolutionManagement` blocks in `settings.gradle.kts`:

```bash
# from the repository root
./gradlew publishToMavenLocal
```

## Mental model

1. You edit Kotlin `Table` objects in `src/main/kotlin/com/example/tables/`.
2. `./gradlew generateMigrations` starts a Gradle Worker, connects to H2 at `databaseUrl`, introspects the **current** schema, compares against the **desired** schema (your code), and writes SQL DDL files into `src/main/resources/db/migration/`.
3. You apply the generated SQL to `data/mydb` yourself so the next `generateMigrations` run sees the updated baseline.

**The plugin does NOT apply migrations.** In production you'd hand them to Flyway/Liquibase; here you apply them manually to keep the test surface small.

## Applying migrations between scenarios (in IntelliJ)

1. **Database tool window** → **+** → **Data Source** → **H2**.
2. URL: `jdbc:h2:file:<absolute-path-to-sample>/data/mydb`, user `sa`, empty password.
3. When a `generateMigrations` run produces `src/main/resources/db/migration/V<...>.sql`, open that file in the editor, right-click → **Execute → <your H2 data source>**.
4. Refresh the data source to verify the schema matches the table object(s).
5. Re-run `generateMigrations` — it should now generate **0 migrations** (DB is in sync with code).

## Known plugin bug workarounds

`build.gradle.kts` contains `tasks.named("generateMigrations") { dependsOn("compileKotlin") }` because the plugin eagerly resolves its classpath `ConfigurableFileCollection` at configuration time and loses the task-dependency chain. See `TEST_GUIDE.md` finding #12.

Scenario 7 in `TEST_GUIDE.md` reproduces another bug (`VersionFormat.findNextMajor` misreads timestamp filenames as major versions). It is **expected to misbehave** — do not treat it as a test failure.

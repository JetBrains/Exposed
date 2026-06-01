# Exposed Gradle Plugin — Sample

A hands-on sample for the `exposed-gradle-plugin` and its `generateMigrations` task.

Uses JDBC + H2 (file-based) in "production" mode (direct `databaseUrl`), not TestContainers.

## Structure

- `build.gradle.kts` — applies `org.jetbrains.exposed.plugin` and configures `exposed { migrations { ... } }`.
- `src/main/kotlin/com/example/tables/` — Exposed `Table` definitions the plugin scans.
- `src/main/resources/db/migration/` — generated migration scripts land here.
- `data/` — H2 database file acting as the "current schema" the plugin diffs against. **Gitignored.**
- `TEST_GUIDE.md` — step-by-step walkthrough of the plugin's features (start here after reading this file).

## Running against an unreleased build of the plugin

This sample applies `org.jetbrains.exposed.plugin` at `1.3.0` — the plugin marker is resolved from the Gradle Plugin Portal (declared in `pluginManagement`) and its Exposed dependencies from Maven Central, both publicly available, so no extra setup is needed. If you're running it against a pre-release build of Exposed (e.g., from a branch), publish locally first and add `mavenLocal()` to the `pluginManagement` and `dependencyResolutionManagement` blocks in `settings.gradle.kts`:

```bash
# from the repository root
./gradlew publishToMavenLocal
```

## Mental model

1. You edit Kotlin `Table` objects in `src/main/kotlin/com/example/tables/`.
2. `./gradlew generateMigrations` starts a Gradle Worker, connects to H2 at `databaseUrl`, introspects the **current** schema, compares against the **desired** schema (your code), and writes SQL DDL files into `src/main/resources/db/migration/`.
3. You apply the generated SQL to `data/mydb` yourself so the next `generateMigrations` run sees the updated baseline.

**The plugin does NOT apply migrations.** In production you'd hand them to Flyway/Liquibase; here you apply them manually to keep the sample small.

## Applying migrations between scenarios (in IntelliJ)

1. **Database tool window** → **+** → **Data Source** → **H2**.
2. URL: `jdbc:h2:file:<absolute-path-to-sample>/data/mydb`, user `sa`, empty password.
3. When a `generateMigrations` run produces `src/main/resources/db/migration/V<...>.sql`, open that file in the editor, right-click → **Execute → <your H2 data source>**.
4. Refresh the data source to verify the schema matches the table object(s).
5. Re-run `generateMigrations` — it should now generate **0 migrations** (DB is in sync with code).

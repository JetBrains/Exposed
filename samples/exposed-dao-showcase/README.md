# exposed-dao-showcase

The same application implemented twice — once with the JDBC DAO and once with the R2DBC DAO — so the
two APIs can be compared side by side.

The domain is a small brokerage: brokers, clients, portfolios, instruments, tags, and trades. It
exercises the relationship types that differ most between the drivers: many-to-one references,
optional references, one-to-many referrers, and many-to-many links.

| Module  | Artifacts                                       |
|---------|-------------------------------------------------|
| `jdbc`  | `exposed-jdbc` + `exposed-dao`                  |
| `r2dbc` | `exposed-r2dbc` + `exposed-dao-r2dbc`           |

Both use H2 in memory, so no database setup is required.

## Requirements

Exposed **1.3.2 or later**: `exposed-dao-r2dbc` is not published in earlier releases.

<!-- If you are building against a local Exposed checkout instead, publish it with
     `./gradlew publishToMavenLocal` from the repository root, add `mavenLocal()` to the repositories
     block in settings.gradle.kts, and set `exposed-version` in gradle/libs.versions.toml to match. -->

## Running

```bash
./gradlew :jdbc:run
./gradlew :r2dbc:run
```

Each module starts a Ktor server on port 8080, so run one at a time. Seed the database first:

```bash
curl -X POST http://localhost:8080/seed
```

Then try the endpoints, for example:

```bash
curl http://localhost:8080/clients/1
curl http://localhost:8080/clients/1/trades
curl http://localhost:8080/instruments
```

## What to compare

The table definitions are identical between the two modules — they come from `exposed-core` and are
shared by both drivers. The differences are concentrated in:

- **`model/entities/`** — reference properties are `var` under JDBC and `val` under R2DBC.
- **`routes/`** — `transaction { }` becomes `suspendTransaction { }`, reference reads become `x()`,
  writes become `x.set(...)`, and collections need `.toList()`.
- **`routes/SeedRoutes.kt`** — the R2DBC version uses the non-suspending `new { }`, which schedules an
  insert instead of issuing it, so several rows are written by one batched statement. Where a
  ready-to-use entity with a generated id is needed, it uses `newSuspend { }` instead.

For a step-by-step account of every difference, see the
[JDBC DAO to R2DBC DAO migration guide](https://www.jetbrains.com/help/exposed/migration-guide-dao-jdbc-to-r2dbc.html).

## Note on the R2DBC DAO

`exposed-dao-r2dbc` is an experimental preview. Its API may change in incompatible ways between
releases, which is why the `r2dbc` module opts in to `@ExperimentalR2dbcDaoApi` in its build file.

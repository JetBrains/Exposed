# exposed-r2dbc-dao-sample

A Ktor application built on the Exposed **R2DBC DAO** — `exposed-r2dbc` + `exposed-dao-r2dbc`.

The domain is a small brokerage: brokers, clients, portfolios, instruments, tags, and trades. It covers the relationship kinds a DAO application normally needs:
many-to-one references, optional references, one-to-many referrers, and many-to-many links.

H2 runs in memory, so no database setup is required.

## Requirements

JDK 17 or later.

`exposed-dao-r2dbc` is not on Maven Central yet. Until the first release that contains it, add
`includeBuild("../..")` to `settings.gradle.kts` to build the sample against this repository.

## Running

```bash
./gradlew run
```

The server listens on port 8081. Seed the database first:

```bash
curl -X POST http://localhost:8081/seed
```

Then try the endpoints, for example:

```bash
curl http://localhost:8081/clients/1
curl http://localhost:8081/clients/1/trades
curl http://localhost:8081/instruments
```

## Where to look

| Path                   | What it shows                                                                       |
|------------------------|-------------------------------------------------------------------------------------|
| `model/tables/`        | Plain `exposed-core` table objects                                                  |
| `model/entities/`      | Entity classes: reference properties are `val` and are read by invoking them, `x()` |
| `routes/`              | `suspendTransaction { }` blocks; collections are flows, so they are collected       |
| `routes/SeedRoutes.kt` | The non-suspending `new { }`, which schedules inserts that flush as one batch       |
| `plugins/Database.kt`  | Connecting, creating the schema, and subscribing an `EntityHook`                    |

## Note on the R2DBC DAO

`exposed-dao-r2dbc` is an experimental preview: its API may change in incompatible ways between releases, which is why the build file opts in to
`@ExperimentalR2dbcDaoApi`.

## See also

[`exposed-jdbc-dao-sample`](../exposed-jdbc-dao-sample) is the same application built on the JDBC DAO. It listens on port 8080, so both samples can run at the same
time. What changes between the two APIs is covered by the JDBC DAO to R2DBC DAO migration guide in the Exposed documentation.

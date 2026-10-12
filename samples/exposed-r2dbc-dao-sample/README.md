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

`exposed-dao-r2dbc` is an experimental preview: its API may change in incompatible ways between releases. Every declaration is annotated
`@ExperimentalR2dbcDaoApi`, so using it requires opting in. This sample does it once for the whole module, in `build.gradle.kts`:

```kotlin
kotlin {
    compilerOptions {
        optIn.add("org.jetbrains.exposed.v1.dao.r2dbc.ExperimentalR2dbcDaoApi")
    }
}
```

The narrower options are `@file:OptIn(ExperimentalR2dbcDaoApi::class)` at the top of a file, or `@OptIn(ExperimentalR2dbcDaoApi::class)` on a single declaration.
Marking your own declaration `@ExperimentalR2dbcDaoApi` instead propagates the requirement to its callers.

## See also

[`exposed-jdbc-dao-sample`](../exposed-jdbc-dao-sample) is the same application built on the JDBC DAO. It listens on port 8080, so both samples can run at the same
time. They do not share data: each runs in its own JVM against its own in-memory database (`broker_jdbc` and `broker_r2dbc`), so seeding one leaves the other empty.

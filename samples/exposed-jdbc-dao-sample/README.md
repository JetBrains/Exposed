# exposed-jdbc-dao-sample

A Ktor application built on the Exposed **JDBC DAO** — `exposed-jdbc` + `exposed-dao`.

The domain is a small brokerage: brokers, clients, portfolios, instruments, tags, and trades. It covers
the relationship kinds a DAO application normally needs: many-to-one references, optional references,
one-to-many referrers, and many-to-many links.

H2 runs in memory, so no database setup is required.

## Requirements

JDK 17 or later, and Exposed **1.4.0 or later**.

<!-- To build against a local Exposed checkout instead, publish it with `./gradlew publishToMavenLocal`
     from the repository root, add `mavenLocal()` to the repositories block in settings.gradle.kts, and
     set `exposed-version` in gradle/libs.versions.toml to match. -->

## Running

```bash
./gradlew run
```

The server listens on port 8080. Seed the database first:

```bash
curl -X POST http://localhost:8080/seed
```

Then try the endpoints, for example:

```bash
curl http://localhost:8080/clients/1
curl http://localhost:8080/clients/1/trades
curl http://localhost:8080/instruments
```

## Where to look

| Path                    | What it shows                                                            |
|-------------------------|--------------------------------------------------------------------------|
| `model/tables/`         | Plain `exposed-core` table objects, shared by both drivers                 |
| `model/entities/`       | Entity classes: column properties and all four relationship kinds          |
| `routes/`               | `transaction { }` blocks that read and write entities                      |
| `routes/SeedRoutes.kt`  | Creating a whole object graph in one go                                    |
| `plugins/Database.kt`   | Connecting, creating the schema, and subscribing an `EntityHook`           |

## The R2DBC counterpart

[`exposed-r2dbc-dao-sample`](../exposed-r2dbc-dao-sample) is the same application written against
`exposed-r2dbc` + `exposed-dao-r2dbc`. Diffing the two `src` trees shows exactly what a migration
touches; the
[JDBC DAO to R2DBC DAO migration guide](https://www.jetbrains.com/help/exposed/migration-guide-dao-jdbc-to-r2dbc.html)
explains each difference.

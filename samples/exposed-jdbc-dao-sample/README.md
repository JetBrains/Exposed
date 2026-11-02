# exposed-jdbc-dao-sample

A Ktor application built on the Exposed **JDBC DAO** — `exposed-jdbc` + `exposed-dao`.

The domain is a small brokerage: brokers, clients, portfolios, instruments, tags, and trades. It covers the relationship kinds a DAO application normally needs:
many-to-one references, optional references, one-to-many referrers, and many-to-many links.

H2 runs in memory, so no database setup is required.

## Requirements

JDK 17 or later. Everything else resolves from Maven Central.

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

| Path                   | What it shows                                                     |
|------------------------|-------------------------------------------------------------------|
| `model/tables/`        | Plain `exposed-core` table objects                                |
| `model/entities/`      | Entity classes: column properties and all four relationship kinds |
| `routes/`              | `transaction { }` blocks that read and write entities             |
| `routes/SeedRoutes.kt` | Creating a whole object graph in one go                           |
| `plugins/Database.kt`  | Connecting, creating the schema, and subscribing an `EntityHook`  |

## See also

[`exposed-r2dbc-dao-sample`](../exposed-r2dbc-dao-sample) is the same application built on the R2DBC DAO. It listens on port 8081, so both samples can run at the same
time. They do not share data: each runs in its own JVM against its own in-memory database (`broker_jdbc` and `broker_r2dbc`), so seeding one leaves the other empty.

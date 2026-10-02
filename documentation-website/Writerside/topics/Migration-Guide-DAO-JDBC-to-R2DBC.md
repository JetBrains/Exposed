<show-structure for="chapter,procedure" depth="2"/>
<link-summary>
Learn how to migrate a DAO-based application from JDBC to R2DBC.
</link-summary>

# Migrating from JDBC DAO to R2DBC DAO

Exposed 1.6.0 introduces experimental R2DBC support for the DAO API through the `exposed-dao-r2dbc` artifact. In this
topic, you will learn how to migrate an existing JDBC DAO application to R2DBC DAO.

The migration changes the transport and DAO artifacts and requires source-code changes.

<include from="lib.topic" element-id="r2dbc-dao-experimental-note"/>

## Prerequisites

Before you start, check whether your application uses JDBC DAO features that are not available in R2DBC DAO.
If it does, you must change those parts of the application before completing the migration.

| JDBC DAO                                     | R2DBC DAO                                                                                |
|----------------------------------------------|------------------------------------------------------------------------------------------|
| `ImmutableEntityClass`                       | Not available.                                                                           |
| `ImmutableCachedEntityClass`                 | Not available.                                                                           |
| `EntityClass.view()` and `View`              | Not available. Use `find()` instead.                                                     |
| `EntityClass.findWithCacheCondition()`       | Not available.                                                                           |
| `EntityClass.testCache(predicate)`           | Not available. Use `EntityClass.testCache(entityId)` instead.                            |
| `Entity.writeValues`, `storeWrittenValues()` | Not available. Pending values belong to the transaction's `EntityCache` instead.         |
| `EntityCache.maxEntitiesToStore`             | Not available. The R2DBC entity cache does not evict entities.                           |
| `EntityCache.invalidateGlobalCaches()`       | Not available. This functionality is part of the `ImmutableCachedEntityClass` machinery. |
| `warmUpReferences()`                         | Available, but does not support the `forUpdate` parameter.                               |

## Update dependencies

Replace the JDBC transport and DAO artifacts with their R2DBC counterparts:

<compare first-title="JDBC DAO" second-title="R2DBC DAO" type="top-bottom">

```kotlin
implementation("org.jetbrains.exposed:exposed-core:%exposed_version%")
implementation("org.jetbrains.exposed:exposed-jdbc:%exposed_version%")
implementation("org.jetbrains.exposed:exposed-dao:%exposed_version%")
```

```kotlin
implementation("org.jetbrains.exposed:exposed-core:%exposed_version%")
implementation("org.jetbrains.exposed:exposed-r2dbc:%exposed_version%")
implementation("org.jetbrains.exposed:exposed-dao-r2dbc:%exposed_version%")
```

</compare>

> Do not keep both JDBC and R2DBC DAO artifacts in the same source set. They define classes, such as `Entity`, `EntityClass`, 
> and `EntityCache` with the same simple names.
> 
{style="note"}

Use the R2DBC driver that corresponds to your database, for example:

<compare first-title="JDBC DAO" second-title="R2DBC DAO" type="top-bottom">

```kotlin
implementation("com.h2database:h2:%h2_db_version%")
```

```kotlin
implementation("io.r2dbc:r2dbc-h2:%h2_r2dbc_version%")
```

</compare>

> For the complete list of supported databases and their corresponding driver dependencies, see [](Working-with-Database.md).
>
{style="tip"}

## Opt in to the experimental API

The R2DBC DAO API is experimental and requires an opt-in. To opt in for the entire module, add `ExperimentalR2dbcDaoApi`
to the Kotlin compiler options:

```kotlin
kotlin {
    compilerOptions {
        optIn.add("org.jetbrains.exposed.v1.dao.r2dbc.ExperimentalR2dbcDaoApi")
    }
}
```

To opt in at a narrower scope, use `@OptIn(ExperimentalR2dbcDaoApi::class)` instead.

## Update imports

The R2DBC DAO API is located in the `org.jetbrains.exposed.v1.dao.r2dbc` package.

For DAO, replace the `org.jetbrains.exposed.v1.dao.` package with `org.jetbrains.exposed.v1.dao.r2dbc.` across your imports.
For example:

| JDBC DAO                                      | R2DBC DAO                                           |
|-----------------------------------------------|-----------------------------------------------------|
| `org.jetbrains.exposed.v1.dao.IntEntity`      | `org.jetbrains.exposed.v1.dao.r2dbc.IntEntity`      |
| `org.jetbrains.exposed.v1.dao.IntEntityClass` | `org.jetbrains.exposed.v1.dao.r2dbc.IntEntityClass` |
| `org.jetbrains.exposed.v1.dao.entityCache`    | `org.jetbrains.exposed.v1.dao.r2dbc.entityCache`    |
| `org.jetbrains.exposed.v1.dao.load`           | `org.jetbrains.exposed.v1.dao.r2dbc.load`           |
| `org.jetbrains.exposed.v1.dao.with`           | `org.jetbrains.exposed.v1.dao.r2dbc.with`           |
| `org.jetbrains.exposed.v1.dao.Referrers`      | `org.jetbrains.exposed.v1.dao.r2dbc.Referrers`      |
| `org.jetbrains.exposed.v1.dao.InnerTableLink` | `org.jetbrains.exposed.v1.dao.r2dbc.InnerTableLink` |

Table definitions don't require changes. They are provided by the `exposed-core` module and are shared by both JDBC and
R2DBC drivers.

## Update transaction handling

R2DBC DAO operations are suspending, so the functions that contain them must be `suspend` when required.
Replace the `transaction()` function with `suspendTransaction()`:

<compare first-title="JDBC DAO" second-title="R2DBC DAO">

```kotlin
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

val client = transaction {
    Client.findById(id)
}
```

```kotlin
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

val client = suspendTransaction {
    Client.findById(id)
}
```

</compare>

> Do not use `org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction` with R2DBC DAO. It is intended for suspending 
> while using a blocking JDBC connection and is unrelated to R2DBC.
>
{style="warning"}

## Update entity creation

R2DBC DAO provides two entity creation functions:

| Function       | Suspends | When the `INSERT` is issued | ID available after the call                                                    |
|----------------|----------|-----------------------------|--------------------------------------------------------------------------------|
| `new()`        | No       | At the next flush           | Only if the ID is explicitly assigned or otherwise available without an insert |
| `newSuspend()` | Yes      | Before the call returns     | Yes, including generated IDs                                                   |

### Use `newSuspend()` for immediate inserts

The `newSuspend()` function suspends and issues the `INSERT` before returning, so a generated ID is available immediately.

Use `newSuspend()` when the entity must be inserted before the function returns:

<compare first-title="JDBC DAO" second-title="R2DBC DAO">

```kotlin
val client = Client.new {
    name = "Alice Johnson"
}
val id = client.id.value
```

```kotlin
val client = Client.newSuspend {
    name = "Alice Johnson"
}
val id = client.id.value
```

</compare>

### Use `new()` for deferred and batch inserts

The `new()` function does not issue the `INSERT` immediately. Instead, it schedules the insert in the transaction's 
entity cache.
This can be useful when you want to create multiple entities and flush them together:

```kotlin
suspendTransaction {
    val tags = listOf("tech", "finance", "energy").map {
        name -> Tag.new {
            this.name = name
        }
    }
    
    flushCache()
    
    val ids = tags.map {
        it.id.value
    }
}
```

The pending inserts are flushed when required by subsequent database operations or when the transaction commits. Call
`flushCache()` when you need to explicitly control when the pending inserts are sent to the database.

> The `new()` function exists in both JDBC and R2DBC DAO but its behavior differs. During migration, review every `new{}`
> call that expects the row or its generated ID to exist immediately.
>
{style="note"}

## Update reference properties

R2DBC reference properties use an accessor because reading a reference can require a suspending database operation.

Change the `referencedOn` and `optionalReferencedOn` properties from `var` to `val`. Read the reference by calling the
accessor and update it with the `.set()` function:

<compare first-title="JDBC DAO" second-title="R2DBC DAO" type="top-bottom">

```kotlin
var broker by Broker referencedOn Clients.broker
var portfolio by Portfolio optionalReferencedOn Trades.portfolio

val name = client.broker.name
client.broker = otherBroker
trade.portfolio = null
```

```kotlin
val broker by Broker referencedOn Clients.broker
val portfolio by Portfolio optionalReferencedOn Trades.portfolio

val name = client.broker().name
client.broker.set(otherBroker)
trade.portfolio.set(null)
```

</compare>

> Leaving the property as `var` causes a compilation error because the R2DBC reference accessor does not provide the
> required `setValue()` operator.
>
{style="note"}

> `client.broker` returns the reference accessor, not the `Broker` entity. Use `client.broker()` to retrieve the
> referenced entity.
> 
{style="note"}

The `backReferencedOn` and `optionalBackReferencedOn` properties remain unchanged. The `via` property remains a `var`
and takes a `SizedCollection`.

## Collect DAO flows

In R2DBC DAO, `SizedIterable` extends `Flow`. As a result, DAO collections are represented as flows rather than Kotlin
collections.

This applies to DAO collections such as referrers, `via` relations, `all()`, and `find()`.

<compare first-title="JDBC DAO" second-title="R2DBC DAO" type="top-bottom">

```kotlin
val names = client.portfolios.map { it.name }
```

```kotlin
val names = client.portfolios.toList().map { it.name }
```

</compare>

Terminal operations such as `count()`, `first()`, `firstOrNull()`, and `single()` do not require any additional changes.

> If `kotlinx.coroutines.flow.map` is in scope, `client.portfolios.map()` still compiles but
> returns a `Flow` instead of a `List`. Add `.toList()` before any operator that
> should produce a collection.
>
{style="warning"}

## Attach entities across transactions

Unline the JDBC DAO, in R2DBC DAO an entity loaded or created in one transaction is not automatically registered with
another R2DBC transaction. This is because that requires a database check and a property setter cannot suspend.

To reuse entities across transactions, call the `attach()` function before modifying an entity from another transaction:

<compare first-title="JDBC DAO" second-title="R2DBC DAO">

```kotlin
val item = transaction {
    Item.new {
        name = "foo"
    }
}

transaction {
    item.name = "bar"
}
```

```kotlin
val item = suspendTransaction {
    Item.newSuspend {
        name = "foo"
    }
}

suspendTransaction {
    Item.attach(item)
    item.name = "bar"
}
```

</compare>

The `attach()` function throws `EntityNotFoundException` if the row no longer exists. If the current transaction already
tracks a different instance of the same row with unflushed changes, `attach()` does not silently replace it.
To replace the tracked instance and discard its unflushed changes, you can use `attach(item, force = true)`.


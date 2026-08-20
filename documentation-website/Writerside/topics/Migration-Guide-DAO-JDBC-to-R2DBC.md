<show-structure for="chapter,procedure" depth="2"/>

# Migrating from the JDBC DAO to the R2DBC DAO

How to move a DAO-based application from `exposed-dao` (JDBC only) to `exposed-dao-r2dbc`.

`exposed-dao-r2dbc` ships alongside `exposed-dao` in the same Exposed release, so this is a change of artifact
rather than an API-version upgrade. It is a recent addition, though: if your build pins an older Exposed release,
bump the version first. If you are also coming from a `0.x` release, apply
[Migrating from 0.61.0 to 1.0.0](Migration-Guide-1-0-0.md) first.

<warning>
The R2DBC DAO is an experimental preview: its API may change in incompatible ways between releases.
</warning>

## Step 0. Check for blockers

These JDBC DAO features have no R2DBC equivalent. If you use one, you cannot finish the migration:

| Not available                            | Note                                                             |
|------------------------------------------|------------------------------------------------------------------|
| `ImmutableEntityClass`                   | —                                                                |
| `ImmutableCachedEntityClass`             | —                                                                |
| `EntityClass.view { }` and `View`        | use `find { }` instead                                           |
| `EntityClass.findWithCacheCondition()`   | —                                                                |
| `EntityClass.testCache(predicate)`       | the `EntityID` overload does exist                               |
| `EntityClass.wrapRows(rows, alias)`      | the `Alias`/`QueryAlias` overloads are missing                   |
| `Entity.lookupInReadValues()`            | —                                                                |
| `Entity.writeValues`, `storeWrittenValues()` | pending values belong to the transaction's `EntityCache` instead |
| `EntityCache.maxEntitiesToStore`         | the R2DBC entity cache does not evict                            |
| `EntityCache.invalidateGlobalCaches()`   | part of the `ImmutableCachedEntityClass` machinery               |
| `warmUpReferences()`                     | the `forUpdate` parameter was dropped                            |

## Step 1. Swap dependencies

<compare first-title="JDBC DAO" second-title="R2DBC DAO">

```kotlin
implementation("org.jetbrains.exposed:exposed-core:%exposed_version%")
implementation("org.jetbrains.exposed:exposed-jdbc:%exposed_version%")
implementation("org.jetbrains.exposed:exposed-dao:%exposed_version%")
implementation("com.h2database:h2:%h2_db_version%")
```

```kotlin
implementation("org.jetbrains.exposed:exposed-core:%exposed_version%")
implementation("org.jetbrains.exposed:exposed-r2dbc:%exposed_version%")
implementation("org.jetbrains.exposed:exposed-dao-r2dbc:%exposed_version%")
implementation("io.r2dbc:r2dbc-h2:%h2_r2dbc_version%")
```

</compare>

Do not keep both DAO artifacts in one source set — they define `Entity`, `EntityClass`, and `EntityCache` with the
same simple names, so wildcard imports collide.

## Step 2. Opt in

Almost the whole API is annotated `@ExperimentalR2dbcDaoApi`, so opt in once per module:

```kotlin
kotlin {
    compilerOptions {
        optIn.add("org.jetbrains.exposed.v1.dao.r2dbc.ExperimentalR2dbcDaoApi")
    }
}
```

## Step 3. Update imports

Add `.r2dbc` to the DAO package. Relationship types and `load`/`with` also move into a `relationships` subpackage.

| JDBC DAO                                      | R2DBC DAO                                                         |
|-----------------------------------------------|-------------------------------------------------------------------|
| `org.jetbrains.exposed.v1.dao.IntEntity`      | `org.jetbrains.exposed.v1.dao.r2dbc.IntEntity`                    |
| `org.jetbrains.exposed.v1.dao.IntEntityClass` | `org.jetbrains.exposed.v1.dao.r2dbc.IntEntityClass`               |
| `org.jetbrains.exposed.v1.dao.entityCache`    | `org.jetbrains.exposed.v1.dao.r2dbc.entityCache`                  |
| `org.jetbrains.exposed.v1.dao.load`           | `org.jetbrains.exposed.v1.dao.r2dbc.relationships.load`           |
| `org.jetbrains.exposed.v1.dao.with`           | `org.jetbrains.exposed.v1.dao.r2dbc.relationships.with`           |
| `org.jetbrains.exposed.v1.dao.Referrers`      | `org.jetbrains.exposed.v1.dao.r2dbc.relationships.Referrers`      |
| `org.jetbrains.exposed.v1.dao.InnerTableLink` | `org.jetbrains.exposed.v1.dao.r2dbc.relationships.InnerTableLink` |

Table definitions need no changes: they come from `exposed-core` and are shared by both drivers.

## Step 4. Replace `transaction` with `suspendTransaction`

Every DAO call must run inside it, and the enclosing functions become `suspend`.

<compare first-title="JDBC DAO" second-title="R2DBC DAO">

```kotlin
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

val client = transaction { Client.findById(id) }
```

```kotlin
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

val client = suspendTransaction { Client.findById(id) }
```

</compare>

<warning>
Do not use <code>org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction</code>. It suspends while holding a
blocking JDBC connection and is unrelated.
</warning>

## Step 5. Rename `new { }` to `newSuspend { }`

Issuing the `INSERT` is the part that has to suspend, so the single JDBC factory is split in two. `new { }` exists
in the R2DBC DAO too, but it only *schedules* the insert — `newSuspend { }` is the one with the JDBC semantics.

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

| Factory          | Suspends | `INSERT` is issued      | Id right after the call              |
|------------------|----------|-------------------------|--------------------------------------|
| `new { }`        | no       | at the next flush       | only if explicit or client-generated |
| `newSuspend { }` | yes      | before the call returns | populated                            |

<warning>
The name <code>new { }</code> exists in both DAOs with different semantics, so this call site keeps compiling and
silently changes behaviour: the row is not written when it returns and <code>entity.id.value</code> throws. Search
for <code>.new {</code> and rename every occurrence that needs the row, or the id, to be there.
</warning>

Once the migration is done, `new { }` is worth a second look in the other direction: it is the only factory callable
without a coroutine, its entity can already be read, written, and used as the target of a reference, and several
pending inserts flush as one batch. The next flush — an explicit `flushCache()`, any other statement in the
transaction, or the commit — issues them.

## Step 6. Change reference properties to `val`

`referencedOn` and `optionalReferencedOn` now return an accessor, because reading a reference has to suspend and a
property getter cannot. So: declare `val`, read with `()`, write with `set()`.

<compare first-title="JDBC DAO" second-title="R2DBC DAO">

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

Leaving it as `var` fails to compile with `Property delegate must have a 'setValue(...)' method`.

<warning>
Reading without the parentheses also compiles. <code>val b = client.broker</code> gives you the accessor, not the
<code>Broker</code>. Always write <code>client.broker()</code>.
</warning>

`backReferencedOn` and `optionalBackReferencedOn` work the same way. `via` is unchanged — it stays a `var` and still
takes a `SizedCollection`.

## Step 7. Collect collections

`SizedIterable` now extends `Flow`, so referrers, `via` relations, `all()`, and `find { }` are flows.

<compare first-title="JDBC DAO" second-title="R2DBC DAO">

```kotlin
val names = client.portfolios.map { it.name }
```

```kotlin
val names = client.portfolios.toList().map { it.name }
```

</compare>

`count()`, `first()`, `firstOrNull()`, and `single()` need no change.

<warning>
If <code>kotlinx.coroutines.flow.map</code> is in scope, <code>client.portfolios.map { }</code> still compiles but
returns a <code>Flow</code> instead of a <code>List</code>. Add <code>.toList()</code> before any operator that
should produce a collection.
</warning>

## Step 8. Add `attach()` across transactions

The JDBC DAO silently re-registers an entity on first write in a new transaction. The R2DBC DAO cannot, because that
check is a database round trip and a property setter cannot suspend. Call `attach()` yourself, or the write throws.

<compare first-title="JDBC DAO" second-title="R2DBC DAO">

```kotlin
val item = transaction { Item.new { name = "foo" } }

transaction {
    item.name = "bar"
}
```

```kotlin
val item = suspendTransaction { Item.newSuspend { name = "foo" } }

suspendTransaction {
    Item.attach(item)
    item.name = "bar"
}
```

</compare>

`attach()` throws `EntityNotFoundException` if the row is gone. It also refuses to replace a *different*
instance of the same row that this transaction already tracks with unflushed changes, since that would drop them
silently — pass `attach(item, force = true)` to discard them deliberately.

## Optional: batch inserts with `new { }`

`newSuspend { }` costs one `INSERT` per entity. `new { }` only schedules, so creating several entities and flushing
once persists them all with a single batched `INSERT` per table:

```kotlin
suspendTransaction {
    val tags = listOf("tech", "finance", "energy")
        .map { name -> Tag.new { this.name = name } }

    flushCache()

    // one INSERT was issued, and every tag now has its id
    val ids = tags.map { it.id.value }
}
```

<warning>
Batching only holds as long as nothing else touches the database in between: an intervening
<code>newSuspend { }</code> splits the batch, another <code>new { }</code> joins it. The explicit
<code>flushCache()</code> only decides <i>where</i> the statement goes out — any other statement in the
transaction, or its commit, flushes the pending inserts just as well.
</warning>

## Samples

The same application written against each DAO, which is the quickest way to compare:

* [`samples/exposed-dao-showcase/jdbc`](https://github.com/JetBrains/Exposed/tree/main/samples/exposed-dao-showcase/jdbc)
* [`samples/exposed-dao-showcase/r2dbc`](https://github.com/JetBrains/Exposed/tree/main/samples/exposed-dao-showcase/r2dbc)

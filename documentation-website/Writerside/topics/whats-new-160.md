# What's new in Exposed 1.6.0

## R2DBC DAO support

<primary-label ref="experimental"/>

Exposed 1.6.0 introduces experimental R2DBC support for the DAO API through the `exposed-dao-r2dbc` artifact. You can
now use Exposed DAO with R2DBC for non-blocking database access while retaining the DAO entity model and familiar
query and relationship APIs.

R2DBC DAO provides the same core DAO concepts as JDBC DAO, including [entities](dao-entity-definition.md), 
[references](dao-relationships.md), [queries](dao-crud-operations.md), and [transactions](Transactions.md),
with APIs adapted for suspending database operations.

To use R2DBC DAO, replace the JDBC DAO dependency with `exposed-dao-r2dbc` and use the `suspendTransaction()` function 
for database operations:

```kotlin
suspendTransaction {
    val film = StarWarsFilmEntity.newSuspend {
        name = "The Last Jedi"
    }
    println(film.id.value)
}
```

R2DBC DAO introduces several API differences required by its suspending nature:

* While the `new()` function schedules the insert until the next flush, the `newSuspend()` function
  creates and immediately inserts an entity before returning it. The `newSuspend()` function should be used if the 
  returned entity's ID or other database-generated values need to be accessed before scheduled flushing. Continuing to
  use `new()` and accessing these fields throws an exception.
* Reference properties, such as `referencedOn`, use accessors, so [a referenced entity is retrieved by invocation](dao-relationships.md#accessing-data)
  such as `filmRating.film()`. Referenced entities are set by invoking the `.set()` function.
* DAO collections are exposed as Kotlin `Flow`, so use terminal operations such as `.toList()` when a collection is required.
* Entities created or loaded in one transaction [must be attached using the `attach()` function](dao-crud-operations.md#update-later)
  before they can be modified in another R2DBC transaction.

> For a complete overview of the required changes, see the [R2DBC DAO migration guide](migrating-from-jdbc-to-r2dbc-dao.md).
> 
{style="tip"}

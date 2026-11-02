<show-structure for="chapter,procedure" depth="2"/>

# What's new in Exposed 1.6.0

## R2DBC DAO support

<primary-label ref="experimental"/>

Exposed 1.6.0 introduces experimental R2DBC support for the DAO API through the `exposed-dao-r2dbc` artifact. You can
now use Exposed DAO with R2DBC for non-blocking database access while retaining the DAO entity model and familiar
query and relationship APIs.

R2DBC DAO provides the same core DAO concepts as JDBC DAO, including [entities](dao-entity-definition.md), 
[references](dao-relationships.md), [queries](dao-crud-operations.md), and [transactions](Transactions.md),
with APIs adapted for suspending database operations.

For example, to create and insert an entity with R2DBC DAO, use the `newSuspend()` function inside a `suspendTransaction {}`
block:

```kotlin
suspendTransaction {
    val film = StarWarsFilmEntity.newSuspend {
        name = "The Last Jedi"
    }
    println(film.id.value)
}
```

>  For R2DBC DAO, import `suspendTransaction()` from `org.jetbrains.exposed.v1.r2dbc.transactions`.
> 
> The `suspendTransaction()` function in `org.jetbrains.exposed.v1.jdbc.transactions` is intended for suspending operations with a blocking JDBC connection.
> 
{style="note"}

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

## Non-null column transformations

You can now use the new [`.nonNullTransform()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.core/-table/non-null-transform.html)
function to represent a nullable database column as a non-nullable Exposed column.

Use this function when a database column allows `null`, but your application has a meaningful non-null representation
for that value.

In the following example, the `UNSCHEDULED` enum entry represents a missing meal time:

```kotlin
enum class Meal {
    BREAKFAST,
    LUNCH,
    DINNER,
    UNSCHEDULED
}

class MealTimeNonNullTransformer : ColumnTransformer<LocalTime?, Meal> {
    override fun wrap(value: LocalTime?): Meal = when {
        value == null -> Meal.UNSCHEDULED
        value.hour < 10 -> Meal.BREAKFAST
        value.hour < 15 -> Meal.LUNCH
        else -> Meal.DINNER
    }

    override fun unwrap(value: Meal): LocalTime? = when (value) {
        Meal.BREAKFAST -> LocalTime(8, 0)
        Meal.LUNCH -> LocalTime(12, 0)
        Meal.DINNER -> LocalTime(18, 0)
        Meal.UNSCHEDULED -> null
    }
}

object Meals : Table() {
    val mealTime: Column<Meal> = time("meal_time")
        .nullable()
        .nonNullTransform(MealTimeNonNullTransformer())
}
```

> For more information, see [](column-transformation.md).
> 
{style="tip"}

## Tableless SELECT queries

Exposed 1.6.0 introduces tableless `SELECT` queries, allowing you to select expressions without specifying a table or
a `FROM` clause.

Use the top-level `select()` function to create a query that returns a single row by default. A `WHERE` condition can 
filter out that row, resulting in an empty result:

```kotlin
val numberOne = intLiteral(1)
val query: Query = select(numberOne, CurrentTimestamp)
val one: Int = query.single()[numberOne]

val query2 = select(listOf(intLiteral(1), stringLiteral("ready")))

val noRows = select(intLiteral(1)).where { Op.FALSE }
```

For queries that select a single expression, use the `selectValue()` function to execute the query immediately and return
the typed result without requiring an expression as a result-row key. The function also accepts supported Kotlin scalar
values directly and wraps them as SQL literals:

```kotlin
val one: Int = selectValue(1)
val ready = selectValue("ready")
val serverTime = selectValue(CurrentTimestamp)
```

Tableless queries are regular `Query` objects and support existing query operations, such as filtering, set operations,
derived-table aliases, and insert-from-select operations.

> For more information, see [](DSL-Querying-data.topic#tableless-select).
>
{style="tip"}

## Configure multiple table packages in migration plugins

You can configure the [Exposed Gradle](Exposed-gradle-plugin.md) and [Maven](exposed-maven-plugin.md) plugins to use multiple table packages when generating
migrations. This is useful when your table definitions are organized into separate packages.

For example, in the Gradle configuration, use the `tablesPackage` property to specify a single package or the
`tablesPackages` property to specify multiple packages:

```kotlin
exposed {
    migrations {
        tablesPackage = "com.example.db.tables"
        // or
        tablesPackages = listOf(
            "com.example.db.auth",
            "com.example.db.billing",
        )
        // ...
    }
}
```

> For more information, see the [Exposed Gradle plugin](Exposed-gradle-plugin.md#configuration) and [Exposed Maven plugin](exposed-maven-plugin.md#configuration)
> documentation.
>
{style="tip"}

# Column transformation

Column transformations allow you to define custom transformations between a database column's types and your application's
data types. This can be particularly useful when you need to store data in one format but work with it in a different format
within your application.

## Basic usage

To apply custom transformations to a table column, use the [`.transform()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.core/-table/transform.html)
function.

The transformation consists of two operations:

* The `wrap()` function converts a value read from the database into the type used by your application.
* The `unwrap()` function converts an application value into the type used by the database for storage.

In the following example, the `Meals` table stores meal times as `LocalTime` values but exposes them in the application
as `Meal` values:

```kotlin
enum class Meal {
    BREAKFAST,
    LUNCH,
    DINNER
}

object Meals : Table() {
    val mealTime: Column<Meal> = time("meal_time")
        .transform(
            wrap = {
                when {
                    it.hour < 10 -> Meal.BREAKFAST
                    it.hour < 15 -> Meal.LUNCH
                    else -> Meal.DINNER
                }
            },
            unwrap = {
                when (it) {
                    Meal.BREAKFAST -> LocalTime(8, 0)
                    Meal.LUNCH -> LocalTime(12, 0)
                    Meal.DINNER -> LocalTime(18, 0)
                }
            }
        )
}
```

Within the `.transform()` function:

* The `wrap()` function transforms the stored `LocalTime` values into `Meal` enums. It checks the hour of the stored time
  and returns the corresponding meal type.
* The `unwrap()` function transforms `Meal` enums back into `LocalTime` values for storage in the database.

## Reusing a transformation

For transformations that are used by multiple columns or require more complexity, you can define a custom [`ColumnTransformer`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.core/-column-transformer/index.html)
and pass it as an argument to the `.transform()` function:

```kotlin
class MealTimeTransformer : ColumnTransformer<LocalTime, Meal> {
    override fun wrap(value: LocalTime): Meal = when {
        value.hour < 10 -> Meal.BREAKFAST
        value.hour < 15 -> Meal.LUNCH
        else -> Meal.DINNER
    }

    override fun unwrap(value: Meal): LocalTime = when (value) {
        Meal.BREAKFAST -> LocalTime(8, 0)
        Meal.LUNCH -> LocalTime(12, 0)
        Meal.DINNER -> LocalTime(18, 0)
    }
}

object Meals : Table() {
    val mealTime: Column<Meal> = time("meal_time").transform(MealTimeTransformer())
}
```

> Column transformations are applied on every access of the table column. To maintain performance efficiency, it is recommended
> to avoid heavy transformations in this context.
>
{style="note"}

## Null transform

The [`.nullTransform()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.core/-table/null-transform.html)
function applies a transformation that allows a non-nullable database column to be represented as a nullable column in Kotlin.

This transformation does not change the column's definition in the database and remains non-nullable. Instead, it allows
the transformation to map particular non-null database values to `null` in the application. This can be useful for
converting an empty string from a non-nullable text column, empty lists, or negative IDs, to `null`.

```kotlin
class MealTimeNullTransformer : ColumnTransformer<LocalTime, Meal?> {
    override fun wrap(value: LocalTime): Meal? = when {
        value.hour == 0 && value.minute == 0 -> null
        value.hour < 10 -> Meal.BREAKFAST
        value.hour < 15 -> Meal.LUNCH
        else -> Meal.DINNER
    }

    override fun unwrap(value: Meal?): LocalTime = when (value) {
        Meal.BREAKFAST -> LocalTime(8, 0)
        Meal.LUNCH -> LocalTime(12, 0)
        Meal.DINNER -> LocalTime(18, 0)
        else -> LocalTime(0, 0)
    }
}

object Meals : Table() {
    val mealTime: Column<Meal?> = time("meal_time").nullTransform(MealTimeNullTransformer())
}
```

* When an ` 00:00 ` value is read from the database, it is converted to `null`.
* When `Meal.BREAKFAST`, `Meal.LUNCH`, or `Meal.DINNER` is written, the value is converted to the
  corresponding `LocalTime`.
* When `null` is written, the value is converted back to `00:00`.

## Non-null transform

The [`.nonNullTransform()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.core/-table/non-null-transform.html)
function allows a nullable database column to be represented as a non-nullable Exposed column.

This is useful when the database allows `null`, but your application has a meaningful non-null representation for that
value. The transformation is responsible for converting `null` database values into an appropriate non-null application
value and for converting that value back when writing to the database.

In the following example, an `UNSCHEDULED` value is added to the `Meal` enum to represent a missing meal time:

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

* When `null` is read from the database, the value is converted to `Meal.UNSCHEDULED`.
* When `Meal.BREAKFAST`, `Meal.LUNCH`, or `Meal.DINNER` is written, the value is converted to the
  corresponding `LocalTime`.
* When `Meal.UNSCHEDULED` is written, the value is converted back to `null`.

The database column remains nullable, while the application uses a non-nullable `Column<Meal>`.

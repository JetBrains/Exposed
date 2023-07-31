# Functions

Exposed provides basic support for classic SQL functions. This topic consists of definitions for those functions, and their 
usage examples. It also explains how to define [custom functions](#custom-functions).

## How to use functions
If you want to retrieve a function result from a query, you have to declare the function as a variable:
```kotlin
val lowerCasedName = FooTable.name.lowerCase()
val lowerCasedNames = FooTable.slice(lowerCasedName).selectAll().map { it[lowerCasedName] }

``` 
Also, functions could be chained and combined:
```kotlin
val trimmedAndLoweredFullName = Concat(FooTable.firstName, stringLiteral(" "), FooTable.lastName).trim().lowerCase()
val fullNames = FooTable.slice(trimmedAndLoweredFullName).selectAll().map { it[trimmedAndLoweredFullName] }

```

## String functions
### LowerCase/UpperCase
Returns a lower-cased/upper-cased string value.
```kotlin
val lowerCasedName = FooTable.name.lowerCase()
val lowerCasedNames = FooTable.slice(lowerCasedName).selectAll().map { it[lowerCasedName] }

```
### Substring
Returns a substring value from the specified start and with the specified length.
```kotlin
val shortenedName = FooTable.name.substring(start = 1, length = 3)
val shortenedNames = FooTable.slice(shortenedName).selectAll().map { it[shortenedName] }

```
### Concat
Returns a string value that concatenates the text representations of all non-null input values, separated by an optional separator.
```kotlin
val userName = concat(stringLiteral("User - "), FooTable.name)
val userNames = FooTable.slice(userName).selectAll().map { it[userName] }

```
### Locate
Returns the index of the first occurrence of a specified substring or 0.
```kotlin
val firstAIndex = FooTable.name.locate("a")
val firstAIndices = FooTable.slice(firstAIndex).selectAll().map { it[firstAIndex] }

```
### CharLength
Returns the length, measured in characters, or `null` if the String value is null.
```kotlin
val nameLength = FooTable.name.charLength()
val nameLengths = FooTable.slice(nameLength).selectAll().map { it[nameLength] }

```

## Aggregating functions
These functions should be used in queries with [[groupBy|DSL#group-by]].
### Min/Max/Average
Returns minimum/maximum/average value and can be applied to any comparable expression:
```kotlin
val minId = FooTable.id.min()
val maxId = FooTable.id.max()
val averageId = FooTable.id.avg()
val (min, max, avg) = FooTable.slice(minId, maxId, averageId).selecAll().map { 
    Triple(it[minId], it[maxId], it[averageId]) 
}

```

## Custom functions
If you can't find your most loved function used in your database (as Exposed provides only basic support for classic SQL functions) you can define your own functions.

Since Exposed 0.15.1 there multiple options to define custom functions:
1. Function without parameters:
```kotlin
val sqrt = FooTable.id.function("SQRT")
```
In SQL representation it will be `SQRT(FooTable.id)`

2. Function with additional parameters:
```kotlin
val replacedName = CustomFunction<String?>("REPLACE", VarCharColumnType(), FooTable.name, stringParam("foo"), stringParam("bar"))

``` 
`CustomFunction` class accepts function name as a first parameter and resulting column type as second, after that you can provide any amount of parameters separated by a comma.

There are also shortcuts for string, long, and datetime functions:
* `CustomStringFunction`
* `CustomLongFunction`
* `CustomDateTimeFunction`

The code above could be simplified to:
```kotlin
val replacedName = CustomStringFunction("REPLACE", FooTable.name, stringParam("foo"), stringParam("bar"))

``` 
For example, the following could be used in SQLite to mimic its `date()` function:
```kotlin
val lastDayOfMonth = CustomDateFunction(
    "date",
    FooTable.dateColumn,
    stringLiteral("start of month"),
    stringLiteral("+1 month"),
    stringLiteral("-1 day")
)
```
3. Function that requires more complex query building:

All functions in Exposed extend the abstract class `Function`, which takes a column type and allows overriding `toQueryBuilder()`. This is what `CustomFunction` actually does, which can be leveraged to create more complex queries.

For example, Exposed provides a `trim()` function that removes leading and trailing whitespace from a String. In MySQL, this is just the default behavior as specifiers can be provided to limit the trim to either leading or trailing, as well as providing a specific substring other than spaces to remove. The custom function below supports this extended behavior:
```kotlin
enum class TrimSpecifier { BOTH, LEADING, TRAILING }

class CustomTrim<T : String?>(
    val expression: Expression<T>,
    val toRemove: Expression<T>?,
    val trimSpecifier: TrimSpecifier
) : Function<T>(TextColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder {
            append("TRIM(")
            append(trimSpecifier.name)
            toRemove?.let { +" $it" }
            append(" FROM ")
            append(expression)
            append(")")
        }
    }
}

fun <T : String?> Expression<T>.customTrim(
    toRemove: Expression<T>? = null,
    specifier: TrimSpecifier = TrimSpecifier.BOTH
): CustomTrim<T> = CustomTrim(this, toRemove, specifier)

transaction {
    FooTable.insert { it[name] = "xxxbarxxx" }

    val leadingXTrim = FooTable.name.customTrim(stringLiteral("x"), TrimSpecifier.LEADING)
    val trailingXTrim = FooTable.name.customTrim(stringLiteral("x"), TrimSpecifier.TRAILING)

    FooTable.slice(leadingXTrim).selectAll() // barxxx
    FooTable.slice(trailingXTrim).selectAll()  // xxxbar
}

```

## Window Functions

Window functions allow calculations across a set of table rows that are related to the current row.

Existing aggregate functions (like `sum()`, `avg()`) can be used, as well as new rank and value functions:
* `cumeDist()`
* `denseRank()`
* `firstValue()`
* `lag()`
* `lastValue()`
* `lead()`
* `nthValue()`
* `nTile()`
* `percentRank()`
* `rank()`
* `rowNumber()`

To use a window function, include the `OVER` clause by chaining `.over()` after the function call. A `PARTITION BY` and 
`ORDER BY` clause can be optionally chained using `.partitionBy()` and `.orderBy()`, which both take multiple arguments:
```kotlin
FooTable.amount.sum().over().partitionBy(FooTable.year, FooTable.product).orderBy(FooTable.amount)

rowNumber().over().partitionBy(FooTable.year, FooTable.product).orderBy(FooTable.amount)

FooTable.amount.sum().over().orderBy(FooTable.year to SortOrder.DESC, FooTable.product to SortOrder.ASC_NULLS_FIRST)
```
Frame clause functions (like `rows()`, `range()`, and `groups()`) are also supported and take a `WindowFrameBound` option 
depending on the expected result:
* `WindowFrameBound.currentRow()`
* `WindowFrameBound.unboundedPreceding()`
* `WindowFrameBound.unboundedFollowing()`
* `WindowFrameBound.offsetPreceding()`
* `WindowFrameBound.offsetFollowing()`
```kotlin
FooTable.amount.sum().over()
    .partitionBy(FooTable.year, FooTable.product)
    .orderBy(FooTable.amount)
    .range(WindowFrameBound.offsetPreceding(2), WindowFrameBound.currentRow())
```
**Note**: If multiple frame clause functions are chained together, only the last one will be used.

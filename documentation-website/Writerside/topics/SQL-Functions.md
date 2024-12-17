<show-structure for="chapter,procedure" depth="2"/>

# SQL Functions

Exposed provides basic support for classic SQL functions. This topic consists of definitions for those functions, and their 
usage examples. It also explains how to define [custom functions](#custom-functions).

## How to use functions
If you want to retrieve a function result from a query, you have to declare the function as a variable:
```kotlin
val lowerCasedName = FooTable.name.lowerCase()
val lowerCasedNames = FooTable.select(lowerCasedName).map { it[lowerCasedName] }

``` 
Also, functions could be chained and combined:
```kotlin
val trimmedAndLoweredFullName = Concat(FooTable.firstName, stringLiteral(" "), FooTable.lastName).trim().lowerCase()
val fullNames = FooTable.select(trimmedAndLoweredFullName).map { it[trimmedAndLoweredFullName] }

```

## String functions
### Lower case and upper case
To convert a string expression to lower-case or upper-case, use the [`.lowerCase()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/lower-case.html)
and
[`.upperCase()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/upper-case.html)
functions respectively.

```kotlin
val lowerCasedName = FooTable.name.lowerCase()
val lowerCasedNames = FooTable.select(lowerCasedName).map { it[lowerCasedName] }

```
### Substring
The [.substring()](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/substring.html)
function returns a substring value from the specified start and with the specified length.

```kotlin
val shortenedName = FooTable.name.substring(start = 1, length = 3)
val shortenedNames = FooTable.select(shortenedName).map { it[shortenedName] }

```
### Concatenate
The [concat()](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/-i-sql-expression-builder/concat.html)
function returns a string value that concatenates the text representations of all non-null input values, separated by an optional separator.

```kotlin
val userName = concat(stringLiteral("User - "), FooTable.name)
val userNames = FooTable.select(userName).map { it[userName] }

```
### Locate
The [.locate()](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/locate.html)
function returns the index of the first occurrence of a specified substring or 0.

```kotlin
val firstAIndex = FooTable.name.locate("a")
val firstAIndices = FooTable.select(firstAIndex).map { it[firstAIndex] }

```
### Character length
The [.charLength()](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/char-length.html)
function returns the length, measured in characters, or `null` if the String value is null.

```kotlin
val nameLength = FooTable.name.charLength()
val nameLengths = FooTable.select(nameLength).map { it[nameLength] }

```

## Aggregating functions
These functions should be used in queries with [groupBy](DSL-Querying-data.topic#group-by).
### Min/Max/Average
To get the minimum, maximum, and average values, use the 
[.min()](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/min.html)
[.max()](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/max.html)
and [.avg](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/avg.html) functions
respectively. These functions can be applied to any comparable expression:

```kotlin
val minId = FooTable.id.min()
val maxId = FooTable.id.max()
val averageId = FooTable.id.avg()
val (min, max, avg) = FooTable.select(minId, maxId, averageId).map { 
    Triple(it[minId], it[maxId], it[averageId]) 
}

```

## Custom functions
If you can't find your most loved function used in your database (as Exposed provides only basic support for classic SQL functions), you can define your own functions.

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
`CustomFunction` class accepts a function name as a first parameter and the resulting column type as second. After that, you can provide any amount of parameters separated by a comma.

There are also shortcuts for string, long, and datetime functions:
* [`CustomStringFunction`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/-custom-string-function.html)
* [`CustomLongFunction`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/-custom-long-function.html)
* [`CustomDateTimeFunction`](https://jetbrains.github.io/Exposed/api/exposed-jodatime/org.jetbrains.exposed.sql.jodatime/-custom-date-time-function.html)

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

All functions in Exposed extend the abstract class [`Function`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/-function/index.html),
which takes a column type and allows overriding `toQueryBuilder()`. This is what `CustomFunction` actually does, 
which can be leveraged to create more complex queries.

For example, Exposed provides a `trim()` function that removes leading and trailing whitespace from a String. In MySQL,
this is just the default behavior as specifiers can be provided to limit the trim to either leading or trailing, as well
as providing a specific substring other than spaces to remove. The custom function below supports this extended behavior:

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

    FooTable.select(leadingXTrim) // barxxx
    FooTable.select(trailingXTrim)  // xxxbar
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

<note>
If multiple frame clause functions are chained together, only the last one will be used.
</note>

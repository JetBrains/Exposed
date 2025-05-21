<show-structure for="chapter,procedure" depth="2"/>

# SQL Functions

Exposed provides basic support for classic SQL functions. This topic consists of definitions for those functions, and their 
usage examples. It also explains how to define [custom functions](#custom-functions).

For the function examples below, consider the following table:

<code-block lang="kotlin"
            src="exposed-sql-functions/src/main/kotlin/org/example/tables/FilmBoxOfficeTable.kt"/>

## How to use functions
To retrieve the result of an SQL function result from a query using `.select()`, declare the function as a variable first:

<code-block lang="kotlin"
            src="exposed-sql-functions/src/main/kotlin/org/example/examples/StringFuncExamples.kt"
            include-lines="35-36"/>

You can alias this function in the same way you [alias a table or query](DSL-Querying-data.topic#alias):

<code-block lang="kotlin"
            src="exposed-sql-functions/src/main/kotlin/org/example/examples/StringFuncExamples.kt"
            include-lines="39-40"/>

SQL functions can be chained and combined as needed. The example below generates SQL that concatenates the string values
stored in two columns, before wrapping the function in `TRIM()` and `LOWER()`:

<code-block lang="kotlin"
            src="exposed-sql-functions/src/main/kotlin/org/example/examples/StringFuncExamples.kt"
            include-lines="43-46"/>

## String functions
### Lower case and upper case
To convert a string expression to lower-case or upper-case, use the [`.lowerCase()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/lower-case.html)
and
[`.upperCase()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/upper-case.html)
functions respectively.

<code-block lang="kotlin"
            src="exposed-sql-functions/src/main/kotlin/org/example/examples/StringFuncExamples.kt"
            include-lines="35-36"/>

### Substring
The [`.substring()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/substring.html)
function returns a substring value from the specified start and with the specified length.

<code-block lang="kotlin"
            src="exposed-sql-functions/src/main/kotlin/org/example/examples/StringFuncExamples.kt"
            include-lines="49-50"/>

### Concatenate
The [`concat()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/-i-sql-expression-builder/concat.html)
function returns a string value that concatenates the text representations of all non-null input values, separated by an optional separator.

<code-block lang="kotlin"
            src="exposed-sql-functions/src/main/kotlin/org/example/examples/StringFuncExamples.kt"
            include-lines="53-57"/>

<note>
This <code>concat()</code> requires import statement <code>import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.concat</code>.
</note>

### Locate
The [`.locate()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/locate.html)
function returns the index of the first occurrence of a specified substring, or 0 if the substring is not found.

<code-block lang="kotlin" src="exposed-sql-functions/src/main/kotlin/org/example/examples/StringFuncExamples.kt" include-lines="60-61"/>

### Character length
The [`.charLength()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/char-length.html)
function returns the length, measured in characters, or `null` if the String value is null.

<code-block lang="kotlin" src="exposed-sql-functions/src/main/kotlin/org/example/examples/StringFuncExamples.kt" include-lines="64-65"/>

## Aggregate functions
These functions should most likely be used in queries with [`.groupBy()`](DSL-Querying-data.topic#group-by).
### Min/Max/Average
To get the minimum, maximum, and average values, use the 
[`.min()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/min.html)
[`.max()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/max.html)
and [`.avg()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/avg.html) functions
respectively. These functions can be applied to any comparable expression:

<code-block lang="kotlin" src="exposed-sql-functions/src/main/kotlin/org/example/examples/AggregateFuncExamples.kt" include-lines="20-28"/>

### Sum/Count
You can use SQL functions like `SUM()` and `COUNT()` directly with a column expression:

<code-block lang="kotlin" src="exposed-sql-functions/src/main/kotlin/org/example/examples/AggregateFuncExamples.kt" include-lines="31-38"/>

### Statistics
Some databases provide aggregate functions specifically for statistics and Exposed provides support for four of these:
[`.stdDevPop()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/std-dev-pop.html),
[`.stdDevSamp()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/std-dev-samp.html),
[`.varPop()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/var-pop.html),
[`.varSamp()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/var-samp.html).
The following example retrieves the population standard deviation of values stored in the `revenue` column:

<code-block lang="kotlin" src="exposed-sql-functions/src/main/kotlin/org/example/examples/AggregateFuncExamples.kt" include-lines="41-45"/>

## Custom functions
If you can't find your most loved function used in your database (as Exposed provides only basic support for classic SQL functions), you can define your own functions.

There are multiple options to define custom functions:

1. [Functions without parameters](#functions-without-parameters)
2. [Functions with additional parameters](#functions-with-additional-parameters)
3. [Functions that require more complex query building](#functions-that-require-more-complex-query-building)

### Functions without parameters

[`.function()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/function.html) simply wraps the column expression 
in parentheses with the string argument as the function name:

<code-block lang="kotlin" src="exposed-sql-functions/src/main/kotlin/org/example/examples/CustomFuncExamples.kt" include-lines="29-34"/>

### Functions with additional parameters

The [`CustomFunction`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/-custom-function/index.html) class accepts 
a function name as the first argument and the column type that should be used to handle its results as the second.
After that, you can provide any amount of additional parameters separated by a comma:

<code-block lang="kotlin" src="exposed-sql-functions/src/main/kotlin/org/example/examples/CustomFuncExamples.kt" include-lines="37-43"/>

There are also shortcuts for `String`, `Long`, and `DateTime` functions:
* [`CustomStringFunction`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/-custom-string-function.html)
* [`CustomLongFunction`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/-custom-long-function.html)
* [`CustomDateTimeFunction`](https://jetbrains.github.io/Exposed/api/exposed-kotlin-datetime/org.jetbrains.exposed.v1.sql.kotlin.datetime/-custom-date-time-function.html)

Using one of these shortcuts, the example above could be simplified to:

<code-block lang="kotlin" src="exposed-sql-functions/src/main/kotlin/org/example/examples/CustomFuncExamples.kt" include-lines="46-48"/>

In the following example, [`CustomDateFunction`](https://jetbrains.github.io/Exposed/api/exposed-kotlin-datetime/org.jetbrains.exposed.v1.sql.kotlin.datetime/-custom-date-function.html) 
is used in an H2 database to mimic its `DATEADD()` function in order to calculate a date three months before the current one.
In is then chained with Exposed's built-in [`.month()`](https://jetbrains.github.io/Exposed/api/exposed-kotlin-datetime/org.jetbrains.exposed.v1.sql.kotlin.datetime/month.html) 
function to return the month of the date found, so it can be used in a query:

<code-block lang="kotlin" src="exposed-sql-functions/src/main/kotlin/org/example/examples/CustomFuncExamples.kt" include-lines="55-65"/>

### Functions that require more complex query building

All functions in Exposed extend the abstract class [`Function`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/-function/index.html),
which takes a column type and allows overriding `toQueryBuilder()`. This is what `CustomFunction` actually does, 
which can be leveraged to create more complex queries.

For example, Exposed provides a [`.trim()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/trim.html) 
function that removes leading and trailing whitespace from a String. In some databases (like H2 and MySQL),
this is just the default behavior as specifiers can be provided to limit the trim to either leading or trailing. These databases also allow you 
to provide a specific substring other than spaces to remove. The custom function below supports this extended behavior:

<code-block lang="kotlin" src="exposed-sql-functions/src/main/kotlin/org/example/examples/CustomTrimFunction.kt" />

<note>
Ensure that the correct import statement is used: <code>import org.jetbrains.exposed.v1.core.Function</code>. Otherwise <code>Function</code> 
from <code>kotlin-stdlib</code> may be resolved instead and cause compilation errors.
</note>

This custom function can then be used to achieve the exact trim that is needed:

<code-block lang="kotlin" src="exposed-sql-functions/src/main/kotlin/org/example/examples/CustomFuncExamples.kt" include-lines="72-81,83-85"/>

## Window functions

Window functions allow calculations across a set of table rows that are related to the current row.

Existing aggregate functions (like `sum()`, `avg()`) can be used, as well as new rank and value functions:
* [`cumeDist()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/-i-sql-expression-builder/cume-dist.html)
* [`denseRank()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/-i-sql-expression-builder/dense-rank.html)
* [`firstValue()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/-i-sql-expression-builder/first-value.html)
* [`lag()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/-i-sql-expression-builder/lag.html)
* [`lastValue()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/-i-sql-expression-builder/last-value.html)
* [`lead()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/-i-sql-expression-builder/lead.html)
* [`nthValue()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/-i-sql-expression-builder/nth-value.html)
* [`nTile()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/-i-sql-expression-builder/ntile.html)
* [`percentRank()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/-i-sql-expression-builder/percent-rank.html)
* [`rank()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/-i-sql-expression-builder/rank.html)
* [`rowNumber()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/-i-sql-expression-builder/row-number.html)

To use a window function, include the `OVER` clause by chaining 
[`.over()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/-window-function/over.html) after the function call. 
A `PARTITION BY` and `ORDER BY` clause can be optionally chained using 
[`.partitionBy()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/-window-function-definition/partition-by.html) 
and [`.orderBy()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/-window-function-definition/order-by.html), 
taking multiple arguments:

<code-block lang="kotlin" src="exposed-sql-functions/src/main/kotlin/org/example/examples/WindowFuncExamples.kt" include-lines="18-22,24-29,31-35"/>

Frame clause functions, such as [`rows()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/-window-function-definition/rows.html), 
[`range()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/-window-function-definition/range.html), 
and [`groups()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/-window-function-definition/groups.html), 
are also supported and take a [`WindowFrameBound`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/-window-frame-bound/index.html) 
option depending on the expected result:
* [`WindowFrameBound.currentRow()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/-window-frame-bound/-companion/current-row.html)
* [`WindowFrameBound.unboundedPreceding()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/-window-frame-bound/-companion/unbounded-preceding.html)
* [`WindowFrameBound.unboundedFollowing()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/-window-frame-bound/-companion/unbounded-following.html)
* [`WindowFrameBound.offsetPreceding()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/-window-frame-bound/-companion/offset-preceding.html)
* [`WindowFrameBound.offsetFollowing()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.sql/-window-frame-bound/-companion/offset-following.html)

<code-block lang="kotlin" src="exposed-sql-functions/src/main/kotlin/org/example/examples/WindowFuncExamples.kt" include-lines="38-43"/>

<note>
If multiple frame clause functions are chained together, only the last one will be used.
</note>

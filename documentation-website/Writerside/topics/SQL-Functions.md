<show-structure for="chapter,procedure" depth="2"/>

# SQL Functions

Exposed provides basic support for classic SQL functions. This topic consists of definitions for those functions, and their 
usage examples. It also explains how to define [custom functions](#custom-functions).

For the function examples below, consider the following table:

<code-block lang="kotlin" src="exposed-sql-functions/src/main/kotlin/org/example/tables/SalesTable.kt"/>

## How to use functions
If you want to retrieve an SQL function result from a query using `.select()`, you should declare the function as a variable first:

<code-block lang="kotlin" src="exposed-sql-functions/src/main/kotlin/org/example/examples/StringFuncExamples.kt" include-lines="35-36"/>

This function could also be aliased, in the same way that a [table or query could be aliased](DSL-Querying-data.topic#alias):

<code-block lang="kotlin" src="exposed-sql-functions/src/main/kotlin/org/example/examples/StringFuncExamples.kt" include-lines="39-40"/>

Also, functions could be chained and combined as needed. The example below generates SQL that concatenates the string values
stored in 2 columns, before wrapping the function in `TRIM()` and `LOWER()`:

<code-block lang="kotlin" src="exposed-sql-functions/src/main/kotlin/org/example/examples/StringFuncExamples.kt" include-lines="43-46"/>

## String functions
### Lower case and upper case
To convert a string expression to lower-case or upper-case, use the [`.lowerCase()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/lower-case.html)
and
[`.upperCase()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/upper-case.html)
functions respectively.

<code-block lang="kotlin" src="exposed-sql-functions/src/main/kotlin/org/example/examples/StringFuncExamples.kt" include-lines="35-36"/>

### Substring
The [`.substring()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/substring.html)
function returns a substring value from the specified start and with the specified length.

<code-block lang="kotlin" src="exposed-sql-functions/src/main/kotlin/org/example/examples/StringFuncExamples.kt" include-lines="49-50"/>

### Concatenate
The [`concat()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/-i-sql-expression-builder/concat.html)
function returns a string value that concatenates the text representations of all non-null input values, separated by an optional separator.

<code-block lang="kotlin" src="exposed-sql-functions/src/main/kotlin/org/example/examples/StringFuncExamples.kt" include-lines="53-57"/>

<note>
This <code>concat()</code> requires import statement <code>import org.jetbrains.exposed.sql.SqlExpressionBuilder.concat</code>.
</note>

### Locate
The [`.locate()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/locate.html)
function returns the index of the first occurrence of a specified substring, or 0 if the substring is not found.

<code-block lang="kotlin" src="exposed-sql-functions/src/main/kotlin/org/example/examples/StringFuncExamples.kt" include-lines="60-61"/>

### Character length
The [`.charLength()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/char-length.html)
function returns the length, measured in characters, or `null` if the String value is null.

<code-block lang="kotlin" src="exposed-sql-functions/src/main/kotlin/org/example/examples/StringFuncExamples.kt" include-lines="64-65"/>

## Aggregate functions
These functions should most likely be used in queries with [`.groupBy()`](DSL-Querying-data.topic#group-by).
### Min/Max/Average
To get the minimum, maximum, and average values, use the 
[`.min()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/min.html)
[`.max()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/max.html)
and [`.avg()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/avg.html) functions
respectively. These functions can be applied to any comparable expression:

<code-block lang="kotlin" src="exposed-sql-functions/src/main/kotlin/org/example/examples/AggregateFuncExamples.kt" include-lines="19-27"/>

### Sum/Count
You can also use SQL functions like `SUM()` and `COUNT()` directly with a column expression:

<code-block lang="kotlin" src="exposed-sql-functions/src/main/kotlin/org/example/examples/AggregateFuncExamples.kt" include-lines="30-37"/>

### Statistics
Some databases provide aggregate functions specifically for statistics and Exposed provides support for 4 of these:
[`.stdDevPop()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/stdDevPop.html),
[`.stdDevSamp()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/stdDevSamp.html),
[`.varPop()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/varPop.html),
[`.varSamp()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/varSamp.html).
The following example retrieves the population standard deviation of values stored in the `amount` column:

<code-block lang="kotlin" src="exposed-sql-functions/src/main/kotlin/org/example/examples/AggregateFuncExamples.kt" include-lines="40-44"/>

## Custom functions
If you can't find your most loved function used in your database (as Exposed provides only basic support for classic SQL functions), you can define your own functions.

There are multiple options to define custom functions:
1. Functions without parameters:

[`.function()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/function.html) simply wraps the column expression 
in parentheses with the string argument as the function name:

<code-block lang="kotlin" src="exposed-sql-functions/src/main/kotlin/org/example/examples/CustomFuncExamples.kt" include-lines="28-33"/>

2. Functions with additional parameters:

The [`CustomFunction`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/-custom-function/index.html) class accepts 
a function name as the first argument and the column type that should be used to handle its results as the second.
After that, you can provide any amount of additional parameters separated by a comma:

<code-block lang="kotlin" src="exposed-sql-functions/src/main/kotlin/org/example/examples/CustomFuncExamples.kt" include-lines="36-42"/>

There are also shortcuts for string, long, and datetime functions:
* [`CustomStringFunction`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/-custom-string-function.html)
* [`CustomLongFunction`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/-custom-long-function.html)
* [`CustomDateTimeFunction`](https://jetbrains.github.io/Exposed/api/exposed-jodatime/org.jetbrains.exposed.sql.jodatime/-custom-date-time-function.html)

Using one of these shortcuts, the example above could be simplified to:

<code-block lang="kotlin" src="exposed-sql-functions/src/main/kotlin/org/example/examples/CustomFuncExamples.kt" include-lines="45-47"/>

As an additional example, the following could be used in H2 to mimic its `DATEADD()` function in order to calculate a date 3 months before the current date.
This could then be chained with Exposed's built-in `.month()` function to return the month of the date found, so it can be used in a query:

<code-block lang="kotlin" src="exposed-sql-functions/src/main/kotlin/org/example/examples/CustomFuncExamples.kt" include-lines="54-64"/>

3. Functions that require more complex query building:

All functions in Exposed extend the abstract class [`Function`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.sql/-function/index.html),
which takes a column type and allows overriding `toQueryBuilder()`. This is what `CustomFunction` actually does, 
which can be leveraged to create more complex queries.

For example, Exposed provides a `.trim()` function that removes leading and trailing whitespace from a String. In some databases (like H2 and MySQL),
this is just the default behavior as specifiers can be provided to limit the trim to either leading or trailing. These databases also allow you 
to provide a specific substring other than spaces to remove. The custom function below supports this extended behavior:

<code-block lang="kotlin" src="exposed-sql-functions/src/main/kotlin/org/example/examples/CustomTrimFunction.kt" />

<note>
Ensure that the correct import statement is used: <code>import org.jetbrains.exposed.sql.Function</code>. Otherwise <code>Function</code> 
from <code>kotlin-stdlib</code> may be resolved instead and cause compilation errors.
</note>

This custom function can then be used to achieve the exact trim that is needed:

<code-block lang="kotlin" src="exposed-sql-functions/src/main/kotlin/org/example/examples/CustomFuncExamples.kt" include-lines="71-80,82-84"/>

## Window functions

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

<code-block lang="kotlin" src="exposed-sql-functions/src/main/kotlin/org/example/examples/WindowFuncExamples.kt" include-lines="17-21,23-28,30-34"/>

Frame clause functions (like `rows()`, `range()`, and `groups()`) are also supported and take a `WindowFrameBound` option 
depending on the expected result:
* `WindowFrameBound.currentRow()`
* `WindowFrameBound.unboundedPreceding()`
* `WindowFrameBound.unboundedFollowing()`
* `WindowFrameBound.offsetPreceding()`
* `WindowFrameBound.offsetFollowing()`

<code-block lang="kotlin" src="exposed-sql-functions/src/main/kotlin/org/example/examples/WindowFuncExamples.kt" include-lines="37-42"/>

<note>
If multiple frame clause functions are chained together, only the last one will be used.
</note>

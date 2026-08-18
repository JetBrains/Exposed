[//]: # (title: JSON and JSONB types)

<show-structure for="chapter" depth="3" />
<var name="artifact_name" value="exposed-json"/>

<tldr>

**Required dependencies**: `org.jetbrains.exposed:exposed-json`

<include from="lib.topic" element-id="jdbc-supported"/>
<include from="lib.topic" element-id="r2dbc-supported"/>
</tldr>

Exposed works together with the JSON serialization library of your choice by allowing column
definitions that accept generic serializer and deserializer arguments through the
[`json()`](https://jetbrains.github.io/Exposed/api/exposed-json/org.jetbrains.exposed.v1.json/json.html)
and
[`jsonb()`](https://jetbrains.github.io/Exposed/api/exposed-json/org.jetbrains.exposed.v1.json/jsonb.html)
functions.

Databases store JSON values in either text or binary format, so Exposed provides a separate type for each.

## Add dependencies {id="add-dependency"}

Before using JSON and JSONB column types or functions, add the `%artifact_name%` module to your build file:

<include from="lib.topic" element-id="add-dependency"/>

## Basic usage {id="basic-usage"}

The following example uses [`kotlinx.serialization`](https://github.com/Kotlin/kotlinx.serialization)
with a `@Serializable` class. This overload of `json()` accepts a `Json` configuration and uses the `KSerializer` for
the specified type:

```kotlin
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/JSONandJSONBExamples.kt" include-lines="7-9,21,29-40"}

You can also provide serializer and deserializer functions directly. For example, the following definition uses
[Jackson](https://github.com/FasterXML/jackson) with the `jackson-module-kotlin` dependency and the full form of
`json()`:

```kotlin
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/JSONandJSONBExamples.kt" include-lines="5-6,9,21,43-53"}

### Insert and update JSON data {id="insert-update-json"}

The following examples use the `TeamsTable` definition from the `kotlinx.serialization` example.

To store a JSON value, assign an instance of the serializable class to the column. Exposed serializes the value using
the `Json` instance passed to the `jsonConfig` parameter of [`json()`](#json):

```kotlin
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/JSONandJSONBExamples.kt" include-lines="64-68"}

To modify a stored value, assign a new instance in an `update()` statement:

```kotlin
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/JSONandJSONBExamples.kt" include-lines="69-71"}

When you read the column, Exposed deserializes the stored JSON back into an instance of the class:

```kotlin
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/JSONandJSONBExamples.kt" include-lines="73-77"}

### Store arrays {id="json-arrays"}

JSON columns can also store arrays. Pass the corresponding Kotlin array type to `json()`, for example `IntArray` for
integers or `Array<Project>` for objects:

```kotlin
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/JSONandJSONBExamples.kt" include-symbol="TeamProjectsTable"}

To insert values into these columns, use standard Kotlin collections:

<tabs>
<tab title="Exposed">

```kotlin
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/JSONandJSONBExamples.kt" include-lines="145-151"}

</tab>
<tab title="SQL">

```sql
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/JSONandJSONBExamples.kt" include-lines="141-142"}

</tab>
</tabs>

## Supported types {id="supported-types"}

The `exposed-json` module provides the following column types:

| Column type         | PostgreSQL | MySQL / MariaDB / H2 | SQLite | SQLServer       | Oracle           |
|---------------------|------------|----------------------|--------|-----------------|------------------|
| [`json()`](#json)   | `JSON`     | `JSON`               | `TEXT` | `NVARCHAR(MAX)` | `VARCHAR2(4000)` |
| [`jsonb()`](#jsonb) | `JSONB`    | `JSON`               | `BLOB` | Not supported   | Not supported    |

The exact SQL type depends on the database dialect. For example, `jsonb()` maps to `JSON` in MySQL and H2 rather than to
a type named `JSONB`.

### `json()` {id="json"}

Use [`json()`](https://jetbrains.github.io/Exposed/api/exposed-json/org.jetbrains.exposed.v1.json/json.html) to define
a column that stores JSON data in a text-based representation.

When using `kotlinx.serialization`, pass the `Json` instance to the `jsonConfig` parameter:

```kotlin
val project = json<Project>("project", jsonConfig = format)
```

### `jsonb()` {id="jsonb"}

Use the [`jsonb()`](https://jetbrains.github.io/Exposed/api/exposed-json/org.jetbrains.exposed.v1.json/jsonb.html) to 
define a column for JSON data that the database can store in a binary representation, where supported.

When using `kotlinx.serialization`, pass the `Json` instance to the `jsonConfig` parameter:

```kotlin
val project = jsonb<Project>("project", jsonConfig = Json.Default)
```

#### JSONB support in SQLite {id="sqlite-jsonb"}

SQLite supports storing JSON data in its binary `JSONB` format starting with version 3.45.0.0. Exposed maps `jsonb()`
columns to `BLOB` and wraps values written to them with SQLite's `JSONB()` function.

This applies to values in DDL default clauses:

<tabs>
<tab title="Exposed">

```kotlin
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/JSONandJSONBExamples.kt" include-lines="196-200"}

</tab>
<tab title="SQL">

```sql
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/JSONandJSONBExamples.kt" include-lines="191-194"}

</tab>
</tabs>

Exposed also wraps values in `JSONB()` in DML operations:

<tabs>
<tab title="Exposed">

```kotlin
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/JSONandJSONBExamples.kt" include-lines="161-163"}

</tab>
<tab title="SQL">

```sql
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/JSONandJSONBExamples.kt" include-lines="157-158"}

</tab>
</tabs>


SQLite stores this value in its binary `JSONB` representation. A serializer that expects `JSON` text cannot decode the 
raw stored value directly.

To make the value available as `JSON` text, SQLite provides the `JSON()` SQL function. By default, Exposed applies this
function when it reads a `jsonb()` column from SQLite.

<tabs>
<tab title="Exposed">

```kotlin
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/JSONandJSONBExamples.kt" include-lines="172-173"}

</tab>
<tab title="SQL">

```sql
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/JSONandJSONBExamples.kt" include-lines="169"}

</tab>
</tabs>

To disable this behavior, set the `castToJsonFormat` parameter to `false` when you define the column:

```kotlin
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/JSONandJSONBExamples.kt" include-lines="202-204"}

Exposed ignores `castToJsonFormat` for databases other than SQLite. To convert an individual JSONB expression to JSON,
use [`.castToJson()`](#cast-to-json).

## JSON functions {id="json-functions"}

### Extract data

Use the [`.extract()`](https://jetbrains.github.io/Exposed/api/exposed-json/org.jetbrains.exposed.v1.json/extract.html)
function to extract a value from a JSON expression at a specific path. You can extract the result as JSON or as a scalar
value of the specified type.

For example, the following query extracts the project name and selects projects whose language is Kotlin:

```kotlin
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/JSONandJSONBExamples.kt" include-lines="88-94"}

For databases that use `$` as the JSON path root, Exposed adds it to the generated path expression automatically, so
don't include `$` in the path you pass to `.extract()`. For example, in MySQL, pass `.name` instead of `$.name`.

### Check if data exists

To check whether data exists within a JSON expression, use the
[`.exists()`](https://jetbrains.github.io/Exposed/api/exposed-json/org.jetbrains.exposed.v1.json/exists.html)
function:

```kotlin
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/JSONandJSONBExamples.kt" include-lines="98-99"}

Some databases also support filter expressions and optional variables in JSON paths:

```kotlin
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/JSONandJSONBExamples.kt" include-lines="106-114"}

### Check if JSON contains an expression

To check whether a JSON expression contains a value, use the
[`.contains()`](https://jetbrains.github.io/Exposed/api/exposed-json/org.jetbrains.exposed.v1.json/contains.html)
function:

```kotlin
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/JSONandJSONBExamples.kt" include-lines="119-120"}

On supported databases, you can also limit the check to a specific JSON path:

```kotlin
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/JSONandJSONBExamples.kt" include-lines="127-134"}

### Cast data to JSON type {id="cast-to-json"}

Use the [`.castToJson()`](https://jetbrains.github.io/Exposed/api/exposed-json/org.jetbrains.exposed.v1.json/cast-to-json.html)
function to cast other supported types, such as JSONB, to JSON:

```kotlin
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/JSONandJSONBExamples.kt" include-lines="178-184"}

As shown on the example above, on supported databases, you can also cast a text column that stores valid JSON strings to
a serializable class.

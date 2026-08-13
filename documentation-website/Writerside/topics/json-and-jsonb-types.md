[//]: # (title: JSON and JSONB types)

<show-structure for="chapter" depth="2" />
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

As databases store JSON values either in text or binary format, Exposed provides two types to
account for any potential differences.

## Add dependencies {id="add-dependency"}

Before using JSON and JSONB column types or functions, add the `%artifact_name%` module to your build file:
<include from="lib.topic" element-id="add-dependency"/>

## Basic usage {id="basic-usage"}

The following example uses [`kotlinx.serialization`](https://github.com/Kotlin/kotlinx.serialization)
with a `@Serializable` class. This overload of `json()` accepts a `Json` configuration and uses the `KSerializer` for
the specified type:

```kotlin
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/JSONandJSONBExamples.kt" include-lines="5-6,7,17,18-19,29-38"}

You can also provide serializer and deserializer functions directly. For example, the following definition uses [Jackson](https://github.com/FasterXML/jackson)
with the `jackson-module-kotlin` dependency and the full form of `json()`:

```kotlin
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/JSONandJSONBExamples.kt" include-lines="3-4,18-19,29,31,41-48"}

## Supported types {id="supported-types"}

The `exposed-json` extension module provides the following additional types:

| Column type         | PostgreSQL | MySQL/MariaDB/H2 | SQLite | SQLServer       | Oracle           |
|---------------------|------------|------------------|--------|-----------------|------------------|
| [`json()`](#json)   | `JSON`     | `JSON`           | `TEXT` | `NVARCHAR(MAX)` | `VARCHAR2(4000)` |
| [`jsonb()`](#jsonb) | `JSONB`    | `JSON`           | `BLOB` | `JSONB`         | Not supported    |

The exact SQL type depends on the database dialect. For example, `jsonb()` maps to `JSON` in MySQL and H2 and `BLOB` in
supported SQLite versions rather than a type named `JSONB`.

### `json()` {id="json"}

The [`json()`](https://jetbrains.github.io/Exposed/api/exposed-json/org.jetbrains.exposed.v1.json/json.html) column type
maps to the database `JSON` type. It is used for storing JSON data in text format.

```kotlin
val project = json<Project>("project", format)
```

> The exact SQL type depends on the database dialect. For exact type mappings, see [](#supported-types).
> 
{style="note"}

### `jsonb()` {id="jsonb"}

The [`jsonb()`](https://jetbrains.github.io/Exposed/api/exposed-json/org.jetbrains.exposed.v1.json/jsonb.html) column type
maps to the database `JSONB` type. It is used for storing JSON data in binary format.

```kotlin
val project = jsonb<Project>("project", Json.Default)
```

> The exact SQL type depends on the database dialect. For exact type mappings, see [](#supported-types).
>
{style="note"}

#### JSONB support in SQLite {id="sqlite-jsonb"}

SQLite supports storing JSON data in its binary `JSONB` format starting with version 3.45.0.0.
For SQLite, Exposed maps `jsonb()` columns to `BLOB` and automatically wraps values written to these columns with SQLite's
`JSONB()` SQL function.

This behavior also applies to values used in DDL default clauses:

<tabs>
<tab title="Exposed">

```kotlin
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/JSONandJSONBExamples.kt" include-lines="30-31,161,170-174"}

</tab>
<tab title="SQL">

```sql
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/JSONandJSONBExamples.kt" include-lines="165-168"}

</tab>
</tabs>


Exposed also wraps values in `JSONB()` when you use them in DML operations:

<tabs>
<tab title="Exposed">

```kotlin
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/JSONandJSONBExamples.kt" include-lines="136-138"}

</tab>
<tab title="SQL">

```sql
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/JSONandJSONBExamples.kt" include-lines="132-133"}

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
{src="exposed-data-types/src/main/kotlin/org/example/examples/JSONandJSONBExamples.kt" include-lines="147-149"}

</tab>
<tab title="SQL">

```sql
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/JSONandJSONBExamples.kt" include-lines="144"}

</tab>
</tabs>

To disable this automatic function wrapping in queries, set the `castToJsonFormat` parameter to `false` when defining
a column:

```kotlin
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/JSONandJSONBExamples.kt" include-lines="176-178"}

The `castToJsonFormat` parameter applies only to SQLite and Exposed ignores it for other databases.
To convert an individual JSONB expression to JSON instead, use the [`.castToJson()`](#cast-to-json) function.

## JSON functions {id="json-functions"}

### Extract data

Use the [`.extract()`](https://jetbrains.github.io/Exposed/api/exposed-json/org.jetbrains.exposed.v1.json/extract.html)
function to extract a value from a JSON expression at a specific path. You can extract the result as JSON or as a scalar
value of the specified type.

For example, the following query extracts the project name and selects projects whose language is Kotlin:

```kotlin
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/JSONandJSONBExamples.kt" include-lines="79-81"}

For databases that use `$` as the JSON path root, Exposed adds it to the generated path expression automatically. Do not
include `$` in the path passed to `.extract()`.

For example, when using MySQL, pass `.name` instead of `$.name`.

### Check if data exists

To check whether data exists within a JSON expression, use the
[`.exists()`](https://jetbrains.github.io/Exposed/api/exposed-json/org.jetbrains.exposed.v1.json/exists.html)
function:

```kotlin
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/JSONandJSONBExamples.kt" include-lines="85-86"}

Some databases also support filter expressions and optional variables in JSON paths:

```kotlin
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/JSONandJSONBExamples.kt" include-lines="93-95"}

### Check if JSON contains an expression

To check whether an expression is contained within a JSON, use the
[`.contains()`](https://jetbrains.github.io/Exposed/api/exposed-json/org.jetbrains.exposed.v1.json/contains.html)
function:

```kotlin
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/JSONandJSONBExamples.kt" include-lines="100-101"}

On supported databases, you can also limit the check to a specific JSON path:

```kotlin
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/JSONandJSONBExamples.kt" include-lines="108-109"}

### Cast data to JSON type {id="cast-to-json"}

You can cast other supported data types, like text or JSONB, to the JSON data type using the
[`.castToJson()`](https://jetbrains.github.io/Exposed/api/exposed-json/org.jetbrains.exposed.v1.json/cast-to-json.html)
function:

```kotlin
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/JSONandJSONBExamples.kt" include-lines="153-158"}

On supported databases, you can use `.castToJson()` to cast valid JSON strings stored in a text column
to the serializable class of your choosing.

## JSON arrays {id="json-arrays"}

JSON columns can also store arrays, allowing structured data to be stored and manipulated
directly in the database.

To define a JSON column that stores an array, use the
[`json()`](https://jetbrains.github.io/Exposed/api/exposed-json/org.jetbrains.exposed.v1.json/json.html)
function with the corresponding Kotlin array type.

The following example defines one column for an array of integers and another for an array of `Project` objects:

```kotlin
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/JSONandJSONBExamples.kt" include-symbol="Project, TeamProjectsTable"}

To insert data into the JSON array columns, use standard Kotlin collections:

<tabs>
<tab title="Exposed">

```kotlin
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/JSONandJSONBExamples.kt" include-lines="120-126"}

</tab>
<tab title="SQL">

```sql
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/JSONandJSONBExamples.kt" include-lines="116-117"}

</tab>
</tabs>

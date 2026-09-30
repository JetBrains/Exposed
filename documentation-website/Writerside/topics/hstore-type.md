[//]: # (title: hstore type)

<show-structure for="chapter" depth="3" />
<var name="artifact_name" value="exposed-postgres"/>

<tldr>

**Required dependencies**: `org.jetbrains.exposed:exposed-postgres`

<include from="lib.topic" element-id="jdbc-supported"/>
<include from="lib.topic" element-id="r2dbc-supported"/>
</tldr>

Exposed provides support for PostgreSQL's [`hstore`](https://www.postgresql.org/docs/current/hstore.html) key-value
data type through the
[`hstore()`](https://jetbrains.github.io/Exposed/api/exposed-postgres/org.jetbrains.exposed.v1.postgresql.hstore/hstore.html)
column type.

`hstore` has no equivalent on other databases, so `hstore()` is only available for `PostgreSQLDialect`. Using it with
any other database throws `UnsupportedByDialectException`. If you need a portable key-value column that works across
databases, use `jsonb()` from `exposed-json` with a `Map<String, String>` type instead.

## Add dependencies {id="add-dependency"}

Before using the `hstore()` column type or its functions, add the `%artifact_name%` module to your build file:

<include from="lib.topic" element-id="add-dependency"/>

## Enable the extension {id="enable-extension"}

`hstore` is provided by a PostgreSQL extension that is not enabled by default. Run the following statement once per
database, before creating any tables that use `hstore()`:

```sql
CREATE EXTENSION IF NOT EXISTS hstore;
```

## Basic usage {id="basic-usage"}

Define a column using `hstore()`. Keys are always non-null strings, but values may be `null`, so the column type is
`Map<String, String?>`:

```kotlin
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/HstoreExamples.kt" include-lines="24-27"}

To insert or update data, assign a `Map<String, String?>` to the column:

```kotlin
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/HstoreExamples.kt" include-lines="30-41"}

```kotlin
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/HstoreExamples.kt" include-lines="43-47"}

## hstore functions {id="hstore-functions"}

### Get a value by key

Use [`.get()`](https://jetbrains.github.io/Exposed/api/exposed-postgres/org.jetbrains.exposed.v1.postgresql.hstore/get.html)
to read the value stored under a key, using PostgreSQL's `->` operator. The result is `null` if the key is not present:

```kotlin
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/HstoreExamples.kt" include-lines="49-53"}

### Check if a value is contained

Use [`.contains()`](https://jetbrains.github.io/Exposed/api/exposed-postgres/org.jetbrains.exposed.v1.postgresql.hstore/contains.html)
to check whether all the key-value pairs in a map are present in the column, using PostgreSQL's `@>` operator:

```kotlin
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/HstoreExamples.kt" include-lines="55-59"}

### Check if a key exists

Use [`.exists()`](https://jetbrains.github.io/Exposed/api/exposed-postgres/org.jetbrains.exposed.v1.postgresql.hstore/exists.html)
to check whether a key is present, using PostgreSQL's `exist()` function:

```kotlin
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/HstoreExamples.kt" include-lines="61-65"}

<note>
The <code>?</code> hstore operator is intentionally not used for this check. A bare <code>?</code> in generated SQL
collides with JDBC's positional parameter placeholders, so Exposed uses the equivalent <code>exist()</code> function
instead.
</note>

### Check if all or any keys exist

Use
[`.existsAll()`](https://jetbrains.github.io/Exposed/api/exposed-postgres/org.jetbrains.exposed.v1.postgresql.hstore/exists-all.html)
and
[`.existsAny()`](https://jetbrains.github.io/Exposed/api/exposed-postgres/org.jetbrains.exposed.v1.postgresql.hstore/exists-any.html)
to check whether all, or any, of a list of keys are present, using PostgreSQL's `?&` and `?|` operators respectively:

```kotlin
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/HstoreExamples.kt" include-lines="67-73"}

### Delete a key

Use [`.delete()`](https://jetbrains.github.io/Exposed/api/exposed-postgres/org.jetbrains.exposed.v1.postgresql.hstore/delete.html)
to return a copy of the column with one or more keys removed, using PostgreSQL's `-` operator. Overloads accept a
single key, a list of keys, or another map whose matching key-value pairs should be removed:

```kotlin
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/HstoreExamples.kt" include-lines="75-79"}

### Concatenate two hstore values

Use [`.concat()`](https://jetbrains.github.io/Exposed/api/exposed-postgres/org.jetbrains.exposed.v1.postgresql.hstore/concat.html)
to merge a map into the column, using PostgreSQL's `||` operator. Where both sides share a key, the value from the
argument wins:

```kotlin
```
{src="exposed-data-types/src/main/kotlin/org/example/examples/HstoreExamples.kt" include-lines="81-85"}

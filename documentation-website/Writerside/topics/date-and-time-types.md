[//]: # (title: Date and time types)

<show-structure for="chapter,procedure" depth="2"/>
<var name="example_name" value="exposed-data-types"/>

<tldr>
    <p>
        <b>Required dependencies</b>: <code>org.jetbrains.exposed:exposed-kotlin-datetime</code>,
        <code>org.jetbrains.exposed:exposed-java-time</code>, or
        <code>org.jetbrains.exposed:exposed-jodatime</code>
    </p>
    <include from="lib.topic" element-id="code_example"/>
    <include from="lib.topic" element-id="jdbc-supported"/>
    <include from="lib.topic" element-id="r2dbc-supported"/>
</tldr>

Exposed provides comprehensive support for date and time operations through dedicated [modules](#add-dependency). Each
module is based on a different date-time library, offering different features and type support.

## Add a dependency {id="add-dependency"}

Before using date and time column types or functions, add one of the following modules to your build file:

| Module                                                                                                                                    | Based on                                                                                | Use                                                                                         |
|-------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| [`exposed-kotlin-datetime`](https://jetbrains.github.io/Exposed/api/exposed-kotlin-datetime/org.jetbrains.exposed.v1.datetime/index.html) | [`kotlinx-datetime`](https://kotlinlang.org/api/kotlinx-datetime/)                      | Modern Kotlin-first approach, recommended for new projects.                                 |
| [`exposed-java-time`](https://jetbrains.github.io/Exposed/api/exposed-java-time/org.jetbrains.exposed.v1.javatime/index.html)             | [Java 8 Time](https://docs.oracle.com/javase/8/docs/api/java/time/package-summary.html) | Good choice when integrating with Java code or when you need Java 8 Time API compatibility. |
| [`exposed-jodatime`](https://jetbrains.github.io/Exposed/api/exposed-jodatime/org.jetbrains.exposed.v1.jodatime/index.html)               | [Joda-Time](https://www.joda.org/joda-time/)                                            | Legacy support. Consider using newer modules for new projects.                              |

The following example shows the dependency declaration for `exposed-kotlin-datetime`:
<var name="artifact_name" value="exposed-kotlin-datetime">
<include from="lib.topic" element-id="add-dependency" />

Replace `exposed-kotlin-datetime` with `exposed-java-time` or `exposed-jodatime` if your project uses the Java Time or 
Joda-Time APIs instead.

## Basic usage

To define date and time columns, use the column functions provided by your selected [date-time module](#add-dependency).
The following examples define columns for common date and time types:

<tabs group="date-time-module">
<tab title="exposed-kotlin-datetime" id="exposed-kotlin-datetime" group-key="exposed-kotlin-datetime">

```kotlin
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.*

object Events : Table() {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 50)
    val startDate = date("start_date")
    val startTime = time("start_time")
    val createdAt = datetime("created_at")
        .defaultExpression(CurrentDateTime)
    val lastModified = timestamp("last_modified")
    val scheduledAt = timestampWithTimeZone("scheduled_at")
    val period = duration("period")

    override val primaryKey = PrimaryKey(id)
}
```

</tab>

<tab title="exposed-java-time" id="exposed-java-time" group-key="exposed-java-time">

```kotlin
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.*

object Events : Table() {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 50)
    val startDate = date("start_date")
    val startTime = time("start_time")
    val createdAt = datetime("created_at")
        .defaultExpression(CurrentDateTime)
    val lastModified = timestamp("last_modified")
    val scheduledAt = timestampWithTimeZone("scheduled_at")
    val period = duration("period")

    override val primaryKey = PrimaryKey(id)
}
```

</tab>
<tab title="exposed-jodatime" id="exposed-jodatime" group-key="exposed-jodatime">

```kotlin
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jodatime.*

object Events : Table() {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 50)
    val startDate = date("start_date")
    val startTime = time("start_time")
    val createdAt = datetime("created_at")
        .defaultExpression(CurrentDateTime)
    val scheduledAt = timestampWithTimeZone("scheduled_at")

    override val primaryKey = PrimaryKey(id)
}
```

</tab>
</tabs>

## Supported types

Each date-time module provides its own set of types. Below are the details for each supported type across all modules:

| Column type                                              | Database type              | Kotlin type                  | Java type                  | Joda-Time type            |
|----------------------------------------------------------|----------------------------|------------------------------|----------------------------|---------------------------|
| [`date()`](#date-type)                                   | `DATE`                     | `kotlinx.datetime.LocalDate` | `java.time.LocalDate`      | `org.joda.time.DateTime`  |
| [`time()`](#time-type)                                   | `TIME`                     | `kotlinx.datetime.LocalTime` | `java.time.LocalTime`      | `org.joda.time.LocalTime` |
| [`datetime()`](#datetime-type)                           | `DATETIME`                 | `kotlinx.datetime.Instant`   | `java.time.LocalDateTime`  | `org.joda.time.DateTime`  |
| [`timestamp()`](#timestamp-type)                         | `TIMESTAMP`                | `kotlinx.datetime.Instant`   | `java.time.Instant`        | —                         |
| [`timestampWithTimeZone()`](#timestampWithTimeZone-type) | `TIMESTAMP WITH TIME ZONE` | `java.time.OffsetDateTime`   | `java.time.OffsetDateTime` | `org.joda.time.DateTime`  |
| [`duration()`](#duration-type)                           | `BIGINT`                   | `kotlin.time.Duration`       | `java.time.Duration`       | —                         |

> Some types may differ for specific database dialects. Refer to your database documentation for exact type mappings.
> 
{style="note"}

### `date()` {id="date-type"}

The `date()` column type maps to the database `DATE` type. It is used for storing date values without a time
component:

```kotlin
val startDate = date("start_date")
```

<tabs group="date-time-module">
<tab title="exposed-kotlin-datetime" group-key="exposed-kotlin-datetime">

```kotlin
import kotlinx.datetime.LocalDate

Events.insert {
    it[startDate] = LocalDate(1990, 1, 1)
}
```

</tab>
<tab title="exposed-java-time" group-key="exposed-java-time">

```kotlin
import java.time.LocalDate

Events.insert {
    it[startDate] = LocalDate.of(1990, 1, 1)
}
```

</tab>
<tab title="exposed-jodatime" group-key="exposed-jodatime">

```kotlin
import org.joda.time.DateTime

Events.insert {
    it[startDate] = DateTime(1990, 1, 1, 0, 0, 0)
}
```

</tab>
</tabs>

### `time()` {id="time-type"}

The `time()` column type maps to the database `TIME` type. It is used for storing time values without a date
component.

```kotlin
val startTime = time("start_time")
```

<tabs group="date-time-module">
<tab title="exposed-kotlin-datetime" group-key="exposed-kotlin-datetime">

```kotlin
import kotlinx.datetime.LocalTime

Events.insert {
    it[startTime] = LocalTime(9, 0) // 09:00
}
```

</tab>
<tab title="exposed-java-time" group-key="exposed-java-time">

```kotlin
import java.time.LocalTime

Events.insert {
    it[startTime] = LocalTime.of(9, 0) // 09:00
}
```

</tab>
<tab title="exposed-jodatime" group-key="exposed-jodatime">

```kotlin
import org.joda.time.LocalTime

Events.insert {
    it[startTime] = LocalTime(9, 0) // 09:00
}
```

</tab>
</tabs>


### `datetime()` {id="datetime-type"}

The `datetime()` column type maps to the database `DATETIME` type. It is used for storing both date and time values.

The following example sets the current date/time when inserting new records:

```kotlin
val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
```

<tabs group="date-time-module">
<tab title="exposed-kotlin-datetime" group-key="exposed-kotlin-datetime">

```kotlin
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

Events.insert {
    it[createdAt] = Clock.System.now().toLocalDateTime(TimeZone.UTC)
}
```

</tab>
<tab title="exposed-java-time" group-key="exposed-java-time">

```kotlin
import java.time.LocalDateTime

Events.insert {
    it[createdAt] = LocalDateTime.now()
}
```

</tab>
<tab title="exposed-jodatime" group-key="exposed-jodatime">

```kotlin
import org.joda.time.DateTime

Events.insert {
    it[createdAt] = DateTime.now()
}
```

</tab>
</tabs>

### `timestamp()` {id="timestamp-type"}

The `timestamp()` column type maps to the database `TIMESTAMP` type. It is used for storing both date and time values. 
> This type is not supported by `exposed-jodatime`.
> 
{style="warning"}

```kotlin
val lastModified = timestamp("last_modified")
```

<tabs group="date-time-module">
<tab title="exposed-kotlin-datetime" group-key="exposed-kotlin-datetime">

```kotlin
import kotlin.time.Clock

Events.insert {
    it[lastModified] = Clock.System.now()
}
```

</tab>
<tab title="exposed-java-time" group-key="exposed-java-time">

```kotlin
import java.time.Instant

Events.insert {
    it[lastModified] = Instant.now()
}
```

</tab>
</tabs>


### `timestampWithTimeZone()` {id="timestampWithTimeZone-type"}

The `timestampWithTimeZone()` column type maps to the database `TIMESTAMP WITH TIME ZONE` type. It is used for storing
both date and time values while preserving timezone information.

```kotlin
val scheduledAt = timestampWithTimeZone("scheduled_at")
```

<tabs group="date-time-module">
<tab title="exposed-kotlin-datetime" group-key="exposed-kotlin-datetime">

```kotlin
import kotlin.time.Clock
import kotlin.time.toJavaInstant

Events.insert {
    it[scheduledAt] = Clock.System.now().toJavaInstant().atOffset(ZoneOffset.UTC)
}
```

</tab>
<tab title="exposed-java-time" group-key="exposed-java-time">

```kotlin
import java.time.OffsetDateTime
import java.time.ZoneOffset

Events.insert {
    it[scheduledAt] = OffsetDateTime.now(ZoneOffset.UTC)
}
```

</tab>
<tab title="exposed-jodatime" group-key="exposed-jodatime">

```kotlin
import org.joda.time.DateTime
import org.joda.time.DateTimeZone

Events.insert {
    it[scheduledAt] = DateTime.now().withZone(DateTimeZone.UTC)
}
```

</tab>
</tabs>


### `duration()` {id="duration-type"}

The `duration()` column type maps to the database `BIGINT` type. It is used for storing the length of time between one
instant and another.
> This type is not supported by `exposed-jodatime`.
>
{style="warning"}

```kotlin
val period = duration("period")
```

<tabs group="date-time-module">
<tab title="exposed-kotlin-datetime" group-key="exposed-kotlin-datetime">

```kotlin
import kotlin.time.Duration.Companion.hours

Events.insert {
    it[period] = 4.hours
}
```

</tab>
<tab title="exposed-java-time" group-key="exposed-java-time">

```kotlin
import java.time.Duration

Events.insert {
    it[period] = Duration.ofHours(4)
}
```

</tab>
</tabs>

> Some databases provide specific interval types (like PostgreSQL's `INTERVAL`) for storing time intervals. Refer to 
> your database documentation for supported time interval types.
> 
> For more details on handling custom time-based values, see the
> [custom date and time types](Custom-data-types.topic#date-and-time-data) section.
> 
{style="tip"}

## Working with date and time expressions

### Extracting date and time components

Exposed provides extension functions for extracting individual components from date and time expressions. These
functions return SQL expressions that can be used in queries, including `SELECT`, `WHERE`, `GROUP BY`, and `ORDER BY`
clauses.

The following functions are available for the date and time types supported by each Exposed date-time module:

<deflist type="medium">
<def id="year">
<title><code>.year()</code></title>

Returns the year as an `Int`.

</def>
<def id="month">
<title><code>.month()</code></title>

Returns the month of the year (`1`–`12`).

</def>
<def id="day">
<title><code>.day()</code></title>

Returns the day of the month (`1`–`31`).

</def>
<def id="hour">
<title><code>.hour()</code></title>

Returns the hour of the day (`0`–`23`).

</def>
<def id="minute">
<title><code>.minute()</code></title>

Returns the minute of the hour (`0`–`59`).

</def>
<def id="second">
<title><code>.second()</code></title>

Returns the second of the minute (`0`–`59`).

</def>
</deflist>

For example, the following query selects all events that start in June:

<tabs group="date-time-module">
<tab title="exposed-kotlin-datetime" group-key="exposed-kotlin-datetime">

```kotlin
import org.jetbrains.exposed.v1.datetime.month

Events.selectAll().where { Events.startDate.month() eq 6 }
```

</tab>
<tab title="exposed-java-time" group-key="exposed-java-time">

```kotlin
import org.jetbrains.exposed.v1.javatime.month

Events.selectAll().where { Events.startDate.month() eq 6 }
```

</tab>
<tab title="exposed-jodatime" group-key="exposed-jodatime">

```kotlin
import org.jetbrains.exposed.v1.jodatime.month

Events.selectAll().where { Events.startDate.month() eq 6 }
```

</tab>
</tabs>


### Using the current date and time

Exposed provides the following expressions for retrieving the current date and time from the database server:

<deflist type="medium">
<def id="CurrentDate">
<title><code>CurrentDate</code></title>
Returns the current date.
</def>
<def id="CurrentTime">
<title><code>CurrentTime</code></title>
Returns the current time.
</def>
<def id="CurrentTimestamp">
<title><code>CurrentTimestamp</code></title>
Returns the current date and time.

> `CurrentTimestamp` is not supported by `exposed-jodatime`.
> 
{style="warning"}
</def>
</deflist>

For example, these expressions can be used as default expressions when defining table columns:

<tabs group="date-time-module">
<tab title="exposed-kotlin-datetime" group-key="exposed-kotlin-datetime">

```kotlin
import org.jetbrains.exposed.v1.datetime.CurrentDate

object Events : Table() {
    val createdAt = datetime("created_at")
        .defaultExpression(CurrentDateTime)
}
```

</tab>
<tab title="exposed-java-time" group-key="exposed-java-time">

```kotlin
import org.jetbrains.exposed.v1.javatime.CurrentDate

object Events : Table() {
    val createdAt = datetime("created_at")
        .defaultExpression(CurrentDateTime)
}
```

</tab>
<tab title="exposed-jodatime" group-key="exposed-jodatime">

```kotlin
import org.jetbrains.exposed.v1.jodatime.CurrentDate

object Events : Table() {
    val createdAt = datetime("created_at")
        .defaultExpression(CurrentDateTime)
}
```

</tab>
</tabs>


These expressions are evaluated by the database when the SQL statement is executed. As a result, the returned values
reflect the database server's current date and time.

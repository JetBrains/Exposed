<show-structure for="chapter,procedure" depth="2"/>

# Entity definition

<tldr>
<p>
    <b>Required dependencies</b>:
    <code filter="JDBC">org.jetbrains.exposed:exposed-dao</code> (JDBC),
    <code filter="R2DBC">org.jetbrains.exposed:exposed-dao-r2dbc</code> (R2DBC)
</p>
<include from="lib.topic" element-id="jdbc-supported"/>
<include from="lib.topic" element-id="r2dbc-limited-support"/>
</tldr>

An [`Entity`](https://jetbrains.github.io/Exposed/api/exposed-dao/org.jetbrains.exposed.v1.dao/-entity/index.html) in
Exposed maps a database table record to a Kotlin object. This ensures type safety and allows you to work with database
records just like regular Kotlin objects, taking full advantage of Kotlin's language features.

When you use the Data Access Object (DAO) approach, the `IdTable` needs to be associated with an `Entity`. This is because
every database record in this table needs to be mapped to an `Entity` instance, identified by its primary key.

## Defining an Entity

You define an entity by creating a class. In the following example, `StarWarsFilmEntity` is the entity class linked to the
table `StarWarsFilmsTable`:

<tabs>
<tab id="StarWarsFilmEntity" title="StarWarsFilmEntity">

```kotlin
```
{src="exposed-dao/src/main/kotlin/org/example/entities/StarWarsFilmEntity.kt"}

</tab>
<tab id="StarWarsFilmsTable" title="StarWarsFilmsTable">
    <include from="DAO-Table-Types.topic" element-id="StarWarsFilmsTable-dao-code-block"/>
</tab>
</tabs>

> In IntelliJ IDEA Ultimate, you can use [the Exposed plugin](https://plugins.jetbrains.com/plugin/24367-exposed) to
> generate entities from your table definitions. For more information, see the
> [Exposed plugin documentation](https://www.jetbrains.com/help/idea/exposed.html).
>
{style="note"}

### Entity type

The entity type determines how the `Entity` class interacts with the table’s primary key. Since `StarWarsFilmsTable` is an
`IntIdTable`, the `StarWarsFilmsEntity` class extends from
[`IntEntity`](https://jetbrains.github.io/Exposed/api/exposed-dao/org.jetbrains.exposed.v1.dao/-int-entity/index.html),
which indicates that the ID and primary key of `StarWarsFilmsTable` is of type `Int`.

The following entity types are supported:

| Entity type | Description |
|-------------|-------------|
| [`IntEntity`](https://jetbrains.github.io/Exposed/api/exposed-dao/org.jetbrains.exposed.v1.dao/-int-entity/index.html) | Base class for an entity instance identified by an ID comprised of a wrapped `Int` value. |
| [`LongEntity`](https://jetbrains.github.io/Exposed/api/exposed-dao/org.jetbrains.exposed.v1.dao/-long-entity/index.html) | Base class for an entity instance identified by an ID comprised of a wrapped `Long` value. |
| [`UIntEntity`](https://jetbrains.github.io/Exposed/api/exposed-dao/org.jetbrains.exposed.v1.dao/-u-int-entity/index.html) | Base class for an entity instance identified by an ID comprised of a wrapped `UInt` value. |
| [`ULongEntity`](https://jetbrains.github.io/Exposed/api/exposed-dao/org.jetbrains.exposed.v1.dao/-u-long-entity/index.html) | Base class for an entity instance identified by an ID comprised of a wrapped `ULong` value. |
| [`UuidEntity`](https://jetbrains.github.io/Exposed/api/exposed-dao/org.jetbrains.exposed.v1.dao/-uuid-entity/index.html) | Base class for an entity instance identified by an ID comprised of a wrapped `kotlin.uuid.Uuid` value. |
| [`UUIDEntity`](https://jetbrains.github.io/Exposed/api/exposed-dao/org.jetbrains.exposed.v1.dao.java/-u-u-i-d-entity/index.html) | Base class for an entity instance identified by an ID comprised of a wrapped `java.util.UUID` value. Available from the package `org.jetbrains.exposed.v1.dao.java`. |
| [`CompositeEntity`](https://jetbrains.github.io/Exposed/api/exposed-dao/org.jetbrains.exposed.v1.dao/-composite-entity/index.html) | Base class for an entity instance identified by an ID comprised of multiple wrapped values. |

### Entity class

The [`EntityClass`](https://jetbrains.github.io/Exposed/api/exposed-dao/org.jetbrains.exposed.v1.dao/-entity-class/index.html)
in the `companion object` block is responsible for managing `Entity` instances, such as creating, querying, and deleting
records. It also maintains the relationship between the entity class (`StarWarsFilmEntity` in this example) and the database
table (`StarWarsFilmsTable`).

Each entity type is supported by a corresponding `EntityClass`, which accepts the following parameters:

| Parameter | Description |
|-----------|-------------|
| `table` | The `IdTable` containing rows mapped to entities that are managed by this class. |
| `entityType` | Optional. The expected type of the `Entity` class. This can be omitted if it is the class of the type argument provided to this `EntityClass` instance. |
| `entityCtor` | Optional. The function that instantiates an `Entity` using the provided `EntityID`. If not provided, reflection is used to determine the primary constructor on the first access, which may affect performance. |

In the example above, `IntEntityClass` is specified in the companion object:

```kotlin
```
{src="exposed-dao/src/main/kotlin/org/example/entities/StarWarsFilmEntity.kt" include-lines="9"}

This setup enables you to use functions provided by `IntEntityClass` to manage instances of `StarWarsFilmEntity`
effectively.

### Properties

Each column in the table is represented as a property in the entity class, where the `by` keyword ensures the data is
fetched or updated from the corresponding column when accessed.

```kotlin
```
{src="exposed-dao/src/main/kotlin/org/example/entities/StarWarsFilmEntity.kt" include-lines="10-13"}

Once the entity class is defined, instances of this class allow you to manipulate individual records from the corresponding
table. For example, by [creating a new record](dao-crud-operations.md#create),
[retrieving a row based on its primary key](dao-crud-operations.md#read), [updating values](dao-crud-operations.md#update),
or [deleting records](dao-crud-operations.md#delete).

## R2DBC differences

<include from="lib.topic" element-id="r2dbc-dao-experimental"/>

Entity definitions are almost identical for both drivers. Table definitions come from `exposed-core` and need no changes
at all.

Change the imports to the `.r2dbc` package:

<tabs group="connectivity">
<tab id="jdbc-entity-def" title="JDBC" group-key="jdbc">

```kotlin
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass

class StarWarsFilmEntity(id: EntityID<Int>) : IntEntity(id) {
companion object : IntEntityClass<StarWarsFilmEntity>(StarWarsFilmsTable)

var sequelId by StarWarsFilmsTable.sequelId
var name by StarWarsFilmsTable.name
var director by StarWarsFilmsTable.director
}
```

</tab>
<tab id="r2dbc-entity-def" title="R2DBC" group-key="r2dbc">

```kotlin
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntity
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntityClass

class StarWarsFilmEntity(id: EntityID<Int>) : IntEntity(id) {
companion object : IntEntityClass<StarWarsFilmEntity>(StarWarsFilmsTable)

var sequelId by StarWarsFilmsTable.sequelId
var name by StarWarsFilmsTable.name
var director by StarWarsFilmsTable.director
}
```

</tab>
</tabs>

Column properties are unchanged. Reference properties are not: they must become `val`. See
[](DAO-Relationships.topic#r2dbc-differences).

[Field transformations](#field-transformations), including memoized ones, work the same way. [Immutable entities](#immutable-entities)
are the one feature on this page with no R2DBC equivalent yet.

## Immutable entities

<tldr>
<include from="lib.topic" element-id="jdbc-supported"/>
<include from="lib.topic" element-id="r2dbc-not-supported"/>
</tldr>

For defining entities that are immutable, Exposed provides the additional
[`ImmutableEntityClass`](https://jetbrains.github.io/Exposed/api/exposed-dao/org.jetbrains.exposed.v1.dao/-immutable-entity-class/index.html)
and
[`ImmutableCachedEntityClass`](https://jetbrains.github.io/Exposed/api/exposed-dao/org.jetbrains.exposed.v1.dao/-immutable-cached-entity-class/index.html).

The `ImmutableCachedEntityClass` uses an internal cache to store entity loading states by the associated database. This
ensures that entity updates are synchronized with this class as the lock object.

To create an immutable entity, use either `ImmutableEntityClass` or `ImmutableCachedEntityClass` as a companion object in
your entity class. For example, here’s how to define a `CityEntity` class:

```kotlin
```
{src="exposed-dao/src/main/kotlin/org/example/entities/CityEntity.kt"}

* Properties are defined using `val` instead of `var`. This enforces immutability, as `val` properties cannot be reassigned
after initial assignment.
* The primary function of the entity class in this context is to query data from the associated table, not to modify it.
Therefore, inserts can only be performed using the DSL [`.insert()`](DSL-CRUD-operations.topic#insert) method and updates
can be done either through the DSL [`.update()`](DSL-CRUD-operations.topic#update) or the
[`ImmutableEntityClass.forceUpdateEntity()`](https://jetbrains.github.io/Exposed/api/exposed-dao/org.jetbrains.exposed.v1.dao/-immutable-entity-class/force-update-entity.html)
methods.

## Class method overrides

You can use override methods in both the `Entity` class and the `EntityClass` companion object to extend functionality or
manage entity behavior.

For example, here’s how to override the `delete()` method in a `User` entity:

```kotlin
```
{src="exposed-dao/src/main/kotlin/org/example/entities/UserEntityWithOverride.kt"}

In this example, a custom message is printed before the `.delete()` function of the superclass (`IntEntity`) completes the
deletion.

## Field transformations

As databases typically store only basic types, such as integers and strings, it's not always convenient to keep the same
simplicity on DAO level.

For example, you might need to parse JSON from a `VARCHAR` column, or retrieve values from a cache based on data from the
database. In such cases, we recommend to use column transformations.

### Example: Defining an Unsigned Integer field

Suppose that you want to define an unsigned integer field on an entity, but Exposed doesn't have such a column type yet.
You can achieve this by using the following implementation:

```kotlin
```
{src="exposed-dao/src/main/kotlin/org/example/entities/EntityWithUInt.kt"}

The
[`.transform()`](https://jetbrains.github.io/Exposed/api/exposed-dao/org.jetbrains.exposed.v1.dao/-entity-class/transform.html)
function accepts two lambdas that convert values to and from the original column type. In this case, you make sure to store
only `UInt` instances in the `uint` field.

Although it is still possible to insert or update values with negative integers via DAO, this approach assures a cleaner
business logic.

### Memoized transformations

If your transformation function involves complex operations that impact performance, you can use
[`.memoizedTransform()`](https://jetbrains.github.io/Exposed/api/exposed-dao/org.jetbrains.exposed.v1.dao/-entity-class/memoized-transform.html)
to cache the result of the transformation.

```kotlin
```
{src="exposed-dao/src/main/kotlin/org/example/entities/EntityWithBase64.kt"}

With memorized transformation, the value is unwrapped only once and then cached for future reads. This cache remains valid
for the lifetime of the entity. If the transaction's entity cache is cleared or the entity is reloaded in a new transaction
(creating a new cache without the existing value), the value will be wrapped again. However, if the original entity is kept
alive outside of the transaction, the cached value persists to avoid re-wrapping.

<show-structure for="chapter" depth="2"/>

# Relationships

<tldr>

**Required dependencies**: `org.jetbrains.exposed:exposed-dao` (JDBC), `org.jetbrains.exposed:exposed-dao-r2dbc` (R2DBC)

<include from="lib.topic" element-id="jdbc-supported"/>
<include from="lib.topic" element-id="r2dbc-limited-support"/>

**Code examples**: [`exposed-dao-relationships`](https://github.com/JetBrains/Exposed/tree/main/documentation-website/Writerside/snippets/exposed-dao-relationships/),
[`exposed-dao-r2dbc-relationships`](https://github.com/JetBrains/Exposed/tree/main/documentation-website/Writerside/snippets/exposed-dao-r2dbc-relationships/)

</tldr>

Relationships define how entities are associated with one another in your database schema and provide
mechanisms to query and manipulate these associations. There are four ways entities could reference one another:

* [Many-to-one reference](#many-to-one-reference)
* [Optional reference](#optional-reference)
* [Many-to-many reference](#many-to-many-reference)
* [Parent-child reference](#parent-child-reference)

<include from="lib.topic" element-id="r2dbc-dao-experimental-note"/>

## Many-to-one reference {id="many-to-one-reference"}

<tldr>

**JDBC**: [`referencedOn()`](https://jetbrains.github.io/Exposed/api/exposed-dao/org.jetbrains.exposed.v1.dao/-entity-class/referenced-on.html)

**R2DBC**: [`referencedOn()`](https://jetbrains.github.io/Exposed/api/exposed-dao-r2dbc/org.jetbrains.exposed.v1.dao.r2dbc/-entity-class/referenced-on.html)

</tldr>

A many-to-one reference is a relationship between two database tables where multiple rows in one table
(the "child" table) can reference a single row in another table (the "parent" table).

Consider the following `UsersTable` and its corresponding entity:

<tabs>
<tab id="users-table" title="UsersTable">

```kotlin
```
{src="exposed-dao-relationships/src/main/kotlin/org/example/tables/UsersTable.kt" include-symbol="UsersTable"}

</tab>
<tab id="user-entity" title="UserEntity">

```kotlin
```
{src="exposed-dao-relationships/src/main/kotlin/org/example/entities/UserEntity.kt" include-lines="17-20,22"}

</tab>
</tabs>

Assume you want to add another table, `UserRatingsTable`, to store user ratings for a particular
film. Each rating is associated with a user, but a user can be associated with many ratings. This is a
many-to-one relationship.

You can implement this relationship by using a reference column in the child table
(`UserRatingsTable`) that links to the parent table (`UsersTable`).

To create a  reference column, use the [`reference()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.core/-table/reference.html)
function:

```kotlin
```
{src="exposed-dao-relationships/src/main/kotlin/org/example/tables/UserRatingsTable.kt" include-symbol="UserRatingsTable"}

In the corresponding entity class, use the `referencedOn()` infix function to register the reference:

<tabs group="connectivity">
<tab group-key="JDBC" id="user-ratings-entity" title="JDBC">

```kotlin
```
{src="exposed-dao-relationships/src/main/kotlin/org/example/entities/UserRatingEntity.kt" include-symbol="UserRatingEntity"}

</tab>
<tab group-key="R2DBC" id="user-ratings-entity-jdbc" title="R2DBC">

```kotlin
```
{src="exposed-dao-r2dbc-relationships/src/main/kotlin/org/example/entities/UserRatingEntity.kt" include-symbol="UserRatingEntity"}

</tab>
</tabs>

* With JDBC, `referencedOn()` returns the referenced entity directly, so properties are declared as `var`.
* With R2DBC, `referencedOn()` return an accessor instead of the referenced entity, so properties are declared as `val`.

### Accessing data {id="accessing-data"}

To retrieve the referenced film from a `UserRatingEntity`, use the syntax appropriate for the connectivity type:

<tabs group="connectivity">
<tab group-key="JDBC" id="accessing-data-jdbc" title="JDBC">

```kotlin
```
{src="exposed-dao-relationships/src/main/kotlin/org/example/examples/OneToManyExamples.kt" include-symbol="film"}

</tab>
<tab group-key="R2DBC" id="accessing-data-r2dbc" title="R2DBC">

```kotlin
```
{src="exposed-dao-r2dbc-relationships/src/main/kotlin/org/example/examples/OneToManyExamples.kt" include-symbol="film"}

</tab>
</tabs>

* With JDBC, the property getter returns the referenced film directly.
* With R2DBC, the property returns an accessor. You can retrieve the referenced entity by invoking it.

### Reverse access {id="reverse-access"}

<tldr>

**JDBC**: [`referrersOn()`](https://jetbrains.github.io/Exposed/api/exposed-dao/org.jetbrains.exposed.v1.dao/-entity-class/referrers-on.html)

**R2DBC**: [`referrersOn()`](https://jetbrains.github.io/Exposed/api/exposed-dao-r2dbc/org.jetbrains.exposed.v1.dao.r2dbc/-entity-class/referrers-on.html)

</tldr>

While you can use the `.find()` function of the entity class to get all ratings for a film, it is preferred to use the 
`referrersOn()` infix function on the class representing the film:

<tabs group="connectivity">
<tab group-key="JDBC" id="reverse-access-jdbc" title="JDBC">

```kotlin
```
{src="exposed-dao-relationships/src/main/kotlin/org/example/entities/StarWarsFilmEntity.kt" include-lines="19-25,27"}

</tab>
<tab group-key="R2DBC" id="reverse-access-r2dbc" title="R2DBC">

```kotlin
```
{src="exposed-dao-r2dbc-relationships/src/main/kotlin/org/example/entities/StarWarsFilmEntity.kt" include-lines="19-25,27"}

</tab>
</tabs>

You can then access this field on an entity object:

<tabs group="connectivity">
<tab group-key="JDBC" id="reverse-access-field-jdbc" title="JDBC">

```kotlin
```
{src="exposed-dao-relationships/src/main/kotlin/org/example/examples/OneToManyExamples.kt" include-symbol="filmRatings"}

</tab>
<tab group-key="R2DBC" id="reverse-access-field-r2dbc" title="R2DBC">

```kotlin
```
{src="exposed-dao-r2dbc-relationships/src/main/kotlin/org/example/examples/OneToManyExamples.kt" include-symbol="filmRatings"}

</tab>
</tabs>

### Back reference {id="back-reference"}

<tldr>

**JDBC**: [`backReferencedOn()`](https://jetbrains.github.io/Exposed/api/exposed-dao/org.jetbrains.exposed.v1.dao/-entity-class/back-referenced-on.html)

**R2DBC**: [`backReferencedOn()`](https://jetbrains.github.io/Exposed/api/exposed-dao-r2dbc/org.jetbrains.exposed.v1.dao.r2dbc/-entity-class/back-referenced-on.html)

</tldr>

If each user rates only one film, you can use the `backReferencedOn()` infix function on the entity class to access the
`UserRatingsTable` data:

```kotlin
```
{src="exposed-dao-relationships/src/main/kotlin/org/example/entities/UserEntity.kt" include-symbol="UserWithSingleRatingEntity"}

You can then access this field on a `UserWithSingleRatingEntity` object:

<tabs group="connectivity">
<tab group-key="JDBC" id="back-ref-jdbc" title="JDBC">

```kotlin
user1.rating
```

</tab>
<tab group-key="R2DBC" id="back-ref-r2dbc" title="R2DBC">

```kotlin
user1.rating()
```

</tab>
</tabs>

## Optional reference {id="optional-reference"}

<tldr>

**JDBC**: [`optionalReferencedOn()`](https://jetbrains.github.io/Exposed/api/exposed-dao/org.jetbrains.exposed.v1.dao/-entity-class/optional-referenced-on.html)

**R2DBC**: [`optionalReferencedOn()`](https://jetbrains.github.io/Exposed/api/exposed-dao-r2dbc/org.jetbrains.exposed.v1.dao.r2dbc/-entity-class/optional-referenced-on.html)

</tldr>

In Exposed, you can also add an optional reference.

For example, if you want to include anonymous user ratings to your table, you can do so by setting
the reference field as optional using the
[`optReference()`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.core/-table/opt-reference.html)
function in your table definition:

```kotlin
```
{src="exposed-dao-relationships/src/main/kotlin/org/example/tables/UserRatingsTable.kt" include-symbol="UserRatingsWithOptionalUserTable"}

In your entity definition, use and `optionalReferencedOn` to register the optional reference:

<tabs group="connectivity">
<tab group-key="JDBC" id="optional-ref-jdbc" title="JDBC">

```kotlin
```
{src="exposed-dao-relationships/src/main/kotlin/org/example/entities/UserRatingEntity.kt" include-symbol="UserRatingWithOptionalUserEntity"}

</tab>
<tab group-key="R2DBC" id="optional-ref-r2dbc" title="R2DBC">

```kotlin
```
{src="exposed-dao-r2dbc-relationships/src/main/kotlin/org/example/entities/UserRatingEntity.kt" include-symbol="UserRatingWithOptionalUserEntity"}

</tab>
</tabs>

## Ordered reference {id="ordered-reference"}

<tldr>

**JDBC**: [`orderBy()`](https://jetbrains.github.io/Exposed/api/exposed-dao/org.jetbrains.exposed.v1.dao/-referrers/order-by.html)

**R2DBC**: [`orderBy()`](https://jetbrains.github.io/Exposed/api/exposed-dao-r2dbc/org.jetbrains.exposed.v1.dao.r2dbc/-referrers/order-by.html)

</tldr>

You can define the order in which referenced entities appear using the `orderBy()` function:

```kotlin
```
{src="exposed-dao-relationships/src/main/kotlin/org/example/entities/UserEntity.kt" include-symbol="UserEntity"}

In a more complex scenario, you can specify multiple columns along with the corresponding sort order
for each:

```kotlin
```
{src="exposed-dao-relationships/src/main/kotlin/org/example/entities/UserEntity.kt" include-lines="23-27,37-41"}

Without using the [infix notation](https://kotlinlang.org/docs/functions.html#infix-notation),
the `orderBy()` function is chained after `referrersOn()`:

```kotlin
```
{src="exposed-dao-relationships/src/main/kotlin/org/example/entities/UserEntity.kt" include-lines="23-27,32-35,41"}

## Many-to-many reference {id="many-to-many-reference"}

<tldr>

**JDBC**: [`via()`](https://jetbrains.github.io/Exposed/api/exposed-dao/org.jetbrains.exposed.v1.dao/-entity/via.html)

**R2DBC**: [`via()`](https://jetbrains.github.io/Exposed/api/exposed-dao-r2dbc/org.jetbrains.exposed.v1.dao/-entity/via.html)

</tldr>

A many-to-many reference is a relationship between two database tables where multiple records in one table
are related to multiple records in another table. This type of relationship is modeled by using an
intermediate table to link the two tables.

Consider the following table, `ActorsTable`, and its corresponding entity:

<tabs>
<tab id="actors-table" title="ActorsTable">

```kotlin
```
{src="exposed-dao-relationships/src/main/kotlin/org/example/tables/ActorsTable.kt" include-symbol="ActorsTable"}

</tab>
<tab id="actor-entity" title="ActorEntity">

```kotlin
```
{src="exposed-dao-relationships/src/main/kotlin/org/example/entities/ActorEntity.kt" include-symbol="ActorEntity"}

</tab>
</tabs>

Suppose you now want to extend this table to include a reference to the `StarWarsFilmEntity`
class. To achieve this, you can create an additional intermediate table to store the references:

```kotlin
```
{src="exposed-dao-relationships/src/main/kotlin/org/example/tables/ActorsTable.kt" include-symbol="StarWarsFilmActorsTable"}

Add a reference to the `ActorEntity` in the `StarWarsFilmEntity` using the `via()` function:

```kotlin
```
{src="exposed-dao-relationships/src/main/kotlin/org/example/entities/StarWarsFilmEntity.kt" include-lines="26"}


The final `StarWarsFilmEntity` looks the following way:


```kotlin
```
{src="exposed-dao-relationships/src/main/kotlin/org/example/entities/StarWarsFilmEntity.kt" include-symbol="StarWarsFilmEntity"}

## Parent-Child reference {id="parent-child-reference"}

A parent-child reference is very similar to a many-to-many relationship, but an intermediate table contains
both references to the same table.

A parent-child relationship can represent hierarchical data, such as a series of films and their directors.
For example, you may want to track how directors oversee multiple Star Wars films, including sequels or
spin-offs. For this, you would create a self-referencing intermediate table to define the relationships
between a parent film (original) and its child films (sequels or spin-offs):

```kotlin
```
{src="exposed-dao-relationships/src/main/kotlin/org/example/tables/StarWarsFilmsTable.kt" include-symbol="StarWarsFilmRelationsTable"}

In this example, `parentFilm` represents the original film, whereas `childFilm`
represents a sequel, prequel, or spin-off. As you can see `StarWarsFilmRelationsTable` columns
target only `StarWarsFilmsWithDirectorTable`.

You then need to update the entity class to include relationships for parent and child films using the
`via()` function:

<tabs group="connectivity">
<tab group-key="JDBC" id="parent-child-jdbc" title="JDBC">

```kotlin
```
{src="exposed-dao-relationships/src/main/kotlin/org/example/entities/StarWarsFilmEntity.kt" include-symbol="StarWarsFilmWithParentAndChildEntity"}

</tab>
<tab group-key="R2DBC" id="parent-child-r2dbc" title="R2DBC">

```kotlin
```
{src="exposed-dao-r2dbc-relationships/src/main/kotlin/org/example/entities/StarWarsFilmEntity.kt" include-symbol="StarWarsFilmWithParentAndChildEntity"}

</tab>
</tabs>

Here’s how you can create and query the parent-child hierarchy for `StarWarsFilmsTable`:

<tabs group="connectivity">
<tab group-key="JDBC" id="parent-child-query-jdbc" title="JDBC">

```kotlin
```
{src="exposed-dao-relationships/src/main/kotlin/org/example/examples/ParentChildExamples.kt" include-lines="16-40"}

</tab>
<tab group-key="R2DBC" id="parent-child-query-r2dbc" title="R2DBC">

```kotlin
```
{src="exposed-dao-r2dbc-relationships/src/main/kotlin/org/example/examples/ParentChildExamples.kt" include-lines="16-40"}

</tab>
</tabs>

## Composite primary key reference {id="composite-primary-key-reference"}

In some database schemas, a composite primary key is used to uniquely identify rows by combining
multiple columns. Here's how you can reference composite ID tables.

Assume that you have the following `CompositeIdTable` and its relevant entity:

<tabs>
<tab id="directors-table" title="DirectorsCompositeIdTable">

```kotlin
```
{src="exposed-dao-relationships/src/main/kotlin/org/example/tables/DirectorsTable.kt" include-symbol="DirectorsCompositeIdTable"}

</tab>
<tab id="director-entity" title="DirectorCompositeIDEntity">

```kotlin
```
{src="exposed-dao-relationships/src/main/kotlin/org/example/entities/DirectorEntity.kt" include-lines="26-29,31"}

</tab>
</tabs>

You can refactor the `StarWarsFilmsTable` table to reference this table by adding columns to hold the
appropriate primary key values and creating a table-level foreign key constraint:

```kotlin
```
{src="exposed-dao-relationships/src/main/kotlin/org/example/tables/StarWarsFilmsTable.kt" include-symbol="StarWarsFilmsWithCompositeRefTable"}

Then, add the field to the entity using the `referencedOn()` function:

<tabs group="connectivity">
<tab group-key="JDBC" id="comp-primary-key-jdbc" title="JDBC">

```kotlin
```
{src="exposed-dao-relationships/src/main/kotlin/org/example/entities/StarWarsFilmEntity.kt" include-symbol="StarWarsFilmWithCompositeRefEntity"}

</tab>
<tab group-key="R2DBC" id="comp-primary-key-r2dbc" title="R2DBC">

```kotlin
```
{src="exposed-dao-r2dbc-relationships/src/main/kotlin/org/example/entities/StarWarsFilmEntity.kt" include-symbol="StarWarsFilmWithCompositeRefEntity"}

</tab>
</tabs>

> For more information on creating table foreign key constraints, see the
> [Foreign Key constraint](Working-with-Tables.topic#foreign-key) section.
>
{style="tip"}

Now you can get the director for a `StarWarsFilm` object, `movie`,
in the same way you would get any other field:

```kotlin
movie.director // returns a Director object
```

If you wanted to get all the films made by a director, you could add a `referrersOn`
field to the `DirectorCompositeIDEntity` class:

```kotlin
```
{src="exposed-dao-relationships/src/main/kotlin/org/example/entities/DirectorEntity.kt" include-symbol="DirectorCompositeIDEntity"}

You can then access this field on a `DirectorCompositeIDEntity` object, `director`:

```kotlin
director.films // returns all StarWarsFilm objects that reference this director
```

Using other previously mentioned [infix functions](https://kotlinlang.org/docs/functions.html#infix-notation),
like `optionalReferencedOn`, `backReferencedOn`, and `optionalReferrersOn`, is also supported for referencing or
referenced `CompositeEntity` objects,
by using the respective overloads that accept an `IdTable` as an argument.
These overloads will automatically resolve the foreign key constraint associated with the composite primary key.

## Eager Loading {id="eager-loading"}

<tldr>

**JDBC**: [`load()`](https://jetbrains.github.io/Exposed/api/exposed-dao/org.jetbrains.exposed.v1.dao/load.html)

**R2DBC**: [`load()`](https://jetbrains.github.io/Exposed/api/exposed-dao-r2dbc/org.jetbrains.exposed.v1.dao.r2dbc/load.html)

</tldr>

References in Exposed are lazily loaded, meaning queries to fetch the data for the reference are made at
the moment the reference is first utilised. In cases where you know you will require references
ahead of time, Exposed can eager load them at the time of the parent query. This is
preventing the classic "N+1" problem as references can be aggregated and loaded in a single query.

To eager load a reference, use the `.load()` function and pass the DAO's reference as
a `KProperty`:

```kotlin
```
{src="exposed-dao-relationships/src/main/kotlin/org/example/examples/EagerLoadingExamples.kt" include-lines="11"}

This works for references of references. For example, if `UserRatingTable` had a `film`
reference you could do the following:

```kotlin
```
{src="exposed-dao-relationships/src/main/kotlin/org/example/examples/EagerLoadingExamples.kt" include-lines="14"}

> References that are eagerly loaded are stored inside the transaction cache.
> This means that they are not available in other transactions
> and thus, must be loaded and referenced inside the same transaction.
> Enabling `keepLoadedReferencesOutOfTransaction` in `DatabaseConfig`
> will allow getting referenced values outside the transaction block.
>
{style="note"}

### Loading collections {id="eager-loading-collections"}

<tldr>

**JDBC**: [`.with()`](https://jetbrains.github.io/Exposed/api/exposed-dao/org.jetbrains.exposed.v1.dao/with.html)

**R2DBC**: [`.with()`](https://jetbrains.github.io/Exposed/api/exposed-dao-r2dbc/org.jetbrains.exposed.v1.dao.r2dbc/with.html)

</tldr>

To eagerly load references on `Collections` of DAO's such as `List`
and `SizedIterable`, use the `.with()` function and pass each reference as `KProperty`:

```kotlin
```
{src="exposed-dao-relationships/src/main/kotlin/org/example/examples/EagerLoadingExamples.kt" include-lines="17"}

`.with()` eagerly loads references for all `Entity` instances in the
`SizedIterable` returned by `.all()` and returns this collection.

> `SizedIterable` requires a transaction to execute any of its methods, so the loaded
> collection cannot be directly used outside a `transaction` block unless it is first
> converted to a standard collection, such as by calling `.toList()`.
>
{style="note"}

### Loading text fields {id="eager-loading-text-fields"}

Some database drivers do not load text content immediately due to performance and memory reasons.
This means that you can obtain the column value only within the open transaction.

To make content available outside the transaction, use the
[`eagerLoading`](https://jetbrains.github.io/Exposed/api/exposed-core/org.jetbrains.exposed.v1.core/-text-column-type/eager-loading.html)
parameter in your field definition:

```kotlin
object StarWarsFilmsTable : Table() {
    //...
    val description = text("name", eagerLoading=true)
}
```

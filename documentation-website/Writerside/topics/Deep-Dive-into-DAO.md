# Deep Dive into DAO

## Overview
The DAO (Data Access Object) API of Exposed, is similar to ORM frameworks like Hibernate with a Kotlin-specific API.  
A DB table is represented by an `object` inherited from `org.jetbrains.exposed.sql.Table` like this:
```kotlin
object StarWarsFilms : Table() {
  val id: Column<Int> = integer("id").autoIncrement()
  val sequelId: Column<Int> = integer("sequel_id").uniqueIndex()
  val name: Column<String> = varchar("name", 50)
  val director: Column<String> = varchar("director", 50)
  override val primaryKey = PrimaryKey(id, name = "PK_StarWarsFilms_Id") // PK_StarWarsFilms_Id is optional here
}
```
Tables that contain an `Int` id with the name `id` can be declared like this:
```kotlin
object StarWarsFilms : IntIdTable() {
  val sequelId: Column<Int> = integer("sequel_id").uniqueIndex()
  val name: Column<String> = varchar("name", 50)
  val director: Column<String> = varchar("director", 50)
}
```
Note that these Column types will be defined automatically, so you can also just leave them out. This would produce the same result as the example above:
```kotlin
object StarWarsFilms : IntIdTable() {
  val sequelId = integer("sequel_id").uniqueIndex()
  val name = varchar("name", 50)
  val director = varchar("director", 50)
}
```
An entity instance or a row in the table is defined as a class instance:
 ```kotlin
class StarWarsFilm(id: EntityID<Int>) : IntEntity(id) {
  companion object : IntEntityClass<StarWarsFilm>(StarWarsFilms)
  var sequelId by StarWarsFilms.sequelId 
  var name     by StarWarsFilms.name
  var director by StarWarsFilms.director
}
```
## Basic CRUD operations
### Create
```kotlin
val movie = StarWarsFilm.new {
  name = "The Last Jedi"
  sequelId = 8
  director = "Rian Johnson"
}
```
### Read
To get entities use one of the following
```kotlin
val movies = StarWarsFilm.all()
val movies = StarWarsFilm.find { StarWarsFilms.sequelId eq 8 }
val movie = StarWarsFilm.findById(5)
```
* For a list of available predicates, see [DSL Where expression](Deep-Dive-into-DSL.md#where-expression).  
  Read a value from a property similar to any property in a Kotlin class:
```kotlin
val name = movie.name
```
#### Sort (Order-by)
Ascending order:
```kotlin
val movies = StarWarsFilm.all().sortedBy { it.sequelId }
```
Descending order:
```kotlin
val movies = StarWarsFilm.all().sortedByDescending{ it.sequelId }
```
### Update
Update the value of a property similar to any property in a Kotlin class:
```kotlin
movie.name = "Episode VIII – The Last Jedi"
```
* Note: Exposed doesn't make an immediate update when you set a new value for Entity, it just stores it on the inner map. "Flushing" values to the database occurs at the end of the transaction, or before the next ` select *` from the database.

Search for an entity by its id and apply an update:
```kotlin
val updatedMovie = StarWarsFilm.findByIdAndUpdate(5) {
    it.name = "Episode VIII – The Last Jedi"
}
```

Search for a single entity by a query and apply an update:
```kotlin
val updatedMovie2 = StarWarsFilm.findSingleByAndUpdate(StarWarsFilms.name eq "The Last Jedi") {
    it.name = "Episode VIII – The Last Jedi"
}
```

### Delete
```kotlin
movie.delete() 
```
## Referencing
### many-to-one reference
Let's say you have this table:
```kotlin
object Users : IntIdTable() {
    val name = varchar("name", 50)
}
class User(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<User>(Users)
    var name by Users.name
}
```
And now you want to add a table referencing this table (and other tables!):
```kotlin
object UserRatings : IntIdTable() {
    val value = long("value")
    val film = reference("film", StarWarsFilms)
    val user = reference("user", Users)
}
class UserRating(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<UserRating>(UserRatings)
    var value by UserRatings.value
    var film by StarWarsFilm referencedOn UserRatings.film // use referencedOn for normal references
    var user by User referencedOn UserRatings.user
}
```
Now you can get the film for a `UserRating` object, `filmRating`, in the same way you would get any other field:
```kotlin
filmRating.film // returns a StarWarsFilm object
```
Now if you wanted to get all the ratings for a film, you could do that by using the `filmRating.find` function, but it is much easier to just add a `referrersOn` field to the `StarWarsFilm` class:
```kotlin
class StarWarsFilm(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<StarWarsFilm>(StarWarsFilms)
    ...
    val ratings by UserRating referrersOn UserRatings.film // make sure to use val and referrersOn
    ...
}
```
You can then access this field on a `StarWarsFilm` object, `movie`:
```kotlin
movie.ratings // returns all UserRating objects with this movie as film
```
Now imagine a scenario where a user only ever rates a single film. If you want to get the single rating for that user, you can add a `backReferencedOn` field to the `User` class to access the `UserRating` table data:
```kotlin
class User(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<User>(Users)
    ...
    val rating by UserRating backReferencedOn UserRatings.user // make sure to use val and backReferencedOn
}
```
You can then access this field on a `User` object, `user1`:
```kotlin
user1.rating // returns a UserRating object
```
### Optional reference
You can also add an optional reference:
```kotlin
object UserRatings: IntIdTable() {
    ...
    val secondUser = reference("second_user", Users).nullable() // this reference is nullable!
    ...
}
class UserRating(id: EntityID<Int>): IntEntity(id) {
    companion object : IntEntityClass<UserRating>(UserRatings)
    ...
    var secondUser by User optionalReferencedOn UserRatings.secondUser // use optionalReferencedOn for nullable references
    ...
}
```
Now `secondUser` will be a nullable field, and `optionalReferrersOn` should be used instead of `referrersOn` to get all the ratings for a `secondUser`.

```kotlin
class User(id: EntityID<Int>): IntEntity(id) {
    companion object : IntEntityClass<User>(Users)
    ...
    val secondRatings by UserRating optionalReferrersOn UserRatings.secondUser // make sure to use val and optionalReferrersOn
    ...
}
```

### Ordered reference

You can also define the order in which referenced entities appear:

```kotlin
class User(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<User>(Users)

    ...
    val ratings by UserRating referrersOn UserRatings.user orderBy UserRatings.value
    ...
}
```

In a more complex scenario, you can specify multiple columns along with the corresponding sort order for each:

```kotlin
class User(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<User>(Users)

    ...
    val ratings by UserRating referrersOn UserRatings.user orderBy listOf(UserRatings.value to DESC, UserRatings.id to ASC)
    ...
}
```

### many-to-many reference
In some cases, a many-to-many reference may be required.
Let's assume you want to add a reference to the following Actors table to the StarWarsFilm class:
```kotlin
object Actors: IntIdTable() {
    val firstname = varchar("firstname", 50)
    val lastname = varchar("lastname", 50)
}
class Actor(id: EntityID<Int>): IntEntity(id) {
    companion object : IntEntityClass<Actor>(Actors)
    var firstname by Actors.firstname
    var lastname by Actors.lastname
}
```
Create an additional intermediate table to store the references:
```kotlin
object StarWarsFilmActors : Table() {
    val starWarsFilm = reference("starWarsFilm", StarWarsFilms)
    val actor = reference("actor", Actors)
    override val primaryKey = PrimaryKey(starWarsFilm, actor, name = "PK_StarWarsFilmActors_swf_act") // PK_StarWarsFilmActors_swf_act is optional here
}
```
Add a reference to `StarWarsFilm`:
```kotlin
class StarWarsFilm(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<StarWarsFilm>(StarWarsFilms)
    ...
    var actors by Actor via StarWarsFilmActors
    ...
}
```
Note: You can set up IDs manually inside a transaction like this:
```kotlin
transaction {
    // only works with UUIDTable and UUIDEntity
    StarWarsFilm.new (UUID.randomUUID()){
        ...
        actors = SizedCollection(listOf(actor))
    }
}
```
### Parent-Child reference
Parent-child reference is very similar to many-to-many version, but an intermediate table contains both references to the same table.
Let's assume you want to build a hierarchical entity which could have parents and children. Our tables and an entity mapping will look like
```kotlin
object NodeTable : IntIdTable() {
    val name = varchar("name", 50)
}
object NodeToNodes : Table() {
    val parent = reference("parent_node_id", NodeTable)
    val child = reference("child_user_id", NodeTable)
}
class Node(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Node>(NodeTable)
    var name by NodeTable.name
    var parents by Node.via(NodeToNodes.child, NodeToNodes.parent)
    var children by Node.via(NodeToNodes.parent, NodeToNodes.child)
}
```
As you can see `NodeToNodes` columns target only `NodeTable` and another version of `via` function were used.
Now you can create a hierarchy of nodes.
```kotlin
val root = Node.new { name = "root" }
val child1 = Node.new {
    name = "child1" 
}
child1.parents = SizedCollection(root) // assign parent
val child2 = Node.new { name = "child2" }
root.children = SizedCollection(listOf(child1, child2)) // assign children
```

### Eager Loading
**Available since 0.13.1**.
References in Exposed are lazily loaded, meaning queries to fetch the data for the reference are made at the moment the reference is first utilised. For scenarios wherefore you know you will require references ahead of time, Exposed can eager load them at the time of the parent query, this is prevents the classic "N+1" problem as references can be aggregated and loaded in a single query.
To eager load a reference you can call the "load" function and pass the DAO's reference as a KProperty:
```kotlin
StarWarsFilm.findById(1).load(StarWarsFilm::actors)
```
This works for references of references also, for example if Actors had a rating reference you could:
```kotlin
StarWarsFilm.findById(1).load(StarWarsFilm::actors, Actor::rating)
```
Similarly, you can eagerly load references on Collections of DAO's such as Lists and SizedIterables, for collections you can use the with function in the same fashion as before, passing the DAO's references as KProperty's.
```kotlin
StarWarsFilm.all().with(StarWarsFilm::actors)
```
NOTE: References that are eagerly loaded are stored inside the transaction cache;
this means that they are not available in other transactions
and thus must be loaded and referenced inside the same transaction.
As of [0.35.1](https://github.com/JetBrains/Exposed/blob/master/docs/ChangeLog.md#0351:~:text=References%20can%20be%20stored%20within%20an%20Entity%20with%20enabled%20keepLoadedReferencesOutOfTransaction%20config%20parameter.%20It%20will%20allow%20getting%20referenced%20values%20outside%20the%20transaction%20block.),
enabling `keepLoadedReferencesOutOfTransaction` in `DatabaseConfig`
will allow getting referenced values outside the transaction block.

#### Eager loading for Text Fields
Some database drivers do not load text content immediately (for performance and memory reasons) which means that you can obtain the column value only within the open transaction.

If you desire to make content available outside the transaction, you can use the eagerLoading param when defining the DB Table.
```kotlin
object StarWarsFilms : Table() {
    ...
    val description = text("name", eagerLoading=true)
}
```
## Advanced CRUD operations
### Read entity with a join to another table
Let's imagine that you want to find all users who rated second SW film with more than 5.
First of all, we should write that query using Exposed DSL.
```kotlin
val query = Users.innerJoin(UserRatings).innerJoin(StarWarsFilm)
    .select(Users.columns)
    .where {
        StarWarsFilms.sequelId eq 2 and (UserRatings.value gt 5) 
    }.withDistinct()
```
After that all we have to do is to "wrap" a result with User entity:
```kotlin
val users = User.wrapRows(query).toList()
```
### Auto-fill created and updated columns on entity change
See example by @PaulMuriithi [here](https://github.com/PaulMuriithi/ExposedDatesAutoFill/blob/master/src/main/kotlin/app/Models.kt).
### Use queries as expressions
Imagine that you want to sort cities by how many users each city has. In order to do so, you can write a sub-query which counts users in each city and order by that number. Though in order to do so you'll have to convert `Query` to `Expression`. This can be done using `wrapAsExpression` function:
```kotlin
val expression = wrapAsExpression<Int>(Users
    .select(Users.id.count())
    .where {
        Cities.id eq Users.cityId
    })
val cities = Cities
    .selectAll()
    .orderBy(expression, SortOrder.DESC)
    .toList()
```

### Add computed fields to entity class
Imagine that you want to use a window function to rank films with each entity fetch. The companion object of the entity class can override any open function in `EntityClass`, but to achieve this functionality only `searchQuery()` needs to
be overriden. The results of the function can then be accessed using a property of the entity class:
```kotlin
object StarWarsFilms : IntIdTable() {
    val sequelId = integer("sequel_id").uniqueIndex()
    val name = varchar("name", 50)
    val rating = double("rating")

    val rank = Rank().over().orderBy(rating, SortOrder.DESC)
}

class StarWarsFilm(id: EntityID<Int>) : IntEntity(id) {
    var sequelId by StarWarsFilms.sequelId
    var name by StarWarsFilms.name
    var rating by StarWarsFilms.rating

    val rank: Long
        get() = readValues[StarWarsFilms.rank]

    companion object : IntEntityClass<StarWarsFilm>(StarWarsFilms) {
        override fun searchQuery(op: Op<Boolean>): Query {
            return super.searchQuery(op).adjustSelect {
                select(columns + StarWarsFilms.rank).set
            }
        }
    }
}

transaction {
    StarWarsFilm.new {
        sequelId = 8
        name = "The Last Jedi"
        rating = 4.2
    }
    // more insertions ...
    entityCache.clear()

    // fetch entities with value (or store entities then read value)
    StarWarsFilm.find { StarWarsFilms.name like "The%" }.map { it.name to it.rank }
}
```

## Entities mapping
### Fields transformation
As databases could store only basic types like integers and strings it's not always conveniently to keep the same simplicity on DAO level.
Sometimes you may want to make some transformations like parsing json from a varchar column or get some value from a cache based on value from a database.
In that case the preferred way is to use column transformations. Assume that we want to define unsigned integer field on Entity, but Exposed doesn't have such column type yet.
```kotlin
object TableWithUnsignedInteger : IntIdTable() {
    val uint = integer("uint")
}
class EntityWithUInt : IntEntity() {
    var uint: UInt by TableWithUnsignedInteger.uint.transform({ it.toInt() }, { it.toUInt() })
    
    companion object : IntEntityClass<EntityWithUInt>()
}
```
`transform` function accept two lambdas to convert values to and from an original column type.
After that in your code you'll be able to put only `UInt` instances into `uint` field.
It still possible to insert/update values with negative integers via DAO, but your business code becomes much cleaner.
Please keep in mind what such transformations will aqure on every access to a field what means that you should avoid heavy transformations here.

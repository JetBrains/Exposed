# Frequently Asked Questions 

### Q: [Squash](https://github.com/orangy/squash) is same as Exposed. Where is the difference?
A: [Ilya Ryzhenkov](https://github.com/orangy/) (Squash maintainer) answers:
> Squash is an attempt to refactor Exposed (long time ago) to fix DSL issues, extensibility on dialect side, support graph fetching and avoid TLS-stored transactions. Unfortunately, I didn’t have enough time to finish the work, but I still hope to return to it some day. We are talking with Exposed maintainer [@tapac](https://github.com/tapac/) about coordinating efforts and eventually joining forces. Note, that none of these libs are “official” JetBrains Kotlin SQL libs, they are both side projects of their respective authors.

### Q: Can I use multiple Database Connections?

A: Yes. See [Transactions](Transactions.md#working-with-multiple-databases)

### Q: Is `Array` column type supported?

A: Yes. See [Data Types](Data-Types.topic#how-to-use-array-types).

### Q: Is `upsert` supported?

A: Yes. See [Insert Or Update](DSL-CRUD-operations.topic#insert-or-update)

### Q: Is `json` type supported?

A: Yes. See [JSON](Data-Types.topic#how-to-use-json-and-jsonb-types)

### Q: How to get a plain SQL query which will be executed?

A:
```kotlin
val plainSQL = FooTable.selectAll().where {}.prepareSQL(QueryBuilder(false)) 
```
Use QueryBuiler with `false` - if you want to inline statement arguments, `true` - to see '?' in query.

### Q: Is it possible to update a field relative to current field value?

A: Yes. See example here: [https://github.com/JetBrains/Exposed/wiki/DSL#update](https://github.com/JetBrains/Exposed/issues/118)

### Q: How can I add another type of Database?

A: Implement `DatabaseDialect` interface and register it with `Database.registerDialect()`.  
If the implementation adds a lot of value consider contributing it as a PR to Exposed.

### Q: Is it possible to create tables with cross / cyclic reference?

A: Yes, it's possible since Exposed 0.11.1 version

### Q: How can I implement nested queries?

A: See example here: [https://github.com/JetBrains/Exposed/issues/248](https://github.com/JetBrains/Exposed/issues/118)

### Q: How can I use SAVEPOINT?
A: It possible only through using a raw connection. See example [here](https://github.com/JetBrains/Exposed/issues/320#issuecomment-394825415).

### Q: How to prepare query like: `SELECT * FROM table WHERE (x,y) IN ((1, 2), (3, 4), (5, 6))`
A: It possible with custom function. See [example](https://github.com/JetBrains/Exposed/issues/373#issuecomment-414123325).

### Q: Where can I find snapshot builds of Exposed
A: You could use jitpack.io service for that.

Add jitpack.io to repositories:
```
repositories {
    maven { url 'https://jitpack.io' }
}
```
Then add Exposed dependency as stated below:
```
dependencies {
    implementation 'com.github.JetBrains:Exposed:-SNAPSHOT'
}
```

### Q: How can I create a custom column type?
A: Just implements [IColumnType](https://github.com/JetBrains/Exposed/blob/76a671e57a0105d6aed79e256c088690bd4a56b6/exposed-core/src/main/kotlin/org/jetbrains/exposed/sql/ColumnType.kt#L25)
and use [registerColumn](https://github.com/JetBrains/Exposed/blob/76a671e57a0105d6aed79e256c088690bd4a56b6/exposed-core/src/main/kotlin/org/jetbrains/exposed/sql/Table.kt#L387)
to [extends](https://kotlinlang.org/docs/extensions.html) a [Table](https://github.com/JetBrains/Exposed/blob/76a671e57a0105d6aed79e256c088690bd4a56b6/exposed-core/src/main/kotlin/org/jetbrains/exposed/sql/Table.kt#L326)


eg: **Create custom UUID types (inpired by [@pjagielski article](https://medium.com/@pjagielski/how-we-use-kotlin-with-exposed-at-touk-eacaae4565b5#e4e4))**
```kotlin
abstract class TypedId(open val id: UUID): Serializable, Comparable<TypedId> {
    override fun compareTo(other: TypedId) = this.id.compareTo(other.id)
}

class TypedUUIDColumnType<T: TypedId>(val constructor: (UUID) -> T, private val uuidColType: UUIDColumnType = UUIDColumnType()): IColumnType by uuidColType {
    override fun valueFromDB(value: Any) = constructor(uuidColType.valueFromDB(value))
    override fun notNullValueToDB(value: Any): Any = uuidColType.notNullValueToDB(valueUnwrap(value))
    override fun nonNullValueToString(value: Any): String = uuidColType.nonNullValueToString(valueUnwrap(value))
    private fun valueUnwrap(value: Any) = (value as? TypedId)?.id ?: value
}

fun <T: TypedId> Table.typedUuid(name: String, constructor: (UUID) -> T) = registerColumn<T>(name, TypedUUIDColumnType<T>(constructor))
fun <T: TypedId> Column<T>.autoGenerate(constructor: (UUID) -> T): Column<T> = clientDefault { constructor(UUID.randomUUID()) }


class StarWarsFilmId(id: UUID): TypedId(id)

object StarWarsFilms : Table() {
    val id = typedUuid("id") { StarWarsFilmId(it) }.autoGenerate{ StarWarsFilmId(it) }
    val name: Column<String> = varchar("name", 50)
    val director: Column<String> = varchar("director", 50)
    final override val primaryKey = PrimaryKey(id)
}
```


Reference: [#149](https://github.com/JetBrains/Exposed/issues/149)

### More questions on Stack Overflow:
[https://stackoverflow.com/questions/tagged/kotlin-exposed](https://stackoverflow.com/questions/tagged/kotlin-exposed)

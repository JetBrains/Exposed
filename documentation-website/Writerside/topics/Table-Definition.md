# Table Definition

This page shows what table types Exposed supports and how to define and create these tables. It also contains tips on configuring 
constraints, such as `PRIMARY KEY`, `DEFAULT`, and `INDEX`. All examples use the H2 database to generate SQL.

## Table Types

The most primitive table type is `Table`. It is located in the **org.jetbrains.exposed.sql** package of the **exposed-core** module.
To configure a custom name for a table, which will be used in actual SQL queries, pass it to the `name` parameter of the `Table()` constructor.
Otherwise, Exposed will generate it from the full class name or the class name without the suffix 'Table', if present.

For example, to create a simple table with an integer `id` column and a string `name` column, use any of the following options:

Omit the `name` parameter to generate the table name from the object name:
```kotlin
object Cities : Table() {
    val id = integer("id")
    val name = varchar("name", 50)
}
```
```sql
CREATE TABLE IF NOT EXISTS CITIES (ID INT NOT NULL, "name" VARCHAR(50) NOT NULL)
```
Omit the `name` parameter to generate the table name from the object name, with any 'Table' suffix removed:
```kotlin
object CitiesTable : Table() {
    val id = integer("id")
    val name = varchar("name", 50)
}
```
```sql
CREATE TABLE IF NOT EXISTS CITIES (ID INT NOT NULL, "name" VARCHAR(50) NOT NULL)
```
Provide an argument to `name` to generate a specific table name:
```kotlin
object Cities : Table("all_cities") {
    val id = integer("id")
    val name = varchar("name", 50)
}
```
```sql
CREATE TABLE IF NOT EXISTS ALL_CITIES (ID INT NOT NULL, "name" VARCHAR(50) NOT NULL)
```
Some databases, like H2, fold unquoted identifiers to upper case. To keep table name case-sensitivity, manually quote the provided argument:
```kotlin
object Cities : Table("\"all_cities\"") {
    val id = integer("id")
    val name = varchar("name", 50)
}
```
```sql
CREATE TABLE IF NOT EXISTS "all_cities" (ID INT NOT NULL, "name" VARCHAR(50) NOT NULL)
```

Depending on what DBMS you use, the types of columns could be different in actual SQL queries.

### IdTable Types

Exposed also provides the base `IdTable` class, which is inherited by `IntIdTable`, `LongIdTable` (and their unsigned variants), `UUIDTable`, and `CompositeIdTable` classes from the 
**org.jetbrains.exposed.dao.id** package of the **exposed-core** module.

These tables could be declared without the `id` column, and IDs of the appropriate type would be generated automatically when creating new table rows.
To configure a custom name for the `id` column, pass it to the `columnName` parameter of the appropriate table constructor.

For example, the `Cities` table could instead be defined as an `IntIdTable`, which would make the `id` column both auto-incrementing and the table's primary key:
```kotlin
object Cities : IntIdTable() {
    val name = varchar("name", 50)
}
```
```sql
CREATE TABLE IF NOT EXISTS CITIES (ID INT AUTO_INCREMENT PRIMARY KEY, "name" VARCHAR(50) NOT NULL)
```

<tip>For more information on <code>IdTable</code> types, see <a href="Deep-Dive-into-DAO.md#table-types">DAO Table Types</a>.</tip>

## Constraints

### Nullable

The `NOT NULL` SQL constraint restricts the column to accept the `null` value. By default, Exposed applies this constraint to 
all the columns. To allow the column to be nullable, apply the `nullable()` method to a definition of an appropriate column.

For example, to make the population column `nullable`, use the following code:
```kotlin
// SQL: POPULATION INT NULL
val population: Column<Int?> = integer("population").nullable()
```

### Default

The `DEFAULT` SQL constraint provides the default value for the column. Exposed supports three methods for configuring 
default values:

* `default(defaultValue: T)` accepts a value with a type of the column.
* `defaultExpression(defaultValue: Expression<T>)` accepts an expression.
* `clientDefault(defaultValue: () -> T)` accepts a function.

For example, to configure the default value for the `name` column, use the following code:
```kotlin
// SQL: "NAME" VARCHAR(50) DEFAULT 'Unknown'
val name: Column<String> = varchar("name", 50).default("Unknown")
```

Exposed also supports marking a column as `databaseGenerated` if the default value of the column is not known at the
time of table creation and/or if it depends on other columns. It makes it possible to omit setting a value for the
column when inserting a new record, without getting an error. The value for the column can be set by creating a TRIGGER
or with a DEFAULT clause, for example.

For example:
```kotlin
val name: Column<String> = varchar("name", 50).databaseGenerated()
```

### Index

The `INDEX` SQL constraint makes traversing through tables quicker. Exposed supports the `index()` method. 
It has six parameters, most of which are optional:

* `val customIndexName: String? = null` is a custom name for the index, which will be used in actual SQL queries.
* `val unique: Boolean` defines whether the index is unique or not.
* `val columns: List<Column<*>>` defines a column set.
* `val functions: List<ExpressionWithColumnType<*>>? = null` defines functional key parts.
* `val indexType: String? = null` is a custom type. Can be `"BTREE"` or `"HASH"`.
* `val filterCondition: (SqlExpressionBuilder.() -> Op<Boolean>)? = null` defines a condition used to create a partial index.

The simplest way to create an index is to use an extension function directly on a column. For example, to apply a non-unique 
`INDEX` constraint to the `name` column, use the following code:
```kotlin
val name = varchar("name", 50).index()
```
If the parameter `customIndexName` is not set, the name of the index is determined by the table and column names.

Also, Exposed supports complex indexes. If you have a frequent query for two columns, Exposed can perform it more efficiently.
It creates a tree from the first column with the references to the second one. For example, to create a non-unique complex
index on the `name` and `population` columns, paste the following code:
```kotlin
val indexName = index("indexName", false, *arrayOf(name, population))
// or inside an init block within the table object
init {
    index("indexName", isUnique = false, name, population)
}
```

Exposed also supports creating an index with a custom type. For example, to retrieve data from the `name` column faster 
with a hash function for traversing, use the following code:
```kotlin
val indexName = index("indexName", false, *arrayOf(name), indexType = "HASH")
```

Some databases support functional key parts that index expressions instead of columns directly:
```kotlin
init {
    index(functions = listOf(name.lowerCase(), address.substring(1, 5)))
    uniqueIndex(columns = arrayOf(name), functions = listOf(Coalesce(address, stringLiteral("*"))))
}
```
Operator expressions, like `plus()`, are also accepted by the `functions` parameter.

Some databases support creating a partial index by defining a filter expression to improve querying performance. The 
created index will only contain entries for the table rows that match this predicate:
```kotlin
init {
    index(columns = arrayOf(name, flag)) { flag eq true }
    index(columns = arrayOf(name, population)) { (name like "A%") and (population greaterEq 10) }
}
```

Once a table has been created, the list of its indices can be accessed using the property `Table.indices`. Table indices 
are represented by the data class `Index`, so its properties can be checked in the following manner, for example:
```kotlin
Table.indices.map { it.indexName to it.createStatement().first() }
```

<note>
An instance of the <code>Index</code> data class can be created directly using its public constructor, for the purpose of 
evaluating or using  create/modify/drop statements, for example. Doing so will not add the instance to an existing table's 
list of indices in the way that using <code>index()</code> would. Also, if an instance is created with arguments provided to the 
<code>functions</code> parameter, a <code>functionsTable</code> argument must also be provided.
</note>

### Unique

The `UNIQUE` SQL constraint restricts duplicates within this column. Exposed supports the `uniqueIndex()` method which 
creates a unique index for the column. This method is the composition of `UNIQUE` and `INDEX` constraint, the quicker 
modification of `UNIQUE` constraint.

For example, to apply `UNIQUE` and `INDEX` constraint to the `name` column, use the following code:
```kotlin
val name = varchar("name", 50).uniqueIndex()
```

### Primary Key

The `PRIMARY KEY` SQL constraint applied to a column means each value in that column identifies the row. This constraint is the composition
of `NOT NULL` and `UNIQUE` constraints.  To change the column set, add columns, or change the primary key name to a custom one, override this field of the table class.

For example, to define the `name` column as the primary key, use the following code. The "Cities_name" string 
will be used as the constraint name in the actual SQL query, if provided; otherwise a name will be generated based on the table's name.
```kotlin
override val primaryKey = PrimaryKey(name, name = "Cities_name")
```
```sql
CONSTRAINT Cities_name PRIMARY KEY ("name")
```

It is also possible to define a primary key on a table using multiple columns:
```kotlin
override val primaryKey = PrimaryKey(id, name)
```
```sql
CONSTRAINT pk_Cities PRIMARY KEY (ID, "name")
```

Except for `CompositeIdTable`, each available class in Exposed that inherits from `IdTable` has the `primaryKey` field automatically defined.
For example, the `IntIdTable` by default has an auto-incrementing integer column, `id`, which is defined as the primary key.

An `IdTable` that requires a primary key with multiple columns can be defined using `CompositeIdTable`.
In this case, each column that is a component of the table's `id` should be identified by `entityId()`:
```kotlin
object Towns : CompositeIdTable("towns") {
    val areaCode = integer("area_code").autoIncrement().entityId()
    val latitude = decimal("latitude", 9, 6).entityId()
    val longitude = decimal("longitude", 9, 6).entityId()
    val name = varchar("name", 32)

    override val primaryKey = PrimaryKey(areaCode, latitude, longitude)
}
```
<tip>For more information on <code>CompositeIdTable</code> types, see <a href="Deep-Dive-into-DAO.md#table-types">DAO Table Types</a>.</tip>

### Foreign Key

The `FOREIGN KEY` SQL constraint links two tables. A foreign key is a column from one table that refers to the primary key 
or columns with a unique index from another table. To configure a foreign key on a column, use `reference()` or `optReference()` 
methods. The latter lets the foreign key accept a `null` value. To configure a foreign key on multiple columns,
use `foreignKey()` directly within an `init` block.

`reference()` and `optReference()` methods have several parameters:

`name: String`
: A name for the foreign key column, which will be used in actual SQL queries.

`ref: Column<T>`
: A target column from another parent table.

`onDelete: ReferenceOption? = null`
: An action for when a linked row from a parent table will be deleted.

`onUpdate: ReferenceOption? = null`
: An action for when a value in a referenced column will be changed.

`fkName: String? = null`
: A name for the foreign key constraint.

Enum class `ReferenceOption` has five values:

`RESTRICT`
: An option that restricts changes on a referenced column, and the default option for most dialects.

`NO_ACTION`
: The same as RESTRICT in some, but not all, databases, and the default option for Oracle and SQL Server dialects.

`CASCADE`
: An option that allows updating or deleting the referring rows.

`SET_NULL`
: An option that sets the referring column values to null.

`SET_DEFAULT`
: An option that sets the referring column values to the default value.

Consider the following `Citizens` table. This table has the `name` and `city` columns. If the `Cities` table has 
configured the `name` column as the primary key, the `Citizens` table can refer to it by its `city` column, which is a foreign key. To 
configure such reference and make it nullable, use the `optReference()` method:
```kotlin
object Citizens : IntIdTable() {
    val name = varchar("name", 50)
    val city = optReference("city", Cities.name, onDelete=ReferenceOption.CASCADE)
}
```

If any `Cities` row will be deleted, the appropriate `Citizens` row will be deleted too.

If instead the `Cities` table has configured multiple columns as the primary key (for example, both `id` and `name` columns as in the above [section](#primary-key)),
the `Citizens` table can refer to it by using a table-level foreign key constraint. In this case, the `Citizens` table must have defined matching columns
to store each component value of the `Cities` table's primary key:
```kotlin
object Citizens : IntIdTable() {
    val name = varchar("name", 50)
    val cityId = integer("city_id")
    val cityName = varchar("city_name", 50)

    init {
        foreignKey(cityId, cityName, target = Cities.primaryKey)
    }
}
```

In the above example, the order of the referencing columns in `foreignKey()` must match the order of columns defined in the target primary key.
If this order is uncertain, the foreign key can be defined with explicit column associations instead:
```kotlin
init {
    foreignKey(cityId to Cities.id, cityName to Cities.name)
}
```

### Check

The `CHECK` SQL constraint checks that all values in a column match some condition. Exposed supports the `check()` method. 
You apply this method to a column and pass the appropriate condition to it.

For example, to check that the `name` column contains strings that begin with a capital letter, use the following code:
```kotlin
// SQL: CONSTRAINT check_Cities_0 CHECK (REGEXP_LIKE("NAME", '^[A-Z].*', 'c')))
val name = varchar("name", 50).check { it regexp "^[A-Z].*" }
```

Some databases, like older MySQL versions, may not support `CHECK` constraints. For more information, consult the relevant documentation.

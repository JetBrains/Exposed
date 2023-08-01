# Table Definition

This topic shows what table types Exposed supports and how to define and create tables. Also, it contains tips on configuring 
constraints, such as `PRIMARY KEY`, `DEFAULT`, `INDEX` and so on.

## Table Types

The most primitive table type is `Table()`. It's located in **org.jetbrains.exposed.sql** package and has `NOT NULL` SQL constraint 
configured by default on all columns. To configure a custom name for the table, which will be used in actual SQL queries, pass 
it to the `name` parameter of the `Table()` constructor. Otherwise, Exposed will generate it from a class name.

For example, to create a simple table called `"citiesTable"` with integer `id` column and string `name` column, use the 
following code.
```kotlin
// SQL: CREATE TABLE IF NOT EXISTS CITIESTABLE (
//          ID INT NOT NULL,
//          "NAME" VARCHAR(50) NOT NULL
//      )
object Cities : Table(name = "citiesTable") {
    val id = integer("id")
    val name = varchar("name", 50)
}
```
Also, Exposed provides `IdTable` class which is inherited by `IntIdTable()`, `LongIdTable()`, and `UUIDTable(`) classes from 
**org.jetbrains.exposed.dao.id** package of **exposed-core** module. These tables could be declared without the `id` attribute. 
IDs of appropriate type will be generated automatically when creating new table rows. To configure a custom name 
for the `id` attribute, pass it to the `columnName` parameter of the appropriate table constructor.

Depending on what DBMS you use, types of columns could be different in actual SQL queries. We use H2 database in our examples.

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
An instance of the `Index` data class can be created directly using its public constructor, for the purpose of 
evaluating or using  create/modify/drop statements, for example. Doing so will not add the instance to an existing table's 
list of indices in the way that using `index()` would. Also, if an instance is created with arguments provided to the 
`functions` parameter, a `functionsTable` argument must also be provided.
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

The `PRIMARY KEY` SQL constraint applied to columns means each value in a column identifies the row. It's the composition 
of `NOT NULL` and `UNIQUE` constraints. Each kind of table in Exposed, inherited from `IdTable()`, has the `primaryKey` 
field. For example, the `IntIdTable` has default integer `id` primary key. If you want to change column set, add columns, 
or change primary key name to a custom one, you need to override this field of the appropriate table class.

For example, if you want to define the `name` column as a primary key, use the following code. The "Cities_name" string 
will be used in actual SQL query.
```kotlin
// SQL: CONSTRAINT Cities_name PRIMARY KEY ("NAME")
override val primaryKey = PrimaryKey(name, name = "Cities_name")
```

### Foreign Key

The `FOREIGN KEY` SQL constraint links two tables. Foreign key is a column from one table that refers to the primary key 
or columns with a unique index from another table. To configure the foreign key, use `reference()` or `optReference()` 
method. The second one let the foreign key accept the `null` value.

`reference()` and `optReference()` methods have several parameters:

* `name: String` is a name for foreign key column, which will be used in actual SQL queries.
* `ref: Column<T>` is a target column from another parent table.
* `onDelete: ReferenceOption? = null` is an action to the case when linked row from a parent table can be deleted.
* `onUpdate: ReferenceOption? = null` is an action to the case when value in a referenced column can be changed.
* `fkName: String? = null` is a foreign key constraint name.

Enum class `ReferenceOption` has four values:

* `RESTRICT` is an option that restricts changes on a referenced column, and the default option for MySQL dialect.
* `NO_ACTION` is the same as RESTRICT, and the default option for Oracle and SQL Server dialects.
* `CASCADE` is an option that allows updating or deleting the referring rows.
* `SET_NULL` is an option that sets the referring column values to null.
* `SET_DEFAULT` is an option that sets the referring column values to the default value.

Consider the following `Citizens` table. This table has the `name` and `city` columns. Since the `Cities` table has 
configured `name` primary key, the `Citizens` table can refer to it by its `city` column, which is a foreign key. To 
configure such reference and make it nullable, use the `optReference()` method:
```kotlin
object Citizens : IntIdTable() {
    val name = varchar("name", 50)
    val city = optReference("city", Cities.name, onDelete=ReferenceOption.CASCADE)
}
```

If any `Cities` row will be deleted, the appropriate `Citizens` row will be deleted too.

### Check

The `CHECK` SQL constraint checks that all values in a column match some condition. Exposed supports the `check()` method. 
You apply this method to a column and pass the appropriate condition to it.

For example, to check that the `name` column contains strings that begin with a capital letter, use the following code:
```kotlin
// SQL: CONSTRAINT check_Cities_0 CHECK (REGEXP_LIKE("NAME", '^[A-Z].*', 'c')))
val name = varchar("name", 50).check { it regexp "^[A-Z].*" }
```

Some databases, like older MySQL versions, may not support `CHECK` constraints. For more information, consult the relevant documentation.

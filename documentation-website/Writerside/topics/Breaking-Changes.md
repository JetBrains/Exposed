# Breaking Changes

## 0.56.0
* If the `distinct` parameter of `groupConcat()` is set to `true`, when using Oracle or SQL Server, this will now fail early with an
  `UnsupportedByDialectException`. Previously, the setting would be ignored and SQL function generation would not include a `DISTINCT` clause.
* In Oracle and H2 Oracle, the `ubyte()` column now maps to data type `NUMBER(3)` instead of `NUMBER(4)`.
* In Oracle and H2 Oracle, the `ushort()` column now maps to data type `NUMBER(5)` instead of `NUMBER(6)`.
* In Oracle and H2 Oracle, the `uinteger()` column now maps to data type `NUMBER(10)` instead of `NUMBER(13)`.
* In Oracle and H2 Oracle, the `integer()` column now maps to data type `NUMBER(10)` and `INTEGER` respectively, instead of `NUMBER(12)`.
  In Oracle and SQLite, using the integer column in a table now also creates a CHECK constraint to ensure that no out-of-range values are inserted.
* In H2 Oracle, the `long()` column now maps to data type `BIGINT` instead of `NUMBER(19)`.
  In Oracle and SQLite, using the long column in a table now also creates a CHECK constraint to ensure that no out-of-range values are inserted.

## 0.55.0
* The `DeleteStatement` property `table` is now deprecated in favor of `targetsSet`, which holds a `ColumnSet` that may be a `Table` or `Join`.
  This enables the use of the new `Join.delete()` function, which performs a delete operation on a specific table from the join relation.
  The original statement class constructor has also been deprecated in favor of the constructor that accepts `targetsSet`, as well as another
  additional parameter `targetTables` (for specifying which table from the join relation, if applicable, to delete from).
* The `DeleteStatement` property `offset` was not being used and is now deprecated, as are the extension functions that have an `offset` parameter.
  `deleteWhere()` and `deleteIgnoreWhere()`, as well as the original statement class constructor, no longer accept an argument for `offset`.
* `SizedIterable.limit(n, offset)` is now deprecated in favor of 2 independent methods, `limit()` and `offset()`.
  In supporting databases, this allows the generation of an OFFSET clause in the SELECT statement without any LIMIT clause.
  Any custom implementations of the `SizedIterable` interface with a `limit()` override will now show a warning that the declaration overrides
  a deprecated member. This override should be split into an implementation of the 2 new members instead.

  The original `FunctionProvider.queryLimit()` is also being deprecated in favor of `queryLimitAndOffset()`, which takes a
  nullable `size` parameter to allow exclusion of the LIMIT clause. This latter deprecation only affects extensions of the
  `FunctionProvider` class when creating a custom `VendorDialect` class.
* In Oracle, the `short` column now maps to data type `NUMBER(5)` instead of `SMALLINT` because `SMALLINT` is stored as `NUMBER(38)` in the database and
  takes up unnecessary storage.
  In Oracle and SQLite, using the `short` column in a table now also creates a check constraint to ensure that no out-of-range values are inserted.
* In Oracle, the `byte` column now maps to data type `NUMBER(3)` instead of `SMALLINT` because `SMALLINT` is stored as `NUMBER(38)` in the database and
  takes up unnecessary storage.
  In SQL Server, the `byte` column now maps to data type `SMALLINT` instead of `TINYINT` because `TINYINT`
  [allows values from 0 to 255](https://learn.microsoft.com/en-us/sql/t-sql/data-types/int-bigint-smallint-and-tinyint-transact-sql?view=sql-server-ver16#:~:text=2%20bytes-,tinyint,-0%20to%20255).
  In SQL Server, SQLite, Oracle, PostgreSQL, and H2 PostgreSQL, using the `byte` column in a table now also creates a check constraint to ensure that no out-of-range
  values are inserted.
* The transformation of a nullable column (`Column<Unwrapped?>.transform()`) requires handling null values.
  This enables conversions from `null` to a non-nullable value, and vice versa.
* In H2 the definition of json column with default value changed from `myColumn JSON DEFAULT '{"key": "value"}'` to `myColumn JSON DEFAULT JSON '{"key": "value"}'`

## 0.54.0

* All objects that are part of the sealed class `ForUpdateOption` are now converted to `data object`.
* The `onUpdate` parameter in `upsert()`, `upsertReturning()`, and `batchUpsert()` will no longer accept a list of column-value pairs as an argument.
  The parameter now takes a lambda block with an `UpdateStatement` as its argument, so that column-value assignments for the UPDATE clause can be set
  in a similar way to `update()`.
  This enables the use of `insertValue(column)` in expressions to specify that the same value to be inserted into a column should be used when updating.
```kotlin
// before
TestTable.upsert(
    onUpdate = listOf(Words.count to Words.count.plus(1))
) {
    it[word] = "Kotlin"
    it[count] = 3
}

// after
TestTable.upsert(
    onUpdate = {
        it[Words.count] = Words.count + 1
    }
) {
    it[word] = "Kotlin"
    it[count] = 3
}

// after - with new value from insert used in update expression
TestTable.upsert(
    onUpdate = {
        it[Words.count] = Words.count + insertValue(Words.count)
    }
) {
    it[word] = "Kotlin"
    it[count] = 3
}
```
* The function `statementsRequiredForDatabaseMigration` has been moved from `SchemaUtils` to `MigrationUtils` in the `exposed-migration` module.
* A nested transaction (with `useNestedTransactions = true`) that throws any exception will now rollback any commits since the last savepoint.
  This ensures that the nested transaction is properly configured to act in the exact same way as a top-level transaction or `inTopLevelTransaction()`.

  An inner transaction (with `useNestedTransactions = false`) that throws any exception will also rollback any commits since the last savepoint.
  This ensures that any exception propagated from the inner transaction to the outer transaction will not be swallowed if caught by some
  exception handler wrapping the inner transaction, and any inner commits will not be saved. In version 0.55.0, this change will be reduced
  so that only inner transactions that throw an `SQLException` from the database will trigger such a rollback.

## 0.53.0

* DAO Entity Transformation Changes
  * **Parameter Renaming**: `transform()` and `memoizedTransform()` now use `wrap` and `unwrap` instead of `toColumn` and `toReal`.
    ```kotlin
    // Old:
    var name by EmployeeTable.name.transform(toColumn = { it.uppercase() }, toReal = { it.lowercase() })
    // New:
    var name by EmployeeTable.name.transform(wrap = { it.uppercase() }, unwrap = { it.lowercase() })
    ```
  * **Class Renaming**: `ColumnWithTransform` is now `EntityFieldWithTransform`, consolidating properties into a single `transformer`.
    ```kotlin
    EntityFieldWithTransform(column, object : ColumnTransformer<String, Int> {
            override fun unwrap(value: Int): String = value.toString()
            override fun wrap(value: String): Int = value.toInt()
        })
    ``` 
  * Entity transformation via DAO is deprecated and should be replaced with DSL transformation.
    ```kotlin
    val tester = object : Table() {
            val value = integer("value")
                .transform(wrap = { ... }, unwrap = { ... })
        }
    ```
    

## 0.51.0

* The `exposed-spring-boot-starter` module no longer provides the entire [spring-boot-starter-data-jdbc](https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-starter-data-jdbc) module.
  It now provides just the [spring-boot-starter-jdbc](https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-starter-jdbc).
  If there was a reliance on this transitive dependency, please directly include a dependency on Spring Data JDBC in your build files.
* `ulong` column type is now NUMERIC(20) instead of BIGINT for H2 (excluding H2_PSQL), SQLite, and SQL Server to allow storing the full range of `ULong`,
  including `ULong.MAX_VALUE`.

## 0.50.0

* The `Transaction` class property `repetitionAttempts` is being deprecated in favor of `maxAttempts`. Additionally, the property `minRepetitionDelay` should be replaced
  with `minRetryDelay`, and `maxRepetitionDelay` with `maxRetryDelay`. These changes also affect the default variants of these properties in `DatabaseConfig`.
* The property `maxAttempts` represents the maximum amount of attempts to perform a transaction block. Setting it, or the now deprecated `repetitionAttempts`, to a
  value less than 1 now throws an `IllegalArgumentException`.
* `IColumnType` and `ColumnType` now expect a type argument. `IColumnType.valueFromDB()` also no longer has a default implementation, so an override for this method
  must be provided in any custom column type implementation. Check this [pull request](https://github.com/JetBrains/Exposed/pull/2027) for details regarding this change.

## 0.49.0

* For SQLite database, Exposed now requires bumping the SQLite JDBC driver version to a minimum of 3.45.0.0.

## 0.48.0

* In `nonNullValueToString` for `KotlinInstantColumnType` and `JavaDateColumnType`, the formatted String for MySQL did not match the format received from the metadata
  when `isFractionDateTimeSupported` is true, so a new formatter specific to that is now used.
* In `nonNullValueToString` for `KotlinLocalDateTimeColumnType`, the formatted String for MySQL did not match the format received from the metadata
  when `isFractionDateTimeSupported` is true, so a new formatter specific to MySQL is now used.
* In `nonNullValueToString` for `DateColumnType`, `JavaLocalDateTimeColumnType`, `JavaLocalTimeColumnType`, `JavaInstantColumnType`, `KotlinLocalDateTimeColumnType`,
  `KotlinLocalTimeColumnType`, and `KotlinInstantColumnType`, the correct formatter for MySQL is used when the version (below 5.6) does not support fractional
  seconds.
* In `nonNullValueToString` for `DateColumnType` and `DateTimeWithTimeZoneColumnType`, the formatters used are changed to reflect the fact that Joda-Time stores
  date/time values only down to the millisecond (up to SSS and not SSSSSS).
* Functions `anyFrom(array)` and `allFrom(array)` now use `ArrayColumnType` to process the provided array argument when query building.
  `ArrayColumnType` requires a base column type to process contents correctly, and Exposed attempts to resolve the best match internally based on the array content type.
  A specific column type argument should be provided to the function parameter `delegateType` if the content requires either an unsupported or custom column type, or
  a column type not defined in the `exposed-core` module.
* `exposed-crypt` module now uses Spring Security Crypto 6.+, which requires Java 17 as a minimum version.

## 0.47.0

* The function `SchemaUtils.checkExcessiveIndices` is used to check both excessive indices and excessive foreign key 
  constraints. It now has a different behavior and deals with excessive indices only. Also, its return type is now
  `List<Index>` instead of `Unit`. A new function, `SchemaUtils.checkExcessiveForeignKeyConstraints`, deals with excessive
  foreign key constraints and has a return type `List<ForeignKeyConstraint>`.

## 0.46.0

* When an Exposed table object is created with a keyword identifier (a table or column name) it now retains the exact case used before being automatically quoted in generated SQL.
  This primarily affects H2 and Oracle, both of which support folding identifiers to uppercase, and PostgresSQL, which folds identifiers to a lower case.

  If `preserveKeywordCasing = true` had been previously set in `DatabaseConfig` to remove logged warnings about any keyword identifiers, this can now be removed as the property is `true` by default.

  To temporarily opt out of this behavior and to not keep the defined casing of keyword identifiers, please set `preserveKeywordCasing = false` in `DatabaseConfig`:
```kotlin
object TestTable : Table("table") {
    val col = integer("select")
}

// default behavior (preserveKeywordCasing is by default set to true)
// H2 generates SQL -> CREATE TABLE IF NOT EXISTS "table" ("select" INT NOT NULL)

// with opt-out
Database.connect(
    url = "jdbc:h2:mem:test",
    driver = "org.h2.Driver",
    databaseConfig = DatabaseConfig {
        @OptIn(ExperimentalKeywordApi::class)
        preserveKeywordCasing = false
    }
)
// H2 generates SQL -> CREATE TABLE IF NOT EXISTS "TABLE" ("SELECT" INT NOT NULL)
```

<note>
The `preserveKeywordCasing` is an experimental flag and requires `@OptIn`. It may become deprecated in future releases.
</note>

## 0.44.0

* `SpringTransactionManager` no longer extends `DataSourceTransactionManager`; instead, it directly extends `AbstractPlatformTransactionManager` while retaining the previous basic functionality.
  The class also no longer implements the Exposed interface `TransactionManager`, as transaction operations are instead delegated to Spring.
  These changes ensure that Exposed's underlying transaction management no longer interferes with the expected behavior of Spring's transaction management, for example,
  when using nested transactions or with `@Transactional` elements like `propagation` or `isolation`.

  If integration still requires a `DataSourceTransactionManager`, please add two bean declarations to the configuration: one for `SpringTransactionManager` and one for `DataSourceTransactionManager`.
  Then define a composite transaction manager that combines these two managers.

  If `TransactionManager` functions were being invoked by a `SpringTransactionManager` instance, please replace these calls with the appropriate Spring annotation
  or, if necessary, by using the companion object of `TransactionManager` directly (for example, `TransactionManager.currentOrNull()`).
* `spring-transaction` and `exposed-spring-boot-starter` modules now use Spring Framework 6.0 and Spring Boot 3.0, which require Java 17 as a minimum version.
* A table that is created with a keyword identifier (a table or column name) now logs a warning that the identifier's case may be lost when it is automatically quoted in generated SQL.
  This primarily affects H2 and Oracle, both of which support folding identifiers to uppercase, and PostgreSQL, which folds identifiers to a lower case.

  To remove these warnings and to ensure that the keyword identifier sent to the database matches the exact case used in the Exposed table object, please set `preserveKeywordCasing = true` in `DatabaseConfig`:
```kotlin
object TestTable : Table("table") {
    val col = integer("select")
}

// without opt-in (default set to false)
// H2 generates SQL -> CREATE TABLE IF NOT EXISTS "TABLE" ("SELECT" INT NOT NULL)

// with opt-in
Database.connect(
    url = "jdbc:h2:mem:test",
    driver = "org.h2.Driver",
    databaseConfig = DatabaseConfig {
        @OptIn(ExperimentalKeywordApi::class)
        preserveKeywordCasing = true
    }
)
// H2 generates SQL -> CREATE TABLE IF NOT EXISTS "table" ("select" INT NOT NULL)
```

<note>
`preserveKeywordCasing` is an experimental flag and requires `@OptIn`. It may become deprecated in future releases, and its behavior when set to `true` may become the default.
</note>

## 0.43.0

* In all databases except MySQL, MariaDB, and SQL Server, the `ubyte()` column now maps to data type `SMALLINT` instead of `TINYINT`, which allows the full range of
  `UByte` values to be inserted without any overflow.
  Registering the column on a table also creates a check constraint that restricts inserted data to the range between 0 and `UByte.MAX_VALUE`.
  If a column that only uses 1 byte of storage is needed, but without allowing any non-negative values to be inserted, please use a signed `byte()` column
  instead with a manually created check constraint:
```kotlin
byte("number").check { it.between(0, Byte.MAX_VALUE) }
// OR
byte("number").check { (it greaterEq 0) and (it lessEq Byte.MAX_VALUE) }
```
* In all databases except MySQL and MariaDB, the `uint()` column now maps to data type `BIGINT` instead of `INT`, which allows the full range of `UInt` values to
  be inserted without any overflow.
  Registering the column on a table also creates a check constraint that restricts inserted data to the range between 0 and `UInt.MAX_VALUE`.
  If a column that only uses 4 bytes of storage is needed, but without allowing any non-negative values to be inserted, please use a signed `integer()` column
  instead with a manually created check constraint:
```kotlin
integer("number").check { it.between(0, Int.MAX_VALUE) }
// OR
integer("number").check { (it greaterEq 0) and (it lessEq Int.MAX_VALUE) }
```

## 0.42.0

* __SQLite__ The table column created using `date()` now uses TEXT datatype instead of DATE (which the database mapped internally to NUMERIC type).
  This applies to the specific `DateColumnType` in all 3 date/time modules and means `LocalDate` comparisons can now be done directly without conversions.
* __H2, PostgreSQL__ Using `replace()` now throws an exception as the REPLACE command is not supported by these databases.
  If `replace()` was being used to perform an insert or update operation, all usages should instead be switched to `upsert()`.
  [See documentation for UPSERT details](DSL-CRUD-operations.topic#insert-or-update)
* Operator classes `exists` and `notExists` have been renamed to `Exists` and `NotExists`.
  The functions `exists()` and `notExists()` have been introduced to return an instance of their respectively-named classes and to avoid unresolved reference issues.
  Any usages of these classes should be renamed to their capitalized forms.
* `customEnumeration()` now registers a `CustomEnumerationColumnType` to allow referencing by another column.
  The signature of `customEnumeration()` has not changed and table columns initialized using it are still of type `Column<DataClass>`.
* `Transaction.suspendedTransaction()` has been renamed to `Transaction.withSuspendTransaction()`.
  Please run `Edit -> Find -> Replace in files...` twice with `suspendedTransaction(` and `suspendedTransaction ` as the search options,
  to ensure that both variants are replaced without affecting `suspendedTransactionAsync()` (if used in code).
* The `repetitionAttempts` parameter in `transaction()` has been removed and replaced with a mutable property in the `Transaction` class.
  Please remove any arguments for this parameter and assign values to the property directly:
```kotlin
// before
transaction(Connection.TRANSACTION_READ_COMMITTED, repetitionAttempts = 10) {
    // statements
}

// after
transaction(Connection.TRANSACTION_READ_COMMITTED) {
    repetitionAttempts = 10
    // statements
}
```
* In all databases except MySQL and MariaDB, the `ushort()` column now maps to data type `INT` instead of `SMALLINT`, which allows the full range of `UShort`
  values to be inserted without any overflow.
  Registering the column on a table also creates a check constraint that restricts inserted data to the range between 0 and `UShort.MAX_VALUE`.
  If a column that only uses 2 bytes of storage is needed, but without allowing any non-negative values to be inserted, please use a signed `short()` column
  instead with a manually created check constraint:
```kotlin
short("number").check { it.between(0, Short.MAX_VALUE) }
// OR
short("number").check { (it greaterEq 0) and (it lessEq Short.MAX_VALUE) }
```

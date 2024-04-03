# Breaking Changes

## 0.49.0

For SQLite database, Exposed now requires bumping the SQLite JDBC driver version to a minimum of 3.45.0.0.

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

The function `SchemaUtils.checkExcessiveIndices` is used to check both excessive indices and excessive foreign key
constraints. It now has a different behavior and deals with excessive indices only. Also, its return type is now
`List<Index>` instead of `Unit`. A new function, `SchemaUtils.checkExcessiveForeignKeyConstraints`, deals with excessive
foreign key constraints and has a return type `List<ForeignKeyConstraint>`.

## 0.46.0
.
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

**Note: ** `preserveKeywordCasing` is an experimental flag and requires `@OptIn`. It may become deprecated in future releases.

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

**Note: ** `preserveKeywordCasing` is an experimental flag and requires `@OptIn`. It may become deprecated in future releases, and its behavior when set to `true` may become the default.

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
  [See documentation for UPSERT details](Deep-Dive-into-DSL.md#insert-or-update)
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
# Breaking Changes

## 0.44.0

* `SpringTransactionManager` no longer extends `DataSourceTransactionManager`; instead, it directly extends `AbstractPlatformTransactionManager` while retaining the previous basic functionality.
  The class also no longer implements the Exposed interface `TransactionManager`, as transaction operations are instead delegated to Spring.
  These changes ensure that Exposed's underlying transaction management no longer interferes with the expected behavior of Spring's transaction management, for example,
  when using nested transactions or with `@Transactional` elements like `propagation` or `isolation`.

  If integration still requires a `DataSourceTransactionManager`, please add two bean declarations to the configuration: one for  `SpringTransactionManager` and one for `DataSourceTransactionManager`.
  Then define a composite transaction manager that combines these two managers.

  If `TransactionManager` functions were being invoked by a `SpringTransactionManager` instance, please replace these calls with the appropriate Spring annotation
  or, if necessary, by using the companion object of `TransactionManager` directly (for example, `TransactionManager.currentOrNull()`).
* A table that is created with a keyword identifier (a table or column name) now logs a warning that the identifier's case may be lost when it is automatically quoted in generated SQL.
  This primarily affects H2 and Oracle, both of which support folding identifiers to uppercase, and PostgreSQL, which folds identifiers to lower case.

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

**Note:** `preserveKeywordCasing` is an experimental flag and requires `@OptIn`. It may become deprecated in future releases and its behavior when set to `true` may become the default.

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

* [SQLite] The table column created using `date()` now uses TEXT datatype instead of DATE (which the database mapped internally to NUMERIC type). 
This applies to the specific `DateColumnType` in all 3 date/time modules and means `LocalDate` comparisons can now be done directly without conversions.
* [H2, PostgreSQL] Using `replace()` now throws an exception as the REPLACE command is not supported by these databases. 
  If `replace()` was being used to perform an insert or update operation, all usages should instead be switched to `upsert()`. 
[See wiki for UPSERT details](https://github.com/JetBrains/Exposed/wiki/DSL#insert-or-update)
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

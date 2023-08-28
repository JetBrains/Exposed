# Breaking Changes

## 0.43.0

* In all databases except MySQL, MariaDB, and SQL Server, the `ubyte()` column now maps to data type `SMALLINT` instead of `TINYINT`, which allows the full range of 
`UByte` values to be inserted without any overflow.
Registering the column on a table also creates a check constraint that restricts inserted data to the range between 0 and `UByte.MAX_VALUE`.
If a column that only uses 1 byte of storage is needed, but without allowing any non-negative values to be inserted, please use a signed `byte()` column
instead with a manually created check constraint:
```kt
byte("number").check { it.between(0, Byte.MAX_VALUE) }
// OR
byte("number").check { (it greaterEq 0) and (it lessEq Byte.MAX_VALUE) }
```
* In all databases except MySQL and MariaDB, the `uint()` column now maps to data type `BIGINT` instead of `INT`, which allows the full range of `UInt` values to 
be inserted without any overflow.
Registering the column on a table also creates a check constraint that restricts inserted data to the range between 0 and `UInt.MAX_VALUE`.
If a column that only uses 4 bytes of storage is needed, but without allowing any non-negative values to be inserted, please use a signed `integer()` column
instead with a manually created check constraint:
```kt
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
```kt
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
```kt
short("number").check { it.between(0, Short.MAX_VALUE) }
// OR
short("number").check { (it greaterEq 0) and (it lessEq Short.MAX_VALUE) }
```

# Breaking Changes

## 0.42.0

* [SQLite] The table column created using `date()` now uses TEXT datatype instead of DATE (which the database mapped internally to NUMERIC type). 
This applies to the specific `DateColumnType` in all 3 date/time modules and means `LocalDate` comparisons can now be done directly without conversions.
* [H2, PostgreSQL] Using `replace()` now throws an exception as the REPLACE command is not supported by these databases. 
  If `replace()` was being used to perform an insert or update operation, all usages should instead be switched to `upsert()`. [See wiki for UPSERT details](https://github.com/JetBrains/Exposed/wiki/DSL#insert-or-update)
* Operator classes `exists` and `notExists` have been renamed to `Exists` and `NotExists`. 
The functions `exists()` and `notExists()` have been introduced to return an instance of their respectively-named classes and to avoid unresolved reference issues. 
Any usages of these classes should be renamed to their capitalized forms.
* `customEnumeration()` now registers a `CustomEnumerationColumnType` to allow referencing by another column. 
The signature of `customEnumeration()` has not changed and table columns initialized using it are still of type `Column<DataClass>`.

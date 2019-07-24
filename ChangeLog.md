# 0.16.3
Features:
* Added `rightJoin`, `fullJoin` and `crossJoin` functions. [Fedor Korotkov](https://github.com/fkorotkov) thanks for PR.  

Bug fixes:
* `Connection is closed` error when used suspended transactions ([#601](https://github.com/JetBrains/Exposed/issues/601))
* Nested transaction partially commits outer transaction since 0.16.1 ([#605](https://github.com/JetBrains/Exposed/issues/605))

# 0.16.2
Bug fixes:
* `Connection is closed` error when used suspended transactions ([#601](https://github.com/JetBrains/Exposed/issues/601))
* EntityHooks fails on commit from suspended transactions
* `TransactionManager.manager` is public again  ([#599](https://github.com/JetBrains/Exposed/issues/599))

# 0.16.1
Features:
* Spring Boot starter for Exposed ([#582](https://github.com/JetBrains/Exposed/issues/582)). Many kudos to [Trey Bastian](https://github.com/TreyBastian)!
* Nested transactions support ([#534](https://github.com/JetBrains/Exposed/issues/534)). Could be enabled via `Database.useNestedTransactions` property. Thanks for initial PR to [Antanas Arvasevicius](https://github.com/antanas-arvasevicius).
* `orWhere` function added ([#584](https://github.com/JetBrains/Exposed/issues/584))
* `neq` infix function on `Column<EntityID<*>>` added ([#590](https://github.com/JetBrains/Exposed/issues/590))

Bug Fixes:
* Build LIMIT only if size is strictly positive ([#586](https://github.com/JetBrains/Exposed/issues/586))
* `optReference` fails with type inference when referenced to non-nullable column ([#593](https://github.com/JetBrains/Exposed/issues/593))
* Impossible to use `references` on `Column<EntityID<*>>` ([#591](https://github.com/JetBrains/Exposed/issues/591))

# 0.15.1
Features:
* `suspendedTransaction` function introduced to work within `suspend` functions. See [wiki section](https://github.com/JetBrains/Exposed/wiki/Transactions#working-with-coroutines) for more details.
* Custom functions support ([#581](https://github.com/JetBrains/Exposed/issues/581)). See [wiki section](https://github.com/JetBrains/Exposed/wiki/Functions#custom-functions) for more details.

Bug Fixes:
* Duplicated columns in the parameter list of slice() caused unexpected value in ResultRow ([#581](https://github.com/JetBrains/Exposed/issues/581))

# 0.14.4
Bug Fixes:
* `concat` function doesn't work with Oracle ([#509](https://github.com/JetBrains/Exposed/issues/509))
* SQLServer metadata already closed issues ([#580](https://github.com/JetBrains/Exposed/issues/580))

# 0.14.3
Features:
* Now it's possible to create Entities from aliased tables/queries. Corresponded `wrapRow/wrapRows` functions were added to `EntityClass` ([#404](https://github.com/JetBrains/Exposed/issues/404))
* Support for `regexp` function (both case-sensitive/insensitive) was provided on every database which provided it
* `concat` function was introduced ([#509](https://github.com/JetBrains/Exposed/issues/509))

Performance optimizations:
* Unnecessary string escaping in `registerArguments` was removed when used prepared statements 

Infrastructure:
* Mysql 8.0 tests added
* Oracle tests were reanimated

# 0.14.2
Deprecations:
* `InsertStatement.generatedKey` field removed

Features:
* `like` operator for `Column<EntityID<String>>` ([#117](https://github.com/JetBrains/Exposed/issues/117))

Bug Fixes:
* SQLServer dialect fails on loading `extraNameCharacters` from DatabaseMetadata
* `insertIgnore` with get id on result fails for MySQL ([#574](https://github.com/JetBrains/Exposed/issues/574))
* Oracle dialect improvements

Infrastructure:
* Run MySQL tests on MySQL 5.7+ with jdbc driver `8.0.16` ([#171](https://github.com/JetBrains/Exposed/issues/171))
* Oracle tests reanimated on Oracle 18 XE

# 0.14.1
Bug Fixes:
* Fixed support for H2 1.4.198+ (CVE-2018-10054) ([#530](https://github.com/JetBrains/Exposed/issues/530), [#558](https://github.com/JetBrains/Exposed/issues/558))
* LazySizedCollection.limit() produce StackOverflowError exception ([#569](https://github.com/JetBrains/Exposed/issues/569))
* Explicitly specify constraint name in create table statement (PR [#570](https://github.com/JetBrains/Exposed/issues/570) from [vetrovosk](https://github.com/vetrovosk))

# 0.13.7
Kotlin updated to 1.3.31

Deprecation:
* `logger` field removed from Transaction ([#447](https://github.com/JetBrains/Exposed/issues/447))
* Deprecated functions removed: wrapRow, createIndex, groupConcat (with boolean order param), join
* enumeration/enumerationByName with Class<*> param removed

Features:
* ANSI keywords list actualized to SQl2003.2  (PR [#551](https://github.com/JetBrains/Exposed/issues/551) from [boonshift](https://github.com/boonshift))

Bug fixes:
* Only warn of schema/db mismatch if its the case. It isn't on left join. (PR [#541](https://github.com/JetBrains/Exposed/issues/541) from [spand](https://github.com/spand))
* Warn full column name in "Column ... is marked as not null" message
* Escape collate in Text and Varchar columns  (PR [#554](https://github.com/JetBrains/Exposed/issues/554) from [doyaaaaaken](https://github.com/doyaaaaaken))
* Aliased tables had use their original names in ON part

# 0.13.6
Feature:
* Attempt to fetch not only autoincrement values but all returned from insert/batchInsert
* New method (`TransactionManger.closeAndUnregister`) to close and unregister a database connection (PR [#529](https://github.com/JetBrains/Exposed/issues/529) from [Max Rumpf](https://github.com/Maxr1998)) 

Bug fixes:
* [MySQL] Constraints fails when table names are in lower case (PR [#539](https://github.com/JetBrains/Exposed/issues/539) from [valepakh](https://github.com/valepakh)) 
* [H2, SQLite] SchemaUtils.createMissingTablesAndColumns doesn't correctly check constraints ([#535](https://github.com/JetBrains/Exposed/issues/535)) 

# 0.13.5
Bug fixes: 
* Initialize client defaults only for target entity table columns (was broken in #0.13.4 with [#526](https://github.com/JetBrains/Exposed/issues/526)

# 0.13.4
Infrastructure:
* Finished moving to Gradle Kotlin DSL. Many thanks to Thanks for PR goes to Vladislav Tankov (@tanvd).  

Deprecation:
* `InsertStatement.generatedKey` is forbidden to use now in favour to `resultValues` and `insertAndGetId`

Features:
* InsertStatement.get function was split into get and getOrNull to simplify get use. 
Thanks for PR goes to Johannes Jensen (@spand) 

Bug fixes:
* clientDefault is called multiple times gives incorrect value ([#526](https://github.com/JetBrains/Exposed/issues/526))
* Fix composite PK bugs (Thanks Aidar Samerkhanov (@Darych) for PR): 
  incorrect evaluation of last column index in PK and
  missed column description DDL in ALTER TABLE for non H2 databases.
* forUpdate value was ignored in warmUp* functions when was applied to mapLazy/cached references
* Join on two tables with more than one foreign key constraint fails even with additionalConstraint ([#522](https://github.com/JetBrains/Exposed/issues/522))  

# 0.13.3
Bug fixes:
* Unable to create index on column that also has a foreign key with `createMissingTablesAndColumns`, fix for MySQL ([#498](https://github.com/JetBrains/Exposed/issues/498))
* Use memberProperties instead of declaredMemberProperties in with/preloadRelations. 

# 0.13.2
Bug fixes: 
* Wrong values returned from warmUp* functions which leads to N + 1

# 0.13.1
Features:
* Eager Loading ([#420](https://github.com/JetBrains/Exposed/issues/420)). More information and how to use it could found at [wiki](https://github.com/JetBrains/Exposed/wiki/DAO#eager-loading).

Bug fixes:
* Unable to create index on column that also has a foreign key with `createMissingTablesAndColumns` ([#498](https://github.com/JetBrains/Exposed/issues/498))
* Using createMissingTablesAndColumns() generates a spurious WARN message in log files ([#480](https://github.com/JetBrains/Exposed/issues/480))
* Unexpected value of type Int: 26 of org.jetbrains.exposed.dao.EntityID ([#501](https://github.com/JetBrains/Exposed/issues/501))
* More proper handling of forUpdate/notForUpdate/orderBy on LazySizedCollection
* Problems on comparing binary types (bit from MySQL) ([#491](https://github.com/JetBrains/Exposed/issues/491))


# 0.12.2
Kotlin updated to 1.3.21

Features:
* DAO: It's possible to call `orderBy` on `SizedIterable` to sort entities ([#476](https://github.com/JetBrains/Exposed/issues/476))
* DSL/DAO: Self join many-to-many relationships ([#106](https://github.com/JetBrains/Exposed/issues/106)). See [wiki page](https://github.com/JetBrains/Exposed/wiki/DAO#parent-child-reference) for example.
* DSL: Allow to apply groupConcat on expressions ([#486](https://github.com/JetBrains/Exposed/issues/486)). Thanks for PR goes to Edvinas Danevičius (@Edvinas01). 
* Log every SQLException thrown inside inTopLevelTransaction with warn instead of info level
* SQLServer dialect will now use `uniqueidentifier` for UUID columns
* Compound operations introduced: `compoundAnd`/`compoundOr`([#469](https://github.com/JetBrains/Exposed/issues/469))
* `Op.TRUE`/`Op.FALSE` expressions added
* OR operator was optimized to use less braces
* Oracle: Speedup extracting tableColumns metadata    

Bug fixes: 
* Impossible to set default value with nullable column ([#474](https://github.com/JetBrains/Exposed/issues/474))
* UUID value not read correctly ([#473](https://github.com/JetBrains/Exposed/issues/473))
* `No key generated` exception thrown when trying to insert row to IdTable using `insertAndGetId` with explicit id and then make search by returned id ([#432](https://github.com/JetBrains/Exposed/issues/432))      

Deprecations:
* orderBy with boolean sort parameters was deprecated
 

# 0.12.1
Features:
* MariaDB support
* Suspending `transaction` functions ([#418](https://github.com/JetBrains/Exposed/issues/418))
* DAO: It's possible to specify forUpdate state in warmup* functions

Bug fixes:
* Fixed condition when IllegalStateException should be thrown on LazySizedCollection forUpdate/notForUpdate
* `inTopLevelTransaction` accepts `Database` instead of `TransactionManager` ([#448](https://github.com/JetBrains/Exposed/issues/448))
* LIMIT is not supported in DELETE SQLite message ([#455](https://github.com/JetBrains/Exposed/issues/455))
* Limit propagate is same entity ([#439](https://github.com/JetBrains/Exposed/issues/439))
* forIds/forEntityIds wont return partially cached values
* Accessing enum field in `Entity` created using `EntityClass.new` causes `ClassCastException` ([#464](https://github.com/JetBrains/Exposed/issues/464))
* `NoClassDefFoundError` when creating table on Android ([#461](https://github.com/JetBrains/Exposed/issues/461))
* `exec(String)` throws `SQLException: ResultSet already requested` ([#414](https://github.com/JetBrains/Exposed/issues/414))
* SQLite autoincrement table is not created correctly ([#328](https://github.com/JetBrains/Exposed/issues/328))  

Deprecations:
* Deprecate InsertStatement.generatedKey ([#424](https://github.com/JetBrains/Exposed/issues/424))
* Using of`Transactions.logger` is prohibited  
* `SchemaUtils.createIndex` with columns and isUnique parameters is prohibited
* `groupConcat` with boolean sortOrder is replaced with similar `SortOrder` version
* `join` is replaced with `innerJoin`
* enumeration/enumerationByName with java `Class` replaced with kotlin `KClass`
 

# 0.11.2
* Kotlin #1.3.0
* Fixed bug that call for createMissingTablesAndColumns could lead to exception while trying to add an index which already exists in db (*MySQL only problem*)

# 0.11.1
Features:
* Support entity references by non-ID column ([#282](https://github.com/JetBrains/Exposed/issues/282))
* groupConcat function reworked and supported on all databases (except SQLite)
* Now it's possible to create cyclic depended tables in single SchemaUtils.createTable() call. 
Relates to Defer Foreign references during creation ([#384](https://github.com/JetBrains/Exposed/issues/384))
* Introduced SchemaUtils.checkCycle() function to check tables for cyclic references
* MOD (%) operator support ([#391](https://github.com/JetBrains/Exposed/issues/391))
* Set RepetitionAttempts in TransactionManager (to set globally) ([#393](https://github.com/JetBrains/Exposed/issues/393))
* Fail with KNPE on not null columns with null
* Throw exception when trying to change onUpdate state for loaded LazySizedCollection
* Database.dialect field is now public

Bug fixes:
* BatchUpdateStatement results in IllegalStateException: Table.columnName is already initialized ([#386](https://github.com/JetBrains/Exposed/issues/386))
* BatchUpdateStatement expects set(..) calls in alphabetical order and fails when given more than 2 batches ([#388](https://github.com/JetBrains/Exposed/issues/388))
* autoIncrement carries to referencing column ([#385](https://github.com/JetBrains/Exposed/issues/385))
* Query fails when same column/expression provided in slice multiple times
* columnConstraints() returns wrong map with keys in wrong order (target instead of referee)
* Try to expand Query in ExposedSQLException even if it wasn't able to use current transaction.
* Autoinc column does not necessarily need to be the first column in the table ([#398](https://github.com/JetBrains/Exposed/issues/398))
* "No transaction in context" when using SpringTransactionManager ([#365](https://github.com/JetBrains/Exposed/issues/365), [#407](https://github.com/JetBrains/Exposed/issues/407))
* uuid values are not properly escaped in subquery ([#415](https://github.com/JetBrains/Exposed/issues/415))
* Foreign Key Constraints being recreated  ([#394](https://github.com/JetBrains/Exposed/issues/394))


# 0.10.5
Features:
* Added `DOUBLE` column type ([#358](https://github.com/JetBrains/Exposed/issues/358)). Thanks go to Jonathan Shore (@tr8dr).
* `Entity.refresh()` introduced to update its state from database (see kdoc)
* All statements classes become open to allowing inheritance ([#350](https://github.com/JetBrains/Exposed/issues/350))
* `where` part made optional in `UPDATE` statement ([#115](https://github.com/JetBrains/Exposed/issues/115))  
* `andWhere` function introduced to help in constructing complex queries ([#375](https://github.com/JetBrains/Exposed/issues/374)). 
See ["Conditional where"](https://github.com/JetBrains/Exposed/wiki/DSL#conditional-where) section on Wiki.    

Bug fixes:
* Varchar column doesn't validate length / Validate length only before execute update ([#300](https://github.com/JetBrains/Exposed/issues/300))
* sqlState doesn't available on `ExposedSQLException` ([#331](https://github.com/JetBrains/Exposed/issues/331))
* [SQLite] `ClassCastException` when reading from VARCHAR column ([#369](https://github.com/JetBrains/Exposed/issues/369))
* Use ::class instead of ::class.java for enumerations ([#370](https://github.com/JetBrains/Exposed/issues/370))
Previous functions `enumeration`/`enumerationByName` were deprecated with quick fix to new ones. Both functions will be removed on next releases.
* Multiple join produce "DSL SYNTAX ERROR" ([#366](https://github.com/JetBrains/Exposed/issues/366))  
* Entities flush to a database after a `DROP` table ([#112](https://github.com/JetBrains/Exposed/issues/112))   
* Entity id is not generated properly when using `clientDefault` on related column ([#313](https://github.com/JetBrains/Exposed/issues/313))
* `ClassCastExpection` when using alias on a expressions ([#379](https://github.com/JetBrains/Exposed/issues/379))
* [MySQL] Entity ids were not generated properly with `rewriteBatchedStatements` flag is on.
* [Oracle] `LONG` type replaced with `CLOB` for text columns ([#382](https://github.com/JetBrains/Exposed/issues/382))
   

# 0.10.4
Features:
* ON UPDATE reference constraint added
* All dialects made public and open for extensibility
* `Transaction.addLogger()` function introduced as replacement to `logger.addLogger()` approach
* Strings will be validated against column max length before inserting to database ([#300](https://github.com/JetBrains/Exposed/issues/300))
* Overriding the fetch size for a Query ([#327](https://github.com/JetBrains/Exposed/pull/327)) 

Bug fixes:
* Extended sql exception logging available only in debug mode
* [PostgreSQL] Table with `customEnumeration` column doesn't work with Entity/DAO API ([#340](https://github.com/JetBrains/Exposed/issues/340))
* Table with composite key created only with specific prop order ([#343](https://github.com/JetBrains/Exposed/issues/343))
* allTableNames in VendorDialect returns only from current database ([#339](https://github.com/JetBrains/Exposed/issues/339))
* "ORA-00972: identifier is too long" on creating table with long name

# 0.10.3
Features:
* Floating point columns
* Check constraint functionallity (kudos to @SackCastellon)
* Possibility to provide custom names for contraints and indexes (thanks to @mduesterhoeft)
* Added support to delete with limit and offset (thanks  @Mon_chi for initial PR)
* Full SQL will be now logged on any SQLException ([#288](https://github.com/JetBrains/Exposed/issues/288) [Suggestion] Log the SQL query when an exception is thrown)
* Postgres support for ignore + replace 
* H2 support for INSERT IGNORE for new H2 version
* Statement interceptors now allow triggering on before commit and rollback
* Database ENUM types supported (read more [here](https://github.com/JetBrains/Exposed/wiki/DataTypes#how-to-use-database-enum-types))

Bug fixes:
*  [#279](https://github.com/JetBrains/Exposed/issues/279) 'SELECT MAX(datetime)' throws ClassCastException 
*  [#289](https://github.com/JetBrains/Exposed/issues/289) UUID's are not shown in logs 
*  [#284](https://github.com/JetBrains/Exposed/issues/284) Postgres, DSL Approach: primary key with custom names beside `...

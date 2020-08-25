# 0.27.1
Feature:
* Nullable CompositeColumn support (with CompositeMoneyColumn as a reference implementation)
* `adjustSlice` now provides current FieldSet as a function parameter
* New `decimalParam` and `decimalLiteral` introduced
* DivideOp with BigDecimals could adjust scale with `withScale` function
* Sequences updated to support Long values with `nextIntVal` and `nextLongVal` functions as replacement of `nextIntVal`
* [SQLite] DateTime column representation changed from `NUMERIC` (with seconds precision) to `TEXT` (with millis)
* Allow setting unwrapped value for EntityID columns with UpdateBuilder (insert/update/etc)
  
Bug fixes:
 
* `wrapAsExpression` somehow makes program fail with "debug" log level ([#1006](https://github.com/JetBrains/Exposed/issues/1006))
* LocalDateTime miss the nanos part ([#1008](https://github.com/JetBrains/Exposed/issues/1008))
* IdTable.new cannot use SEQUENCE nextVal ([#1002](https://github.com/JetBrains/Exposed/issues/1002))
* [SQLite] `WHERE` clause with DateTime expressions could return wrong result. PR by [hfazai](https://github.com/hfazai).
* [SQLServer] Don't use `OUTPUT` clause in inserts when `shouldReturnGeneratedValues=false`
* [SQLServer ] Unnecessary limit for OUTPUT command when using batch insert ([#440](https://github.com/JetBrains/Exposed/issues/440))

# 0.26.2
Infrastructure:
* MySQL related tests were moved from old embedded-mxj to test-containers ([#957](https://github.com/JetBrains/Exposed/issues/957)). Kudos to [KushalP](https://github.com/KushalP)   

Bug Fixes:
* Fix for java9+ returns empty string for Class.simpleName of anonymous classes, what cause an empty table name
* Enable use of Exposed with Quarkus / GraalVM Native Image. Compile time dependency to H2 was removed ([#919](https://github.com/JetBrains/Exposed/issues/919)).
* Between function doesn't work when run against Oracle database ([#974](https://github.com/JetBrains/Exposed/issues/974)).
* [MySQL] Infinite recursion in `match` operator ([#978](https://github.com/JetBrains/Exposed/issues/978)).
* Using `exposed-java-time` module on Android could end with NoSuchMethodError ([#991](https://github.com/JetBrains/Exposed/issues/991)), ([#998](https://github.com/JetBrains/Exposed/issues/998)).
* Binary type doesn't honor max length. Client-side length validation for BinaryColumnType ([#993](https://github.com/JetBrains/Exposed/issues/993)). 
* CharColumnType equals doesn't check colLength parameter
* Creating similar tables/indices on different schemas could fail with an error ([#803](https://github.com/JetBrains/Exposed/issues/803)).   
* [SQLite] Allow providing `limit` for UPDATE/DELETE statements if SQLite was compiled with `optional_limit_and_order_by_clauses` option ([#979](https://github.com/JetBrains/Exposed/issues/979)). [harry huang](https://github.com/banjiaojuhao), thank you for a PR.  

# 0.26.1
Infrastructure:
* Gradle 6.5. Thanks to [KushalP](https://github.com/KushalP)

Features:
* New composite column type to create complex mappings to multiple columns. 
* Money column type and a new `exposed-money` module. This and above were implemented by [encircled](https://github.com/encircled)   
* Duration column type (`exposed-java-time` module only) provided by [CommanderTvis](https://github.com/CommanderTvis)
* `Transaction.exec` functions accept list of parameters. PR by [uuf6429](https://github.com/uuf6429).   
* [H2] Native `REPLACE` instead of `INSERT... ON DUPLICATE UPDATE` by [pilotclass](https://github.com/pilotclass). 
* Other unsigned types: ubyte, ushort, uint added by [kenta.koyama](https://github.com/doyaaaaaken).
* Index type support added by [ryanpbrewster](https://github.com/ryanpbrewster).

Bug Fixes:
* Upgrading from 0.17.7 to 0.25.1 leading to overload resolution ambiguity, created `Database.connectPool` ([#938](https://github.com/JetBrains/Exposed/issues/938)
* Impossible to refresh entity created via new(id: ID?, ...) ([#925](https://github.com/JetBrains/Exposed/issues/925)), PR by [socar-bad](https://github.com/socar-brad) 
* Concat function should accept any expression not only strings. PR by [palevomr](https://github.com/palevomr).
* Irregular order of operands in generated CHECK constraint ([#920](https://github.com/JetBrains/Exposed/issues/920)) 
* [SQLite] allow NOT NULL modifier for PK column. Fixed by [hfazai](https://github.com/hfazai). 
* `SchemaUtils.createMissingTablesAndColumns` fails with references ([#866](https://github.com/JetBrains/Exposed/issues/866), [#934](https://github.com/JetBrains/Exposed/issues/934), [#952](https://github.com/JetBrains/Exposed/issues/952)). Fixed by [jschmid](https://github.com/jschmid).


# 0.25.1
Features:
* UNSIGNED LONG column support (PR by [kenta.koyama](https://github.com/doyaaaaaken))
* `shouldReturnGeneratedValues` param added to batchInsert function and statement. Useful when `reWriteBatchedInserts` enabled.
* Eager loading for text columns / H2: column of 'text' type requires open transaction even if it was already read successfully ([#848](https://github.com/JetBrains/Exposed/issues/848))
  
  
Bug Fixes:
* Problem with suspended transaction functions and spring transaction manager
* Using blocking transactions after a suspending transaction causes a "Connection is closed" exception
* SQLite driver name does not recognize correctly ([#905](https://github.com/JetBrains/Exposed/issues/905))
* limit(0) doesn't work ([#845](https://github.com/JetBrains/Exposed/issues/845)) 
* Ignore missing fields instead of crashing
* DatabaseDialect.modifyColumn doesn't work for H2 ([#911](https://github.com/JetBrains/Exposed/issues/911)) 
* ClassCastException when using selectBatched with integer autoinc table (PR by [DarkXanteR](https://github.com/DarkXanteR))   
* ResultSet & Statement leaks in simple query ([#871](https://github.com/JetBrains/Exposed/issues/871))  / Possible fix for not closed inputStreams. 

# 0.24.1
Infrastructure:
* Kotlin 1.3.72
* Kotlin Coroutines 1.3.5
* MariaDB driver 2.6.0
* MySQL drivers 5.1.49 and 8.0.20
* Oracle driver replaced from Atlasian fork (12.1.0.1) to original 12.2.0.1
* PostgreSQL driver 42.2.12.jre6
* PostgreSQL NG driver 0.8.4
* SQLServer driver 8.2.2.jre8
* SQLite driver 3.31.1 

Features:
* A lot of improvements on working with `Schema` (checking for existence, additional parameters like password, defaultTablespace, etc). Thanks a lot to [hfazai](https://github.com/hfazai). 
* `CHAR(N)` column type support ([#858](https://github.com/JetBrains/Exposed/issues/858)).
* `BYTE` column type support (PR [#876](https://github.com/JetBrains/Exposed/issues/876) from [spand](https://github.com/spand)).
* `timestampParam`, `timestampLiteral` and `CustomTimeStampFunction` added by [spand](https://github.com/spand) (PR [#876](https://github.com/JetBrains/Exposed/issues/876)).
* Support for `javax.sql.ConnectionPoolDataSource` in `Database.connect` ([#902](https://github.com/JetBrains/Exposed/issues/902))
* Allow to provide transactionIsolation level on newSuspendedTransaction/suspendedTransactionAsync

Bug fixes:
* No need to provide explicit transaction isolation level for SQLite and Oracle
* [PostgreSQL] Exception when using jsonb question mark operator ('?') ([#890](https://github.com/JetBrains/Exposed/issues/890) fixed by [qoomon](https://github.com/qoomon)) 
* [Spring Boot] `NoUniqueBeanDefinitionException` thrown ([#869](https://github.com/JetBrains/Exposed/issues/869))  
* Do not request generated key if no autoinc/sequence columns provided in an insert statement / reWriteBatchedInserts does not work with PostgreSQL ([#881](https://github.com/JetBrains/Exposed/issues/881))
* `castTo` should return the expression of exact type ([#874](https://github.com/JetBrains/Exposed/issues/874))
* DateTime problem with SQLite (using exposed.java-time) / Different amount of millis stored and restored  ([#823](https://github.com/JetBrains/Exposed/issues/823))    

Code specific changes:
* `SqlExpressionBuilderClass` deprecated in favor to ISqlExpressionBuilder (PR [#859](https://github.com/JetBrains/Exposed/issues/859) from [Ali Lozano](https://github.com/AliLozano)).
* `equals` and `hashCode` on `ColumnType` (PR [#860](https://github.com/JetBrains/Exposed/issues/860) from [Ali Lozano](https://github.com/AliLozano)).
 

# 0.23.1
Features:
* Schema support: create/set current/drop. Many thanks to [hfazai](https://github.com/hfazai).

Bug fixes:
* Spring transaction doesn't close/commit transaction at the end of `transaction` block ([#831](https://github.com/JetBrains/Exposed/issues/831)).
* SpringTransactionManager has connection leak when used with `transaction` instead of @Transaction annotation  ([#831](https://github.com/JetBrains/Exposed/issues/831)).
* `id not in record set` when getting by id which alias to another column ([#820](https://github.com/JetBrains/Exposed/issues/820)). 
* Fixed `tableConstraints` retrieval fails for column names that need quoting fixed by [t-kameyama](https://github.com/t-kameyama), thank you ([#839](https://github.com/JetBrains/Exposed/issues/839)).
* Fixed attempt to create the same foreign key constraint even if one already exists ([#843](https://github.com/JetBrains/Exposed/issues/843)).  

# 0.22.1
Documentation on SQL functions was added by [Juan José González Abril](https://github.com/SackCastellon)

Broken Changes:
* Return type of `SizedIterable.count()` (and `Query.count()` as an inheritor) was changed from Int to Long to support very large tables. 

Also, `offset` parameter of `SizedIterable.limit()` and `DeleteStatement` functions were changed accordingly. `limit` parameter stays untouched to be in sync with Kotlin `Collection.size`

Features:
* New pgjdbc-ng driver for PostgreSQL supported with help of [hfazai](https://github.com/hfazai)
* Updates with joins supported ([#671](https://github.com/JetBrains/Exposed/issues/671)), ([#636](https://github.com/JetBrains/Exposed/issues/636)), ([#671](https://github.com/JetBrains/Exposed/issues/671)).
* Custom names for foreign keys supported by [t-kameyama](https://github.com/t-kameyama) ([#510](https://github.com/JetBrains/Exposed/issues/510)). 
* Support for `notInSubQuery` expression ([#791](https://github.com/JetBrains/Exposed/issues/791)), thanks for PR, [dolgopolovwork](https://github.com/dolgopolovwork).
* `Database.connect()` could be used without providing an explicit driver. It will be resolved from a connection url. Improvement provided by [Ali Lozano](https://github.com/AliLozano).
* Most of SQL operators can be called on Expressions instead of ExpressionWithColumnType #829

Bug fixes:
* Respect schema in JdbcDatabaseMetadataImpl.tableNames ([#797](https://github.com/JetBrains/Exposed/issues/797)).
* JavaLocalDateTimeColumnType not setting nanoseconds correctly on Timestamp ([#793](https://github.com/JetBrains/Exposed/issues/793)).
* Correct precedence for arithmetic operators ([#788](https://github.com/JetBrains/Exposed/issues/788)) fixed by [toefel18](https://github.com/toefel18).
* Default value was not generated in DDL for columns within a primary key ([#810](https://github.com/JetBrains/Exposed/issues/810)).
* `QueryAlias.get(original: Expression<T>)` lose query's alias ([#633](https://github.com/JetBrains/Exposed/issues/633)).
* `Query.copy()` doesn't clone internal lists was fixed by [Ali Lozano](https://github.com/AliLozano).
* Unable to create entity with overridden equals method due to field initialization error ([#806](https://github.com/JetBrains/Exposed/issues/806)).

# 0.21.1
Public methods and classes of database dialects, tables and columns were covered with documentation by [Juan José González Abril](https://github.com/SackCastellon). Thanks a lot!

Features:
* Sequences support improved: added all parameters like startWith, incrementBy and other, nextVal expression. Thank you [hfazai](https://github.com/hfazai) for such great PR.
* `CustomOperator` introduced to be used when you need to create an SQL operator. PR from [gelineau](https://github.com/gelineau).
* `SchemaUtils.createDatabase` and `SchemaUtils.dropDatabase` added by [hfazai](https://github.com/hfazai)
* GUID values could be stored in UUID columns ([#767](https://github.com/JetBrains/Exposed/issues/767)). Fixed by [hfazai](https://github.com/hfazai).

Bug fixes:
* spring-configuration-metadata.json is not located inside exposed-spring-boot-starter.jar ([#767](https://github.com/JetBrains/Exposed/issues/767))
* It wasn't possible to use `eq` operator on nullable entity id column and base column value ([#748](https://github.com/JetBrains/Exposed/issues/748))
* `java.lang.IllegalStateException` was thrown when one table references to other by id column ([#501](https://github.com/JetBrains/Exposed/issues/501))

# 0.20.3
Features:
* `size` property added for `ColumnMetadata` ([#748](https://github.com/JetBrains/Exposed/issues/748)). Thank you, [kenta.koyama](https://github.com/doyaaaaaken).

Bug fixes: 
* Can't load implementation for DatabaseConnectionAutoRegistration ([#748](https://github.com/JetBrains/Exposed/issues/748)).
* Proper length check when using Unicode strings ([#743](https://github.com/JetBrains/Exposed/issues/743)). PR from [pt2121](https://github.com/pt2121), thank you.
* Custom enumeration not working with default value ([#750](https://github.com/JetBrains/Exposed/issues/750))
* [SQLite] fixing a bug that happens when creating table with autoInc column and custom primarykey constraint name ([#755](https://github.com/JetBrains/Exposed/issues/755)). Fixed by [hfazai](https://github.com/hfazai).
* Flushing a new entity fails with an exception ([#761](https://github.com/JetBrains/Exposed/issues/761))
* Update event wasn't fired on Entity.flush() ([#764](https://github.com/JetBrains/Exposed/issues/764))

# 0.20.2
Bug fixes:
* Predefined `IntIdTable`/`LongIdTable`/`UUIDTable` should respect deprecated primaryKey declaration.

# 0.20.1
Features:
* New way to define primary keys on tables were implemented by [hfazai](https://github.com/hfazai) to support custom primary key constraint keys. 
If have to use `override val primaryKey: PrimaryKey?` on your table if you want to define a custom name. 
For all users of predefined `IntIdTable`/`LongIdTable`/`UUIDTable` nothing changed. 
Old `Column.primaryKey` function was depricated and will be removed in the future releases.
* `java.time.Instant` supported with `timestamp` column type (`exposed-java-time` module only) ([#724](https://github.com/JetBrains/Exposed/issues/724)). Many thanks to [Lukáš Křečan](https://github.com/lukas-krecan).
* Support for unsized binary columns (Oracle and PostgreSQL dialects) ([#716](https://github.com/JetBrains/Exposed/issues/716)). Another great PR from [hfazai](https://github.com/hfazai).
* A unique identifier for `Transaction` instance introduced and supported in EntityHooks/EntityEvents by [mpe85](https://github.com/mpe85).

Bug fixes:
* Annoying `NoSuchElementException` from 0.19.3 fixed by [Toshiaki Kameyama](https://github.com/t-kameyama) ([#731](https://github.com/JetBrains/Exposed/issues/731))
* Prevent defining duplicated column name in a table. Now you will get `DuplicateColumnException` at runtime ([#709](https://github.com/JetBrains/Exposed/issues/709)). Nice work [hfazai](https://github.com/hfazai)!
* `batchInsert` will throw `BatchDataInconsistentException` instead of `NoSuchElementException` when it wasn't possible to make insertion ([#741](https://github.com/JetBrains/Exposed/issues/741)).

# 0.19.3
Deprecations:
* `Query.orderBy()` functions with boolean sort parameter were removed

Bug fixes:
* NoSuchElementException: List is empty on updating not flushed entities ([#708](https://github.com/JetBrains/Exposed/issues/708))
* Replace function fails on H2 in MySQL mode when using pool 
* Delayed DAO update flush causes unique constraint violation ([#717](https://github.com/JetBrains/Exposed/issues/717))
* `Query.empty()` shouldn't add LIMIT 1 if query is marked as forUpdate() ([#684](https://github.com/JetBrains/Exposed/issues/684))


# 0.19.1
Broken changes:
* `EntityID`, `IdTable`, `IntIdTable`, `LongIdTable`, `UUIDTable` classes from `exposed-core` 
were moved from `org.jetbrains.exposed.dao` to `org.jetbrains.exposed.dao.id` package along with `exposed-jodatime` module classes to support Java 9 module system.
To help with migration, old classes were deprecated with proper `replaceWith` option. Read [migration guide](https://github.com/JetBrains/Exposed/wiki/Migration-Guide#migrating-to-019) for more details.

Features:
* `selectBatched` and `selectAllBatched` functions added to make queries in batches ([#642](https://github.com/JetBrains/Exposed/issues/642)). Many thanks to [Pin-Sho Feng](https://github.com/red-avtovo) for a PR.
* Added UpdateBuilder.update expression builder version ([#700](https://github.com/JetBrains/Exposed/issues/700)). Another useful PR from [spand](https://github.com/spand)
* New SQL datetime functions year, day, hour, minute, second ([#707](https://github.com/JetBrains/Exposed/issues/707)). Helpful PR from [hfazai](https://github.com/hfazai)

Bug fixes:
* SpringTransactionManager doesn't properly rollback transactions in some cases ([#666](https://github.com/JetBrains/Exposed/issues/666))
* Compilation error Field name 'Oracle12+' cannot be represented in dex format. ([#668](https://github.com/JetBrains/Exposed/issues/668))
* Enable comparing(less/lessEq/greater/greaterEq) to nullable column ([#694](https://github.com/JetBrains/Exposed/issues/694)). Thank you [Toshiaki Kameyama](https://github.com/t-kameyama).
* Fixed insert statements with expression as a value.
* Do not load related entities multiple times when using eager loading with the same entities. Small but useful fix from [kenta.koyama](https://github.com/doyaaaaaken)
* Failed on create a FK for a table created in another database schema ([#701](https://github.com/JetBrains/Exposed/issues/701))

# 0.18.1
Major changes:
* New modules model. For more details please read dependencies section and migration guide in wiki.
* Java 8 Time API support

# 0.17.7
Deprecations:
* Remove deprecated Expression.toSQL 
* `newSuspendedTransactionAsync` now returns `Deferred` instead of `TransactionResult` (which was removed completely)
* `TransactionResult.andThen` function was removed 

Features:
* Added `autoGenerate` method for uuid columns to generate UUID on client side ([#664](https://github.com/JetBrains/Exposed/issues/664)). PR from [Aleks](https://github.com/red-avtovo).

Bug fixes:
* [Coroutines] newSuspendedTransaction captures outer transaction and then close it before return to outer block
* [Coroutines] IllegalArgumentException thrown when List<Deferred<T>>.awaitAll() is used on List<TransactionResult<T>> ([#658](https://github.com/JetBrains/Exposed/issues/658))
* identifierLengthLimit should not be lazy initialized
* Immutable entities wasn't invalidated in some cases ([#665](https://github.com/JetBrains/Exposed/issues/665)). PR from [Dmitry Dolzhenko](https://github.com/dsdolzhenko).
* Rework resolving auto increment type to handle own ColumnType implementation ([#663](https://github.com/JetBrains/Exposed/issues/663)). PR from [Mateusz Śledź](https://github.com/sledzmateusz).
* Fixing the `month` built-in function in case of all database dialects ([#670](https://github.com/JetBrains/Exposed/issues/670)). PR from [hfazai](https://github.com/hfazai)
* Compilation error Field name 'Oracle12+' cannot be represented in dex format. ([#668](https://github.com/JetBrains/Exposed/issues/668))
* [SQLite] Problem with `autoIncrement()` and `primaryKey()`. ([#649](https://github.com/JetBrains/Exposed/issues/649)), ([#669](https://github.com/JetBrains/Exposed/issues/669))
* fixes in error logging of expressions.

# 0.17.6
Critical bug fix:
* Outer transaction wasn't restored after inner transaction failed on exception

Bug fixes:
* ORA-00972: identifier is too long. Oracle 11g ([#654](https://github.com/JetBrains/Exposed/issues/654))

Features:
* shortParam/shortLiteral, floatParam/floatLiteral, doubleParam/doubleLiteral added ([#648](https://github.com/JetBrains/Exposed/issues/648))
* Warmup for backReferences via `load` and `with` functions was supported

# 0.17.5
Critical bug fix:
* Transaction isolation level wasn't applied to transaction

# 0.17.4
Critical bug fix:
* `and` function executed on `And` expression mutates expression state

Features:
* `SchemaUtils` functions (`create/createMissingTablesAndColumns/drop`) could be executed with `inBatch` flag to perform related functions in a single batch (where possible)

Performance:
* Speed up scheme analysis within for MySQL in place where column constraints metadata is required

# 0.17.3
Infrastructure:
* Kotlin updated to 1.3.50

Features:
* Added `short` column type support backed with `SMALLINT` SQL type
* Added `greater/greaterEq/less/lessEq` functions with EntityID parameter ([#619](https://github.com/JetBrains/Exposed/issues/619))

Bug fixes:
* CURRENT_TIMESTAMP/NOW used as DEFAULT were broken for MySQL in some cases ([#631](https://github.com/JetBrains/Exposed/issues/631). Thanks [Saeed Shahrivari](https://github.com/shahrivari) for initial PR.   

# 0.17.2
Bug fixes:
* MySQL match function is broken
* notInList function doesn't work with list of EntityID. Thank you [kenta.koyama](https://github.com/doyaaaaaken) for a PR.

# 0.17.1
Performance:
* QueryBuilder and Expression were reworked to lower object allocation when preparing SQL.

**Important**: 

This version has a broken change: `Expression.toSQL` was replaced with `Expression.toQueryBuilder`.
Difference is what `toQueryBuilder` doesn't return `String` representation of an `Expression` but only register it in a `QueryBuilder`.
If you need to get raw SQL value please use `Expression.toString`.

# 0.16.4
Features:
* Added where in subquery ([#610](https://github.com/JetBrains/Exposed/issues/610)). Kudos to [Manabu OHTAKE](https://github.com/otkmnb2783) for PR. 

Bug fixes:
* ClassCastException on an attempt to flush IdTable with reference to Table
* Fixed needQuotes check of already quoted identifiers ([#614](https://github.com/JetBrains/Exposed/issues/614)). Thanks [mpe85](https://github.com/mpe85)
* Suspend transactions broken when working with multiple databases ([#624](https://github.com/JetBrains/Exposed/issues/624))
* Unnecessary select when preloading absent references ([#615](https://github.com/JetBrains/Exposed/issues/615))
* Updates of entities were executed in random order what could lead to unexpected behavior 

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

# 0.41.1
Infrastructure:
* Kotlin 1.7.21 
* spring-security-crypto 5.7.3
* Few improvements in text infrastructure with help of [valery1707](https://github.com/valery1707)

Deprecations:
* `StatementInterceprot.afterCommit` and `StatementInterceptor.afterRollback` without `transaction` parameter have ERROR deprecation level
* `Column<*>.autoIncSeqName` has HIDDEN deprecation level
* `Database.connect` with `datasource: ConnectionPoolDataSource` parameter has HIDDEN deprecation level
* `SchemaUtils.createFKey` with `reference: Column<*>` parameter has ERROR deprecation level
* `Sequence.nextVal` has ERROR deprecation level
* `SqlExpressionBuilderClass` has ERROR deprecation level
* `CurrentDateTime()`  has ERROR deprecation level 

Feature:
* [PostgreSQL] `NOWAIT/SKIP LOCKED` modes supported in all kinds of `FOR UPDATE`. Also, it's now possible to provide tables to be locked ([#1623](https://github.com/JetBrains/Exposed/issues/1623)) 
*`ExposedBlob` now wraps `InputStream` instead of `ByteArray` that can lead to significant memory improvements ([#1617](https://github.com/JetBrains/Exposed/issues/1617))
* [H2] All [compatibility modes](http://www.h2database.com/html/features.html#compatibility) are fully supported for known dialects ([#1615](https://github.com/JetBrains/Exposed/issues/1615))
* `mod`/`rem` operations with `EntityID` expressions supported with help of [Alexey Soshin](https://github.com/AlexeySoshin) ([#1597](https://github.com/JetBrains/Exposed/issues/1597))

Bug Fixes:
* Don't alert entity hook subscribers while inserting new entities as it can lead to entity cache misses
* `clientDefault` should allow nullable values ([#1617](https://github.com/JetBrains/Exposed/issues/1617)). Reported and fixed by [terminalnode](https://github.com/terminalnode)
* [Oracle] Use VARCHAR2 column for VarCharColumnType ([#1628](https://github.com/JetBrains/Exposed/issues/1628)). Reported and fixed by [dakriy](https://github.com/dakriy)
* [PostgreSQL] Correct comparison of defaults for String type columns ([#1587](https://github.com/JetBrains/Exposed/issues/1587)). Resolved by [Alexey Soshin](https://github.com/AlexeySoshin)
* Transaction was initialized with a wrong isolation level ([#1575](https://github.com/JetBrains/Exposed/issues/1575))

# 0.40.1
Infrastructure:
* Kotlin 1.7.20

Feature:
* Read-only transactions/connections support. Read-Only option can be set on all levels (via `DatabaseConfig`, `transaction`, `TransactionManager`). Thanks [Alex Shubert](https://github.com/lure) for the improvement
* `Table.deleteWhere` now captures receiver table and allows to omit the table in a lambda. Greetings to [Alexey Soshin](https://github.com/AlexeySoshin) for the first PR in the project!  
* New `mediumText` and `largeText` columns were introduced by [Alex Shubert](https://github.com/lure) to allow use more suitable data types for databases where they are supported.
* `ForUpdateOption` (like `ForUpdateOption.PostgreSQL.ForKeyShare`) added for more flexible management of locks in your `SELECT` queries. You can set it as a parameter via `Query.forUpdate` function. Another kudos goes to [Alex Shubert](https://github.com/lure)
* Preserve a colection type for `Iterable.with()` function 
* `LazySizedCollection` can be checked for loaded data with `LazySizedCollection.isLoaded()`. Added by [unbearables](https://github.com/unbearables)

Bug Fixes:
* [Regression] `NoSuchMethod` error: `long kotlin.time.TimeSource$Monotonic.markNow` ([#1556](https://github.com/JetBrains/Exposed/issues/1540)) 
* `insertIgnoreAndGet` must explicitly mark failed insert on conflicts. Fixed by [Alex Shubert](https://github.com/lure) in PR ([#1584](https://github.com/JetBrains/Exposed/issues/1584))
* Comma is missing in `UPDATE` with multiple tables ([#1595](https://github.com/JetBrains/Exposed/issues/1595))
* `suspendedTransaction` should accept `CoroutineContext` instead of `CourutineDispatcher` was fixed by [rasharab](https://github.com/rasharab) in PR ([#1515](https://github.com/JetBrains/Exposed/issues/1515))
* [MySQL/MariaDB] `REPLACE` fails when `Expression` used as a replacement parameter. Thank you [Tiscs](https://github.com/Tiscs) for the fix.
* `EntityClass#wramUpReferences` should cache reference of referrer. Located and fixed by [Joddev](https://github.com/Joddev).  
* `NullPointerException` when IdTable with overridden tableName is defined ([#1588](https://github.com/JetBrains/Exposed/issues/1588))
* `SchemaUtils.createMissingTablesAndColumns` raises `NoSuchElementException` ([#1568](https://github.com/JetBrains/Exposed/issues/1568))
* Better handling of tables with names covered with quotes. Issue [#1550](https://github.com/JetBrains/Exposed/issues/1550) resolved by [Alexey Soshin](https://github.com/AlexeySoshin). 

# 0.39.2
Infrastructure:
* All modules built with Kotlin 1.6 as a target

Libs updates:
* h2-database 2.1.214
* MaridDB driver 2.7.6 and 3.0.6
* MySQL driver 8.0.30
* PostgreSQL driver 42.4.0
* SQLServer driver 9.4.1.jre8
* Java Money API 1.1
* Spring Framework 5.3.22
* Spring Boot 2.7.2
* Spring Security Crypto 5.6.6

Bug Fixes:
* Converting of `LocalDateTime` values to `Instant` supported for `JavaInstantColumnType` by [hfazai](https://github.com/hfazai)
* [Oracle] Better handling of bool column in queries ([#1540](https://github.com/JetBrains/Exposed/issues/1540)). PR by [maio](https://github.com/maio)
* [Oracle] Proper resolving tables with schemas

# 0.39.1
Infrastructure:
* Kotlin 1.7.10
* Kotlin Coroutines 1.6.4
* Datetime/Timestamp comparison test improvements by [Jerbell](https://github.com/Jerbell)  

Feature:
* `Transaction` added into `afterCommit` and `afterRollback` in `StatementInterceptor` ([#1530](https://github.com/JetBrains/Exposed/issues/1530)). PR by [rsromanowski](https://github.com/rsromanowski) 
* `andIfNotNull` and `orIfNotNull` operators was added by [xJoeWoo](https://github.com/xJoeWoo) to perform logical operations with nullable condition
* `like/notLike` support providing escape character when used with `LikePattern`. Improvement contributed by [spand](https://github.com/spand)
* `CurrentDate` function introduced by [naftalmm](https://github.com/naftalmm)
* Better representation of long query in logs 

Bug Fixes:
* `neq` incorrectly resolved with nullable values  ([#1489](https://github.com/JetBrains/Exposed/issues/1489))
* `newSuspendedTransaction` now accepts generic `CoroutineContext` instead of `CoroutineDispatcher`, fixed by [rasharab](https://github.com/rasharab) 
* Argument value error acquires when `REPLACE` used with expressions under MySQL. Founded and fixed by [Tiscs](https://github.com/Tiscs)
* `EntityClass#wramUpReferences` doesn't store the cached values. Resolved by [Joddev](https://github.com/Joddev)
* Creating tables with composite primary key and defined schema doesn't work. Fixed by [davidwheeler123](https://github.com/davidwheeler123)
* Eager loading of Parent-Child relations doen't work ([#1363](https://github.com/JetBrains/Exposed/issues/1363))
* Possible StackOverflowError when processing entities hooks which executes flush

# 0.38.2
Infrastructure:
* Kotlin Coroutines 1.6.1
* slf4j 1.7.36
* log4j2 2.17.2
* h2-database 2.1.212
* MaridDB driver 2.7.5
* MySQL driver 8.0.28
* PostgreSQL driver 42.3.3

Feature:
* New `optimizedLoad` param introduced for `EntityClass.warmUpLinkedReferences`. 
It will force to make to two queries to load ids and referenced entities separately.
Can be useful when references target the same entities. That will prevent from loading them multiple times 
(per each reference row) and will require less memory/bandwidth for "heavy" entities (with a lot of columns or columns with huge data in it)

Bug Fixes:
* Regression on 0.38.1 - SpringTransactionManager requires DatabaseConfig ([#1488](https://github.com/JetBrains/Exposed/issues/1488))
* `inList`/`notInList` doesn't work with list of EntityIDs ([#1490](https://github.com/JetBrains/Exposed/issues/1490))
* `eq`/`neq` was broken for nullable columns with nullable value ([#1489](https://github.com/JetBrains/Exposed/issues/1489))
* `Except` union operation doesn't work on Oracle
* ORA-00972: identifier is too long. Oracle 12.1.0.2.0 ([#1483](https://github.com/JetBrains/Exposed/issues/1483))
* Can't create arbitrary-size BINARY column in SQLite ([#1443](https://github.com/JetBrains/Exposed/issues/1443))


# 0.38.1
Infrastructure:
* Kotlin 1.6.20
* h2 updated to 2.1.210
* MariaDB driver 3.0.4 supported
* Exposed can be build on Java 17+ (`MaxPermSize` is optional in build scripts), thanks to [MrPowerGamerBR](https://github.com/MrPowerGamerBR)

Feature:
* New `exposed-crypt` module added. Module contains two new `encryptedVarchar` and `encryptedBinary` columns which allows to store encrypted values in database and encode/decode them on client. 
  Check [`SelectTests.test encryptedColumnType with a string`](https://github.com/JetBrains/Exposed/blob/0.38.1/exposed-tests/src/test/kotlin/org/jetbrains/exposed/sql/tests/shared/dml/SelectTests.kt#L264) test for the sample usage 
* Allow to pass DatabaseConfig in SpringTransactionManager. PR by [stengvac](https://github.com/stengvac)
* `CompoundBooleanOp` (`AndOp` and `OrOp`) is sealed class now
* Entity explicit constructor lambda can be defined on `EntityClass` via `entityCtor` parameter to prevent using reflection (for example). Improved by [m-sasha](https://github.com/m-sasha)
* `memoizedTransform` function similar to `transform` added. The delegate will cache value on read for the same value from DB.
* Reified versions of `enumeration` and `enumerationByName` functions
* `CurrentDateTime` became object instance

Performance:
* Faster initialization: reflection replaced with regular code in `Column<T>.cloneWithAutoInc`. Found and fixed by [m-sasha](https://github.com/m-sasha)
* ResultRow stores evaluated values in local cache 
* Prevent unnecessary calls to get current transaction isolation level. Problem located and fixed by [shunyy](https://github.com/shunyy)

Bug Fixes:
* Proper handling of Op.NULL in eq/neq and other places, found by [naftalmm](https://github.com/naftalmm)
* Wrong SortOrder representation in order by and group by
* SQLServer dialect now have default transaction isolation level READ_COMMITTED.
* Unable to use nullable reference with lookup ([#1437](https://github.com/JetBrains/Exposed/issues/1437)) fixed by [naftalmm](https://github.com/naftalmm)
* Wrong behavior when ColumnType returns null on read ([#1435](https://github.com/JetBrains/Exposed/issues/1435)), also fixed by [naftalmm](https://github.com/naftalmm)
* `closeAndUnregister` makes a thread local transaction manager completely dead ([#1476](https://github.com/JetBrains/Exposed/issues/1476))
* Call to `Entity.load` for new entities leads to exception ([#1472](https://github.com/JetBrains/Exposed/issues/1472))
* Broken behavior of `customEnumeration` for Enums with overridden `toString` ([#1475](https://github.com/JetBrains/Exposed/issues/1475)) 
* Another attempt to fix `optReference` column should allow update { it[column] = nullableValue } ([#1275](https://github.com/JetBrains/Exposed/issues/1275))

# 0.37.3
Bug Fixes:
* Many-to-many reference broken in version 0.37.1 ([#1413](https://github.com/JetBrains/Exposed/issues/1413))
* NPE on Enum columns initialization

# 0.37.2
Features:
* `adjustHaving`, `andHaving`, `orHaving` extension methods for `Query` added by [naftalmm](https://github.com/naftalmm)

Bug Fixes:
* Change default for `logTooMuchResultSetsThreshold` to 0 (no log)

# 0.37.1
Infrastructure:
* Major test infrastructure rework by [naftalmm](https://github.com/naftalmm). Now it's possible to run tests on any dialect directly from IDE.
* Kotlin 1.6.10
* Kotlin Coroutines 1.6.0
* kotlinx-datetime-jvm 0.3.1
* Spring framework 5.3.13
* Spring boot 2.6.1
* Detekt 1.19.0

Performance:
* Cache `enumConstants` values in `EnumerationColumnType` and `EnumerationNameColumnType` as it makes copy on access
* Better handling for opened result sets

Features:
* H2 2.x supported
* Composite foreign key supported by [naftalmm](https://github.com/naftalmm). Check the sample below.
```kotlin
object ParentTable : Table("parent1") {
    val idA = integer("id_a")
    val idB = integer("id_b")
    override val primaryKey = PrimaryKey(idA, idB)
}

object ChildTable : Table("child1") {
    val idA = integer("id_a")
    val idB = integer("id_b")

    init {
        foreignKey(
            idA, idB,
            target = ParentTable.primaryKey,
            onUpdate = ReferenceOption.RESTRICT,
            onDelete = ReferenceOption.RESTRICT,
            name = "MyForeignKey1"
        )
        
        // or
        foreignKey(
            idA to ParentTable.idA, idB to ParentTable.idB,
            onUpdate = ReferenceOption.RESTRICT,
            onDelete = ReferenceOption.RESTRICT,
            name = "MyForeignKey1"
        ) 
    }
}

```
* Now it's possible to use embedded entity initialization like:
```kotlin
val post = Post.new {
    parent = Post.new {
        board = Board.new {
            name = "Parent Board"
        }
        category = Category.new {
            title = "Parent Category"
        }
    }
    category = Category.new {
        title = "Child Category"
    }

    optCategory = parent!!.category
}
```
* New `DatabaseConfig.logTooMuchResultSetsThreshold` param added to log when too much result sets opened in parallel in the single transaction 

Bug fixes:
* Providing a String ID to `DaoEntity.new(EntityId)` makes Exposed "forget" about the last field passed to the new call ([#1379](https://github.com/JetBrains/Exposed/issues/1379))
* Proper column name casing change for `SchemaUtils.addMissingColumnsStatement`. PR by [spand](https://github.com/spand)
* Incorrect behavior of `TransactionManager.closeAndUnregister` when calling from different threads ([#1387](https://github.com/JetBrains/Exposed/issues/1387))
* `withLogs` parameter on SchemaUtils#createMissingTablesAndColumns isn't passed to `SchemaUtils.addMissingColumnsStatement` ([#1383](https://github.com/JetBrains/Exposed/issues/1383))
* `optReference` column should allow update { it[column] = nullableValue } ([#1275](https://github.com/JetBrains/Exposed/issues/1275))
* `SchemaUtils.create` make app crashes on Android ([#1398](https://github.com/JetBrains/Exposed/issues/1398))
* `LocalDate` from `kotlinx-datetime` stored in seconds instead of milliseconds. Found and revolved by [Abhishek Singh](https://github.com/abhisheksingh0x558).  


# 0.36.2
Feature:
* Allow skipping SchemaUtils logging with help of new `withLogs` param on functions ([#1378](https://github.com/JetBrains/Exposed/issues/1378))

Bug fixes:
* Prevent too aggressive entity cache invalidation
* Foreign Key with camel-case name throws `java.util.NoSuchElementException`. Fixed by [sultanofcardio](https://github.com/sultanofcardio) 
* Union of queries with differently derived columns loses the derived columns ([#1373](https://github.com/JetBrains/Exposed/issues/1373))

# 0.36.1
Deprecations:
* `NotRegexpOp/notRegexp` was removed
* `ResultRow.tryGet` was removed
* `ResiltSet.create(rs: ResultSet, fields: List<Expression<*>>)` was removed
* `Seq` data class was removed
* `EntityID`, `IdTable`, `IntIdTable`, `LongIdTable`, `UUIDTable`  from `org.jetbrains.exposed.dao` package were removed
* All classes and functions from `org.jetbrains.exposed.sql.java-time` were removed in favour to `org.jetbrains.exposed.sql.javatime`
* `Column.primaryKey` function was removed in favor to explicit `Table.primaryKey` declaration  

Breaking Changes:
* All inheritors of `IdTable` (`IntIdTable`, `LongIdTable`, `UUIDTable`) have their `id` and `primaryKey` fields are final 

Features:
* `DatabaseConfig.defaultSchema` was introduced to set schema before first call in transaction
* `Coalesce` now accepts any number for arguments

Bug fixes:
* EntityCache was reinitialized on explicit `Transaction.commit` 

# 0.35.3
Bug fixes:
* Invalid column's default value check in `SchemaUtils.addMissingColumnsStatements` could lead unneeded column modification  

# 0.35.2
Feature:
* `DatabaseConfig.explicitDialect` param added to predefine dialect for a Database

Bug fixes:
* Don't fail when getting dialectName for user's defined drivers
* [Spring] Possible connection leak within SpringTransactionManager [#1355](https://github.com/JetBrains/Exposed/issues/1355)
* Referrers cache wasn't invalidated when statement was executed on reference table (`via` use-case)
* New entity was flushed on `Entity.reload(flush = false)` what can lead to unexpected results
* ResultSet stayed unclosed if Query's result was not iterated till the end  

# 0.35.1
Features:
* `kotlin-datetime` can be used for datetime mappings with new 'exposed-kotlin-datetime' module
* NULL FIRST/LAST sorting in queries with new SortOrder options ([#478](https://github.com/JetBrains/Exposed/issues/478), [#1343](https://github.com/JetBrains/Exposed/issues/1343)). Many thanks to [erwinw](https://github.com/erwinw) for a PR
* A new way to configure common settings for a database via `DatabaseConfig` ():
```kotlin
// An example with current available settings and their defaults
val dbConfig = DatabaseConfig {
    sqlLogger = Slf4jSqlDebugLogger
    useNestedTransactions = false
    defaultFetchSize = null // unlimited
    defaultIsolationLevel = -1 // DB specific
    defaultRepetitionAttempts = 3
    warnLongQueriesDuration = null // no long query tracing
    maxEntitiesToStoreInCachePerEntity = Int.MAX_VALUE // unlimited 
    keepLoadedReferencesOutOfTransaction = false
}
// databaseConfig available on each connect/connectPool functions
val database = Database.connect(datasource, databaseConfig = databaseConfig)
```
* It's possible to limit the number of entities stored in EntityCache via `maxEntitiesToStoreInCachePerEntity` config parameter [#1302](https://github.com/JetBrains/Exposed/issues/1302)
* References can be stored within an Entity with enabled `keepLoadedReferencesOutOfTransaction` config parameter. It will allow getting referenced values outside the transaction block.  
* `INSTERSECT` and `EXCEPT` set operators supported ([#402](https://github.com/JetBrains/Exposed/issues/402))
* `SchemaUtils.statementsRequiredToActualizeScheme` function to get the list of statements required to actualize scheme by tables mappings

# 0.34.2
Features:
* Supporting subqueries in insert and update statements. Added by [hfazai](https://github.com/hfazai)
* SQL highlighting in `Transaction.exec` with raw SQL ([#1337](https://github.com/JetBrains/Exposed/issues/1337)) 

Bug Fixes:
* [SQLServer] Properly sanitize column default read from database metadata ([#1341](https://github.com/JetBrains/Exposed/issues/1341))
* Table.id is not in record set ([#1341](https://github.com/JetBrains/Exposed/issues/1341))
* newSuspendedTransaction often doesn't honor TransactionManager.defaultDatabase changes ([#1342](https://github.com/JetBrains/Exposed/issues/1342))
* `Database.name` failed on parsing connection string with '/' in parameter list. Founded and fixed by [RaySmith-ttc](https://github.com/RaySmith-ttc)
* Import of Exposed BOM failed when imported as a platform dependency. Fixed by [clarkperkins](https://github.com/clarkperkins) 

# 0.34.1
Infrastructure:
* Kotlin 1.5.30

Features:
* `Op.nullOp()` function added to allow set or compare with `NULL` ([#1315](https://github.com/JetBrains/Exposed/issues/1315))
* [Spring Boot] Enable sql query logging to stdout with `spring.exposed.show-sql` configuration parameter
* `Table.Dual` introduced to allow queries without the real tables
* `Table.batchReplace` function similar to `Table.batchInsert` added by [pilotclass](https://github.com/pilotclass)
* Column default change detected in `SchemaUtils.addMissingColumnsStatements` with help of [spand](https://github.com/spand) 

Bug Fixes:
* [PostgreSQL] `GroupConcat` with distinct fails ([#1313](https://github.com/JetBrains/Exposed/issues/1313)) 
* `UpdateBuilder` inconsistently handles value set check 
* Empty update statement causes SQL Syntax error ([#1241](https://github.com/JetBrains/Exposed/issues/1241))
* Don't call `super.equals` on `Column.equals` to prevent "toString" comparing.  
* [Oracle] `count()` fails on `Union` fixed by [dakriy](https://github.com/dakriy), also `AS` keyword was removed from Aliases  
* [SQLServer]Many to many relationship update breaks when updating from exposed 0.26.2 to 0.27.1 ([#1319](https://github.com/JetBrains/Exposed/issues/1319))

Performance:
* A lot of low-level improvements in different places

# 0.33.1
Infrastructure:
* Kotlin 1.5.21
* Kotlin Coroutines 1.5.1
* kotlinter replaced with Detekt. Many thanks to [BorzdeG](https://github.com/BorzdeG) for PR 

Breaking Changes:
* `EntityCache` internal representation was reworked to lower overhead on cache operations and to create more O(1) 
when working with references. `EntityCache.inserts` and `EntityCache.referrers` fields are not publicly available anymore. 

Features:
* Different math and trigonometrical functions were added. Check `org.jetbrains.exposed.sql.functions.math` package
* Bitwise AND, OR and, XOR were added by [Max Rumpf](https://github.com/Maxr1998)
* `PrepareStatement` can be cancelled, thanks [Alex Shubert](https://github.com/lure) for supporting it
* `ForeignKeyConstraint.customFkName` was added by [spand](https://github.com/spand)
* All types of joins now accepts `additionalConstraint` lambda (PR from [spand](https://github.com/spand))
* `InsertStatement` now stores number of inserted rows in `insertedCount` field ([#851](https://github.com/JetBrains/Exposed/issues/851))
* `batchInsert` function now can be called on `Sequences`. Feature added by [Philip Wedemann](https://github.com/hfhbd) 

Bug Fixes:
* [MySQL/MariaDB] Restore old 0000-00-00 00:00:00 as null behavior for Mysql and MariaDb (PR from [spand](https://github.com/spand)).
* `datetime` column looses nanos part ([#1028](https://github.com/JetBrains/Exposed/issues/1028))
* Setting value for the same column multiple times in UpdateBuilder fails ([#1177](https://github.com/JetBrains/Exposed/issues/1177))
* [SQLite] `primaryKey` override ignored ([#1258]((https://github.com/JetBrains/Exposed/issues/1258))
* Transaction can be unexpectedly initialized when working with coroutines
* [PostgreSQL] `REAL` type will be used instead of `FLOAT8` for `float` column. Thanks [Philip Wedemann](https://github.com/hfhbd) for fix
* [Oracle] `TIME` is not supported on Oracle, mimic it with datetime type

# 0.32.1
Infrastructure:
* Kotlin 1.5.10
* Kotlin Coroutines 1.5.0
* slf4j 1.7.30
* Spring 5.3.7
* Spring Boot 2.5.0
* [Bill Of Materials](https://github.com/JetBrains/Exposed/tree/master/exposed-bom) (BOM) available, many thanks to [DRSchlaubi](https://github.com/DRSchlaubi)

Features:
* Auto-increment columns state change detected (PR from [spand](https://github.com/spand))
* Explicit statementType for `Transaction.exec` functions (also, `EXEC` `StatementType` was introduced). ([390](https://github.com/JetBrains/Exposed/issues/390), [1249](https://github.com/JetBrains/Exposed/issues/1249))

Bug Fixes:
* Entities should be removed from the cache on update/delete made with DSL queries
* Regression: Clientside length validation in ColumnType breaks otherwise harmless where clause ([1204](https://github.com/JetBrains/Exposed/issues/1204), [1222](https://github.com/JetBrains/Exposed/issues/1222))
* Using Entity.flush does not alert EntityHook subscribers ([1225](https://github.com/JetBrains/Exposed/issues/1225))
* TransactionScope throws NullPointerException instead of IllegalStateException when used outside the transaction ([1250](https://github.com/JetBrains/Exposed/issues/1250))
* Spring transaction connection leaks when used with non-exposed transactions ([1167](https://github.com/JetBrains/Exposed/issues/1167))

# 0.31.1
Infrastructure:
* Linting and formatting with [kotliner](https://github.com/jeremymailen/kotlinter-gradle) gradle plugin added by [jnfeinstein](https://github.com/jnfeinstein)

Features:
* TIME data type support in `exposed-java-time` module ([224](https://github.com/JetBrains/Exposed/issues/224)). Improvement provided by [vorotynsky](https://github.com/vorotynsky) and [Jhyub](https://github.com/Jhyub)
* inList with Pairs and Triples support ([643](https://github.com/JetBrains/Exposed/issues/643))

Bug Fixes:
* Proper support for Sequences as default value or autoincrement ([492](https://github.com/JetBrains/Exposed/issues/492), [1164](https://github.com/JetBrains/Exposed/issues/1164), [1209](https://github.com/JetBrains/Exposed/issues/1209))
* [SQL Server] Proper support for 'DEFAULT' keyword ([1207](https://github.com/JetBrains/Exposed/issues/1207)). PR by [ahayanm001](https://github.com/ahayanm001)

Performance:
* Lower footprint on creating ResultRow from ResultSet. Fix was inspired by [maio](https://github.com/maio)

# 0.30.2
Bug Fixes:                                                                         
* Null Durations Convert to 0 ([1196](https://github.com/JetBrains/Exposed/issues/1196))
* Bugs in ISqlExpressionBuilder.coalesce() affecting return value type ([1199](https://github.com/JetBrains/Exposed/issues/1199))
* SELECT is called twice if the `with` method called on a Query ([1202](https://github.com/JetBrains/Exposed/issues/1202))
* Early versions of MySQL Connector don't work with Exposed ([1203](https://github.com/JetBrains/Exposed/issues/1203)). PR by [MeowRay](https://github.com/MeowRay)
* `Query.prepareSQL(QueryBuilder)` is made public to allow preparing raw SQLs ([1206](https://github.com/JetBrains/Exposed/issues/1206) 

# 0.30.1
Infrastructure:
* Artifact publishing moved from jcenter/Bintray to Maven Central
* Kotlin 1.4.32
* Kotlin Coroutines 1.4.3

Feature:
* `UNION` and `UNION ALL` set operations support with related `union`, `unionAll` functions ([402](https://github.com/JetBrains/Exposed/issues/402))  
* `like` and `notLike` methods work with string expression, PR from [hfazai](https://github.com/hfazai)
* [Eager loading](https://github.com/JetBrains/Exposed/wiki/DAO#eager-loading) now works with any iterable

Performance:
* Different minor memory improvements in `exposed-dao` module by [jnfeinstein](https://github.com/jnfeinstein)
* Less entity cache invalidations when works with a single entity

Bug fixes:
* MySQL text type is now treated as `longtext`, SQLServer is `VARCHAR(MAX)`, thanks to [Dmitry Kolmogortsev](https://github.com/koldn)
* Fix to support recent PostgreSQL NG driver by [hfazai](https://github.com/hfazai)
* String functions failed to work with strings longer than 255 characters
* `Query.count()` and `Query.empty()` functions can lead to ResultSet memory leaks
* Alias was lost in update with join queries
* [SQLServer] Problem with blob columns when assigning null value
* Deleting an entity after it is created does not delete it from cache ([1175](https://github.com/JetBrains/Exposed/issues/1175))
* EnumerationNameColumnType fails with vague exception when unknown value in DB ([1176](https://github.com/JetBrains/Exposed/issues/1176))

# 0.29.1
Infrastructure:
* Kotlin 1.4.21
* Kotlin Coroutines 1.4.1
* Spring Framework 5.3.3
* Spring Boot 2.4.2

Feature:
* Now it's possible to define default Database (it will not be overridden by next `Database.connect()`) ([1125](https://github.com/JetBrains/Exposed/issues/1125)). Fix provided by [jnfeinstein](https://github.com/jnfeinstein). Check [wiki](https://github.com/JetBrains/Exposed/wiki/Transactions#setting-default-database) for details.
* New `eqSubQuery` and `notEqSubQuery` functions added by [xJoeWoo](https://github.com/xJoeWoo) to compare value with sub-query result.
* New functions to build expressions in a chain-like manner (`and`, `or`, `andNot`, `orNot`). Idea and realisation by [SchweinchenFuntik](https://github.com/SchweinchenFuntik).

Bug fixes:
* DatasourceHealthIndicator consumes all the DB connections from the pool when used with Exposed Spring Boot starter ([1077](https://github.com/JetBrains/Exposed/issues/1077)).
* Ignore internal SQLite indices on check. PR by [hannesbraun](https://github.com/hannesbraun).
* Narrow scope of referring cache evictions on inserts and deletes. [jnfeinstein](https://github.com/jnfeinstein) thank you for PR.
* At least one column should be provided in `Table.slice()`. Fixed by [hfazai](https://github.com/hfazai).
* Multiple attempts to create indices that already exist ([1031](https://github.com/JetBrains/Exposed/issues/1031)) fixed by [gerritc](https://github.com/gerritc).
* "id not in record set" exception when read value by a column that has related id column ([1032](https://github.com/JetBrains/Exposed/issues/1032))
* Read datetime fails with "No transaction in context" when called outside the transaction with already fetched data ([1130](https://github.com/JetBrains/Exposed/issues/1130)).
* Incorrect state for TransactionManager.manager after calling closeAndUnregister ([1100](https://github.com/JetBrains/Exposed/issues/1100)).
* Connection not available after exceptions with suspendable transaction ([1138](https://github.com/JetBrains/Exposed/issues/1138))
* Entities weren't flushed when executing query with only expressions in select part. Reported and fixed by [jnfeinstein](https://github.com/jnfeinstein).
* Fix for exposed-jodatime module to work with MySQL ConnectorJ 8.0.23

# 0.28.1
Breaking Changes:
* `referrersOn`/`optionalReferrersOn` is now have `cache=true` by default [1046](https://github.com/JetBrains/Exposed/issues/1046). 
  It should help to prevent excessive queries when reading referenced values withing the same transaction but may require more memory to store the cached values.
* Default isolation level for PostgreSQL now set to `READ_COMMITTED`. PR by [uryyyyyyy](https://github.com/uryyyyyyy)  
* [Oracle] Binary column type without length prohibited in favour to blob

Infrastructure:
* Kotlin 1.4.10
* Kotlin Coroutines 1.3.9
* Spring Framework 5.2.9
* Spring Boot 2.3.3

Feature:
* Custom jdbc-driver registration supported with `Database.registerJdbcDriver` function ([#1023](https://github.com/JetBrains/Exposed/issues/1023)), thanks [rnentjes](https://github.com/rnentjes) for the improvement.  

Bug fixes:
* Confusing slice api distincts same expressions ([#1020](https://github.com/JetBrains/Exposed/issues/1020))
* Can't read text column, if it exceeds 255 chars. ([#1029](https://github.com/JetBrains/Exposed/issues/1029))
* SchemaUtils#addMissingColumnsStatements function made public ([#1030](https://github.com/JetBrains/Exposed/issues/1030))
* Sum on Duration columns fails with exception ([#1033](https://github.com/JetBrains/Exposed/issues/1033))
* Batch insert can't be used with nullable collections ([#847](https://github.com/JetBrains/Exposed/issues/847)). PR by [JamiesWhiteShirt](https://github.com/JamiesWhiteShirt)
* Nullable columns can't have default values. Fixed by [xGabrielDEV](https://github.com/xGabrielDEV)
* A possible speedup for Schema related operations on fetching metadata
* It was impossible to make tables join with additional constraint only, implicit join part always added to a join
* [SQLite] Wrong datetime format used
* [H2] Problems with creating primary keys ([#841](https://github.com/JetBrains/Exposed/issues/841), [#1051](https://github.com/JetBrains/Exposed/issues/1051))
* [Oracle] A lot of fixes for datatime column types
* [Oracle] Tables weren't resolved from Scheme

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

Breaking Changes:
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
Breaking Changes:
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

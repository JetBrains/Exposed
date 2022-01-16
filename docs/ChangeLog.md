# 0.4.1

Fetched the following versions and changes from upstream:

- prevent call to Expression.toString() when constructing Expression with `eq` ([#1417](https://github.com/JetBrains/Exposed/pull/1417))
- Fix addAutoPrimaryKey test ([#1418](https://github.com/JetBrains/Exposed/pull/1418))
- Allow to pass DatabaseConfig in SpringTransactionManager ([#1429](https://github.com/JetBrains/Exposed/pull/1429))
- Bumped `com.jfrog.artifactory` to `4.26.1`

# 0.4.0
Fetched the following versions & changes from upstream:

### Upstream Version 0.37.3
Bug Fixes:
* Many-to-many reference broken in version 0.37.1 ([#1413](https://github.com/JetBrains/Exposed/issues/1413))
* NPE on Enum columns initialization

### Upstream Version 0.37.2
Features:
* `adjustHaving`, `andHaving`, `orHaving` extension methods for `Query` added by [naftalmm](https://github.com/naftalmm)

Bug Fixes:
* Change default for `logTooMuchResultSetsThreshold` to 0 (no log)

### Upstream Version 0.37.1
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


### Upstream Version 0.36.2
Feature:
* Allow skipping SchemaUtils logging with help of new `withLogs` param on functions ([#1378](https://github.com/JetBrains/Exposed/issues/1378))

Bug fixes:
* Prevent too aggressive entity cache invalidation
* Foreign Key with camel-case name throws `java.util.NoSuchElementException`. Fixed by [sultanofcardio](https://github.com/sultanofcardio) 
* Union of queries with differently derived columns loses the derived columns ([#1373](https://github.com/JetBrains/Exposed/issues/1373))

### Upstream Version 0.36.1
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

### Upstream Version 0.35.3
Bug fixes:
* Invalid column's default value check in `SchemaUtils.addMissingColumnsStatements` could lead unneeded column modification  

### Upstream Version 0.35.2
Feature:
* `DatabaseConfig.explicitDialect` param added to predefine dialect for a Database

Bug fixes:
* Don't fail when getting dialectName for user's defined drivers
* [Spring] Possible connection leak within SpringTransactionManager [#1355](https://github.com/JetBrains/Exposed/issues/1355)
* Referrers cache wasn't invalidated when statement was executed on reference table (`via` use-case)
* New entity was flushed on `Entity.reload(flush = false)` what can lead to unexpected results
* ResultSet stayed unclosed if Query's result was not iterated till the end  

### Upstream Version 0.35.1
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

### Upstream Version 0.34.2
Features:
* Supporting subqueries in insert and update statements. Added by [hfazai](https://github.com/hfazai)
* SQL highlighting in `Transaction.exec` with raw SQL ([#1337](https://github.com/JetBrains/Exposed/issues/1337)) 

Bug Fixes:
* [SQLServer] Properly sanitize column default read from database metadata ([#1341](https://github.com/JetBrains/Exposed/issues/1341))
* Table.id is not in record set ([#1341](https://github.com/JetBrains/Exposed/issues/1341))
* newSuspendedTransaction often doesn't honor TransactionManager.defaultDatabase changes ([#1342](https://github.com/JetBrains/Exposed/issues/1342))
* `Database.name` failed on parsing connection string with '/' in parameter list. Founded and fixed by [RaySmith-ttc](https://github.com/RaySmith-ttc)
* Import of Exposed BOM failed when imported as a platform dependency. Fixed by [clarkperkins](https://github.com/clarkperkins) 

### Upstream Version 0.34.1

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


# 0.3.0
- `com.jfrog.artifactory` version `4.25.3`.
- `org.jetbrains.kotlin.jvm` version `1.6.10`.

# 0.2.2
- Updated `idTable.batchUpdate` to return the number of updated rows.

# 0.2.1
- Opened up `IdTableWithDefaultFilterStriped` for extension
- Updated `README.md`

# 0.2.0
Feature:
* Added the ability to temporarily strip a tables default scope via the `table.stripDefaultFilter()` method.

# 0.1.0
Feature:
* Added the ability to set a defaultFilter on a table as follows:
    ```kotlin
    object table : Table() {
        val tenantId = uuid("tenant_id")
        override val defaultFilter = Op.build { tenantId eq currentTenantId() }
    }
    ```

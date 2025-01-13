# 0.58.0

## What's Changed

Breaking changes:
* fix: EXPOSED-662 SchemaUtils.listTables() returns empty list & closes db connection by @joc-a in https://github.com/JetBrains/Exposed/pull/2331
  * `SchemaUtils.listTables()` returns only the tables from the current schema. The behaviour was returned to the 0.56.0 version.
  * To get tables from all the schemas the method `SchemaUtils.listTablesInAllSchemas()` could be used.

Features:
* feat: [MariaDB] Support RETURNING clause by @devgor88 in https://github.com/JetBrains/Exposed/pull/2330
* feat: EXPOSED-654 Allow customizing the check constraint names of columns with check constraints by @joc-a in https://github.com/JetBrains/Exposed/pull/2340

Infrastructure:
* springFramework 6.2.1
* log4j2 2.24.3
* org.junit:junit-bom 5.11.4
* org.jetbrains.dokka 2.0.0
* springBoot 3.4.1
* kotlinCoroutines 1.10.0
* kotlinCoroutines 1.10.1
* org.xerial:sqlite-jdbc from 3.47.2.0
* org.jetbrains.kotlinx.binary-compatibility-validator 0.17.0
* org.jetbrains.kotlinx:kotlinx-serialization-json 1.8.0

Docs:
* docs: Update documentation website by @github-actions in https://github.com/JetBrains/Exposed/pull/2304
* docs: EXPOSED-207 Add link to SQLite ALTER TABLE restrictions in SchemaUtils Kdocs by @bog-walk in https://github.com/JetBrains/Exposed/pull/2338
* docs: EXPOSED-670 Adjust YouTrack issue visibility and PR guidelines by @bog-walk in https://github.com/JetBrains/Exposed/pull/2337
* docs: EXPOSED-600 Add links to API docs for functions and classes by @vnikolova in https://github.com/JetBrains/Exposed/pull/2339
* docs: EXPOSED-682 Switch api link from deprecated select() by @bog-walk in https://github.com/JetBrains/Exposed/pull/2349
* EXPOSED-675 Clarify that sort methods are from the Kotlin std lib by @vnikolova in https://github.com/JetBrains/Exposed/pull/2352


# 0.57.0
Infrastructure:
* io.github.hakky54:logcaptor 2.10.0
* Spring Boot 3.4.0
* Spring Framework 6.2.0
* log4j2 2.24.2
* SQLite driver 3.47.1.0
* Foojay Toolchains Plugin 0.9.0

Breaking changes:
* fix! EXPOSED-458 Stop sending default and null values in insert state… by @obabichevjb in https://github.com/JetBrains/Exposed/pull/2295
* chore!: Raise deprecation level of `OptionalReferrers` by @joc-a in https://github.com/JetBrains/Exposed/pull/2325

Features:
* feat: EXPOSED-628 Add comment position for optimizer hints after SELECT by @bog-walk in https://github.com/JetBrains/Exposed/pull/2294
* chore: EXPOSED-642 Support both DSL and DAO transform by @obabichevjb in https://github.com/JetBrains/Exposed/pull/2309
* feat: EXPOSED-555 Allow read-only suspendable transactions by @RenanKummer in https://github.com/JetBrains/Exposed/pull/2274
* feat: Add ability to use aliases on expressions with column type to fix EXPOSED-544 Coalesce mismatch error by @joc-a in https://github.com/JetBrains/Exposed/pull/2308

Bug fixes:
* fix: EXPOSED-623 Offset not applied in COUNT query by @obabichevjb in https://github.com/JetBrains/Exposed/pull/2288
* fix: EXPOSED-621 IllegalStateException on accessing autoincrement column after insert using Entity by @joc-a in https://github.com/JetBrains/Exposed/pull/2291
* fix: EXPOSED-583 alias from inner query missing from outer select by @obabichevjb in https://github.com/JetBrains/Exposed/pull/2281
* fix: EXPOSED-629 aliased array throws java.lang.ClassCastException: o… by @obabichevjb in https://github.com/JetBrains/Exposed/pull/2296
* fix: EXPOSED-625 `SchemaUtils.listTables()` retrieves tables for the default schema only by @joc-a in https://github.com/JetBrains/Exposed/pull/2301
* fix: EXPOSED-641 Byte, Short, Int, Long, UInt, ULong falsely generate database migration statements when they have a default (PostgreSQL and SQL Server) by @joc-a in https://github.com/JetBrains/Exposed/pull/2307
* fix: Make using Java's ServiceLoader optional on Database.connect() by @makeevrserg in https://github.com/JetBrains/Exposed/pull/2293
* fix: EXPOSED-646 count() voids distinctOn call on query by @obabichevjb in https://github.com/JetBrains/Exposed/pull/2311
* fix: EXPOSED-651 Try to close connection in ThreadLocalTransactionManager#connectionLazy if setup fails by @m-burst in https://github.com/JetBrains/Exposed/pull/2320

Docs:
* docs: EXPOSED-626 fix nullTransform example code by @kdhyo in https://github.com/JetBrains/Exposed/pull/2292
* docs: EXPOSED-639 Add note about required imports with deleteWhere() by @bog-walk in https://github.com/JetBrains/Exposed/pull/2303
* EXPOSED-414 Docs Search: Add Algolia parameters to Writerside config by @vnikolova in https://github.com/JetBrains/Exposed/pull/2298
* docs: EXPOSED-578 Extend the Entity definition topic and add examples by @vnikolova in https://github.com/JetBrains/Exposed/pull/2302
* docs: EXPOSED-601 Add Exposed logo favicon to Writerside config by @vnikolova in https://github.com/JetBrains/Exposed/pull/2306
* docs: EXPOSED-640 Extract DSL code snippets to a snippets project by @vnikolova in https://github.com/JetBrains/Exposed/pull/2321
* docs: Fix markup validity issues in the Get started tutorial by @vnikolova in https://github.com/JetBrains/Exposed/pull/2312

# 0.56.0
Infrastructure:
* junit-bom 5.11.3
* SQLite driver 3.47.0.0
* log4j2 2.24.1
* Oracle driver 19.24.0.0
* Spring Framework 6.1.14
* Spring Boot 3.3.5

Breaking changes:
* fix!: EXPOSED-569 groupConcat uses wrong SQLite syntax & ignores DISTINCT in Oracle & SQL Server by @bog-walk in https://github.com/JetBrains/Exposed/pull/2257
* chore!: Change Oracle and H2 Oracle uintegerType and uintegerAutoincType from NUMBER(13) to NUMBER(10) by @joc-a in https://github.com/JetBrains/Exposed/pull/2268
* chore!: Change Oracle and H2 Oracle ushortType from NUMBER(6) to NUMBER(5) by @joc-a in https://github.com/JetBrains/Exposed/pull/2268
* chore!: Change Oracle and H2 Oracle ubyteType from NUMBER(4) to NUMBER(3) by @joc-a in https://github.com/JetBrains/Exposed/pull/2268
* chore!: Change Oracle and H2 Oracle integerType and integerAutoincType from NUMBER(12) to NUMBER(10) and INTEGER respectively and add CHECK constraint in SQLite by @joc-a in https://github.com/JetBrains/Exposed/pull/2270
* feat!: EXPOSED-359 Add support for multidimensional arrays by @obabichevjb in https://github.com/JetBrains/Exposed/pull/2250
* feat!: EXPOSED-577 Allow Entity and EntityID parameters to not be Comparable by @bog-walk in https://github.com/JetBrains/Exposed/pull/2277
* More details at [Breaking changes](https://jetbrains.github.io/Exposed/breaking-changes.html#0-56-0)

Features:
* feat: Support partially filled composite IDs by @sickfar in https://github.com/JetBrains/Exposed/pull/2282
* feat: EXPOSED-494 Inline DSL statement and query functions by @bog-walk in https://github.com/JetBrains/Exposed/pull/2272
* feat: EXPOSED-560 Support DISTINCT ON from Postgres by @obabichevjb in https://github.com/JetBrains/Exposed/pull/2275

Bug fixes:
* fix: EXPOSED-565 Subquery alias with id fails to use correct alias with eq by @bog-walk in https://github.com/JetBrains/Exposed/pull/2258
* fix: EXPOSED-278 Invalid Oracle statement when adding a new column that is used in a primary key by @joc-a in https://github.com/JetBrains/Exposed/pull/2259
* fix: EXPOSED-576 DAO Entity.new() fails if there is column with default value and transformation by @obabichevjb in https://github.com/JetBrains/Exposed/pull/2263
* fix: EXPOSED-580 MigrationsUtils.statementsRequiredForDatabaseMigration throws an error when a table is passed that does not already exist in the database by @joc-a in https://github.com/JetBrains/Exposed/pull/2271
* fix: EXPOSED-588 Foreign key error when table has dot in its name by @joc-a in https://github.com/JetBrains/Exposed/pull/2276
* fix: EXPOSED-602 Column name that includes table name is aliased with upserts by @bog-walk in https://github.com/JetBrains/Exposed/pull/2287

Docs:
* docs: EXPOSED-532 Split the DAO topic into subtopics by @vnikolova in https://github.com/JetBrains/Exposed/pull/2254
* docs: Extend entities definition docs and reference code from snippets by @vnikolova in https://github.com/JetBrains/Exposed/pull/2264
* docs: EXPOSED-572 Add code sample for 'auto-fill columns on entity change' by @bog-walk in https://github.com/JetBrains/Exposed/pull/2278

# 0.55.0

Infrastructure:
* Spring Framework 6.1.13
* Spring Boot 3.3.4
* log4j2 2.24.0
* detekt 1.23.7
* joda-time:joda-time 2.13.0
* kotlinCoroutines 1.9.0

Breaking changes:
* feat!: EXPOSED-514 Support DELETE from tables in join by @bog-walk in https://github.com/JetBrains/Exposed/pull/2223
* feat!: EXPOSED-497 Allow OFFSET without LIMIT in query by @bog-walk in https://github.com/JetBrains/Exposed/pull/2226
* fix!: EXPOSED-545 Byte column allows out-of-range values by @joc-a in https://github.com/JetBrains/Exposed/pull/2239
* fix!: EXPOSED-536 Short column allows out-of-range values by @joc-a in https://github.com/JetBrains/Exposed/pull/2231
* fix!: EXPOSED-482 Cannot use `Column.transform()` to return null by @obabichevjb in https://github.com/JetBrains/Exposed/pull/2235
* fix!: EXPOSED-546 [H2] Column with Json default value can not be used as databaseGenerated by @obabichevjb in https://github.com/JetBrains/Exposed/pull/2241

Deprecations:
* deprecate!: EXPOSED-550 DeleteStatement holds unused offset property by @bog-walk in https://github.com/JetBrains/Exposed/pull/2243

Features:
* feat: EXPOSED-498 Handle auto-increment status change on a column by @joc-a in https://github.com/JetBrains/Exposed/pull/2216
* feat: EXPOSED-552 Include DROP statements for unmapped columns for migration by @joc-a in https://github.com/JetBrains/Exposed/pull/2249

Bug fixes:
* fix: EXPOSED-513 DROP SEQUENCE fails when there is a dot in the sequence name by @joc-a in https://github.com/JetBrains/Exposed/pull/2220
* fix: EXPOSED-496 reference() idColumn equality check with referree's id is insufficient by @bog-walk in https://github.com/JetBrains/Exposed/pull/2222
* fix: EXPOSED-528 Escape parameter placeholder '?' by double question mark '??' by @JajaComp in https://github.com/JetBrains/Exposed/pull/2227
* fix: EXPOSED-509 Upsert with escaped multiline string fails in prepared statement by @bog-walk in https://github.com/JetBrains/Exposed/pull/2224
* fix: EXPOSED-495 Unable to create new Entity when server-side default valu… by @obabichevjb in https://github.com/JetBrains/Exposed/pull/2240
* fix: EXPOSED-547 idParam() registers composite id value with a single placeholder by @bog-walk in https://github.com/JetBrains/Exposed/pull/2242
* fix: EXPOSED-527 BUG: mergeFrom(...) using a query with const-condition do… by @obabichevjb in https://github.com/JetBrains/Exposed/pull/2236
* fix: EXPOSED-551 [SQL Server] Update from join with limit throws syntax exception by @bog-walk in https://github.com/JetBrains/Exposed/pull/2244
* fix: EXPOSED-558 Entity cache for upsertReturning statements results in stale return values by @rasharab in https://github.com/JetBrains/Exposed/pull/2248
* fix: EXPOSED-373 Close ResultSet before closing Statement to suppress Agroal leak warning by @ivan-gomes in https://github.com/JetBrains/Exposed/pull/2247
* fix: EXPOSED-562 Any caught exception from inner transaction triggers full rollback by @bog-walk in https://github.com/JetBrains/Exposed/pull/2251

Docs:
* docs: EXPOSED-515 How to identify composite key columns that use reference() by @bog-walk in https://github.com/JetBrains/Exposed/pull/2225

# 0.54.0

Infrastructure:
* Kotlin 2.0.0
* Kotlinx Serialization Json 1.7.1
* Spring Framework 6.1.12
* junit-bom 5.11.0
* SQLite driver 3.46.1.0
* Kotlinx Datetime JVM 0.6.1
* Spring Boot 3.3.3
* PostgreSQL driver 42.7.4

Breaking changes:
* feat!: EXPOSED-476 Update Kotlin to 2.0.0 by @bog-walk in https://github.com/JetBrains/Exposed/pull/2188
* refactor!: Move `statementsRequiredForDatabaseMigration` function from `SchemaUtils` to `MigrationUtils` by @joc-a in https://github.com/JetBrains/Exposed/pull/2195
* feat!: EXPOSED-436 Allow using insert values on update with upsert() by @bog-walk in https://github.com/JetBrains/Exposed/pull/2172
* fix!: EXPOSED-439 Outer transaction commits rows from failed inner transaction by @bog-walk in https://github.com/JetBrains/Exposed/pull/2186

Deprecations:
* deprecate: Raise deprecation levels of API elements by @bog-walk in https://github.com/JetBrains/Exposed/pull/2208
* deprecate: Raise deprecation levels of API property setters by @bog-walk in https://github.com/JetBrains/Exposed/pull/2209

Features:
* feat: Add `isNullOrEmpty` function by @joc-a in https://github.com/JetBrains/Exposed/pull/2184
* feat: EXPOSED-487 Add ability to pass custom sequence to auto-increment column by @joc-a in https://github.com/JetBrains/Exposed/pull/2197
* feat: EXPOSED-486 Support REPLACE INTO ... SELECT clause by @bog-walk in https://github.com/JetBrains/Exposed/pull/2199

Bug fixes:
* fix: EXPOSED-464 `CurrentTimestampWithTimeZone` expression does not work as a default by @joc-a in https://github.com/JetBrains/Exposed/pull/2180
* fix: EXPOSED-474 Unexpected value of type when using a ColumnTransfor… by @obabichevjb in https://github.com/JetBrains/Exposed/pull/2191
* fix: EXPOSED-472 Alias IdTable fails with isNull and eq ops by @bog-walk in https://github.com/JetBrains/Exposed/pull/2189
* fix: EXPOSED-467 Decimal type precision and scale not checked by SchemaUtils by @bog-walk in https://github.com/JetBrains/Exposed/pull/2192
* EXPOSED-203 Lightweight DAO insert with encryptedVarchar attemtps to … by @obabichevjb in https://github.com/JetBrains/Exposed/pull/2194
* fix: EXPOSED-481 Bug with batch-flushing of CompositeID entities by @bystam in https://github.com/JetBrains/Exposed/pull/2196
* fix: EXPOSED-492 Eq/Neq op with partial CompositeID unwrapped value fails by @bog-walk in https://github.com/JetBrains/Exposed/pull/2205
* fix: EXPOSED-485 ClassCastException when eager loading referrersOn with uuid().references() by @bog-walk in https://github.com/JetBrains/Exposed/pull/2198
* fix: EXPOSED-493 Update with join query throws if WHERE clause present by @bog-walk in https://github.com/JetBrains/Exposed/pull/2207
* fix: EXPOSED-501 Column.transform() ignores custom setParameter() logic by @bog-walk in https://github.com/JetBrains/Exposed/pull/2214

Docs:
* Add links in Learn more section on documentation home page by @Selemba1000 in https://github.com/JetBrains/Exposed/pull/2187
* chore: fix some comments by @riskrose in https://github.com/JetBrains/Exposed/pull/2185
* docs: EXPOSED-489 Replace the header logo with a white version by @vnikolova in https://github.com/JetBrains/Exposed/pull/2202
* docs: Add navigation structure and remove redundant topics by @vnikolova in https://github.com/JetBrains/Exposed/pull/2203
* docs: EXPOSED-499 Add examples of custom data types by @bog-walk in https://github.com/JetBrains/Exposed/pull/2213
* docs: Split up the "Deep dive into DSL" topic into several new topics by @vnikolova in https://github.com/JetBrains/Exposed/pull/2217

# 0.53.0
Infrastructure:
* SQLite driver 3.46.0.1
* Spring Framework 6.1.11
* Spring Boot 3.3.2
* junit-bom 5.10.3

Breaking changes:
* feat!: EXPOSED-388 Support for column type converters by @obabichevjb in https://github.com/JetBrains/Exposed/pull/2143

Features:
* feat: Add time extension function for temporal expressions in Kotlin and Java by @joc-a in https://github.com/JetBrains/Exposed/pull/2121
* feat: EXPOSED-435 Allow insertReturning() to set isIgnore = true by @bog-walk in https://github.com/JetBrains/Exposed/pull/2148
* feat: EXPOSED-77 Support entity class for table with composite primary key by @bog-walk in https://github.com/JetBrains/Exposed/pull/1987
* feat: EXPOSED-446 Support N-column inList equality comparisons by @bog-walk in https://github.com/JetBrains/Exposed/pull/2157
* feat: EXPOSED-450 Merge command: PostgreSQL improvements by @obabichevjb in https://github.com/JetBrains/Exposed/pull/2161
* Adding comment text for a query SQL by @xJoeWoo in https://github.com/JetBrains/Exposed/pull/2088
* feat: EXPOSED-459 Open AbstractQuery.copyTo() to allow custom Query class extension by @bog-walk in https://github.com/JetBrains/Exposed/pull/2173
* feat: EXPOSED-461 Add time column in Joda-Time module by @joc-a in https://github.com/JetBrains/Exposed/pull/2175

Bug fixes:
* fix: EXPOSED-424 ClassCastException exception when using `fetchBatchedResults` with `alias` by @joc-a in https://github.com/JetBrains/Exposed/pull/2140
* fix: EXPOSED-407 compositeMoney() nullability definition issues by @bog-walk in https://github.com/JetBrains/Exposed/pull/2137
* fix: EXPOSED-415 SchemaUtils incorrectly generates ALTER statements for existing nullable columns by @obabichevjb in https://github.com/JetBrains/Exposed/pull/2136
* fix: EXPOSED-363 LocalTime and literal(LocalTime) are not the same by @joc-a in https://github.com/JetBrains/Exposed/pull/2152
* fix: EXPOSED-432 CurrentDate default is generated as null in MariaDB by @joc-a in https://github.com/JetBrains/Exposed/pull/2149
* fix: Allow column reference in default expressions for MySQL and MariaDB by @joc-a in https://github.com/JetBrains/Exposed/pull/2159
* fix: EXPOSED-430 Insert and BatchInsert do not return default values by @obabichevjb in https://github.com/JetBrains/Exposed/pull/2158
* fix: EXPOSED-452 Flaky H2_Oracle test `testTimestampWithTimeZoneDefaults` by @joc-a in https://github.com/JetBrains/Exposed/pull/2169
* EXPOSED-457 The column default value always compares unequal by @obabichevjb in https://github.com/JetBrains/Exposed/pull/2170
* EXPOSED-409 Custom primary key. Access to the primary key fails with ClassCastException by @obabichevjb in https://github.com/JetBrains/Exposed/pull/2151
* fix: EXPOSED-447 Eager loading does not work with composite PK entity by @bog-walk in https://github.com/JetBrains/Exposed/pull/2177

Docs:
* chore: Add migration sample by @joc-a in https://github.com/JetBrains/Exposed/pull/2144
* docs: Change repetitionAttempts to maxAttempts in website docs by @bog-walk in https://github.com/JetBrains/Exposed/pull/2164
* docs: EXPOSED-445 Add documentation for DSL & DAO composite primary keys by @bog-walk in https://github.com/JetBrains/Exposed/pull/2165
* docs: EXPOSED-419 Rework the getting started tutorial by @vnikolova in https://github.com/JetBrains/Exposed/pull/2160
* Configure API documentation for Exposed by @e5l in https://github.com/JetBrains/Exposed/pull/2171

# 0.52.0
Breaking changes: 
* feat: EXPOSED-295 Support subqueries with preceding LATERAL by @obabichevjb in https://github.com/JetBrains/Exposed/pull/2095

Features:
* feat: EXPOSED-334 Support MERGE statement by @obabichevjb in https://github.com/JetBrains/Exposed/pull/2047
* feat: EXPOSED-368 Ordering on References by @obabichevjb in https://github.com/JetBrains/Exposed/pull/2083
* Feat: EXPOSED-396 Supports fetchBatchedResults with sorting order  by @roharon in https://github.com/JetBrains/Exposed/pull/2102
* feat: Add OffsetDateTime extension functions by @joc-a in https://github.com/JetBrains/Exposed/pull/2118
* feat: EXPOSED-336 Support Where clause with batchUpsert by @bog-walk in https://github.com/JetBrains/Exposed/pull/2120
* feat: EXPOSED-416 Support adding special database-specific column definitions by @bog-walk in https://github.com/JetBrains/Exposed/pull/2125

Bug fixes:
* fix: EXPOSED-389 Coalesce operator returning nullable value by @joc-a in https://github.com/JetBrains/Exposed/pull/2107
* fix: EXPOSED-390 ASC_NULLS_LAST and DESC_NULLS_FIRST for MySQL string columns by @zly2006 in https://github.com/JetBrains/Exposed/pull/2091
* fix: EXPOSED-402 ClassCastException when eager loading with uuid().references() by @bog-walk in https://github.com/JetBrains/Exposed/pull/2112
* fix(DoubleColumnType): correctly handle precision when casting Float to DoubleColumnType for a `real` column by @jackgisel-RL in https://github.com/JetBrains/Exposed/pull/2115
* fix: EXPOSED-277 statementsRequiredToActualizeScheme does not check s… by @obabichevjb in https://github.com/JetBrains/Exposed/pull/2096
* fix: EXPOSED-411 ClassCastException when `uuid().references()` is used with `referrersOn` by @joc-a in https://github.com/JetBrains/Exposed/pull/2127
* fix: EXPOSED-412 Remove all the usage of isOldMySql function in tests by @obabichevjb in https://github.com/JetBrains/Exposed/pull/2126
* fix: EXPOSED-405 SQLite bugs: Table with custom ID behaves weirdly in DAO and batchInsert by @obabichevjb in https://github.com/JetBrains/Exposed/pull/2119
* fix: EXPOSED-393 H2 upsert with JSON column creates invalid data by @obabichevjb in https://github.com/JetBrains/Exposed/pull/2104
* fix: EXPOSED-400 ClassCastException when using `fetchBatchedResults` by @joc-a in https://github.com/JetBrains/Exposed/pull/2113
* EXPOSED-398 Gradle task testH2_v1 runs tests on version 2.2.224 by @obabichevjb in https://github.com/JetBrains/Exposed/pull/2110
* test: EXPOSED-191 Flaky Oracle test on TC build by @obabichevjb in https://github.com/JetBrains/Exposed/pull/2098

Infrastructure:
* Spring Boot 3.3.1
* io.github.hakky54:logcaptor 2.9.3
* Spring Framework 6.1.10
* org.junit:junit-bom 5.10.2
* chore: Fix TC Docker `version` is obsolete by @bog-walk in https://github.com/JetBrains/Exposed/pull/2111
* test: EXPOSED-249 Add MySQL8 to tests for AllAnyFromBaseOp feature by @bog-walk in https://github.com/JetBrains/Exposed/pull/2123
* chore: Add migration module and move `generateMigrationScript` function to it by @joc-a in https://github.com/JetBrains/Exposed/pull/2128
* Add workflow to build documentation website by @e5l in https://github.com/JetBrains/Exposed/pull/2134


# 0.51.1
Bug fixes:
* fix: EXPOSED-389 Coalesce operator returning nullable value by @joc-a in https://github.com/JetBrains/Exposed/pull/2107

# 0.51.0
Infrastructure:
* Spring Boot 3.3.0
* Kotlin Coroutines 1.8.1
* Spring Framework 6.1.8
* SQLite driver 3.46.0.0
* Kotlinx Datetime JVM 0.6.0

Breaking changes:
* build!: EXPOSED-315 Use the slimmer `spring-boot-starter-jdbc` instead of `spring-boot-starter-data-jdbc` by @bystam
  in https://github.com/JetBrains/Exposed/pull/2055
* fix!: EXPOSED-360 Storing ULong.MAX_VALUE in ulong column not working by @joc-a in https://github.com/JetBrains/Exposed/pull/2068
* More details at [Breaking changes](https://jetbrains.github.io/Exposed/breaking-changes.html#0-51-0)

Features:
* feat: Add support for variable-length binary columns in H2 by @rnett in https://github.com/JetBrains/Exposed/pull/2100

Bug fixes:
* fix: EXPOSED-353 dateLiteral does not work on OracleDB 12c or 19c by @obabichevjb in https://github.com/JetBrains/Exposed/pull/2057
* fix: EXPOSED-382 ClassCastException when uuid().references() is used with EntityID column by @bog-walk in https://github.com/JetBrains/Exposed/pull/2079
* fix: EXPOSED-384 CurrentTimestamp cannot be used with OffsetDateTimeColumnType by @bog-walk in https://github.com/JetBrains/Exposed/pull/2081
* EXPOSED-372 UpsertStatement.resultedValues contains incorrect value by @obabichevjb in https://github.com/JetBrains/Exposed/pull/2075
* EXPOSED-365 Unable to insert values into `Array` column by @obabichevjb in https://github.com/JetBrains/Exposed/pull/2072
* EXPOSED-376 batchUpsert does not return database values on conflict by @obabichevjb in https://github.com/JetBrains/Exposed/pull/2082
* EXPOSED-387 Exposed Join.lastQueryAlias not working correctly by @obabichevjb in https://github.com/JetBrains/Exposed/pull/2085
* fix: Crash in aliased OpBoolean by @joc-a in https://github.com/JetBrains/Exposed/pull/2094
* fix: EXPOSED-395 ClassCastException with EntityId column operations by @joc-a in https://github.com/JetBrains/Exposed/pull/2103
* fix: EXPOSED-391 Cannot map columns to different types anymore by @joc-a in https://github.com/JetBrains/Exposed/pull/2099

Docs:
* docs: fix typos in foreignKey documentation by @plplmax in https://github.com/JetBrains/Exposed/pull/2077
* docs: Specify a URL for clicks on the header logo by @vnikolova in https://github.com/JetBrains/Exposed/pull/2080

# 0.50.1
Bug fixes:
* fix: EXPOSED-366 inList with EntityID column causes type mismatch error by @bog-walk in https://github.com/JetBrains/Exposed/pull/2070
* fix: EXPOSED-371 Fix incorrect table reference passed to EntityID instance when using value-based utility functions by @dzikoysk in https://github.com/JetBrains/Exposed/pull/2074

Docs:
* update: update Exposed logo by @koshachy in https://github.com/JetBrains/Exposed/pull/2071

# 0.50.0
Infrastructure:
* Spring Framework 6.1.6

Breaking changes:
* fix!: EXPOSED-317 repetitionAttempts property is misleading by @bog-walk in https://github.com/JetBrains/Exposed/pull/2042
* refactor!: Column type safety by @joc-a in https://github.com/JetBrains/Exposed/pull/2027
* More details at [Breaking changes](https://jetbrains.github.io/Exposed/breaking-changes.html#0-50-0)

Deprecations:
* deprecate: Raise deprecation levels of API elements by @bog-walk in https://github.com/JetBrains/Exposed/pull/2038
* deprecate: EXPOSED-354 Database.connectPool() with ConnectionPoolDataSource by @bog-walk in https://github.com/JetBrains/Exposed/pull/2059

Features:
* feat: EXPOSED-327 Support GraalVM native images with Spring Boot by @joshlong and @bog-walk in https://github.com/JetBrains/Exposed/pull/2039. Many thanks to [joshlong](https://github.com/joshlong) for the support.
* feat: EXPOSED-296 Add ability to check if a Sequence exists in a database by @joc-a in https://github.com/JetBrains/Exposed/pull/2045
* feat: EXPOSED-355 Support INSERT...RETURNING statement by @bog-walk in https://github.com/JetBrains/Exposed/pull/2060
* feat: EXPOSED-357 Support DELETE...RETURNING statement by @bog-walk in https://github.com/JetBrains/Exposed/pull/2061
* feat: EXPOSED-356 Support UPDATE...RETURNING statement by @bog-walk in https://github.com/JetBrains/Exposed/pull/2062

Bug fixes:
* fix(jdbc): existingIndices() misses indexes from tables with a schema by @jackgisel-RL in https://github.com/JetBrains/Exposed/pull/2033
* fix: EXPOSED-259 supportsSubqueryUnions is too strict for PostgreSQL 12+ by @bog-walk in https://github.com/JetBrains/Exposed/pull/2037
* fix: EXPOSED-339 Oracle alias for blob does not work by @obabichevjb in https://github.com/JetBrains/Exposed/pull/2048
* fix: EXPOSED-340 Syntax error using upsert with MySQL8 below 8.0.19 by @bog-walk in https://github.com/JetBrains/Exposed/pull/2049
* fix: EXPOSED-349 "defaultValueFun" is lost from Column in Alias by @joc-a in https://github.com/JetBrains/Exposed/pull/2058
* fix: Error when updating different entities mapped to the same table by @joc-a in https://github.com/JetBrains/Exposed/pull/2065
* fix: EXPOSED-350 keepLoadedReferencesOutOfTransaction causes duplicate query when true by @bog-walk in https://github.com/JetBrains/Exposed/pull/2064

Docs:
* Move wiki to github pages documentation by @e5l in https://github.com/JetBrains/Exposed/pull/2034
* docs: EXPOSED-313 JSON columns support libraries other than kotlinx.serialization by @bog-walk in https://github.com/JetBrains/Exposed/pull/2041
* docs: Update Contributing documentation with code style details by @obabichevjb in https://github.com/JetBrains/Exposed/pull/2051
* docs: EXPOSED-319 H2 customEnumeration example throws by @bog-walk in https://github.com/JetBrains/Exposed/pull/2056

Tests:
* Move BLOB tests to own source files by @obabichevjb in https://github.com/JetBrains/Exposed/pull/2053

# 0.49.0
Infrastructure:
* log4j2 2.23.1
* SQLite driver 3.45.2.0
* Spring Framework 6.1.5
* PostgreSQL driver 42.7.3
* Detekt 1.23.6

[Breaking changes](https://jetbrains.github.io/Exposed/breaking-changes.html#0-49-0):
* fix!: EXPOSED-269 Incompatible with sqlite-jdbc 3.45.0.0 by @joc-a in https://github.com/JetBrains/Exposed/pull/2030

Features:
* feat: EXPOSED-238 Support EXPLAIN statements by @bog-walk in https://github.com/JetBrains/Exposed/pull/2022
* feat: Include DROP statements for unmapped indices in list of statements returned by `statementsRequiredForDatabaseMigration` function by @joc-a
  in https://github.com/JetBrains/Exposed/pull/2023
* feat: EXPOSED-310 Add support for ULongIdTable and ULongEntity by @joc-a in https://github.com/JetBrains/Exposed/pull/2025
* feat: EXPOSED-316 Add support for UIntIdTable and UIntEntity by @joc-a in https://github.com/JetBrains/Exposed/pull/2026

Bug fixes:
* fix: Tests `testAdjustQueryHaving`, `testQueryAndHaving`, and `testQueryOrHaving` resolve wrong `eq` function, and `testGroupBy03` shows compiler warning by @joc-a
  in https://github.com/JetBrains/Exposed/pull/2016
* fix: EXPOSED-217 Unnecessary query after calling with() and iteration by @bog-walk in https://github.com/JetBrains/Exposed/pull/2017
* fix: EXPOSED-307 [SQLite] Delete ignore not supported and throws by @bog-walk in https://github.com/JetBrains/Exposed/pull/2021

Docs:
* docs: Add missing KDocs for exposed-dao Entity API by @bog-walk in https://github.com/JetBrains/Exposed/pull/2012
* docs: Add missing KDocs for exposed-dao EntityClass API by @bog-walk in https://github.com/JetBrains/Exposed/pull/2018
* docs: Add missing KDocs for exposed-dao Entity References API by @bog-walk in https://github.com/JetBrains/Exposed/pull/2020
* docs: Add missing KDocs for exposed-dao EntityCache API by @bog-walk in https://github.com/JetBrains/Exposed/pull/2019
* Improve instructions for Maven users by @breun in https://github.com/JetBrains/Exposed/pull/2031

# 0.48.0
Infrastructure:
* PostgreSQL driver 42.7.2
* Joda Time 2.12.7
* Kotlin Coroutines 1.8.0
* log4j2 2.23.0
* Kotlinx Serialization Json 1.6.3
* Spring Framework 6.1.4
* Spring Security Crypto 6.2.1

Breaking changes:
* `nonNullValueToString()` in some date/time column types now uses more appropriate string formatters.
* `anyFrom(array)` and `allFrom(array)` may require an additional argument if a matching column type cannot be resolved for the array contents.
* `exposed-crypt` module now uses Spring Security Crypto 6.+, which requires Java 17 as a minimum version.
* More details at [Breaking changes](https://jetbrains.github.io/Exposed/breaking-changes.html#0-48-0)

Features:
* feat: EXPOSED-248 Support array column type by @bog-walk in https://github.com/JetBrains/Exposed/pull/1986
* feat: EXPOSED-290 Support ANY and ALL operators using array column expressions by @bog-walk in https://github.com/JetBrains/Exposed/pull/1988
* EXPOSED-121, allowing option for "real" blobs in postgres by @elektro-wolle in https://github.com/JetBrains/Exposed/pull/1822
* feat: EXPOSED-258 Enhance upsert to allow exclusion of columns set on conflict by @bog-walk in https://github.com/JetBrains/Exposed/pull/2006

Bug fixes:
* fix: EXPOSED-272 [MySQL, Oracle] Unsupported type BIGINT UNSIGNED for auto-increment by @bog-walk in https://github.com/JetBrains/Exposed/pull/1982
* fix: EXPOSED-266 Between() accepts arguments of different type than column type by @bog-walk in https://github.com/JetBrains/Exposed/pull/1983
* fix: EXPOSED-280 Comparison operators show incorrect compiler warning with datetime columns by @bog-walk in https://github.com/JetBrains/Exposed/pull/1984
* fix: EXPOSED-287 Wrong parenthesis with advanced use of isDistinctFrom by @bog-walk in https://github.com/JetBrains/Exposed/pull/1990
* fix: EXPOSED-282 Timestamp with timezone column default falsely triggers ALTER statement by @joc-a in https://github.com/JetBrains/Exposed/pull/1981
* fix!: EXPOSED-282 Timestamp column default falsely triggers ALTER statement by @joc-a in https://github.com/JetBrains/Exposed/pull/1981
* fix!: EXPOSED-284 Datetime column default falsely triggers ALTER statement by @joc-a in https://github.com/JetBrains/Exposed/pull/1981
* fix: EXPOSED-285 Time column defaults falsely trigger ALTER statements by @joc-a in https://github.com/JetBrains/Exposed/pull/1981
* fix: EXPOSED-256 Date column defaults falsely trigger ALTER statements by @joc-a in https://github.com/JetBrains/Exposed/pull/1981
* fix: EXPOSED-292 Explicit nulls in insert with databaseGenerated() by @bog-walk in https://github.com/JetBrains/Exposed/pull/1993
* fix!: Use correct formatter for MySQL when the version does not support fractional seconds by @joc-a in https://github.com/JetBrains/Exposed/pull/1997
* fix!: Change formatters in DateColumnType to reflect the fact that Joda-Time stores date/time values only down to the millisecond by @joc-a in https://github.com/JetBrains/Exposed/pull/1997
* fix!: EXPOSED-288 Extend ANY and ALL operators to use ArrayColumnType by @bog-walk in https://github.com/JetBrains/Exposed/pull/1992
* fix: Add ArrayColumnType default override for datetime module types by @bog-walk in https://github.com/JetBrains/Exposed/pull/1995
* fix: EXPOSED-299 [H2 modes] SchemaUtils drops and adds identical composite foreign key by @bog-walk in https://github.com/JetBrains/Exposed/pull/2005
* fix: EXPOSED-301 Update with join throws if additionalConstraint provided by @bog-walk in https://github.com/JetBrains/Exposed/pull/2007
* fix: EXPOSED-302 Count with alias fails if table name includes schema by @bog-walk in https://github.com/JetBrains/Exposed/pull/2008
* fix: EXPOSED-293 Logger prints plaintext value of encryptedVarchar by @bog-walk in https://github.com/JetBrains/Exposed/pull/2009

Build:
* build!: EXPOSED-234 Set exposed-crypt to jdk 17 & bump spring-security-crypto to 6.+ by @bog-walk in https://github.com/JetBrains/Exposed/pull/2001

Docs:
* docs: Add missing KDocs for EntityID and Entity subclasses API by @bog-walk in https://github.com/JetBrains/Exposed/pull/1991
* docs: Add details about ArrayColumnType and ANY/ALL operators by @bog-walk in https://github.com/JetBrains/Exposed/pull/2000
* docs: Fix foreign key KDocs that swap parent and child references by @bog-walk in https://github.com/JetBrains/Exposed/pull/2004

# 0.47.0
Infrastructure:
* Joda Time 2.12.6
* Spring Framework 6.1.3
* Foojay Toolchains Plugin 0.8.0
* Java Money Moneta 1.4.4

Breaking changes:
* [Breaking changes](https://jetbrains.github.io/Exposed/breaking-changes.html#0-47-0)

Features:
* feat: Add `ALL` and `ANY` operators accepting array, subquery, or table parameters by @ShreckYe in https://github.com/JetBrains/Exposed/pull/1886
* feat: Add findByIdAndUpdate functions to DAO API by @reidbuzby in https://github.com/JetBrains/Exposed/pull/1773
* In the convenience methods for `join`, make column equality optional by @yeogai in https://github.com/JetBrains/Exposed/pull/1692
* feat: EXPOSED-255 Generate database migration script that can be used with any migration tool by @joc-a in https://github.com/JetBrains/Exposed/pull/1968

Bug fixes:
* fix: EXPOSED-244 [PostgreSQL] Collate option on column not recognized by @bog-walk in https://github.com/JetBrains/Exposed/pull/1956
* Use concat when strings are added by @e5l in https://github.com/JetBrains/Exposed/pull/1958
* fix: EXPOSED-114 Type parameter can't be inferred for EntityID with eq/neq op by @bog-walk in https://github.com/JetBrains/Exposed/pull/1961
* fix: EXPOSED-252 Json contains() throws with iterable as argument by @bog-walk in https://github.com/JetBrains/Exposed/pull/1963
* fix: EXPOSED-257 Upsert incorrectly parameterizes non-literal WHERE arguments by @bog-walk in https://github.com/JetBrains/Exposed/pull/1965
* fix: Allow exec() transform block to return null if ResultSet is empty by @micheljung in https://github.com/JetBrains/Exposed/pull/1696
* fix: EXPOSED-261 [H2] JSON column throws when setting nullable parameters by @bog-walk in https://github.com/JetBrains/Exposed/pull/1969
* fix: Support Short type for BigDecimal conversion functions by @timeking in https://github.com/JetBrains/Exposed/pull/1746
* fix: EXPOSED-260 [Oracle] Pair.inList() fails if list contains single element by @bog-walk in https://github.com/JetBrains/Exposed/pull/1970
* fix: EXPOSED-263 Null arg parameter in exec() throws if logger enabled by @bog-walk in https://github.com/JetBrains/Exposed/pull/1973
* fix: EXPOSED-270 Crash when `Duration.INFINITE` is used for duration column type by @winkey728 in https://github.com/JetBrains/Exposed/pull/1975

Docs:
* docs: [SQL Server, Oracle] Edit KDocs for Op.TRUE/FALSE by @bog-walk in https://github.com/JetBrains/Exposed/pull/1957
* chore: Add Kdocs and update DSL for AllAnyFromBaseOp feature by @bog-walk in https://github.com/JetBrains/Exposed/pull/1960
* docs: Add details about @EnableTransactionManagement to spring-boot-starter docs by @bog-walk in https://github.com/JetBrains/Exposed/pull/1959

# 0.46.0
Infrastructure:
* Kotlinx Datetime JVM 0.5.0
* Joda Time 2.12.5
* Kotlinx Serialization Json 1.6.2
* log4j2 2.22.0
* slf4j 2.0.9
* MariaDB (V3) driver 3.3.1
* PostgreSQL driver 42.7.1
* h2-database (V2) driver 2.2.224
* SQLite driver 3.44.1.0
* Spring Framework 6.1.2
* Spring Boot 3.2.0
* Spring Security Crypto 5.8.8

Breaking changes:
* chore!: EXPOSED-239 Set `preserveKeywordCasing` flag to true by default by @bog-walk in https://github.com/JetBrains/Exposed/pull/1948
* More details at [Breaking changes](https://jetbrains.github.io/Exposed/breaking-changes.html#0-46-0)

Features:
* feat: EXPOSED-65 Design query DSL consistent with SQL language by @bog-walk in https://github.com/JetBrains/Exposed/pull/1916
* More details in the [Migration guide](https://jetbrains.github.io/Exposed/migration-guide.html)

Bug fixes:
* perf: EXPOSED-204 Performance problem with getConnection() by @bog-walk in https://github.com/JetBrains/Exposed/pull/1943
* fix: EXPOSED-242 [PostgreSQL] Cannot change connection setting in middle of a transaction by @bog-walk in https://github.com/JetBrains/Exposed/pull/1949

Build:
* build: Add dependencies to Version Catalog by @pank-su in https://github.com/JetBrains/Exposed/pull/1887

Docs:
* docs: Add KDoc for `databaseGenerated` feature by @joc-a in https://github.com/JetBrains/Exposed/pull/1904
* WRS-3621 Update project configuration by @e5l in https://github.com/JetBrains/Exposed/pull/1911
* docs: Add missing Wiki documentation by @bog-walk in https://github.com/JetBrains/Exposed/pull/1910
* docs: Apply query DSL changes to writerside docs by @bog-walk in https://github.com/JetBrains/Exposed/pull/1926
* docs: Add missing KDocs for exposed-core queries API by @bog-walk in https://github.com/JetBrains/Exposed/pull/1941
* docs: Add missing KDocs for exposed-core database API by @bog-walk in https://github.com/JetBrains/Exposed/pull/1945
* docs: Add missing KDocs for exposed-core table API by @bog-walk in https://github.com/JetBrains/Exposed/pull/1946
* docs: Add MIGRATION_GUIDE by @bog-walk in https://github.com/JetBrains/Exposed/pull/1933

# 0.45.0
Infrastructure:
* Kotlin 1.9.21

Features:
* feat: EXPOSED-220 Support multiple statements returning a result in exec() by @bog-walk in https://github.com/JetBrains/Exposed/pull/1888
* feat: EXPOSED-224 Add query timeout at Transaction by @FullOfOrange in https://github.com/JetBrains/Exposed/pull/1890
* feat: EXPOSED-225 Support transaction timeout in SpringTransactionManager by @FullOfOrange in https://github.com/JetBrains/Exposed/pull/1897

Bug fixes:
* fix: EXPOSED-93 Error when using `with` by @joc-a in https://github.com/JetBrains/Exposed/pull/1891
* fix: EXPOSED-227 Slice() with empty list creates invalid SQL by @bog-walk in https://github.com/JetBrains/Exposed/pull/1899
* fix: EXPOSED-226 Upsert fails with only key columns in update by @bog-walk in https://github.com/JetBrains/Exposed/pull/1900
* fix: Error when using `with` when the child references a parent but not using the parent's id column, but rather another column that is a unique index. by @joc-a in https://github.com/JetBrains/Exposed/pull/1902

Docs:
* docs: Add missing KDocs for exposed-core statements API by @bog-walk in https://github.com/JetBrains/Exposed/pull/1893
* docs: Add missing KDocs for exposed-core vendors API by @bog-walk in https://github.com/JetBrains/Exposed/pull/1892
* docs: Add missing KDocs for exposed-core api package by @bog-walk in https://github.com/JetBrains/Exposed/pull/1896
* docs: Add missing KDocs for exposed-core transactions API by @bog-walk in https://github.com/JetBrains/Exposed/pull/1898

# 0.44.1
Infrastructure:
* MySQL driver 8.0.33

Bug fixes:
* [MySQL] Fix bug when inserted ids are not returned if `rewriteBatchedStatements` property set to true by @Tapac in https://github.com/JetBrains/Exposed/pull/1873
* fix: Incorrect SQL statements when creating a table with a dot in its name by @joc-a in https://github.com/JetBrains/Exposed/pull/1871
* fix: Parsing failure when selecting CURRENT_TIMESTAMP using CurrentDateTime function by @joc-a in https://github.com/JetBrains/Exposed/pull/1879

Deprecations:
* deprecate: Raise deprecation levels of API elements by @bog-walk in https://github.com/JetBrains/Exposed/pull/1872
* deprecate: Raise deprecation level of currentScheme property by @bog-walk in https://github.com/JetBrains/Exposed/pull/1874

Docs:
* Version Notice for JVM by @adambrangenberg in https://github.com/JetBrains/Exposed/pull/1858
* docs: Add missing KDocs in all modules except core and dao by @bog-walk in https://github.com/JetBrains/Exposed/pull/1876
* docs: EXPOSED-199 Update configuration section in spring-boot-starter README by @bog-walk in https://github.com/JetBrains/Exposed/pull/1878

# 0.44.0
Infrastructure:
* Kotlin 1.9.10
* Kotlin Coroutines 1.7.3
* log4j2 2.20.0
* h2-database driver 2.2.220
* MariaDB driver 2.7.9 and 3.1.4
* MySQL driver 8.0.30
* PostgreSQL driver 42.6.0
* SQLite driver 3.43.0.0
* Spring Framework 6.0.11
* Spring Boot 3.1.3

Breaking changes:
* `SpringTransactionManager` no longer extends `DataSourceTransactionManager`; instead, it directly extends `AbstractPlatformTransactionManager`.
  The class also no longer implements the Exposed interface `TransactionManager`, as transaction operations are instead delegated to Spring.
* `spring-transaction` and `exposed-spring-boot-starter` modules now use Spring Framework 6.0 and Spring Boot 3.0, which require Java 17 as a minimum version.
* A table that is created with a keyword identifier now logs a warning that the identifier's case may be lost when it is automatically quoted in generated SQL.
  `DatabaseConfig` now includes the property `preserveKeywordCasing`, which can be set to `true` to remove these warnings and to ensure that the identifier matches the exact case used.
* More details at [Breaking changes](https://jetbrains.github.io/Exposed/breaking-changes.html#0-44-0)

Features:
* feat!: EXPOSED-109 Improve implementation of Spring transaction manager by @FullOfOrange in https://github.com/JetBrains/Exposed/pull/1840
* feat: EXPOSED-78 Support database-generated values for columns by @joc-a in https://github.com/JetBrains/Exposed/pull/1844
* feat: EXPOSED-188 Support Propagation in SpringTransactionManager by @FullOfOrange in https://github.com/JetBrains/Exposed/pull/1867

Bug fixes:
* docs: EXPOSED-159 Add KDocs for EntityClass reference functions by @bog-walk in https://github.com/JetBrains/Exposed/pull/1848
* fix: EXPOSED-158 avoid SQL syntax error of CASE WHEN using nested CASE by @ymotchi in https://github.com/JetBrains/Exposed/pull/1847
* Fix how changes are calculated for non-default schema table by @AlexeySoshin in https://github.com/JetBrains/Exposed/pull/1678
* fix: Fix tables creation depending on each other via foreignKey constraint by @naftalmm in https://github.com/JetBrains/Exposed/pull/1649
* fix: Verbose logging in test module by @Hakky54 in https://github.com/JetBrains/Exposed/pull/1852
* fix: EXPOSED-161 SQL Server syntax incorrectly allows CASCADE with dropSchema by @bog-walk in https://github.com/JetBrains/Exposed/pull/1850
* chore: Reuse Containers in tests; Add Test parameters by @e5l in https://github.com/JetBrains/Exposed/pull/1853
* docs: EXPOSED-124 Add Spring Boot samples by @FullOfOrange in https://github.com/JetBrains/Exposed/pull/1826
* fix: EXPOSED-117 Set jvmToolchain to 8 for all modules by @e5l in https://github.com/JetBrains/Exposed/pull/1855
* chore: Adjust test md files by @joc-a in https://github.com/JetBrains/Exposed/pull/1857
* fix: EXPOSED-162 SQLite generatedKeys exception by @joc-a in https://github.com/JetBrains/Exposed/pull/1854
* fix: Unable to download required toolchain on MAC by @joc-a in https://github.com/JetBrains/Exposed/pull/1859
* fix: EXPOSED-171 Switch from spring.factories to AutoConfiguration.imports by @rbraeunlich in https://github.com/JetBrains/Exposed/pull/1645
* fix: EXPOSED-179 Unsigned column check constraint is not unique to table by @bog-walk in https://github.com/JetBrains/Exposed/pull/1860
* fix: EXPOSED-182 Schema name breaks Create Table with default column in SQLServer by @bog-walk in https://github.com/JetBrains/Exposed/pull/1861
* fix: Exception when using RESTRICT reference option by @joc-a in https://github.com/JetBrains/Exposed/pull/1862
* fix!: EXPOSED-150 Auto-quoted column names change case across databases by @bog-walk in https://github.com/JetBrains/Exposed/pull/1841
* docs: EXPOSED-132 Add annotations to spring-boot-starter README samples by @bog-walk in https://github.com/JetBrains/Exposed/pull/1856
* fix: EXPOSED-173 UPDATE_RULE read incorrectly for Oracle by @joc-a in https://github.com/JetBrains/Exposed/pull/1865
* chore: EXPOSED-186 Replace JDK 1.7 support in exposed-jodatime classes by @bog-walk in https://github.com/JetBrains/Exposed/pull/1866
* fix: EXPOSED-178 DELETE_RULE read incorrectly for Oracle by @joc-a in https://github.com/JetBrains/Exposed/pull/1868

# 0.43.0
Infrastructure:
* Kotlin 1.9.10

Features:
* feat: EXPOSED-85 Add support for changing default value in SQL Server by @joc-a in https://github.com/JetBrains/Exposed/pull/1812

Bug fixes:
* fix: EXPOSED-107 Inaccurate UByte column type mapping by @bog-walk in https://github.com/JetBrains/Exposed/pull/1808
* fix: EXPOSED-108 Incorrect mapping for UInt data type by @bog-walk in https://github.com/JetBrains/Exposed/pull/1809
* fix: Inaccurate drop database statement in Oracle by @bog-walk in https://github.com/JetBrains/Exposed/pull/1807
* test: Fix failing datetime comparison tests in Oracle by @bog-walk in https://github.com/JetBrains/Exposed/pull/1813
* fix: EXPOSED-111 Allow check constraint statements in MySQL8 by @bog-walk in https://github.com/JetBrains/Exposed/pull/1817
* fix: EXPOSED-116 UUID conversion error with upsert in H2 by @bog-walk in https://github.com/JetBrains/Exposed/pull/1823
* fix: EXPOSED-112 SchemaUtils fails to compare default CURRENT_TIMESTAMP by @bog-walk in https://github.com/JetBrains/Exposed/pull/1819
* fix: EXPOSED-123 ExposedBlob.getBytes() fails on Oracle with IOException by @bog-walk in https://github.com/JetBrains/Exposed/pull/1824
* fix: EXPOSED-128 Update with Join and Where clause fails in Oracle by @bog-walk in https://github.com/JetBrains/Exposed/pull/1825
* fix: EXPOSED-135 Oracle does not use setSchema value as currentScheme by @bog-walk in https://github.com/JetBrains/Exposed/pull/1828
* fix: EXPOSED-122 Fix timestampWithTimeZone tests in Oracle by @bog-walk in https://github.com/JetBrains/Exposed/pull/1829
* fix: EXPOSED-137 SET DEFAULT reference option should not be supported in Oracle by @bog-walk in https://github.com/JetBrains/Exposed/pull/1830
* test: Fix failing Oracle tests in exposed-tests by @bog-walk in https://github.com/JetBrains/Exposed/pull/1831
* fix: EXPOSED-127 Default values for JSON columns are not quoted by @bog-walk in https://github.com/JetBrains/Exposed/pull/1827
* fix: EXPOSED-145 Quoted table name breaks CREATE SEQUENCE in Oracle by @bog-walk in https://github.com/JetBrains/Exposed/pull/1836
* fix: EXPOSED-130 Logger throws ClassCastException with JSON and ListSerializer by @bog-walk in https://github.com/JetBrains/Exposed/pull/1835
* fix: EXPOSED-133 Suspend transactions blocking Hikari connection pool by @bog-walk in https://github.com/JetBrains/Exposed/pull/1837
* fix: EXPOSED-151 Quoted identifiers cause incorrect schema validation by @bog-walk in https://github.com/JetBrains/Exposed/pull/1842
* fix: Remove false warning log by @joc-a in https://github.com/JetBrains/Exposed/pull/1843

Breaking changes:
* [Breaking changes](https://jetbrains.github.io/Exposed/breaking-changes.html#0-43-0)

# 0.42.1
Infrastructure:
* Kotlin 1.9.0

Bug fixes:
* fix: exposed-bom module missing when publishing by @joc-a in https://github.com/JetBrains/Exposed/pull/1818

# 0.42.0
Infrastructure:
* Kotlin 1.9.0

Deprecations:
* deprecate: EXPOSED-84 Raise deprecation levels of API elements by @bog-walk in https://github.com/JetBrains/Exposed/pull/1771

Breaking changes:
* [Breaking changes](https://jetbrains.github.io/Exposed/breaking-changes.html#0-42-0)

Features:
* Add CHARINDEX function for sqlserver by @eukleshnin in https://github.com/JetBrains/Exposed/pull/1675
* feat: EXPOSED-32 Support string function CHAR_LENGTH by @bog-walk in https://github.com/JetBrains/Exposed/pull/1737
* feat: EXPOSED-37 Support null-safe equality comparison by @bog-walk in https://github.com/JetBrains/Exposed/pull/1739
* feat: EXPOSED-45 Support single statement UPSERT by @bog-walk in https://github.com/JetBrains/Exposed/pull/1743
* feat: EXPOSED-52 Support batch UPSERT by @bog-walk in https://github.com/JetBrains/Exposed/pull/1749
* feat: EXPOSED-47 Add support for SET DEFAULT reference option by @joc-a in https://github.com/JetBrains/Exposed/pull/1744
* control whether arguments should be inlined or passed in. by @lure in https://github.com/JetBrains/Exposed/pull/1621
* feat: Add partial index support (Postgres only) by @lure in https://github.com/JetBrains/Exposed/pull/1748
* add afterStatementPrepared method to StatementInterceptor by @lure in https://github.com/JetBrains/Exposed/pull/1622
* feat: EXPOSED-60 Support json/json(b) column types by @bog-walk in https://github.com/JetBrains/Exposed/pull/1762
* feat: EXPOSED-66 Extend partial index to SQLServer and SQLite by @bog-walk in https://github.com/JetBrains/Exposed/pull/1763
* [EXPOSED-46] Add a possibility to set a delay for the repetition attempts by @mgrati in https://github.com/JetBrains/Exposed/pull/1742
* feat: EXPOSED-69 Extend json support to H2, Oracle (text) and DAO by @bog-walk in https://github.com/JetBrains/Exposed/pull/1766
* feat: EXPOSED-68 Add more json/json(b) column functions by @bog-walk in https://github.com/JetBrains/Exposed/pull/1770
* #623 Add support of window functions in Exposed DSL by @Legohuman in https://github.com/JetBrains/Exposed/pull/1651
* feat: EXPOSED-89 Support functions in Create Index by @bog-walk in https://github.com/JetBrains/Exposed/pull/1788
* feat: Add spring mutli container support by @FullOfOrange in https://github.com/JetBrains/Exposed/pull/1781
* feat: EXPOSED-43 Add support for timestamp with time zone by @joc-a in https://github.com/JetBrains/Exposed/pull/1787

Bug fixes:
* Fix an error when updating an entity with a foreign key id (issue 880) by @forketyfork in https://github.com/JetBrains/Exposed/pull/1668
* EXPOSED-15 Fix running mysql tests on M1 by @e5l in https://github.com/JetBrains/Exposed/pull/1719
* Fix grammar in error message by @micheljung in https://github.com/JetBrains/Exposed/pull/1717
* Fix: PostgreSQLDialect.modifyColumn is not able to drop default values by @michael-markl in https://github.com/JetBrains/Exposed/pull/1716
* Fix UInt value out of bounds by @keta1 in https://github.com/JetBrains/Exposed/pull/1709
* fix: EXPOSED-16 Failed tests in KotlinTimeTests by @joc-a in https://github.com/JetBrains/Exposed/pull/1724
* EXPOSED-21 Primary key constraint not created by @bog-walk in https://github.com/JetBrains/Exposed/pull/1728
* EXPOSED-19 Max timestamp in SQLite not working by @joc-a in https://github.com/JetBrains/Exposed/pull/1725
* fix: EXPOSED-27 Id is not in record set by @joc-a in https://github.com/JetBrains/Exposed/pull/1731
* fix: EXPOSED-28 Update with join fails on H2 in MySql mode by @bog-walk in https://github.com/JetBrains/Exposed/pull/1732
* fix: EXPOSED-29 Cannot set nullable composite column in InsertStatement by @joc-a in https://github.com/JetBrains/Exposed/pull/1733
* fix: EXPOSED-23 H2 unsupported indexing behavior by @bog-walk in https://github.com/JetBrains/Exposed/pull/1734
* fix: EXPOSED-31 Landing Readme links and demo code by @bog-walk in https://github.com/JetBrains/Exposed/pull/1736
* fix: EXPOSED-36 LocalDate comparison in SQLite by @bog-walk in https://github.com/JetBrains/Exposed/pull/1741
* fix: EXPOSED-42 Can't create BLOB column with default value by @joc-a in https://github.com/JetBrains/Exposed/pull/1740
* fix: EXPOSED-49 Replace statement defined as upsert statement by @bog-walk in https://github.com/JetBrains/Exposed/pull/1747
* fix: EXPOSED-48 Incorrect statistics aggregate functions by @bog-walk in https://github.com/JetBrains/Exposed/pull/1745
* Sum batch results for inserts by @johnzeringue in https://github.com/JetBrains/Exposed/pull/1641
* fix: EXPOSED-57 BatchInsertStatement can't be used with MySQL upsert by @bog-walk in https://github.com/JetBrains/Exposed/pull/1754
* fix: EXPOSED-50 customEnumeration reference column error by @bog-walk in https://github.com/JetBrains/Exposed/pull/1785
* fix: EXPOSED-91 NPE in existingIndices() with function index by @bog-walk in https://github.com/JetBrains/Exposed/pull/1791
* fix: SQLServerException: The port number -1 is not valid. by @joc-a in https://github.com/JetBrains/Exposed/pull/1789
* fix: EXPOSED-80 Set repetition policy for suspended transactions by @bog-walk in https://github.com/JetBrains/Exposed/pull/1774
* fix: Exclude deleted and renamed files from detekt GitHub Action by @joc-a in https://github.com/JetBrains/Exposed/pull/1795
* fix: EXPOSED-97 Unsigned column types truncate MySQL values by @bog-walk in https://github.com/JetBrains/Exposed/pull/1796
* fix: EXPOSED-98: Add instructions to log-in to see and log issues by @jasonjmcghee in https://github.com/JetBrains/Exposed/pull/1798
* fix: EXPOSED-83 createMissingTablesAndColumns not detecting missing PK by @bog-walk in https://github.com/JetBrains/Exposed/pull/1797
* test: Fix failing exposed-tests in SQL Server by @bog-walk in https://github.com/JetBrains/Exposed/pull/1801
* fix: EXPOSED-54 CaseWhen.Else returns narrow Expression<R> by @bog-walk in https://github.com/JetBrains/Exposed/pull/1800
* fix: EXPOSED-99 SchemaUtils incorrectly compares datetime defaults by @bog-walk in https://github.com/JetBrains/Exposed/pull/1802
* test: Fix failing exposed-tests in Oracle by @bog-walk in https://github.com/JetBrains/Exposed/pull/1803
* fix: EXPOSED-82 Inaccurate UShort column type mapping by @bog-walk in https://github.com/JetBrains/Exposed/pull/1799
* test: Fix failing datetime tests in MariaDB by @bog-walk in https://github.com/JetBrains/Exposed/pull/1805

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
    * **Breaking change**: Code using `deleteWhere` with `eq` will need to `import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq`.
      Lambdas previously using an implicit `it` reference from an outer scope will also need to introduce an explicit name to access that binding.
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

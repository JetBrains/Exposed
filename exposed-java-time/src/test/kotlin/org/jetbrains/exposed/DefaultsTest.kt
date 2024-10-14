package org.jetbrains.exposed

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.flushCache
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.*
import org.jetbrains.exposed.sql.statements.BatchDataInconsistentException
import org.jetbrains.exposed.sql.statements.BatchInsertStatement
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.constraintNamePart
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.inProperCase
import org.jetbrains.exposed.sql.tests.insertAndWait
import org.jetbrains.exposed.sql.tests.shared.assertEqualCollections
import org.jetbrains.exposed.sql.tests.shared.assertEqualLists
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.jetbrains.exposed.sql.vendors.OracleDialect
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
import org.jetbrains.exposed.sql.vendors.SQLiteDialect
import org.jetbrains.exposed.sql.vendors.h2Mode
import org.junit.Test
import java.time.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val dbTimestampNow: CustomFunction<OffsetDateTime>
    get() = object : CustomFunction<OffsetDateTime>("now", JavaOffsetDateTimeColumnType()) {}

class DefaultsTest : DatabaseTestsBase() {
    object TableWithDBDefault : IntIdTable() {
        var cIndex = 0
        val field = varchar("field", 100)
        val t1 = datetime("t1").defaultExpression(CurrentDateTime)
        val clientDefault = integer("clientDefault").clientDefault { cIndex++ }
    }

    class DBDefault(id: EntityID<Int>) : IntEntity(id) {
        var field by TableWithDBDefault.field
        var t1 by TableWithDBDefault.t1
        var clientDefault by TableWithDBDefault.clientDefault

        override fun equals(other: Any?): Boolean {
            return (other as? DBDefault)?.let { id == it.id && field == it.field && equalDateTime(t1, it.t1) } ?: false
        }

        override fun hashCode(): Int = id.value.hashCode()

        companion object : IntEntityClass<DBDefault>(TableWithDBDefault)
    }

    @Test
    fun testCanUseClientDefaultOnNullableColumn() {
        val defaultValue: Int? = null
        val table = object : IntIdTable() {
            val clientDefault = integer("clientDefault").nullable().clientDefault { defaultValue }
        }
        val returnedDefault = table.clientDefault.defaultValueFun?.invoke()

        assertTrue(table.clientDefault.columnType.nullable, "Expected clientDefault columnType to be nullable")
        assertNotNull(table.clientDefault.defaultValueFun, "Expected clientDefault column to have a default value fun, but was null")
        assertEquals(defaultValue, returnedDefault, "Expected clientDefault to return $defaultValue, but was $returnedDefault")
    }

    @Test
    fun testCanSetNullableColumnToUseClientDefault() {
        val defaultValue = 123
        val table = object : IntIdTable() {
            val clientDefault = integer("clientDefault").clientDefault { defaultValue }.nullable()
        }
        val returnedDefault = table.clientDefault.defaultValueFun?.invoke()

        assertTrue(table.clientDefault.columnType.nullable, "Expected clientDefault columnType to be nullable")
        assertNotNull(table.clientDefault.defaultValueFun, "Expected clientDefault column to have a default value fun, but was null")
        assertEquals(defaultValue, returnedDefault, "Expected clientDefault to return $defaultValue, but was $returnedDefault")
    }

    @Test
    fun testDefaultsWithExplicit01() {
        withTables(TableWithDBDefault) {
            val created = listOf(
                DBDefault.new { field = "1" },
                DBDefault.new {
                    field = "2"
                    t1 = LocalDateTime.now().minusDays(5)
                }
            )
            commit()
            created.forEach {
                DBDefault.removeFromCache(it)
            }

            val entities = DBDefault.all().toList()
            assertEqualCollections(created.map { it.id }, entities.map { it.id })
        }
    }

    @Test
    fun testDefaultsWithExplicit02() {
        withTables(TableWithDBDefault) {
            val created = listOf(
                DBDefault.new {
                    field = "2"
                    t1 = LocalDateTime.now().minusDays(5)
                },
                DBDefault.new { field = "1" }
            )

            flushCache()
            created.forEach {
                DBDefault.removeFromCache(it)
            }
            val entities = DBDefault.all().toList()
            assertEqualCollections(created, entities)
        }
    }

    @Test
    fun testDefaultsInvokedOnlyOncePerEntity() {
        withTables(TableWithDBDefault) {
            TableWithDBDefault.cIndex = 0
            val db1 = DBDefault.new { field = "1" }
            val db2 = DBDefault.new { field = "2" }
            flushCache()
            assertEquals(0, db1.clientDefault)
            assertEquals(1, db2.clientDefault)
            assertEquals(2, TableWithDBDefault.cIndex)
        }
    }

    @Test
    fun testDefaultsCanBeOverridden() {
        withTables(TableWithDBDefault) {
            TableWithDBDefault.cIndex = 0
            val db1 = DBDefault.new { field = "1" }
            val db2 = DBDefault.new { field = "2" }
            db1.clientDefault = 12345
            flushCache()
            assertEquals(12345, db1.clientDefault)
            assertEquals(1, db2.clientDefault)
            assertEquals(2, TableWithDBDefault.cIndex)

            flushCache()
            assertEquals(12345, db1.clientDefault)
        }
    }

    private val initBatch = listOf<(BatchInsertStatement) -> Unit>(
        {
            it[TableWithDBDefault.field] = "1"
        },
        {
            it[TableWithDBDefault.field] = "2"
            it[TableWithDBDefault.t1] = LocalDateTime.now()
        }
    )

    @Test
    fun testRawBatchInsertFails01() {
        withTables(TableWithDBDefault) {
            expectException<BatchDataInconsistentException> {
                BatchInsertStatement(TableWithDBDefault).run {
                    initBatch.forEach {
                        addBatch()
                        it(this)
                    }
                }
            }
        }
    }

    @Test
    fun testBatchInsertNotFails01() {
        withTables(TableWithDBDefault) {
            TableWithDBDefault.batchInsert(initBatch) { foo ->
                foo(this)
            }
        }
    }

    @Test
    fun testBatchInsertFails01() {
        withTables(TableWithDBDefault) {
            expectException<BatchDataInconsistentException> {
                TableWithDBDefault.batchInsert(listOf(1)) {
                    this[TableWithDBDefault.t1] = LocalDateTime.now()
                }
            }
        }
    }

    @Test
    fun testDefaults01() {
        val currentDT = CurrentDateTime
        val nowExpression = object : Expression<LocalDateTime>() {
            override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
                +when (val dialect = currentDialectTest) {
                    is OracleDialect -> "SYSDATE"
                    is SQLServerDialect -> "GETDATE()"
                    is MysqlDialect -> if (dialect.isFractionDateTimeSupported()) "NOW(6)" else "NOW()"
                    is SQLiteDialect -> "CURRENT_TIMESTAMP"
                    else -> "NOW()"
                }
            }
        }
        val dtConstValue = LocalDate.of(2010, 1, 1)
        val dLiteral = dateLiteral(dtConstValue)
        val dtLiteral = dateTimeLiteral(dtConstValue.atStartOfDay())
        val tsConstValue = dtConstValue.atStartOfDay(ZoneOffset.UTC).plusSeconds(42).toInstant()
        val tsLiteral = timestampLiteral(tsConstValue)
        val durConstValue = Duration.between(Instant.EPOCH, tsConstValue)
        val durLiteral = durationLiteral(durConstValue)
        val tmConstValue = LocalTime.of(12, 0)
        val tLiteral = timeLiteral(tmConstValue)

        val testTable = object : IntIdTable("t") {
            val s = varchar("s", 100).default("test")
            val sn = varchar("sn", 100).default("testNullable").nullable()
            val l = long("l").default(42)
            val c = char("c").default('X')
            val t1 = datetime("t1").defaultExpression(currentDT)
            val t2 = datetime("t2").defaultExpression(nowExpression)
            val t3 = datetime("t3").defaultExpression(dtLiteral)
            val t4 = date("t4").default(dtConstValue)
            val t5 = timestamp("t5").default(tsConstValue)
            val t6 = timestamp("t6").defaultExpression(tsLiteral)
            val t7 = duration("t7").default(durConstValue)
            val t8 = duration("t8").defaultExpression(durLiteral)
            val t9 = time("t9").default(tmConstValue)
            val t10 = time("t10").defaultExpression(tLiteral)
        }

        fun Expression<*>.itOrNull() = when {
            currentDialectTest.isAllowedAsColumnDefault(this) ->
                "DEFAULT ${currentDialectTest.dataTypeProvider.processForDefaultValue(this)} NOT NULL"
            else -> "NULL"
        }

        withTables(testTable) { testDb ->
            val dtType = currentDialectTest.dataTypeProvider.dateTimeType()
            val dType = currentDialectTest.dataTypeProvider.dateType()
            val longType = currentDialectTest.dataTypeProvider.longType()
            val timeType = currentDialectTest.dataTypeProvider.timeType()
            val varcharType = currentDialectTest.dataTypeProvider.varcharType(100)
            val q = db.identifierManager.quoteString
            val baseExpression = "CREATE TABLE " + addIfNotExistsIfSupported() +
                "${"t".inProperCase()} (" +
                "${"id".inProperCase()} ${currentDialectTest.dataTypeProvider.integerAutoincType()}${
                    testDb.takeIf { it != TestDB.SQLITE }?.let { " PRIMARY KEY" } ?: ""
                }, " +
                "${"s".inProperCase()} $varcharType${testTable.s.constraintNamePart()} DEFAULT 'test' NOT NULL, " +
                "${"sn".inProperCase()} $varcharType${testTable.sn.constraintNamePart()} DEFAULT 'testNullable' NULL, " +
                "${"l".inProperCase()} ${currentDialectTest.dataTypeProvider.longType()}${testTable.l.constraintNamePart()} DEFAULT 42 NOT NULL, " +
                "$q${"c".inProperCase()}$q CHAR${testTable.c.constraintNamePart()} DEFAULT 'X' NOT NULL, " +
                "${"t1".inProperCase()} $dtType${testTable.t1.constraintNamePart()} ${currentDT.itOrNull()}, " +
                "${"t2".inProperCase()} $dtType${testTable.t2.constraintNamePart()} ${nowExpression.itOrNull()}, " +
                "${"t3".inProperCase()} $dtType${testTable.t3.constraintNamePart()} ${dtLiteral.itOrNull()}, " +
                "${"t4".inProperCase()} $dType${testTable.t4.constraintNamePart()} ${dLiteral.itOrNull()}, " +
                "${"t5".inProperCase()} $dtType${testTable.t5.constraintNamePart()} ${tsLiteral.itOrNull()}, " +
                "${"t6".inProperCase()} $dtType${testTable.t6.constraintNamePart()} ${tsLiteral.itOrNull()}, " +
                "${"t7".inProperCase()} $longType${testTable.t7.constraintNamePart()} ${durLiteral.itOrNull()}, " +
                "${"t8".inProperCase()} $longType${testTable.t8.constraintNamePart()} ${durLiteral.itOrNull()}, " +
                "${"t9".inProperCase()} $timeType${testTable.t9.constraintNamePart()} ${tLiteral.itOrNull()}, " +
                "${"t10".inProperCase()} $timeType${testTable.t10.constraintNamePart()} ${tLiteral.itOrNull()}" +
                when (testDb) {
                    TestDB.SQLITE ->
                        ", CONSTRAINT chk_t_signed_integer_id CHECK (${"id".inProperCase()} BETWEEN ${Int.MIN_VALUE} AND ${Int.MAX_VALUE})" +
                            ", CONSTRAINT chk_t_signed_long_l CHECK (typeof(l) = 'integer')"
                    TestDB.ORACLE ->
                        ", CONSTRAINT chk_t_signed_integer_id CHECK (${"id".inProperCase()} BETWEEN ${Int.MIN_VALUE} AND ${Int.MAX_VALUE})" +
                            ", CONSTRAINT chk_t_signed_long_l CHECK (L BETWEEN ${Long.MIN_VALUE} AND ${Long.MAX_VALUE})"
                    else -> ""
                } +
                ")"

            val expected = if (currentDialectTest is OracleDialect || currentDialectTest.h2Mode == H2Dialect.H2CompatibilityMode.Oracle) {
                arrayListOf("CREATE SEQUENCE t_id_seq START WITH 1 MINVALUE 1 MAXVALUE 9223372036854775807", baseExpression)
            } else {
                arrayListOf(baseExpression)
            }

            assertEqualLists(expected, testTable.ddl)

            val id1 = testTable.insertAndGetId { }

            val row1 = testTable.selectAll().where { testTable.id eq id1 }.single()
            assertEquals("test", row1[testTable.s])
            assertEquals("testNullable", row1[testTable.sn])
            assertEquals(42, row1[testTable.l])
            assertEquals('X', row1[testTable.c])
            assertEqualDateTime(dtConstValue.atStartOfDay(), row1[testTable.t3])
            assertEqualDateTime(dtConstValue, row1[testTable.t4])
            assertEqualDateTime(tsConstValue, row1[testTable.t5])
            assertEqualDateTime(tsConstValue, row1[testTable.t6])
            assertEquals(durConstValue, row1[testTable.t7])
            assertEquals(durConstValue, row1[testTable.t8])
            assertEquals(tmConstValue, row1[testTable.t9])
            assertEquals(tmConstValue, row1[testTable.t10])
        }
    }

    @Test
    fun testDefaultExpressions01() {
        fun abs(value: Int) = object : ExpressionWithColumnType<Int>() {
            override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append("ABS($value)") }

            override val columnType: IColumnType<Int> = IntegerColumnType()
        }

        val foo = object : IntIdTable("foo") {
            val name = text("name")
            val defaultDateTime = datetime("defaultDateTime").defaultExpression(CurrentDateTime)
            val defaultDate = date("defaultDate").defaultExpression(CurrentDate)
            val defaultInt = integer("defaultInteger").defaultExpression(abs(-100))
        }

        withTables(foo) {
            val id = foo.insertAndGetId {
                it[foo.name] = "bar"
            }
            val result = foo.selectAll().where { foo.id eq id }.single()

            assertEquals(today, result[foo.defaultDateTime].toLocalDate())
            assertEquals(today, result[foo.defaultDate])
            assertEquals(100, result[foo.defaultInt])
        }
    }

    @Test
    fun testDefaultExpressions02() {
        val foo = object : IntIdTable("foo") {
            val name = text("name")
            val defaultDateTime = datetime("defaultDateTime").defaultExpression(CurrentDateTime)
        }

        val nonDefaultDate = LocalDate.of(2000, 1, 1).atStartOfDay()

        withTables(foo) {
            val id = foo.insertAndGetId {
                it[foo.name] = "bar"
                it[foo.defaultDateTime] = nonDefaultDate
            }

            val result = foo.selectAll().where { foo.id eq id }.single()

            assertEquals("bar", result[foo.name])
            assertEqualDateTime(nonDefaultDate, result[foo.defaultDateTime])

            foo.update({ foo.id eq id }) {
                it[foo.name] = "baz"
            }

            val result2 = foo.selectAll().where { foo.id eq id }.single()
            assertEquals("baz", result2[foo.name])
            assertEqualDateTime(nonDefaultDate, result2[foo.defaultDateTime])
        }
    }

    @Test
    fun testBetweenFunction() {
        val foo = object : IntIdTable("foo") {
            val dt = datetime("dateTime")
        }

        withTables(foo) {
            val dt2020 = LocalDateTime.of(2020, 1, 1, 1, 1)
            foo.insert { it[dt] = LocalDateTime.of(2019, 1, 1, 1, 1) }
            foo.insert { it[dt] = dt2020 }
            foo.insert { it[dt] = LocalDateTime.of(2021, 1, 1, 1, 1) }
            val count = foo.selectAll().where { foo.dt.between(dt2020.minusWeeks(1), dt2020.plusWeeks(1)) }.count()
            assertEquals(1, count)
        }
    }

    @Test
    fun testConsistentSchemeWithFunctionAsDefaultExpression() {
        val foo = object : IntIdTable("foo") {
            val name = text("name")
            val defaultDate = date("default_date").defaultExpression(CurrentDate)
            val defaultDateTime = datetime("default_date_time").defaultExpression(CurrentDateTime)
            val defaultTimeStamp = timestamp("default_time_stamp").defaultExpression(CurrentTimestamp)
        }

        withDb {
            try {
                SchemaUtils.create(foo)

                val actual = SchemaUtils.statementsRequiredToActualizeScheme(foo)

                assertTrue(actual.isEmpty())
            } finally {
                SchemaUtils.drop(foo)
            }
        }
    }

    @Test
    fun testTimestampWithTimeZoneDefaults() {
        // UTC time zone
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(ZoneOffset.UTC))
        assertEquals("UTC", ZoneId.systemDefault().id)

        val nowWithTimeZone = OffsetDateTime.parse("2024-07-18T13:19:44.000+00:00")
        val timestampWithTimeZoneLiteral = timestampWithTimeZoneLiteral(nowWithTimeZone)

        val testTable = object : IntIdTable("t") {
            val t1 = timestampWithTimeZone("t1").default(nowWithTimeZone)
            val t2 = timestampWithTimeZone("t2").defaultExpression(timestampWithTimeZoneLiteral)
            val t3 = timestampWithTimeZone("t3").defaultExpression(CurrentTimestampWithTimeZone)
        }

        fun Expression<*>.itOrNull() = when {
            currentDialectTest.isAllowedAsColumnDefault(this) ->
                "DEFAULT ${currentDialectTest.dataTypeProvider.processForDefaultValue(this)} NOT NULL"
            else -> "NULL"
        }

        withTables(excludeSettings = TestDB.ALL_MARIADB + TestDB.MYSQL_V5, testTable) { testDb ->
            val timestampWithTimeZoneType = currentDialectTest.dataTypeProvider.timestampWithTimeZoneType()

            val baseExpression = "CREATE TABLE " + addIfNotExistsIfSupported() +
                "${"t".inProperCase()} (" +
                "${"id".inProperCase()} ${currentDialectTest.dataTypeProvider.integerAutoincType()}${
                    testDb.takeIf { it != TestDB.SQLITE }?.let { " PRIMARY KEY" } ?: ""
                }, " +
                "${"t1".inProperCase()} $timestampWithTimeZoneType${testTable.t1.constraintNamePart()} ${timestampWithTimeZoneLiteral.itOrNull()}, " +
                "${"t2".inProperCase()} $timestampWithTimeZoneType${testTable.t2.constraintNamePart()} ${timestampWithTimeZoneLiteral.itOrNull()}, " +
                "${"t3".inProperCase()} $timestampWithTimeZoneType${testTable.t3.constraintNamePart()} ${CurrentTimestampWithTimeZone.itOrNull()}" +
                when (testDb) {
                    TestDB.SQLITE, TestDB.ORACLE ->
                        ", CONSTRAINT chk_t_signed_integer_id CHECK (${"id".inProperCase()} BETWEEN ${Int.MIN_VALUE} AND ${Int.MAX_VALUE})"
                    else -> ""
                } +
                ")"

            val expected = if (currentDialectTest is OracleDialect ||
                currentDialectTest.h2Mode == H2Dialect.H2CompatibilityMode.Oracle
            ) {
                arrayListOf(
                    "CREATE SEQUENCE t_id_seq START WITH 1 MINVALUE 1 MAXVALUE 9223372036854775807",
                    baseExpression
                )
            } else {
                arrayListOf(baseExpression)
            }

            assertEqualLists(expected, testTable.ddl)

            val id1 = testTable.insertAndGetId { }

            val row1 = testTable.selectAll().where { testTable.id eq id1 }.single()
            assertEquals(nowWithTimeZone, row1[testTable.t1])
            assertEquals(nowWithTimeZone, row1[testTable.t2])
            val dbDefault = row1[testTable.t3]
            assertEquals(dbDefault.offset, nowWithTimeZone.offset)
            assertTrue { dbDefault.toLocalDateTime() >= nowWithTimeZone.toLocalDateTime() }
        }
    }

    @Test
    fun testDefaultCurrentDateTime() {
        val testDate = object : IntIdTable("TestDate") {
            val time = datetime("time").defaultExpression(CurrentDateTime)
        }

        fun LocalDateTime.millis(): Long = this.toEpochSecond(ZoneOffset.UTC) * 1000

        withTables(testDate) {
            val duration: Long = 2000

            repeat(2) {
                testDate.insertAndWait(duration)
            }

            Thread.sleep(duration)

            repeat(2) {
                testDate.insertAndWait(duration)
            }

            val sortedEntries: List<LocalDateTime> = testDate.selectAll().map { it[testDate.time] }.sorted()

            assertTrue(sortedEntries[1].millis() - sortedEntries[0].millis() >= 2000)
            assertTrue(sortedEntries[2].millis() - sortedEntries[0].millis() >= 6000)
            assertTrue(sortedEntries[3].millis() - sortedEntries[0].millis() >= 8000)
        }
    }

    @Test
    fun testDateDefaultDoesNotTriggerAlterStatement() {
        val date = LocalDate.of(2024, 2, 1)

        val tester = object : Table("tester") {
            val dateWithDefault = date("dateWithDefault").default(date)
        }

        // SQLite does not support ALTER TABLE on a column that has a default value
        withTables(excludeSettings = listOf(TestDB.SQLITE), tester) {
            val statements = SchemaUtils.addMissingColumnsStatements(tester)
            assertEquals(0, statements.size)
        }
    }

    @Test
    fun testTimestampDefaultDoesNotTriggerAlterStatement() {
        val instant = Instant.parse("2023-05-04T05:04:00.700Z") // In UTC

        val tester = object : Table("tester") {
            val timestampWithDefault = timestamp("timestampWithDefault").default(instant)
            val timestampWithDefaultExpression = timestamp("timestampWithDefaultExpression").defaultExpression(CurrentTimestamp)
        }

        // SQLite does not support ALTER TABLE on a column that has a default value
        withTables(excludeSettings = listOf(TestDB.SQLITE), tester) {
            val statements = SchemaUtils.addMissingColumnsStatements(tester)
            assertEquals(0, statements.size)
        }
    }

    @Test
    fun testDatetimeDefaultDoesNotTriggerAlterStatement() {
        val datetime = LocalDateTime.parse("2023-05-04T05:04:07.000")

        val tester = object : Table("tester") {
            val datetimeWithDefault = datetime("datetimeWithDefault").default(datetime)
            val datetimeWithDefaultExpression = datetime("datetimeWithDefaultExpression").defaultExpression(CurrentDateTime)
        }

        // SQLite does not support ALTER TABLE on a column that has a default value
        withTables(excludeSettings = listOf(TestDB.SQLITE), tester) {
            val statements = SchemaUtils.addMissingColumnsStatements(tester)
            assertEquals(0, statements.size)
        }
    }

    @Test
    fun testTimeDefaultDoesNotTriggerAlterStatement() {
        val time = LocalDateTime.now(ZoneId.of("Japan")).toLocalTime()

        val tester = object : Table("tester") {
            val timeWithDefault = time("timeWithDefault").default(time)
        }

        // SQLite does not support ALTER TABLE on a column that has a default value
        withTables(excludeSettings = listOf(TestDB.SQLITE), tester) {
            val statements = SchemaUtils.addMissingColumnsStatements(tester)
            assertEquals(0, statements.size)
        }
    }

    @Test
    fun testTimestampWithTimeZoneDefaultDoesNotTriggerAlterStatement() {
        val offsetDateTime = OffsetDateTime.parse("2024-02-08T20:48:04.700+09:00")

        val tester = object : Table("tester") {
            val timestampWithTimeZoneWithDefault = timestampWithTimeZone("timestampWithTimeZoneWithDefault").default(offsetDateTime)
        }

        // SQLite does not support ALTER TABLE on a column that has a default value
        // MariaDB does not support TIMESTAMP WITH TIME ZONE column type
        val unsupportedDatabases = TestDB.ALL_MARIADB + listOf(TestDB.SQLITE, TestDB.MYSQL_V5)
        withTables(excludeSettings = unsupportedDatabases, tester) {
            val statements = SchemaUtils.addMissingColumnsStatements(tester)
            assertEquals(0, statements.size)
        }
    }

    object DefaultTimestampTable : IntIdTable("test_table") {
        val timestamp: Column<OffsetDateTime> =
            timestampWithTimeZone("timestamp").defaultExpression(dbTimestampNow)
    }

    class DefaultTimestampEntity(id: EntityID<Int>) : Entity<Int>(id) {
        companion object : EntityClass<Int, DefaultTimestampEntity>(DefaultTimestampTable)

        var timestamp: OffsetDateTime by DefaultTimestampTable.timestamp
    }

    @Test
    fun testCustomDefaultTimestampFunctionWithEntity() {
        withTables(excludeSettings = TestDB.ALL - TestDB.ALL_POSTGRES - TestDB.MYSQL_V8 - TestDB.ALL_H2, DefaultTimestampTable) {
            val entity = DefaultTimestampEntity.new {}

            val timestamp = DefaultTimestampTable.selectAll().first()[DefaultTimestampTable.timestamp]

            assertEquals(timestamp, entity.timestamp)
        }
    }

    @Test
    fun testCustomDefaultTimestampFunctionWithInsertStatement() {
        // Only Postgres allows to get timestamp values directly from the insert statement due to implicit 'returning *'
        withTables(excludeSettings = TestDB.ALL - TestDB.ALL_POSTGRES, DefaultTimestampTable) {
            val entity = DefaultTimestampTable.insert { }

            val timestamp = DefaultTimestampTable.selectAll().first()[DefaultTimestampTable.timestamp]

            assertEquals(timestamp, entity[DefaultTimestampTable.timestamp])
        }
    }
}

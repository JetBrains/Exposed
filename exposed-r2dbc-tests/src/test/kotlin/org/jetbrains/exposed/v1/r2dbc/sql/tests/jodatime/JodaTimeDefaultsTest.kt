package org.jetbrains.exposed.v1.r2dbc.sql.tests.jodatime

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.exposed.v1.dao.id.IntIdTable
import org.jetbrains.exposed.v1.r2dbc.sql.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.sql.batchInsert
import org.jetbrains.exposed.v1.r2dbc.sql.insert
import org.jetbrains.exposed.v1.r2dbc.sql.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.sql.selectAll
import org.jetbrains.exposed.v1.r2dbc.sql.tests.*
import org.jetbrains.exposed.v1.r2dbc.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.sql.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.sql.tests.inProperCase
import org.jetbrains.exposed.v1.r2dbc.sql.tests.insertAndWait
import org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.assertEqualLists
import org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.assertTrue
import org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.expectException
import org.jetbrains.exposed.v1.r2dbc.sql.update
import org.jetbrains.exposed.v1.sql.*
import org.jetbrains.exposed.v1.sql.jodatime.*
import org.jetbrains.exposed.v1.sql.statements.BatchDataInconsistentException
import org.jetbrains.exposed.v1.sql.statements.BatchInsertStatement
import org.jetbrains.exposed.v1.sql.vendors.*
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.LocalTime
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val dbTimestampNow: CustomFunction<DateTime>
    get() = object : CustomFunction<DateTime>("now", DateTimeWithTimeZoneColumnType()) {}

class JodaTimeDefaultsTest : R2dbcDatabaseTestsBase() {
    object TableWithDBDefault : IntIdTable() {
        var cIndex = 0
        val field = varchar("field", 100)
        val t1 = datetime("t1").defaultExpression(CurrentDateTime)
        val t2 = date("t2").defaultExpression(CurrentDate)
        val clientDefault = integer("clientDefault").clientDefault { cIndex++ }
    }

    private val initBatch = listOf<(BatchInsertStatement) -> Unit>(
        {
            it[TableWithDBDefault.field] = "1"
        },
        {
            it[TableWithDBDefault.field] = "2"
            it[TableWithDBDefault.t1] = DateTime.now()
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
    fun testRawBatchInsertFails02() {
        withTables(TableWithDBDefault) {
            TableWithDBDefault.batchInsert(initBatch) { foo ->
                foo(this)
            }
        }
    }

    @Test
    fun testDefaults01() {
        val currentDT = CurrentDateTime
        val nowExpression = object : Expression<DateTime>() {
            override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
                +when (val dialect = currentDialect) {
                    is OracleDialect -> "SYSDATE"
                    is SQLServerDialect -> "GETDATE()"
                    is MysqlDialect -> if (dialect.isFractionDateTimeSupported()) "NOW(6)" else "NOW()"
                    is SQLiteDialect -> "CURRENT_TIMESTAMP"
                    else -> "NOW()"
                }
            }
        }
        val dateConstValue = DateTime.parse("2010-01-01").withZone(DateTimeZone.UTC)
        val instConstValue = dateConstValue.withTimeAtStartOfDay()
        val dateTimeConstValue = instConstValue.toLocalDateTime().toDateTime(DateTimeZone.UTC)
        val dLiteral = dateLiteral(dateConstValue)
        val dtLiteral = dateTimeLiteral(dateTimeConstValue)
        val tmConstValue = LocalTime(12, 0)
        val tLiteral = timeLiteral(tmConstValue)

        val testTable = object : IntIdTable("t") {
            val s = varchar("s", 100).default("test")
            val sn = varchar("sn", 100).default("testNullable").nullable()
            val l = long("l").default(42)
            val c = char("c").default('X')
            val t1 = datetime("t1").defaultExpression(currentDT)
            val t2 = datetime("t2").defaultExpression(nowExpression)
            val t3 = datetime("t3").defaultExpression(dtLiteral)
            val t4 = date("t4").default(dateConstValue)
            val t5 = time("t5").default(tmConstValue)
            val t6 = time("t6").defaultExpression(tLiteral)
        }

        fun Expression<*>.itOrNull() = when {
            currentDialectTest.isAllowedAsColumnDefault(this) ->
                "DEFAULT ${currentDialectTest.dataTypeProvider.processForDefaultValue(this)} NOT NULL"
            else -> "NULL"
        }

        withTables(testTable) { testDb ->
            val dtType = currentDialectTest.dataTypeProvider.dateTimeType()
            val dType = currentDialectTest.dataTypeProvider.dateType()
            val timeType = currentDialectTest.dataTypeProvider.timeType()
            val varCharType = currentDialectTest.dataTypeProvider.varcharType(100)
            val q = db.identifierManager.quoteString
            val baseExpression = "CREATE TABLE " + addIfNotExistsIfSupported() +
                "${"t".inProperCase()} (" +
                "${"id".inProperCase()} ${currentDialectTest.dataTypeProvider.integerAutoincType()}${
                    testDb?.let { " PRIMARY KEY" } ?: ""
                }, " +
                "${"s".inProperCase()} $varCharType${testTable.s.constraintNamePart()} DEFAULT 'test' NOT NULL, " +
                "${"sn".inProperCase()} $varCharType${testTable.sn.constraintNamePart()} DEFAULT 'testNullable' NULL, " +
                "${"l".inProperCase()} ${currentDialectTest.dataTypeProvider.longType()}${testTable.l.constraintNamePart()} DEFAULT 42 NOT NULL, " +
                "$q${"c".inProperCase()}$q CHAR${testTable.c.constraintNamePart()} DEFAULT 'X' NOT NULL, " +
                "${"t1".inProperCase()} $dtType${testTable.t1.constraintNamePart()} ${currentDT.itOrNull()}, " +
                "${"t2".inProperCase()} $dtType${testTable.t2.constraintNamePart()} ${nowExpression.itOrNull()}, " +
                "${"t3".inProperCase()} $dtType${testTable.t3.constraintNamePart()} ${dtLiteral.itOrNull()}, " +
                "${"t4".inProperCase()} $dType${testTable.t4.constraintNamePart()} ${dLiteral.itOrNull()}, " +
                "${"t5".inProperCase()} $timeType${testTable.t5.constraintNamePart()} ${tLiteral.itOrNull()}, " +
                "${"t6".inProperCase()} $timeType${testTable.t6.constraintNamePart()} ${tLiteral.itOrNull()}" +
                when (testDb) {
                    TestDB.ORACLE ->
                        ", CONSTRAINT chk_t_signed_integer_id CHECK (${"id".inProperCase()} BETWEEN ${Int.MIN_VALUE} AND ${Int.MAX_VALUE})" +
                            ", CONSTRAINT chk_t_signed_long_l CHECK (L BETWEEN ${Long.MIN_VALUE} AND ${Long.MAX_VALUE})"
                    else -> ""
                } +
                ")"

            val expected = if (currentDialectTest is OracleDialect || currentDialectTest.h2Mode == H2Dialect.H2CompatibilityMode.Oracle) {
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
            assertEquals("test", row1[testTable.s])
            assertEquals("testNullable", row1[testTable.sn])
            assertEquals(42, row1[testTable.l])
            assertEquals('X', row1[testTable.c])
            assertEquals(dateTimeConstValue, row1[testTable.t3])
            assertEquals(dateConstValue, row1[testTable.t4])
            assertEquals(tmConstValue, row1[testTable.t5])
            assertEquals(tmConstValue, row1[testTable.t6])
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

        // MySql 5 is excluded because it does not support `CURRENT_DATE()` as a default value
        withTables(excludeSettings = listOf(TestDB.MYSQL_V5), foo) {
            val id = foo.insertAndGetId {
                it[foo.name] = "bar"
            }
            val result = foo.selectAll().where { foo.id eq id }.single()

            assertEquals(today, result[foo.defaultDateTime].withTimeAtStartOfDay())
            assertEquals(today, result[foo.defaultDate])
            assertEquals(100, result[foo.defaultInt])
        }
    }

    @Test
    fun testDefaultExpressions02() {
        val foo = object : IntIdTable("foo") {
            val name = text("name")
            val defaultDateTime = datetime("defaultDateTime").defaultExpression(CurrentDateTime)
            val defaultDate = date("defaultDate").defaultExpression(CurrentDate)
        }

        val nonDefaultDate = DateTime.parse("2000-01-01")

        withTables(foo) {
            val id = foo.insertAndGetId {
                it[foo.name] = "bar"
                it[foo.defaultDateTime] = nonDefaultDate
                it[foo.defaultDate] = nonDefaultDate
            }

            val result = foo.selectAll().where { foo.id eq id }.single()

            assertEquals("bar", result[foo.name])
            assertEqualDateTime(nonDefaultDate, result[foo.defaultDateTime])
            assertEqualDateTime(nonDefaultDate, result[foo.defaultDate])

            foo.update({ foo.id eq id }) {
                it[foo.name] = "baz"
            }

            val result2 = foo.selectAll().where { foo.id eq id }.single()
            assertEquals("baz", result2[foo.name])
            assertEqualDateTime(nonDefaultDate, result2[foo.defaultDateTime])
            assertEqualDateTime(nonDefaultDate, result[foo.defaultDate])
        }
    }

    @Test
    fun testDefaultCurrentDateTime() {
        val testDate = object : IntIdTable("TestDate") {
            val time = datetime("time").defaultExpression(CurrentDateTime)
        }

        withTables(testDate) { testDb ->
            val duration: Long = 2000

            // insert only default values
            testDate.insertAndWait(duration)

            // an epsilon value for SQL Server, which has been flaky with average results +/- 10 compared to expected
            if (testDb == TestDB.SQLSERVER) Thread.sleep(1000L)

            repeat(2) {
                testDate.insertAndWait(duration)
            }

            val sortedEntries = testDate.selectAll().map { it[testDate.time] }.sorted()

            assertTrue(sortedEntries[1].millis - sortedEntries[0].millis >= 2000)
            assertTrue(sortedEntries[2].millis - sortedEntries[0].millis >= 4000)
        }
    }

    @Test
    fun `test No transaction in context when accessing datetime field outside the transaction`() {
        val testData = object : IntIdTable("TestData") {
            val name = varchar("name", length = 50)
            val dateTime = datetime("date-time")
        }

        val date = DateTime.now()
        var list1: ResultRow? = null
        withTables(testData) {
            testData.insert {
                it[name] = "test1"
                it[dateTime] = date
            }

            list1 = assertNotNull(testData.selectAll().singleOrNull())
            assertEquals("test1", list1?.get(testData.name))
            assertEquals(date.millis, list1?.get(testData.dateTime)?.millis)
        }
        assertEquals("test1", list1?.get(testData.name))
        assertEquals(date.millis, list1?.get(testData.dateTime)?.millis)
    }

    @Test
    fun testTimestampWithTimeZoneDefaults() {
        // UTC time zone
        DateTimeZone.setDefault(DateTimeZone.UTC)
        assertEquals("UTC", DateTimeZone.getDefault().id)

        val nowWithTimeZone = DateTime.parse("2024-07-18T13:19:44.000").withZone(DateTimeZone.UTC)
        val timestampWithTimeZoneLiteral = timestampWithTimeZoneLiteral(nowWithTimeZone)

        val testTable = object : IntIdTable("t") {
            val t1 = timestampWithTimeZone("t1").default(nowWithTimeZone)
            val t2 = timestampWithTimeZone("t2").defaultExpression(timestampWithTimeZoneLiteral)
            val t3 = timestampWithTimeZone("t3").defaultExpression(CurrentDateTime)
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
                    testDb?.let { " PRIMARY KEY" } ?: ""
                }, " +
                "${"t1".inProperCase()} $timestampWithTimeZoneType${testTable.t1.constraintNamePart()} ${timestampWithTimeZoneLiteral.itOrNull()}, " +
                "${"t2".inProperCase()} $timestampWithTimeZoneType${testTable.t2.constraintNamePart()} ${timestampWithTimeZoneLiteral.itOrNull()}, " +
                "${"t3".inProperCase()} $timestampWithTimeZoneType${testTable.t3.constraintNamePart()} ${CurrentDateTime.itOrNull()}" +
                when (testDb) {
                    TestDB.ORACLE ->
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
            assertTrue { row1[testTable.t3].millis >= nowWithTimeZone.millis }
        }
    }

    @Test
    fun testConsistentSchemeWithFunctionAsDefaultExpression() {
        val foo = object : IntIdTable("foo") {
            val name = text("name")
            val defaultDate = date("default_date").defaultExpression(CurrentDate)
            val defaultDateTime = datetime("defaultDateTime").defaultExpression(CurrentDateTime)
        }

        withTables(foo) {
            val actual = SchemaUtils.statementsRequiredToActualizeScheme(foo)

            assertTrue(actual.isEmpty())
        }
    }

    @Test
    fun testDatetimeDefaultDoesNotTriggerAlterStatement() {
        val datetime = DateTime.parse("2023-05-04T05:04:07.000").withZone(DateTimeZone.forID("Japan"))

        val tester = object : Table("tester") {
            val datetimeWithDefault = datetime("datetimeWithDefault").default(datetime)
            val datetimeWithDefaultExpression = datetime("datetimeWithDefaultExpression").defaultExpression(CurrentDateTime)
        }

        withTables(tester) {
            val statements = SchemaUtils.addMissingColumnsStatements(tester)
            assertEquals(0, statements.size)
        }
    }

    @Test
    fun testDateDefaultDoesNotTriggerAlterStatement() {
        val date = DateTime.now(DateTimeZone.forID("Japan"))

        val tester = object : Table("tester") {
            val dateWithDefault = date("dateWithDefault").default(date)
        }

        withTables(tester) {
            val statements = SchemaUtils.addMissingColumnsStatements(tester)
            assertEquals(0, statements.size)
        }
    }

    @Test
    fun testTimestampWithTimeZoneDefaultDoesNotTriggerAlterStatement() {
        val dateTime = DateTime.parse("2024-02-08T20:48:04.700").withZone(DateTimeZone.forID("Japan"))

        val tester = object : Table("tester") {
            val timestampWithTimeZoneWithDefault = timestampWithTimeZone("timestampWithTimeZoneWithDefault").default(dateTime)
        }

        // SQLite does not support ALTER TABLE on a column that has a default value
        // MariaDB does not support TIMESTAMP WITH TIME ZONE column type
        val unsupportedDatabases = TestDB.ALL_MARIADB + TestDB.MYSQL_V5
        withTables(excludeSettings = unsupportedDatabases, tester) {
            val statements = SchemaUtils.addMissingColumnsStatements(tester)
            assertEquals(0, statements.size)
        }
    }

    object DefaultTimestampTable : IntIdTable("test_table") {
        val timestamp: Column<DateTime> =
            timestampWithTimeZone("timestamp").defaultExpression(dbTimestampNow)
    }

    @Test
    fun testCustomDefaultTimestampFunctionWithInsertStatement() {
        // Only Postgres allows to get timestamp values directly from the insert statement due to implicit 'returning *'
        withTables(excludeSettings = TestDB.ALL - TestDB.ALL_POSTGRES, DefaultTimestampTable) {
            val entity = DefaultTimestampTable.insert { }
            val entityValue = entity[DefaultTimestampTable.timestamp]

            val timestamp = DefaultTimestampTable.selectAll().first()[DefaultTimestampTable.timestamp]

            assertEquals(timestamp, entityValue)
        }
    }
}

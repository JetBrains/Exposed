package org.jetbrains.exposed

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.flushCache
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.jodatime.*
import org.jetbrains.exposed.sql.statements.BatchDataInconsistentException
import org.jetbrains.exposed.sql.statements.BatchInsertStatement
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.inProperCase
import org.jetbrains.exposed.sql.tests.shared.assertEqualCollections
import org.jetbrains.exposed.sql.tests.shared.assertEqualLists
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.jetbrains.exposed.sql.vendors.OracleDialect
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.junit.Test

class JodaTimeDefaultsTest : JodaTimeBaseTest() {
    object TableWithDBDefault : IntIdTable() {
        var cIndex = 0
        val field = varchar("field", 100)
        val t1 = datetime("t1").defaultExpression(CurrentDateTime())
        val clientDefault = integer("clientDefault").clientDefault { cIndex++ }
    }

    class DBDefault(id: EntityID<Int>): IntEntity(id) {
        var field by TableWithDBDefault.field
        var t1 by TableWithDBDefault.t1
        val clientDefault by TableWithDBDefault.clientDefault

        override fun equals(other: Any?): Boolean {
            return (other as? DBDefault)?.let { id == it.id && field == it.field && equalDateTime(t1, it.t1) } ?: false
        }

        override fun hashCode(): Int = id.value.hashCode()

        companion object : IntEntityClass<DBDefault>(TableWithDBDefault)
    }

    @Test
    fun testDefaultsWithExplicit01() {
        withTables(TableWithDBDefault) {
            val created = listOf(
                    DBDefault.new { field = "1" },
                    DBDefault.new {
                        field = "2"
                        t1 = DateTime.now().minusDays(5)
                    })
            flushCache()
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
                    DBDefault.new{
                        field = "2"
                        t1 = DateTime.now().minusDays(5)
                    }, DBDefault.new{ field = "1" })

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
            val db1 = DBDefault.new{ field = "1" }
            val db2 = DBDefault.new{ field = "2" }
            flushCache()
            assertEquals(0, db1.clientDefault)
            assertEquals(1, db2.clientDefault)
            assertEquals(2, TableWithDBDefault.cIndex)
        }
    }

    private val initBatch = listOf<(BatchInsertStatement) -> Unit>({
        it[TableWithDBDefault.field] = "1"
    }, {
        it[TableWithDBDefault.field] = "2"
        it[TableWithDBDefault.t1] = DateTime.now()
    })

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
        val currentDT = CurrentDateTime()
        val nowExpression = object : Expression<DateTime>() {
            override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
                +when (val dialect = currentDialect) {
                    is OracleDialect -> "SYSDATE"
                    is SQLServerDialect -> "GETDATE()"
                    is MysqlDialect -> if (dialect.isFractionDateTimeSupported()) "NOW(6)" else "NOW()"
                    else -> "NOW()"
                }
            }
        }
        val dtConstValue = DateTime.parse("2010-01-01").withZone(DateTimeZone.UTC)
        val dtLiteral = dateLiteral(dtConstValue)
        val TestTable = object : IntIdTable("t") {
            val s = varchar("s", 100).default("test")
            val sn = varchar("sn", 100).default("testNullable").nullable()
            val l = long("l").default(42)
            val c = char("c").default('X')
            val t1 = datetime("t1").defaultExpression(currentDT)
            val t2 = datetime("t2").defaultExpression(nowExpression)
            val t3 = datetime("t3").defaultExpression(dtLiteral)
            val t4 = date("t4").default(dtConstValue)
        }

        fun Expression<*>.itOrNull() = when {
            currentDialectTest.isAllowedAsColumnDefault(this)  ->
                "DEFAULT ${currentDialectTest.dataTypeProvider.processForDefaultValue(this)} NOT NULL"
            else -> "NULL"
        }

        withTables(listOf(TestDB.SQLITE), TestTable) {
            val dtType = currentDialectTest.dataTypeProvider.dateTimeType()
            val q = db.identifierManager.quoteString
            val baseExpression = "CREATE TABLE " + addIfNotExistsIfSupported() +
                    "${"t".inProperCase()} (" +
                    "${"id".inProperCase()} ${currentDialectTest.dataTypeProvider.integerAutoincType()} PRIMARY KEY, " +
                    "${"s".inProperCase()} VARCHAR(100) DEFAULT 'test' NOT NULL, " +
                    "${"sn".inProperCase()} VARCHAR(100) DEFAULT 'testNullable' NULL, " +
                    "${"l".inProperCase()} ${currentDialectTest.dataTypeProvider.longType()} DEFAULT 42 NOT NULL, " +
                    "$q${"c".inProperCase()}$q CHAR DEFAULT 'X' NOT NULL, " +
                    "${"t1".inProperCase()} $dtType ${currentDT.itOrNull()}, " +
                    "${"t2".inProperCase()} $dtType ${nowExpression.itOrNull()}, " +
                    "${"t3".inProperCase()} $dtType ${dtLiteral.itOrNull()}, " +
                    "${"t4".inProperCase()} DATE ${dtLiteral.itOrNull()}" +
                    ")"

            val expected = if (currentDialectTest is OracleDialect)
                arrayListOf("CREATE SEQUENCE t_id_seq", baseExpression)
            else
                arrayListOf(baseExpression)

            assertEqualLists(expected, TestTable.ddl)

            val id1 = TestTable.insertAndGetId {  }

            val row1 = TestTable.select { TestTable.id eq id1 }.single()
            assertEquals("test", row1[TestTable.s])
            assertEquals("testNullable", row1[TestTable.sn])
            assertEquals(42, row1[TestTable.l])
            assertEquals('X', row1[TestTable.c])
            assertEqualDateTime(dtConstValue.withTimeAtStartOfDay(), row1[TestTable.t3].withTimeAtStartOfDay())
            assertEqualDateTime(dtConstValue.withTimeAtStartOfDay(), row1[TestTable.t4].withTimeAtStartOfDay())

            val id2 = TestTable.insertAndGetId { it[TestTable.sn] = null }

            val row2 = TestTable.select { TestTable.id eq id2 }.single()
        }
    }

    @Test
    fun testDefaultExpressions01() {

        fun abs(value: Int) = object : ExpressionWithColumnType<Int>() {
            override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append("ABS($value)") }

            override val columnType: IColumnType = IntegerColumnType()
        }

        val foo = object : IntIdTable("foo") {
            val name = text("name")
            val defaultDateTime = datetime("defaultDateTime").defaultExpression(CurrentDateTime())
            val defaultInt = integer("defaultInteger").defaultExpression(abs(-100))
        }

        withTables(foo) {
            val id = foo.insertAndGetId {
                it[foo.name] = "bar"
            }
            val result = foo.select { foo.id eq id }.single()

            assertEquals(today, result[foo.defaultDateTime].withTimeAtStartOfDay())
            assertEquals(100, result[foo.defaultInt])
        }
    }

    @Test
    fun testDefaultExpressions02() {
        val foo = object : IntIdTable("foo") {
            val name = text("name")
            val defaultDateTime = datetime("defaultDateTime").defaultExpression(CurrentDateTime())
        }

        val nonDefaultDate = DateTime.parse("2000-01-01")

        withTables(foo) {
            val id = foo.insertAndGetId {
                it[foo.name] = "bar"
                it[foo.defaultDateTime] = nonDefaultDate
            }

            val result = foo.select { foo.id eq id }.single()

            assertEquals("bar", result[foo.name])
            assertEqualDateTime(nonDefaultDate, result[foo.defaultDateTime])

            foo.update({foo.id eq id}) {
                it[foo.name] = "baz"
            }

            val result2 = foo.select { foo.id eq id }.single()
            assertEquals("baz", result2[foo.name])
            assertEqualDateTime(nonDefaultDate, result2[foo.defaultDateTime])
        }
    }

    @Test
    fun defaultCurrentDateTimeTest() {
        val TestDate = object : IntIdTable("TestDate") {
            val time = datetime("time").defaultExpression(CurrentDateTime())
        }

        withTables(TestDate) {
            val duration: Long = 2_000

            val before = currentDateTime()
            Thread.sleep(duration)
            for (i in 0..1) {
                TestDate.insertAndWait(duration)
            }
            val middle = currentDateTime()
            Thread.sleep(duration)
            for (i in 0..1) {
                TestDate.insertAndWait(duration)
            }
            val after = currentDateTime()

            assertEquals(0, TestDate.select { TestDate.time less    before }.count())
            assertEquals(4, TestDate.select { TestDate.time greater before }.count())
            assertEquals(2, TestDate.select { TestDate.time less    middle }.count())
            assertEquals(2, TestDate.select { TestDate.time greater middle }.count())
            assertEquals(4, TestDate.select { TestDate.time less    after  }.count())
            assertEquals(0, TestDate.select { TestDate.time greater after  }.count())
        }
    }

    // Checks that old numeric datetime columns works fine with new text representation
    @Test
    fun testSQLiteDateTimeFieldRegression() {
        val TestDate = object : IntIdTable("TestDate") {
            val time = datetime("time").defaultExpression(CurrentDateTime())
        }

        withDb(TestDB.SQLITE) {
            try {
                exec("CREATE TABLE IF NOT EXISTS TestDate (id INTEGER PRIMARY KEY AUTOINCREMENT, \"time\" NUMERIC DEFAULT (CURRENT_TIMESTAMP) NOT NULL);")
                TestDate.insert { }
                val year = TestDate.time.year()
                val month = TestDate.time.month()
                val day = TestDate.time.day()
                val hour = TestDate.time.hour()
                val minute = TestDate.time.minute()

                val result = TestDate.slice(year, month, day, hour, minute).selectAll().single()

                val now = DateTime.now()
                assertEquals(now.year, result[year])
                assertEquals(now.monthOfYear, result[month])
                assertEquals(now.dayOfMonth, result[day])
                assertEquals(now.hourOfDay, result[hour])
                assertEquals(now.minuteOfHour, result[minute])
            } finally {
                SchemaUtils.drop(TestDate)
            }
        }
    }
}

fun Table.insertAndWait(duration: Long) {
    this.insert {  }
    TransactionManager.current().commit()
    Thread.sleep(duration)
}

fun currentDateTime(): DateTime = DateTime.now().withZone(DateTimeZone.getDefault())

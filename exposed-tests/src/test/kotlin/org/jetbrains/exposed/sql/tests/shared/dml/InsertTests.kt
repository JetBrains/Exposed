package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.shared.*
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class InsertTests : DatabaseTestsBase() {
    @Test
    fun testInsertAndGetId01() {
        val idTable = object : IntIdTable("tmp") {
            val name = varchar("foo", 10).uniqueIndex()
        }

        withTables(idTable) {
            idTable.insertAndGetId {
                it[idTable.name] = "1"
            }

            assertEquals(1L, idTable.selectAll().count())

            idTable.insertAndGetId {
                it[idTable.name] = "2"
            }

            assertEquals(2L, idTable.selectAll().count())

            assertFailAndRollback("Unique constraint") {
                idTable.insertAndGetId {
                    it[idTable.name] = "2"
                }
            }
        }
    }

    private val insertIgnoreSupportedDB = TestDB.values().toList() -
            listOf(TestDB.SQLITE, TestDB.MYSQL, TestDB.H2_MYSQL, TestDB.POSTGRESQL, TestDB.POSTGRESQLNG)

    @Test
    fun testInsertIgnoreAndGetId01() {
        val idTable = object : IntIdTable("tmp") {
            val name = varchar("foo", 10).uniqueIndex()
        }


        withTables(insertIgnoreSupportedDB, idTable) {
            idTable.insertIgnoreAndGetId {
                it[idTable.name] = "1"
            }

            assertEquals(1L, idTable.selectAll().count())

            idTable.insertIgnoreAndGetId {
                it[idTable.name] = "2"
            }

            assertEquals(2L, idTable.selectAll().count())

            val idNull = idTable.insertIgnoreAndGetId {
                it[idTable.name] = "2"
            }

            assertEquals(null, idNull)
        }
    }

    @Test
    fun `test insert and get id when column has different name`() {
        val testTableWithId = object : IdTable<Int>("testTableWithId") {
            val code = integer("code")
            override val id: Column<EntityID<Int>> = code.entityId()
        }

        withTables(testTableWithId) {
            val id1 = testTableWithId.insertAndGetId {
                it[code] = 1
            }
            assertNotNull(id1)
            assertEquals(1, id1.value)

            val id2 = testTableWithId.insert {
                it[code] = 2
            } get testTableWithId.id
            assertNotNull(id2)
            assertEquals(2, id2.value)
        }
    }

    @Test
    fun testInsertIgnoreAndGetIdWithPredefinedId() {
        val idTable = object : IntIdTable("tmp") {
            val name = varchar("foo", 10).uniqueIndex()
        }

        val insertIgnoreSupportedDB = TestDB.values().toList() -
                listOf(TestDB.SQLITE, TestDB.MYSQL, TestDB.H2_MYSQL, TestDB.POSTGRESQL, TestDB.POSTGRESQLNG)
        withTables(insertIgnoreSupportedDB, idTable) {
            val id = idTable.insertIgnore {
                it[idTable.id] = EntityID(1, idTable)
                it[idTable.name] = "1"
            } get idTable.id
            assertEquals(1, id.value)
        }
    }


    @Test
    fun testBatchInsert01() {
        withCitiesAndUsers { cities, users, _ ->
            val cityNames = listOf("Paris", "Moscow", "Helsinki")
            val allCitiesID = cities.batchInsert(cityNames) { name ->
                this[cities.name] = name
            }
            assertEquals(cityNames.size, allCitiesID.size)

            val userNamesWithCityIds = allCitiesID.mapIndexed { index, id ->
                "UserFrom${cityNames[index]}" to id[cities.id] as Number
            }

            val generatedIds = users.batchInsert(userNamesWithCityIds) { (userName, cityId) ->
                this[users.id] = java.util.Random().nextInt().toString().take(6)
                this[users.name] = userName
                this[users.cityId] = cityId.toInt()
            }

            assertEquals(userNamesWithCityIds.size, generatedIds.size)
            assertEquals(userNamesWithCityIds.size.toLong(), users.select { users.name inList userNamesWithCityIds.map { it.first } }.count())
        }
    }

    @Test
    fun testGeneratedKey01() {
        withTables(DMLTestsData.Cities) {
            val id = DMLTestsData.Cities.insert {
                it[DMLTestsData.Cities.name] = "FooCity"
            } get DMLTestsData.Cities.id
            assertEquals(DMLTestsData.Cities.selectAll().last()[DMLTestsData.Cities.id], id)
        }
    }

    object LongIdTable : Table() {
        val id = long("id").autoIncrement("long_id_seq")
        val name = text("name")

        override val primaryKey = PrimaryKey(id)
    }

    @Test
    fun testGeneratedKey02() {
        withTables(LongIdTable) {
            val id = LongIdTable.insert {
                it[LongIdTable.name] = "Foo"
            } get LongIdTable.id
            assertEquals(LongIdTable.selectAll().last()[LongIdTable.id], id)
        }
    }

    object IntIdTestTable : IntIdTable() {
        val name = text("name")
    }

    @Test
    fun testGeneratedKey03() {
        withTables(IntIdTestTable) {
            val id = IntIdTestTable.insertAndGetId {
                it[IntIdTestTable.name] = "Foo"
            }
            assertEquals(IntIdTestTable.selectAll().last()[IntIdTestTable.id], id)
        }
    }

    @Test fun testInsertWithPredefinedId() {
        val stringTable = object : IdTable<String>("stringTable") {
            override val id = varchar("id", 15).entityId()
            val name = varchar("name", 10)
        }
        withTables(stringTable) {
            val entityID = EntityID("id1", stringTable)
            val id1 = stringTable.insertAndGetId {
                it[id] = entityID
                it[name] = "foo"
            }

            stringTable.insertAndGetId {
                it[id] = EntityID("testId", stringTable)
                it[name] = "bar"
            }

            assertEquals(id1, entityID)
            val row1 = stringTable.select { stringTable.id eq entityID }.singleOrNull()
            assertEquals(row1?.get(stringTable.id), entityID)

            val row2 = stringTable.select { stringTable.id like "id%" }.singleOrNull()
            assertEquals(row2?.get(stringTable.id), entityID)
        }
    }

    @Test fun testInsertWithExpression() {

        val tbl = object : IntIdTable("testInsert") {
            val nullableInt = integer("nullableIntCol").nullable()
            val string = varchar("stringCol", 20)
        }

        fun expression(value: String) = stringLiteral(value).trim().substring(2, 4)

        fun verify(value: String) {
            val row = tbl.select{ tbl.string eq value }.single()
            assertEquals(row[tbl.string], value)
        }

        withTables(tbl) {
            addLogger(StdOutSqlLogger)
            tbl.insert {
                it[string] = expression(" _exp1_ ")
            }

            verify("exp1")

            tbl.insert {
                it[string] = expression(" _exp2_ ")
                it[nullableInt] = 5
            }

            verify("exp2")

            tbl.insert {
                it[string] = expression(" _exp3_ ")
                it[nullableInt] = null
            }

            verify("exp3")
        }
    }

    @Test fun testInsertWithColumnExpression() {

        val tbl1 = object : IntIdTable("testInsert1") {
            val string1 = varchar("stringCol", 20)
        }
        val tbl2 = object : IntIdTable("testInsert2") {
            val string2 = varchar("stringCol", 20).nullable()
        }

        fun verify(value: String) {
            val row = tbl2.select{ tbl2.string2 eq value }.single()
            assertEquals(row[tbl2.string2], value)
        }

        withTables(tbl1, tbl2) {
            addLogger(StdOutSqlLogger)

            val id = tbl1.insertAndGetId {
                it[string1] = " _exp1_ "
            }

            val expr1 = tbl1.string1.trim().substring(2, 4)
            tbl2.insert {
                it[string2] = wrapAsExpression(tbl1.slice(expr1).select { tbl1.id eq id })
            }

            verify("exp1")
        }
    }

    private object OrderedDataTable : IntIdTable()
    {
        val name = text("name")
        val order = integer("order")
    }

    class OrderedData(id : EntityID<Int>) : IntEntity(id)
    {
        companion object : IntEntityClass<OrderedData>(OrderedDataTable)

        var name by OrderedDataTable.name
        var order by OrderedDataTable.order
    }

    // https://github.com/JetBrains/Exposed/issues/192
    @Test fun testInsertWithColumnNamedWithKeyword() {
        withTables(OrderedDataTable) {

            val foo = OrderedData.new {
                name = "foo"
                order = 20
            }
            val bar = OrderedData.new {
                name = "bar"
                order = 10
            }

            assertEqualLists(listOf(bar, foo), OrderedData.all().orderBy(OrderedDataTable.order to SortOrder.ASC).toList())
        }
    }

    @Test fun testInsertEmojis() {
        val table = object : Table("tmp") {
            val emoji = varchar("emoji", 16)
        }
        val emojis = "\uD83D\uDC68\uD83C\uDFFF\u200D\uD83D\uDC69\uD83C\uDFFF\u200D\uD83D\uDC67\uD83C\uDFFF\u200D\uD83D\uDC66\uD83C\uDFFF"

        withTables(listOf(TestDB.H2, TestDB.H2_MYSQL, TestDB.SQLSERVER), table) {
            val isOldMySQL = currentDialectTest is MysqlDialect && db.isVersionCovers(BigDecimal("5.5"))
            if (isOldMySQL) {
                exec("ALTER TABLE ${table.nameInDatabaseCase()} DEFAULT CHARSET utf8mb4, MODIFY emoji VARCHAR(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;")
            }
            table.insert {
                it[table.emoji] = emojis
            }

            assertEquals(1L, table.selectAll().count())
        }
    }

    @Test fun testInsertEmojisWithInvalidLength() {
        val table = object : Table("tmp") {
            val emoji = varchar("emoji", 10)
        }
        val emojis = "\uD83D\uDC68\uD83C\uDFFF\u200D\uD83D\uDC69\uD83C\uDFFF\u200D\uD83D\uDC67\uD83C\uDFFF\u200D\uD83D\uDC66\uD83C\uDFFF"

        withTables(listOf(TestDB.SQLITE, TestDB.H2, TestDB.H2_MYSQL, TestDB.POSTGRESQL, TestDB.POSTGRESQLNG), table) {
            expectException<IllegalArgumentException> {
                table.insert {
                    it[table.emoji] = emojis
                }
            }
        }
    }

    /*
    @Test fun testGeneratedKey04() {
        val CharIdTable = object : IdTable<String>("charId") {
            override val id = varchar("id", 50).primaryKey()
                    .clientDefault { UUID.randomUUID().toString() }
                    .entityId()
            val foo = integer("foo")
        }
        withTables(CharIdTable){
            val id = IntIdTestTable.insertAndGetId {
                it[CharIdTable.foo] = 5
            }
            assertNotNull(id?.value)
        }
    } */

/*
    Test fun testInsert05() {
        val stringThatNeedsEscaping = "multi\r\nline"
        val t = DMLTestsData.Misc
        withTables(t) {
            t.insert {
                it[n] = 42
                it[d] = today
                it[e] = DMLTestsData.E.ONE
                it[s] = stringThatNeedsEscaping
            }

            val row = t.selectAll().single()
            t.checkRow(row, 42, null, today, null, DMLTestsData.E.ONE, null, stringThatNeedsEscaping, null)
        }
    }
*/
}
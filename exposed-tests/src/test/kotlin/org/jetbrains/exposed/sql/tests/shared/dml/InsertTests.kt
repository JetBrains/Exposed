package org.jetbrains.exposed.sql.tests.shared.dml

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestamp
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.statements.BatchInsertStatement
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentTestDB
import org.jetbrains.exposed.sql.tests.inProperCase
import org.jetbrains.exposed.sql.tests.shared.assertEqualLists
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.assertFailAndRollback
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.jetbrains.exposed.sql.tests.shared.entities.EntityTests
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.junit.Assume
import org.junit.Test
import java.sql.SQLException
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

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

    private val insertIgnoreUnsupportedDB = TestDB.entries -
        listOf(TestDB.SQLITE, TestDB.MYSQL_V5, TestDB.H2_V2_MYSQL, TestDB.POSTGRESQL, TestDB.POSTGRESQLNG, TestDB.H2_V2_PSQL)

    @Test
    fun testInsertIgnoreAndGetId01() {
        val idTable = object : IntIdTable("tmp") {
            val name = varchar("foo", 10).uniqueIndex()
        }

        withTables(excludeSettings = insertIgnoreUnsupportedDB, idTable) {
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

            val shouldNotReturnProvidedIdOnConflict = idTable.insertIgnoreAndGetId {
                it[idTable.id] = EntityID(100, idTable)
                it[idTable.name] = "2"
            }

            assertEquals(null, shouldNotReturnProvidedIdOnConflict)
        }
    }

    @Test
    fun `test insert and get id when column has different name and get value by id column`() {
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
    fun `test id and column have different names and get value by original column`() {
        val exampleTable = object : IdTable<String>("test_id_and_column_table") {
            val exampleColumn = varchar("example_column", 200)
            override val id = exampleColumn.entityId()
        }

        withTables(exampleTable) {
            val value = "value"
            exampleTable.insert {
                it[exampleColumn] = value
            }

            val resultValues: List<String> = exampleTable.selectAll().map { it[exampleTable.exampleColumn] }

            assertEquals(value, resultValues.first())
        }
    }

    @Test
    fun testInsertIgnoreAndGetIdWithPredefinedId() {
        val idTable = object : IntIdTable("tmp") {
            val name = varchar("foo", 10).uniqueIndex()
        }

        withTables(excludeSettings = insertIgnoreUnsupportedDB, idTable) {
            val insertedStatement = idTable.insertIgnore {
                it[idTable.id] = EntityID(1, idTable)
                it[idTable.name] = "1"
            }
            assertEquals(1, insertedStatement[idTable.id].value)
            assertEquals(1, insertedStatement.insertedCount)

            val notInsertedStatement = idTable.insertIgnore {
                it[idTable.id] = EntityID(1, idTable)
                it[idTable.name] = "2"
            }

            assertEquals(1, notInsertedStatement[idTable.id].value)
            assertEquals(0, notInsertedStatement.insertedCount)
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
            assertEquals(
                userNamesWithCityIds.size.toLong(),
                users.selectAll().where { users.name inList userNamesWithCityIds.map { it.first } }.count()
            )
        }
    }

    @Test
    fun testBatchInsertWithSequence() {
        val cities = DMLTestsData.Cities
        withTables(cities) {
            val names = List(25) { UUID.randomUUID().toString() }.asSequence()
            cities.batchInsert(names) { name -> this[cities.name] = name }

            val batchesSize = cities.selectAll().count()

            assertEquals(25, batchesSize)
        }
    }

    @Test
    fun `batchInserting using empty sequence should work`() {
        val cities = DMLTestsData.Cities
        withTables(cities) {
            val names = emptySequence<String>()
            cities.batchInsert(names) { name -> this[cities.name] = name }

            val batchesSize = cities.selectAll().count()

            assertEquals(0, batchesSize)
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
        val id = long("id").autoIncrement()
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

    @Test
    fun testInsertWithPredefinedId() {
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
            val row1 = stringTable.selectAll().where { stringTable.id eq entityID }.singleOrNull()
            assertEquals(row1?.get(stringTable.id), entityID)

            val row2 = stringTable.selectAll().where { stringTable.id like "id%" }.singleOrNull()
            assertEquals(row2?.get(stringTable.id), entityID)
        }
    }

    @Test
    fun testInsertWithForeignId() {
        val idTable = object : IntIdTable("idTable") {}
        val standardTable = object : Table("standardTable") {
            val externalId = reference("externalId", idTable.id)
        }
        withTables(idTable, standardTable) {
            val id1 = idTable.insertAndGetId {}

            standardTable.insert {
                it[externalId] = id1.value
            }

            val allRecords = standardTable.selectAll().map { it[standardTable.externalId] }
            assertTrue(allRecords == listOf(id1))
        }
    }

    @Test
    fun testInsertWithExpression() {
        val tbl = object : IntIdTable("testInsert") {
            val nullableInt = integer("nullableIntCol").nullable()
            val string = varchar("stringCol", 20)
        }

        fun expression(value: String) = stringLiteral(value).trim().substring(2, 4)

        fun verify(value: String) {
            val row = tbl.selectAll().where { tbl.string eq value }.single()
            assertEquals(row[tbl.string], value)
        }

        withTables(tbl) {
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

    @Test
    fun testInsertWithColumnExpression() {
        val tbl1 = object : IntIdTable("testInsert1") {
            val string1 = varchar("stringCol", 20)
        }
        val tbl2 = object : IntIdTable("testInsert2") {
            val string2 = varchar("stringCol", 20).nullable()
        }

        fun verify(value: String) {
            val row = tbl2.selectAll().where { tbl2.string2 eq value }.single()
            assertEquals(row[tbl2.string2], value)
        }

        withTables(tbl1, tbl2) {
            val id = tbl1.insertAndGetId {
                it[string1] = " _exp1_ "
            }

            val expr1 = tbl1.string1.trim().substring(2, 4)
            tbl2.insert {
                it[string2] = wrapAsExpression(tbl1.select(expr1).where { tbl1.id eq id })
            }

            verify("exp1")
        }
    }

    private object OrderedDataTable : IntIdTable() {
        val name = text("name")
        val order = integer("order")
    }

    class OrderedData(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<OrderedData>(OrderedDataTable)

        var name by OrderedDataTable.name
        var order by OrderedDataTable.order
    }

    // https://github.com/JetBrains/Exposed/issues/192
    @Test
    fun testInsertWithColumnNamedWithKeyword() {
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

    @Test
    fun testInsertEmojis() {
        val table = object : Table("tmp") {
            val emoji = varchar("emoji", 16)
        }
        val emojis = "\uD83D\uDC68\uD83C\uDFFF\u200D\uD83D\uDC69\uD83C\uDFFF\u200D\uD83D\uDC67\uD83C\uDFFF\u200D\uD83D\uDC66\uD83C\uDFFF"

        withTables(excludeSettings = TestDB.ALL_H2 + TestDB.SQLSERVER, table) { testDb ->
            if (testDb == TestDB.MYSQL_V5) {
                exec("ALTER TABLE ${table.nameInDatabaseCase()} DEFAULT CHARSET utf8mb4, MODIFY emoji VARCHAR(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;")
            }
            table.insert {
                it[table.emoji] = emojis
            }

            assertEquals(1L, table.selectAll().count())
        }
    }

    @Test
    fun testInsertEmojisWithInvalidLength() {
        val table = object : Table("tmp") {
            val emoji = varchar("emoji", 10)
        }
        val emojis = "\uD83D\uDC68\uD83C\uDFFF\u200D\uD83D\uDC69\uD83C\uDFFF\u200D\uD83D\uDC67\uD83C\uDFFF\u200D\uD83D\uDC66\uD83C\uDFFF"

        withTables(listOf(TestDB.SQLITE, TestDB.H2_V2, TestDB.H2_V2_MYSQL, TestDB.POSTGRESQL, TestDB.POSTGRESQLNG, TestDB.H2_V2_PSQL), table) {
            expectException<IllegalArgumentException> {
                table.insert {
                    it[table.emoji] = emojis
                }
            }
        }
    }

    @Test
    fun `test that column length checked on insert`() {
        val stringTable = object : IntIdTable("StringTable") {
            val name = varchar("name", 10)
        }

        withTables(stringTable) {
            val veryLongString = "1".repeat(255)
            expectException<IllegalArgumentException> {
                stringTable.insert {
                    it[name] = veryLongString
                }
            }
        }
    }

    @Test
    fun `test subquery in an insert or update statement`() {
        val tab1 = object : Table("tab1") {
            val id = varchar("id", 10)
        }
        val tab2 = object : Table("tab2") {
            val id = varchar("id", 10)
        }

        withTables(tab1, tab2) {
            // Initial data
            tab2.insert { it[id] = "foo" }
            tab2.insert { it[id] = "bar" }

            // Use sub query in an insert
            tab1.insert { it[id] = tab2.select(tab2.id).where { tab2.id eq "foo" } }

            // Check inserted data
            val insertedId = tab1.select(tab1.id).single()[tab1.id]
            assertEquals("foo", insertedId)

            // Use sub query in an update
            tab1.update({ tab1.id eq "foo" }) { it[id] = tab2.select(tab2.id).where { tab2.id eq "bar" } }

            // Check updated data
            val updatedId = tab1.select(tab1.id).single()[tab1.id]
            assertEquals("bar", updatedId)
        }
    }

    @Test
    fun testGeneratedKey04() {
        val charIdTable = object : IdTable<String>("charId") {
            override val id = varchar("id", 50)
                .clientDefault { UUID.randomUUID().toString() }
                .entityId()
            val foo = integer("foo")

            override val primaryKey: PrimaryKey = PrimaryKey(id)
        }
        withTables(charIdTable) {
            val id = charIdTable.insertAndGetId {
                it[charIdTable.foo] = 5
            }
            assertNotNull(id.value)
        }
    }

    @Test
    fun testRollbackOnConstraintExceptionWithNormalTransactions() {
        val testTable = object : IntIdTable("TestRollback") {
            val foo = integer("foo").check { it greater 0 }
        }
        val dbToTest = TestDB.enabledDialects() - setOfNotNull(
            TestDB.SQLITE,
            TestDB.MYSQL_V5.takeIf { System.getProperty("exposed.test.mysql8.port") == null }
        )
        Assume.assumeTrue(dbToTest.isNotEmpty())
        dbToTest.forEach { db ->
            try {
                try {
                    withDb(db) {
                        SchemaUtils.create(testTable)
                        testTable.insert { it[foo] = 1 }
                        testTable.insert { it[foo] = 0 }
                    }
                    fail("Should fail on constraint > 0 with $db")
                } catch (_: SQLException) {
                    // expected
                }
                withDb(db) {
                    assertTrue(testTable.selectAll().empty())
                }
            } finally {
                withDb(db) {
                    SchemaUtils.drop(testTable)
                }
            }
        }
    }

    @Test
    fun testRollbackOnConstraintExceptionWithSuspendTransactions() {
        val testTable = object : IntIdTable("TestRollback") {
            val foo = integer("foo").check { it greater 0 }
        }
        val dbToTest = TestDB.enabledDialects() - setOfNotNull(
            TestDB.SQLITE,
            TestDB.MYSQL_V5.takeIf { System.getProperty("exposed.test.mysql8.port") == null }
        )
        Assume.assumeTrue(dbToTest.isNotEmpty())
        dbToTest.forEach { db ->
            try {
                try {
                    withDb(db) {
                        SchemaUtils.create(testTable)
                    }
                    runBlocking {
                        newSuspendedTransaction(db = db.db) {
                            testTable.insert { it[foo] = 1 }
                            testTable.insert { it[foo] = 0 }
                        }
                    }
                    fail("Should fail on constraint > 0")
                } catch (_: SQLException) {
                    // expected
                }

                withDb(db) {
                    assertTrue(testTable.selectAll().empty())
                }
            } finally {
                withDb(db) {
                    SchemaUtils.drop(testTable)
                }
            }
        }
    }

    @Test
    fun `test optReference allows null values`() {
        withTables(EntityTests.Posts) {
            val id1 = EntityTests.Posts.insertAndGetId {
                it[board] = null
                it[category] = null
            }

            val inserted1 = EntityTests.Posts.selectAll().where { EntityTests.Posts.id eq id1 }.single()
            assertNull(inserted1[EntityTests.Posts.board])
            assertNull(inserted1[EntityTests.Posts.category])

            val boardId = EntityTests.Boards.insertAndGetId {
                it[name] = UUID.randomUUID().toString()
            }
            val categoryId = EntityTests.Categories.insert {
                it[title] = "Category"
            }[EntityTests.Categories.uniqueId]

            val id2 = EntityTests.Posts.insertAndGetId {
                it[board] = Op.nullOp()
                it[category] = categoryId
                it[board] = boardId.value
            }

            EntityTests.Posts.deleteWhere { EntityTests.Posts.id eq id2 }

            val nullableCategoryID: UUID? = categoryId
            val nullableBoardId: Int? = boardId.value
            EntityTests.Posts.insertAndGetId {
                it[board] = Op.nullOp()
                it[category] = nullableCategoryID
                it[board] = nullableBoardId
            }
        }
    }

    class BatchInsertOnConflictDoNothing(
        table: Table,
    ) : BatchInsertStatement(table) {
        override fun prepareSQL(transaction: Transaction, prepared: Boolean) = buildString {
            val insertStatement = super.prepareSQL(transaction, prepared)
            when (val db = currentTestDB) {
                in TestDB.ALL_MYSQL_LIKE -> {
                    append("INSERT IGNORE ")
                    append(insertStatement.substringAfter("INSERT "))
                }
                else -> {
                    append(insertStatement)
                    val identifier = if (db == TestDB.H2_V2_PSQL) "" else "(id) "
                    append(" ON CONFLICT ${identifier}DO NOTHING")
                }
            }
        }
    }

    @Test
    fun testBatchInsertNumberOfInsertedRows() {
        val tab = object : Table("tab") {
            val id = varchar("id", 10).uniqueIndex()
        }

        withTables(excludeSettings = insertIgnoreUnsupportedDB, tab) {
            tab.insert { it[id] = "foo" }

            val numInserted = BatchInsertOnConflictDoNothing(tab).run {
                addBatch()
                this[tab.id] = "foo"

                addBatch()
                this[tab.id] = "bar"

                execute(this@withTables)
            }

            assertEquals(1, numInserted)
        }
    }

    @Test
    fun testInsertIntoNullableGeneratedColumn() {
        withDb(excludeSettings = TestDB.ALL_H2_V1) { testDb ->
            val generatedTable = object : IntIdTable("generated_table") {
                val amount = integer("amount").nullable()
                val computedAmount = integer("computed_amount").nullable().databaseGenerated().apply {
                    if (testDb == TestDB.ORACLE || testDb in TestDB.ALL_H2_V2) {
                        withDefinition("GENERATED ALWAYS AS (AMOUNT + 1)")
                    } else {
                        withDefinition("GENERATED ALWAYS AS (AMOUNT + 1) STORED")
                    }
                }
            }

            try {
                val computedName = generatedTable.computedAmount.name.inProperCase()
                val computedType = generatedTable.computedAmount.columnType.sqlType()
                val computation = "${generatedTable.amount.name.inProperCase()} + 1"

                val createStatement = """CREATE TABLE ${addIfNotExistsIfSupported()}${generatedTable.tableName.inProperCase()} (
                        ${generatedTable.id.descriptionDdl()},
                        ${generatedTable.amount.descriptionDdl()},
                """.trimIndent()

                when (testDb) {
                    // MariaDB does not support GENERATED ALWAYS AS with any null constraint definition
                    in TestDB.ALL_MARIADB -> {
                        exec("${createStatement.trimIndent()} $computedName $computedType GENERATED ALWAYS AS ($computation) STORED)")
                    }
                    // SQL SERVER only supports the AS variant if column_type is not defined
                    TestDB.SQLSERVER -> {
                        exec("${createStatement.trimIndent()} $computedName AS ($computation))")
                    }
                    else -> SchemaUtils.create(generatedTable)
                }

                assertFailAndRollback("Generated columns are auto-derived and read-only") {
                    generatedTable.insert {
                        it[amount] = 99
                        it[computedAmount] = 100
                    }
                }

                generatedTable.insert {
                    it[amount] = 99
                }

                val result1 = generatedTable.selectAll().single()
                assertEquals(result1[generatedTable.amount]?.plus(1), result1[generatedTable.computedAmount])

                generatedTable.insert {
                    it[amount] = null
                }

                val result2 = generatedTable.selectAll().where { generatedTable.amount.isNull() }.single()
                assertNull(result2[generatedTable.amount])
                assertNull(result2[generatedTable.computedAmount])
            } finally {
                SchemaUtils.drop(generatedTable)
            }
        }
    }

    @Test
    fun testNoAutoIncrementAppliedToCustomStringPrimaryKey() {
        val tester = object : IdTable<String>("test_no_auto_increment_table") {
            val customId = varchar("custom_id", 128)
            override val primaryKey: PrimaryKey = PrimaryKey(customId)
            override val id: Column<EntityID<String>> = customId.entityId()
        }

        withTables(tester) {
            val result1 = tester.batchInsert(listOf("custom-id-value")) { username ->
                this[tester.customId] = username
            }.single()
            assertEquals("custom-id-value", result1[tester.id].value)
            assertEquals("custom-id-value", result1[tester.customId])
        }
    }

    @Test
    fun testInsertReturnsValuesFromDefaultExpression() {
        val tester = object : Table() {
            val defaultDate = timestamp(name = "default_date").defaultExpression(CurrentTimestamp)
        }

        withTables(excludeSettings = TestDB.ALL - TestDB.ALL_POSTGRES, tester) {
            val entry = tester.insert {}

            assertNotNull(entry[tester.defaultDate])
        }
    }

    @Test
    fun testDatabaseGeneratedUUIDasPrimaryKey() {
        val randomPGUUID = object : CustomFunction<UUID>("gen_random_uuid", UUIDColumnType()) {}

        val tester = object : IdTable<UUID>("testTestTest") {
            override val id = uuid("id").defaultExpression(randomPGUUID).entityId()
            override val primaryKey = PrimaryKey(id)
        }

        withTables(excludeSettings = TestDB.ALL - TestDB.ALL_POSTGRES, tester) {
            val result = tester.insert {}
            assertNotNull(result[tester.id])
        }
    }

    @Test
    fun testDefaultValuesAndNullableColumnsNotInBatchInsertArguments() {
        val tester = object : IntIdTable("test_batch_insert_defaults") {
            val number = integer("number")
            val default = varchar("default", 128).default("default")
            val defaultExpression = varchar("defaultExpression", 128).defaultExpression(stringLiteral("defaultExpression"))
            val nullable = varchar("nullable", 128).nullable()
            val nullableDefaultNull = varchar("nullableDefaultNull", 128).nullable().default(null)
            val nullableDefaultNotNull = varchar("nullableDefaultNotNull", 128).nullable().default("nullableDefaultNotNull")
            val databaseGenerated = integer("databaseGenerated").withDefinition("DEFAULT 1").databaseGenerated()
        }

        val testerWithFakeDefaults = object : IntIdTable("test_batch_insert_defaults") {
            val number = integer("number")
            val default = varchar("default", 128).default("default-fake")
            val defaultExpression = varchar("defaultExpression", 128).defaultExpression(stringLiteral("defaultExpression-fake"))
            val nullable = varchar("nullable", 128).nullable().default("null-fake")
            val nullableDefaultNull = varchar("nullableDefaultNull", 128).nullable().default("null-fake")
            val nullableDefaultNotNull = varchar("nullableDefaultNotNull", 128).nullable().default("nullableDefaultNotNull-fake")
            val databaseGenerated = integer("databaseGenerated").default(-1)
        }

        withTables(tester) {
            val statement = testerWithFakeDefaults.batchInsert(listOf(1, 2, 3)) {
                this[testerWithFakeDefaults.number] = 10
            }
            statement.forEach {
                println("id: ${it[testerWithFakeDefaults.id]}")
            }

            testerWithFakeDefaults.selectAll().forEach {
                assertEquals("default", it[testerWithFakeDefaults.default])
                assertEquals("defaultExpression", it[testerWithFakeDefaults.defaultExpression])
                assertEquals(null, it[testerWithFakeDefaults.nullable])
                assertEquals(null, it[testerWithFakeDefaults.nullableDefaultNull])
                assertEquals("nullableDefaultNotNull", it[testerWithFakeDefaults.nullableDefaultNotNull])
                assertEquals(1, it[testerWithFakeDefaults.databaseGenerated])
            }
        }
    }
}

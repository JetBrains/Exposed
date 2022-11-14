package org.jetbrains.exposed.sql.tests.shared.ddl

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.inProperCase
import org.jetbrains.exposed.sql.tests.shared.assertEqualCollections
import org.jetbrains.exposed.sql.tests.shared.assertEqualLists
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.assertFailAndRollback
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.jetbrains.exposed.sql.vendors.OracleDialect
import org.junit.Test
import java.math.BigDecimal
import java.util.*
import kotlin.properties.Delegates

class CreateMissingTablesAndColumnsTests : DatabaseTestsBase() {

    @Test
    fun testCreateMissingTablesAndColumns01() {
        val TestTable = object : Table("test_table") {
            val id = integer("id")
            val name = varchar("name", length = 42)
            val time = long("time").uniqueIndex()

            override val primaryKey = PrimaryKey(id)
        }

        withTables(excludeSettings = listOf(TestDB.H2_MYSQL), tables = arrayOf(TestTable)) {
            SchemaUtils.createMissingTablesAndColumns(TestTable)
            assertTrue(TestTable.exists())
            SchemaUtils.drop(TestTable)
        }
    }

    @Test
    fun testCreateMissingTablesAndColumns02() {
        val TestTable = object : IdTable<String>("Users2") {
            override val id: Column<EntityID<String>> = varchar("id", 64).clientDefault { UUID.randomUUID().toString() }.entityId()

            val name = varchar("name", 255)
            val email = varchar("email", 255).uniqueIndex()
            val camelCased = varchar("camelCased", 255).index()

            override val primaryKey = PrimaryKey(id)
        }

        withDb { dbSetting ->
            val tooOldMysql = dbSetting == TestDB.MYSQL && !db.isVersionCovers(BigDecimal("5.6"))
            if (!tooOldMysql) {
                SchemaUtils.createMissingTablesAndColumns(TestTable)
                assertTrue(TestTable.exists())
                try {
                    SchemaUtils.createMissingTablesAndColumns(TestTable)
                } finally {
                    SchemaUtils.drop(TestTable)
                }
            }
        }
    }

    @Test
    fun testCreateMissingTablesAndColumnsChangeNullability() {
        val t1 = object : IntIdTable("foo") {
            val foo = varchar("foo", 50)
        }

        val t2 = object : IntIdTable("foo") {
            val foo = varchar("foo", 50).nullable()
        }

        withDb(excludeSettings = listOf(TestDB.SQLITE)) {
            SchemaUtils.createMissingTablesAndColumns(t1)
            t1.insert { it[foo] = "ABC" }
            assertFailAndRollback("Can't insert to not-null column") {
                t2.insert { it[foo] = null }
            }

            SchemaUtils.createMissingTablesAndColumns(t2)
            t2.insert { it[foo] = null }
            assertFailAndRollback("Can't make column non-null while has null value") {
                SchemaUtils.createMissingTablesAndColumns(t1)
            }

            t2.deleteWhere { t2.foo.isNull() }

            SchemaUtils.createMissingTablesAndColumns(t1)
            assertFailAndRollback("Can't insert to nullable column") {
                t2.insert { it[foo] = null }
            }
            SchemaUtils.drop(t1)
        }
    }

    @Test
    fun testCreateMissingTablesAndColumnsChangeAutoincrement() {
        val t1 = object : Table("foo") {
            val id = integer("idcol").autoIncrement()
            val foo = varchar("foo", 50)

            override val primaryKey = PrimaryKey(id)
        }

        val t2 = object : Table("foo") {
            val id = integer("idcol")
            val foo = varchar("foo", 50)

            override val primaryKey = PrimaryKey(id)
        }

        withDb(db = listOf(TestDB.H2)) {
            SchemaUtils.createMissingTablesAndColumns(t1)
            t1.insert { it[foo] = "ABC" }

            SchemaUtils.createMissingTablesAndColumns(t2)
            assertFailAndRollback("Can't insert without primaryKey value") {
                t2.insert { it[foo] = "ABC" }
            }

            t2.insert {
                it[id] = 3
                it[foo] = "ABC"
            }

            SchemaUtils.drop(t1)
        }
    }

    @Test
    fun testAddMissingColumnsStatementsChangeCasing() {
        val t1 = object : Table("foo") {
            val id = integer("idCol")

            override val primaryKey = PrimaryKey(id)
        }

        val t2 = object : Table("foo") {
            val id = integer("idcol")

            override val primaryKey = PrimaryKey(id)
        }

        withDb {
            if (db.supportsAlterTableWithAddColumn) {
                SchemaUtils.createMissingTablesAndColumns(t1)

                val missingStatements = SchemaUtils.addMissingColumnsStatements(t2)

                val alterColumnWord = when (currentDialectTest) {
                    is MysqlDialect -> "MODIFY COLUMN"
                    is OracleDialect -> "MODIFY"
                    else -> "ALTER COLUMN"
                }

                val expected = if (t1.id.nameInDatabaseCase() != t2.id.nameInDatabaseCase()) {
                    "ALTER TABLE ${t2.nameInDatabaseCase()} $alterColumnWord ${t2.id.nameInDatabaseCase()} INT"
                } else null

                assertEquals(expected, missingStatements.firstOrNull())

                SchemaUtils.drop(t1)
            }
        }
    }

    @Test
    fun testAddMissingColumnsStatementsIdentical() {
        val t1 = object : Table("foo") {
            val id = integer("idcol")

            override val primaryKey = PrimaryKey(id)
        }

        val t2 = object : Table("foo") {
            val id = integer("idcol")

            override val primaryKey = PrimaryKey(id)
        }

        withDb {
//        withDb(db = listOf(TestDB.H2)) {
            SchemaUtils.createMissingTablesAndColumns(t1)

            val missingStatements = SchemaUtils.addMissingColumnsStatements(t2)

            assertEqualCollections(missingStatements, emptyList())

            SchemaUtils.drop(t1)
        }
    }

    @Test
    fun testAddMissingColumnsStatementsIdentical2() {
        val t1 = object : Table("foo") {
            val id = integer("idCol")

            override val primaryKey = PrimaryKey(id)
        }

        val t2 = object : Table("foo") {
            val id = integer("idCol")

            override val primaryKey = PrimaryKey(id)
        }

        withDb {
//        withDb(db = listOf(TestDB.H2)) {
            SchemaUtils.createMissingTablesAndColumns(t1)

            val missingStatements = SchemaUtils.addMissingColumnsStatements(t2)

            assertEqualCollections(missingStatements, emptyList())

            SchemaUtils.drop(t1)
        }
    }

    @Test
    fun testCreateMissingTablesAndColumnsChangeCascadeType() {
        val fooTable = object : IntIdTable("foo") {
            val foo = varchar("foo", 50)
        }

        val barTable1 = object : IntIdTable("bar") {
            val foo = optReference("foo", fooTable, onDelete = ReferenceOption.NO_ACTION)
        }

        val barTable2 = object : IntIdTable("bar") {
            val foo = optReference("foo", fooTable, onDelete = ReferenceOption.CASCADE)
        }

        withTables(fooTable, barTable1) {
            SchemaUtils.createMissingTablesAndColumns(barTable2)
        }
    }

    @Test fun addAutoPrimaryKey() {
        val tableName = "Foo"
        val initialTable = object : Table(tableName) {
            val bar = text("bar")
        }
        val t = IntIdTable(tableName)

        withTables(excludeSettings = TestDB.allH2TestDB + TestDB.SQLITE, tables = arrayOf(initialTable)) {
            assertEquals("ALTER TABLE ${tableName.inProperCase()} ADD ${"id".inProperCase()} ${t.id.columnType.sqlType()} PRIMARY KEY", t.id.ddl)
            assertEquals(1, currentDialectTest.tableColumns(t)[t]!!.size)
            SchemaUtils.createMissingTablesAndColumns(t)
            assertEquals(2, currentDialectTest.tableColumns(t)[t]!!.size)
        }
    }

    @Test
    fun `columns with default values that haven't changed shouldn't trigger change`() {
        var table by Delegates.notNull<Table>()
        withDb { testDb ->
            try {
                // MySQL doesn't support default values on text columns, hence excluded
                table = if(testDb != TestDB.MYSQL) {
                    object : Table("varchar_test") {
                        val varchar = varchar("varchar_column", 255).default(" ")
                        val text = text("text_column").default(" ")
                    }
                } else {

                    object : Table("varchar_test") {
                        val varchar = varchar("varchar_column", 255).default(" ")
                    }
                }

                // MySQL doesn't support default values on text columns, hence excluded

                SchemaUtils.create(table)
                val actual = SchemaUtils.statementsRequiredToActualizeScheme(table)
                assertEqualLists(emptyList(), actual)
            } finally {
                SchemaUtils.drop(table)
            }
        }
    }

    private class StringFieldTable(name: String, isTextColumn: Boolean, default: String) : IntIdTable(name) {
        // nullable column is here as Oracle treat '' as NULL
        val column: Column<String?> = if (isTextColumn) {
            text("test_column").default(default).nullable()
        } else {
            varchar("test_column", 255).default(default).nullable()
        }
    }

    @Test
    fun `columns with default values that are whitespaces shouldn't be treated as empty strings`() {
        val tableWhitespaceDefaultVarchar = StringFieldTable("varchar_whitespace_test", false," ")

        val tableWhitespaceDefaultText = StringFieldTable("text_whitespace_test", true, " ")

        val tableEmptyStringDefaultVarchar = StringFieldTable("varchar_whitespace_test", false, "")

        val tableEmptyStringDefaultText = StringFieldTable("text_whitespace_test", true, "")

        // SQLite doesn't support alter table with add column, so it doesn't generate the statements, hence excluded
        withDb(excludeSettings = listOf(TestDB.SQLITE)) { testDb ->
            // MySQL doesn't support default values on text columns, hence excluded
            val supportsTextDefault = testDb !in listOf(TestDB.MYSQL)
            val tablesToTest = listOfNotNull(
                tableWhitespaceDefaultVarchar to tableEmptyStringDefaultVarchar,
                (tableWhitespaceDefaultText to tableEmptyStringDefaultText).takeIf { supportsTextDefault },
            )
            tablesToTest.forEach { (whiteSpaceTable, emptyTable) ->
                try {
                    SchemaUtils.create(whiteSpaceTable)

                    val whiteSpaceId = whiteSpaceTable.insertAndGetId { }

                    assertEquals(" ", whiteSpaceTable.select { whiteSpaceTable.id eq whiteSpaceId }.single()[whiteSpaceTable.column])

                    val actual = SchemaUtils.statementsRequiredToActualizeScheme(emptyTable)
                    assertEquals(1, actual.size)

                    // SQL Server requires drop/create constraint to change defaults, unsupported for now
                    // Oracle treat '' as NULL column and can't alter from NULL to NULL
                    if (testDb !in listOf(TestDB.SQLSERVER, TestDB.ORACLE)) {
                        // Apply changes
                        actual.forEach { exec(it) }
                    } else {
                        SchemaUtils.drop(whiteSpaceTable)
                        SchemaUtils.create(whiteSpaceTable)
                    }

                    val emptyId = emptyTable.insertAndGetId { }

                    // null is here as Oracle treat '' as NULL
                    val expectedEmptyValue = when (testDb) {
                        TestDB.ORACLE, TestDB.H2_ORACLE -> null
                        else -> ""
                    }

                    assertEquals(expectedEmptyValue, emptyTable.select { emptyTable.id eq emptyId }.single()[emptyTable.column])
                } finally {
                    SchemaUtils.drop(whiteSpaceTable, emptyTable)
                }
            }
        }
    }

    @Test
    fun testAddMissingColumnsStatementsChangeDefault() {
        val t1 = object : Table("foo") {
            val id = integer("idcol")
            val col = integer("col").nullable()
            val strcol = varchar("strcol", 255).nullable()

            override val primaryKey = PrimaryKey(id)
        }

        val t2 = object : Table("foo") {
            val id = integer("idcol")
            val col = integer("col").default(1)
            val strcol = varchar("strcol", 255).default("def")

            override val primaryKey = PrimaryKey(id)
        }

        val excludeSettings = listOf(TestDB.SQLITE, TestDB.SQLSERVER)
        val complexAlterTable = listOf(TestDB.POSTGRESQL, TestDB.POSTGRESQLNG, TestDB.ORACLE, TestDB.H2_PSQL)
        withDb(excludeSettings = excludeSettings) { testDb ->
            try {
                SchemaUtils.createMissingTablesAndColumns(t1)

                val missingStatements = SchemaUtils.addMissingColumnsStatements(t2)

                if (testDb !in complexAlterTable) {
                    val alterColumnWord = when (currentDialectTest) {
                        is MysqlDialect -> "MODIFY COLUMN"
                        is OracleDialect -> "MODIFY"
                        else -> "ALTER COLUMN"
                    }
                    val expected = setOf(
                        "ALTER TABLE ${t2.nameInDatabaseCase()} $alterColumnWord ${t2.col.nameInDatabaseCase()} ${t2.col.columnType.sqlType()} DEFAULT 1 NOT NULL",
                        "ALTER TABLE ${t2.nameInDatabaseCase()} $alterColumnWord ${t2.strcol.nameInDatabaseCase()} ${t2.strcol.columnType.sqlType()} DEFAULT 'def' NOT NULL",
                    )
                    assertEquals(expected, missingStatements.toSet())
                } else {
                    assertEquals(true, missingStatements.isNotEmpty())
                }

                missingStatements.forEach {
                    exec(it)
                }
            } finally {
                SchemaUtils.drop(t1)
            }
        }

        withDb(excludeSettings = excludeSettings) { testDb ->
            try {
                SchemaUtils.createMissingTablesAndColumns(t2)

                val missingStatements = SchemaUtils.addMissingColumnsStatements(t1)

                if (testDb !in complexAlterTable) {
                    val alterColumnWord = when (currentDialectTest) {
                        is MysqlDialect -> "MODIFY COLUMN"
                        is OracleDialect -> "MODIFY"
                        else -> "ALTER COLUMN"
                    }
                    val expected = setOf(
                        "ALTER TABLE ${t2.nameInDatabaseCase()} $alterColumnWord ${t1.col.nameInDatabaseCase()} ${t1.col.columnType.sqlType()} NULL",
                        "ALTER TABLE ${t2.nameInDatabaseCase()} $alterColumnWord ${t1.strcol.nameInDatabaseCase()} ${t1.strcol.columnType.sqlType()} NULL",
                    )
                    assertEquals(expected, missingStatements.toSet())
                } else {
                    assertEquals(true, missingStatements.isNotEmpty())
                }

                missingStatements.forEach {
                    exec(it)
                }
            } finally {
                SchemaUtils.drop(t2)
            }
        }
    }

    private enum class TestEnum { A, B, C }

    @Test
    fun `check that running addMissingTablesAndColumns multiple time doesnt affect schema`() {
        val table = object : Table("defaults2") {
            val bool1 = bool("boolCol1").default(false)
            val bool2 = bool("boolCol2").default(true)
            val int = integer("intCol").default(12345)
            val float = float("floatCol").default(123.45f)
            val decimal = decimal("decimalCol", 10, 1).default(BigDecimal.TEN)
            val string = varchar("varcharCol", 50).default("12345")
            val enum1 = enumeration("enumCol1", TestEnum::class).default(TestEnum.B)
            val enum2 = enumerationByName("enumCol2", 25, TestEnum::class).default(TestEnum.B)
        }

        withDb {
            try {
                SchemaUtils.create(table)
                assertEqualLists(emptyList(), SchemaUtils.statementsRequiredToActualizeScheme(table))
            } finally {
                SchemaUtils.drop(table)
            }
        }
    }

    @Test
    fun createTableWithMultipleIndexes() {
        withDb {
            try {
                SchemaUtils.createMissingTablesAndColumns(MultipleIndexesTable)
            } finally {
                SchemaUtils.drop(MultipleIndexesTable)
            }
        }
    }

    @Test
    fun testForeignKeyCreation() {
        val usersTable = object : IntIdTable("tmpusers") {}
        val spacesTable = object : IntIdTable("spaces") {
            val userId = reference("userId", usersTable)
        }

        withDb {
            SchemaUtils.createMissingTablesAndColumns(usersTable, spacesTable)
            assertTrue(usersTable.exists())
            assertTrue(spacesTable.exists())
            SchemaUtils.drop(usersTable, spacesTable)
        }
    }

    @Test
    fun testCamelCaseForeignKeyCreation() {
        val ordersTable = object : IntIdTable("tmporders") {
            val traceNumber = char("traceNumber", 10).uniqueIndex()
        }
        val receiptsTable = object : IntIdTable("receipts") {
            val traceNumber = reference("traceNumber", ordersTable.traceNumber)
        }

        withDb {
            SchemaUtils.createMissingTablesAndColumns(ordersTable, receiptsTable)
            assertTrue(ordersTable.exists())
            assertTrue(receiptsTable.exists())
            SchemaUtils.drop(ordersTable, receiptsTable)
        }
    }

    object MultipleIndexesTable : Table("H2_MULTIPLE_INDEXES") {
        val value1 = varchar("value1", 255)
        val value2 = varchar("value2", 255)

        init {
            uniqueIndex("index1", value1, value2)
            uniqueIndex("index2", value2, value1)
        }
    }

    @Test fun testCreateTableWithReferenceMultipleTimes() {
        withTables(PlayerTable, SessionsTable) {
            SchemaUtils.createMissingTablesAndColumns(PlayerTable, SessionsTable)
            SchemaUtils.createMissingTablesAndColumns(PlayerTable, SessionsTable)
        }
    }

    object PlayerTable : IntIdTable() {
        val username = varchar("username", 10).uniqueIndex().nullable()
    }

    object SessionsTable : IntIdTable() {
        val playerId = integer("player_id").references(PlayerTable.id)
    }

    @Test fun createTableWithReservedIdentifierInColumnName() {
        withDb(TestDB.MYSQL) {
            SchemaUtils.createMissingTablesAndColumns(T1, T2)
            SchemaUtils.createMissingTablesAndColumns(T1, T2)

            assertTrue(T1.exists())
            assertTrue(T2.exists())
        }
    }

    object ExplicitTable : IntIdTable() {
        val playerId = integer("player_id").references(PlayerTable.id, fkName = "Explicit_FK_NAME")
    }
    object NonExplicitTable : IntIdTable() {
        val playerId = integer("player_id").references(PlayerTable.id)
    }

    @Test fun explicitFkNameIsExplicit() {
        withTables(ExplicitTable, NonExplicitTable) {
            assertEquals("Explicit_FK_NAME", ExplicitTable.playerId.foreignKey!!.customFkName)
            assertEquals(null, NonExplicitTable.playerId.foreignKey!!.customFkName)
        }
    }

    object T1 : Table("ARRAY") {
        val name = integer("name").uniqueIndex()
        val tmp = varchar("temp", 255)
    }
    object T2 : Table("CHAIN") {
        val ref = integer("ref").references(T1.name)
    }

    @Test
    fun `test create table with name from system scheme`() {
        val usersTable = object : IdTable<String>("users") {
            override var id: Column<EntityID<String>> = varchar("id", 190).entityId()

            override val primaryKey = PrimaryKey(id)
        }
        withDb {
            try {
                SchemaUtils.createMissingTablesAndColumns(usersTable)
                assertTrue(usersTable.exists())
            } finally {
                SchemaUtils.drop(usersTable)
            }
        }
    }

    object CompositePrimaryKeyTable : Table("H2_COMPOSITE_PRIMARY_KEY") {
        val idA = varchar("id_a", 255)
        val idB = varchar("id_b", 255)
        override val primaryKey = PrimaryKey(idA, idB)
    }

    object CompositeForeignKeyTable : Table("H2_COMPOSITE_FOREIGN_KEY") {
        val idA = varchar("id_a", 255)
        val idB = varchar("id_b", 255)

        init {
            foreignKey(idA, idB, target = CompositePrimaryKeyTable.primaryKey)
        }
    }

    @Test
    fun testCreateCompositePrimaryKeyTableAndCompositeForeignKeyTableMultipleTimes() {
        withTables(CompositePrimaryKeyTable, CompositeForeignKeyTable) {
            SchemaUtils.createMissingTablesAndColumns(CompositePrimaryKeyTable, CompositeForeignKeyTable)
            SchemaUtils.createMissingTablesAndColumns(CompositePrimaryKeyTable, CompositeForeignKeyTable)
        }
    }
}

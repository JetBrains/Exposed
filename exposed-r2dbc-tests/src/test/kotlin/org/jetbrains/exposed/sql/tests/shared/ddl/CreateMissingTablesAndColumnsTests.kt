package org.jetbrains.exposed.sql.tests.shared.ddl

import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.r2dbc.sql.exists
import org.jetbrains.exposed.r2dbc.sql.insert
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectMetadataTest
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.inProperCase
import org.jetbrains.exposed.sql.tests.shared.assertEqualCollections
import org.jetbrains.exposed.sql.tests.shared.assertEqualLists
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.assertFailAndRollback
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.jetbrains.exposed.sql.vendors.OracleDialect
import org.jetbrains.exposed.sql.vendors.PrimaryKeyMetadata
import org.junit.Test
import java.math.BigDecimal
import java.util.*
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CreateMissingTablesAndColumnsTests : R2dbcDatabaseTestsBase() {
    @Test
    fun testCreateMissingTablesAndColumns01() = runTest {
        val testTable = object : Table("test_table") {
            val id = integer("id")
            val name = varchar("name", length = 42)
            val time = long("time").uniqueIndex()

            override val primaryKey = PrimaryKey(id)
        }

        withTables(excludeSettings = listOf(TestDB.H2_V2_MYSQL), tables = arrayOf(testTable)) {
            SchemaUtils.createMissingTablesAndColumns(testTable)
            assertTrue(testTable.exists())
            SchemaUtils.drop(testTable)
        }
    }

    @Test
    fun testCreateMissingTablesAndColumns02() = runTest {
        val testTable = object : IdTable<String>("Users2") {
            override val id: Column<EntityID<String>> = varchar("id", 64).clientDefault { UUID.randomUUID().toString() }.entityId()

            val name = varchar("name", 255)
            val email = varchar("email", 255).uniqueIndex()
            val camelCased = varchar("camelCased", 255).index()

            override val primaryKey = PrimaryKey(id)
        }

        withDb {
            SchemaUtils.createMissingTablesAndColumns(testTable)
            assertTrue(testTable.exists())
            try {
                SchemaUtils.createMissingTablesAndColumns(testTable)
            } finally {
                SchemaUtils.drop(testTable)
            }
        }
    }

    @Test
    fun testCreateMissingTablesAndColumnsChangeAutoincrement() = runTest {
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

        withDb(db = listOf(TestDB.H2_V2)) {
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
    fun testAddMissingColumnsStatementsChangeCasing() = runTest {
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
                } else {
                    null
                }

                assertEquals(expected, missingStatements.firstOrNull())

                SchemaUtils.drop(t1)
            }
        }
    }

    @Test
    fun testAddMissingColumnsStatementsIdentical() = runTest {
        val t1 = object : Table("foo") {
            val id = integer("idcol")

            override val primaryKey = PrimaryKey(id)
        }

        val t2 = object : Table("foo") {
            val id = integer("idcol")

            override val primaryKey = PrimaryKey(id)
        }

        withDb {
            SchemaUtils.createMissingTablesAndColumns(t1)

            val missingStatements = SchemaUtils.addMissingColumnsStatements(t2)

            assertEqualCollections(missingStatements, emptyList())

            SchemaUtils.drop(t1)
        }
    }

    @Test
    fun testAddMissingColumnsStatementsIdentical2() = runTest {
        val t1 = object : Table("foo") {
            val id = integer("idCol")

            override val primaryKey = PrimaryKey(id)
        }

        val t2 = object : Table("foo") {
            val id = integer("idCol")

            override val primaryKey = PrimaryKey(id)
        }

        withDb {
            SchemaUtils.createMissingTablesAndColumns(t1)

            val missingStatements = SchemaUtils.addMissingColumnsStatements(t2)

            assertEqualCollections(missingStatements, emptyList())

            SchemaUtils.drop(t1)
        }
    }

    @Test
    fun testCreateMissingTablesAndColumnsChangeCascadeType() = runTest {
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

    @Test
    fun addAutoPrimaryKey() = runTest {
        val tableName = "Foo"
        val initialTable = object : Table(tableName) {
            val bar = text("bar")
        }
        val t = IntIdTable(tableName)

        withTables(excludeSettings = TestDB.ALL_H2, tables = arrayOf(initialTable)) {
            assertEquals("ALTER TABLE ${tableName.inProperCase()} ADD ${"id".inProperCase()} ${t.id.columnType.sqlType()} PRIMARY KEY", t.id.ddl)
            assertEquals(1, currentDialectMetadataTest.tableColumns(t)[t]!!.size)
            SchemaUtils.createMissingTablesAndColumns(t)
            assertEquals(2, currentDialectMetadataTest.tableColumns(t)[t]!!.size)
        }
    }

    @Test
    fun testAddNewPrimaryKeyOnExistingColumn() = runTest {
        val tableName = "tester"
        val noPKTable = object : Table(tableName) {
            val bar = integer("bar")
        }

        val singlePKTable = object : Table(tableName) {
            val bar = integer("bar")

            override val primaryKey = PrimaryKey(bar)
        }

        withDb {
            SchemaUtils.createMissingTablesAndColumns(noPKTable)
            var primaryKey: PrimaryKeyMetadata? = currentDialectMetadataTest.existingPrimaryKeys(singlePKTable)[singlePKTable]
            assertNull(primaryKey)

            val expected = "ALTER TABLE ${tableName.inProperCase()} ADD PRIMARY KEY (${noPKTable.bar.nameInDatabaseCase()})"
            val statements = SchemaUtils.statementsRequiredToActualizeScheme(singlePKTable)
            assertEquals(expected, statements.single())

            SchemaUtils.createMissingTablesAndColumns(singlePKTable)
            primaryKey = currentDialectMetadataTest.existingPrimaryKeys(singlePKTable)[singlePKTable]
            assertNotNull(primaryKey)
            assertEquals("bar".inProperCase(), primaryKey.columnNames.single())

            SchemaUtils.drop(noPKTable)
        }
    }

    private enum class TestEnum { A, B, C }

    @Test
    fun `check that running addMissingTablesAndColumns multiple time doesnt affect schema`() = runTest {
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

        withDb(excludeSettings = TestDB.ALL_MARIADB + TestDB.ORACLE + TestDB.SQLSERVER) {
            try {
                SchemaUtils.create(table)
                assertEqualLists(emptyList(), SchemaUtils.statementsRequiredToActualizeScheme(table))
            } finally {
                SchemaUtils.drop(table)
            }
        }
    }

    @Test
    fun createTableWithMultipleIndexes() = runTest {
        withDb {
            try {
                SchemaUtils.createMissingTablesAndColumns(MultipleIndexesTable)
            } finally {
                SchemaUtils.drop(MultipleIndexesTable)
            }
        }
    }

    @Test
    fun testForeignKeyCreation() = runTest {
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
    fun testCamelCaseForeignKeyCreation() = runTest {
        val ordersTable = object : IntIdTable("tmporders") {
            val traceNumber = char("traceNumber", 10).uniqueIndex()
        }
        val receiptsTable = object : IntIdTable("receipts") {
            val traceNumber = reference("traceNumber", ordersTable.traceNumber)
        }

        // Oracle metadata only returns foreign keys that reference primary keys
        withDb(excludeSettings = listOf(TestDB.ORACLE)) {
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

    @Test
    fun testCreateTableWithReferenceMultipleTimes() = runTest {
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

    @Test
    fun createTableWithReservedIdentifierInColumnName() = runTest {
        withDb(TestDB.MYSQL_V5) {
            SchemaUtils.createMissingTablesAndColumns(T1, T2)
            SchemaUtils.createMissingTablesAndColumns(T1, T2)

            assertTrue(T1.exists())
            assertTrue(T2.exists())

            SchemaUtils.drop(T1, T2)
        }
    }

    object ExplicitTable : IntIdTable() {
        val playerId = integer("player_id").references(PlayerTable.id, fkName = "Explicit_FK_NAME")
    }

    object NonExplicitTable : IntIdTable() {
        val playerId = integer("player_id").references(PlayerTable.id)
    }

    @Test
    fun explicitFkNameIsExplicit() = runTest {
        withTables(PlayerTable, ExplicitTable, NonExplicitTable) {
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
    fun `test create table with name from system scheme`() = runTest {
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
    fun testCreateCompositePrimaryKeyTableAndCompositeForeignKeyTableMultipleTimes() = runTest {
        withTables(CompositePrimaryKeyTable, CompositeForeignKeyTable) {
            SchemaUtils.createMissingTablesAndColumns(CompositePrimaryKeyTable, CompositeForeignKeyTable)
            SchemaUtils.createMissingTablesAndColumns(CompositePrimaryKeyTable, CompositeForeignKeyTable)
            val statements = SchemaUtils.statementsRequiredToActualizeScheme(CompositePrimaryKeyTable, CompositeForeignKeyTable)
            println(statements)
            assertTrue(statements.isEmpty())
        }
    }

    @Test
    fun testCreateTableWithQuotedIdentifiers() = runTest {
        val identifiers = listOf("\"IdentifierTable\"", "\"IDentiFierCoLUmn\"")
        val quotedTable = object : Table(identifiers[0]) {
            val column1 = varchar(identifiers[1], 32)
        }

        withDb {
            try {
                SchemaUtils.createMissingTablesAndColumns(quotedTable)
                assertTrue(quotedTable.exists())

                val statements = SchemaUtils.statementsRequiredToActualizeScheme(quotedTable)
                assertTrue(statements.isEmpty())
            } finally {
                SchemaUtils.drop(quotedTable)
            }
        }
    }

    @Test
    fun testCreateCompositePrimaryKeyTableAndCompositeForeignKeyInVariousOrder() = runTest {
        withTables(CompositeForeignKeyTable, CompositePrimaryKeyTable) {
            SchemaUtils.createMissingTablesAndColumns(CompositePrimaryKeyTable, CompositeForeignKeyTable)
        }
        withTables(CompositeForeignKeyTable, CompositePrimaryKeyTable) {
            SchemaUtils.createMissingTablesAndColumns(CompositeForeignKeyTable, CompositePrimaryKeyTable)
        }
        withTables(CompositePrimaryKeyTable, CompositeForeignKeyTable) {
            SchemaUtils.createMissingTablesAndColumns(CompositePrimaryKeyTable, CompositeForeignKeyTable)
        }
        withTables(CompositePrimaryKeyTable, CompositeForeignKeyTable) {
            SchemaUtils.createMissingTablesAndColumns(CompositeForeignKeyTable, CompositePrimaryKeyTable)
        }
    }

    @Test
    fun testCreateTableWithSchemaPrefix() = runTest {
        val schemaName = "my_schema"
        // index and foreign key both use table name to auto-generate their own names & to compare metadata
        // default columns in SQL Server requires a named constraint that uses table name
        val parentTable = object : IntIdTable("$schemaName.parent_table") {
            val secondId = integer("second_id").uniqueIndex()
            val column1 = varchar("column_1", 32).default("TEST")
        }
        val childTable = object : LongIdTable("$schemaName.child_table") {
            val parent = reference("my_parent", parentTable)
        }

        withDb(excludeSettings = listOf(TestDB.ORACLE, TestDB.SQLSERVER, TestDB.POSTGRESQL)) { testDb ->
            val schema = if (testDb == TestDB.SQLSERVER) {
                Schema(schemaName, "guest")
            } else {
                Schema(schemaName)
            }

            // Should not require to be in the same schema
            SchemaUtils.createSchema(schema)
            SchemaUtils.create(parentTable, childTable)

            try {
                // Try in different schema
                SchemaUtils.createMissingTablesAndColumns(parentTable, childTable)
                assertTrue(parentTable.exists())
                assertTrue(childTable.exists())

                // Try in the same schema
                if (testDb != TestDB.ORACLE) {
                    SchemaUtils.setSchema(schema)
                    SchemaUtils.createMissingTablesAndColumns(parentTable, childTable)
                    assertTrue(parentTable.exists())
                    assertTrue(childTable.exists())
                }
            } finally {
                if (testDb == TestDB.SQLSERVER) {
                    SchemaUtils.drop(childTable, parentTable)
                    SchemaUtils.dropSchema(schema)
                } else {
                    SchemaUtils.dropSchema(schema, cascade = true)
                }
            }
        }
    }

    @Test
    fun testNoChangesOnCreateMissingNullableColumns() = runTest {
        val testerWithDefaults = object : Table("tester") {
            val defaultNullNumber = integer("default_null_number").nullable().default(null)
            val defaultNullWord = varchar("default_null_word", 8).nullable().default(null)
            val nullNumber = integer("null_number").nullable()
            val nullWord = varchar("null_word", 8).nullable()
            val defaultNumber = integer("default_number").default(999).nullable()
            val defaultWord = varchar("default_word", 8).default("Hello").nullable()
        }

        val testerWithoutDefaults = object : Table("tester") {
            val defaultNullNumber = integer("default_null_number").nullable()
            val defaultNullWord = varchar("default_null_word", 8).nullable()
            val nullNumber = integer("null_number").nullable()
            val nullWord = varchar("null_word", 8).nullable()
            val defaultNumber = integer("default_number").default(999).nullable()
            val defaultWord = varchar("default_word", 8).default("Hello").nullable()
        }

        listOf(
            testerWithDefaults to testerWithDefaults,
            testerWithDefaults to testerWithoutDefaults,
            testerWithoutDefaults to testerWithDefaults,
            testerWithoutDefaults to testerWithoutDefaults
        ).forEach { (existingTable, definedTable) ->
            withTables(TestDB.ALL_MARIADB + TestDB.ORACLE + TestDB.SQLSERVER, existingTable) {
                SchemaUtils.statementsRequiredToActualizeScheme(definedTable).also {
                    assertTrue(it.isEmpty())
                }
            }
        }
    }

    @Test
    fun testFloatDefaultColumnValue() = runTest {
        val tester = object : Table("testFloatDefaultColumnValue") {
            val float = float("float_value").default(30.0f)
            val double = double("double_value").default(30.0)
            val floatExpression = float("float_expression_value").defaultExpression(floatLiteral(30.0f))
            val doubleExpression = double("double_expression_value").defaultExpression(doubleLiteral(30.0))
        }
        withTables(TestDB.ALL_MARIADB + TestDB.ORACLE + TestDB.SQLSERVER, tester) {
            assertEqualLists(emptyList(), SchemaUtils.statementsRequiredToActualizeScheme(tester))
        }
    }
}

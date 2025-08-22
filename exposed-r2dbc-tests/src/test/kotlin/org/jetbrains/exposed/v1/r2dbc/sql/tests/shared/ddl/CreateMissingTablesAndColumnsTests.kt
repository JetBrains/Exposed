package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.ddl

import kotlinx.coroutines.flow.single
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.vendors.MysqlDialect
import org.jetbrains.exposed.v1.core.vendors.OracleDialect
import org.jetbrains.exposed.v1.core.vendors.PrimaryKeyMetadata
import org.jetbrains.exposed.v1.core.vendors.inProperCase
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.exists
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.currentDialectMetadataTest
import org.jetbrains.exposed.v1.r2dbc.tests.currentDialectTest
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEqualCollections
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEqualLists
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertFailAndRollback
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertTrue
import org.jetbrains.exposed.v1.r2dbc.tests.shared.expectException
import org.junit.Test
import java.math.BigDecimal
import java.util.*
import kotlin.properties.Delegates
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CreateMissingTablesAndColumnsTests : R2dbcDatabaseTestsBase() {

    @Test
    fun testCreateMissingTablesAndColumns01() {
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
    fun testCreateMissingTablesAndColumns02() {
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
    fun testCreateMissingTablesAndColumnsChangeNullability() {
        val t1 = object : IntIdTable("foo") {
            val foo = varchar("foo", 50)
        }

        val t2 = object : IntIdTable("foo") {
            val foo = varchar("foo", 50).nullable()
        }

        withDb {
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

                val missingStatements = org.jetbrains.exposed.v1.r2dbc.SchemaUtils.addMissingColumnsStatements(t2)

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
            SchemaUtils.createMissingTablesAndColumns(t1)

            val missingStatements = org.jetbrains.exposed.v1.r2dbc.SchemaUtils.addMissingColumnsStatements(t2)

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
            SchemaUtils.createMissingTablesAndColumns(t1)

            val missingStatements = org.jetbrains.exposed.v1.r2dbc.SchemaUtils.addMissingColumnsStatements(t2)

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

    @OptIn(InternalApi::class)
    @Test
    fun addAutoPrimaryKey() {
        val tableName = "Foo"
        val initialTable = object : Table(tableName) {
            val bar = text("bar")
        }
        val t = IntIdTable(tableName)

        withTables(excludeSettings = TestDB.ALL_H2_V2, tables = arrayOf(initialTable)) {
            assertEquals("ALTER TABLE ${tableName.inProperCase()} ADD ${"id".inProperCase()} ${t.id.columnType.sqlType()} PRIMARY KEY", t.id.ddl)
            assertEquals(1, currentDialectMetadataTest.tableColumns(t)[t]!!.size)
            SchemaUtils.createMissingTablesAndColumns(t)
            assertEquals(2, currentDialectMetadataTest.tableColumns(t)[t]!!.size)
        }
    }

    @OptIn(InternalApi::class)
    @Test
    fun testAddNewPrimaryKeyOnExistingColumn() {
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

    @Test
    fun columnsWithDefaultValuesThatHaveNotChangedShouldNotTriggerChange() {
        var table by Delegates.notNull<Table>()
        withDb { testDb ->
            try {
                // MySQL doesn't support default values on text columns, hence excluded
                table = if (testDb !in TestDB.ALL_MYSQL) {
                    object : Table("varchar_test") {
                        val varchar = varchar("varchar_column", 255).default(" ")
                        val text = text("text_column").default(" ")
                    }
                } else {
                    object : Table("varchar_test") {
                        val varchar = varchar("varchar_column", 255).default(" ")
                    }
                }

                org.jetbrains.exposed.v1.r2dbc.SchemaUtils.create(table)
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
    fun columnsWithDefaultValuesThatAreWhitespacesShouldNotBeTreatedAsEmptyStrings() {
        val tableWhitespaceDefaultVarchar = StringFieldTable("varchar_whitespace_test", false, " ")

        val tableWhitespaceDefaultText = StringFieldTable("text_whitespace_test", true, " ")

        val tableEmptyStringDefaultVarchar = StringFieldTable("varchar_whitespace_test", false, "")

        val tableEmptyStringDefaultText = StringFieldTable("text_whitespace_test", true, "")

        withDb { testDb ->
            // MySQL doesn't support default values on text columns, hence excluded
            val supportsTextDefault = testDb !in TestDB.ALL_MYSQL
            val tablesToTest = listOfNotNull(
                tableWhitespaceDefaultVarchar to tableEmptyStringDefaultVarchar,
                (tableWhitespaceDefaultText to tableEmptyStringDefaultText).takeIf { supportsTextDefault },
            )
            tablesToTest.forEach { (whiteSpaceTable, emptyTable) ->
                try {
                    org.jetbrains.exposed.v1.r2dbc.SchemaUtils.create(whiteSpaceTable)

                    val whiteSpaceId = whiteSpaceTable.insertAndGetId { }

                    assertEquals(
                        " ",
                        whiteSpaceTable.selectAll().where {
                            whiteSpaceTable.id eq whiteSpaceId
                        }.single()[whiteSpaceTable.column]
                    )

                    val actual = SchemaUtils.statementsRequiredToActualizeScheme(emptyTable)
                    val expected = if (testDb == TestDB.SQLSERVER) 2 else 1
                    assertEquals(expected, actual.size)

                    // Oracle treat '' as NULL column and can't alter from NULL to NULL
                    if (testDb != TestDB.ORACLE) {
                        // Apply changes
                        actual.forEach { exec(it) }
                    } else {
                        SchemaUtils.drop(whiteSpaceTable)
                        org.jetbrains.exposed.v1.r2dbc.SchemaUtils.create(emptyTable)
                    }

                    val emptyId = emptyTable.insertAndGetId { }

                    // null is here as Oracle treat '' as NULL
                    val expectedEmptyValue = when (testDb) {
                        TestDB.ORACLE, TestDB.H2_V2_ORACLE -> null
                        else -> ""
                    }

                    assertEquals(
                        expectedEmptyValue,
                        emptyTable.selectAll().where { emptyTable.id eq emptyId }.single()[emptyTable.column]
                    )
                } finally {
                    SchemaUtils.drop(whiteSpaceTable, emptyTable)
                }
            }
        }
    }

    @Test
    @Suppress("MaximumLineLength")
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

        val complexAlterTable = TestDB.ALL_POSTGRES_LIKE + TestDB.ORACLE + TestDB.SQLSERVER
        withDb { testDb ->
            try {
                SchemaUtils.createMissingTablesAndColumns(t1)

                val missingStatements = org.jetbrains.exposed.v1.r2dbc.SchemaUtils.addMissingColumnsStatements(t2)

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

        withDb { testDb ->
            try {
                SchemaUtils.createMissingTablesAndColumns(t2)

                val missingStatements = org.jetbrains.exposed.v1.r2dbc.SchemaUtils.addMissingColumnsStatements(t1)

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
                org.jetbrains.exposed.v1.r2dbc.SchemaUtils.create(table)
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
    fun testCreateTableWithReferenceMultipleTimes() {
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
    fun createTableWithReservedIdentifierInColumnName() {
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
    fun explicitFkNameIsExplicit() {
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
            val statements = SchemaUtils.statementsRequiredToActualizeScheme(CompositePrimaryKeyTable, CompositeForeignKeyTable)
            assertTrue(statements.isEmpty())
        }
    }

    @Test
    fun testCreateTableWithQuotedIdentifiers() {
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
    fun testCreateCompositePrimaryKeyTableAndCompositeForeignKeyInVariousOrder() {
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
    fun testCreateTableWithSchemaPrefix() {
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

        withDb { testDb ->
            val schema = if (testDb == TestDB.SQLSERVER) {
                Schema(schemaName, "guest")
            } else {
                Schema(schemaName)
            }

            // Should not require to be in the same schema
            org.jetbrains.exposed.v1.r2dbc.SchemaUtils.createSchema(schema)
            org.jetbrains.exposed.v1.r2dbc.SchemaUtils.create(parentTable, childTable)

            try {
                // Try in different schema
                SchemaUtils.createMissingTablesAndColumns(parentTable, childTable)
                assertTrue(parentTable.exists())
                assertTrue(childTable.exists())

                // Try in the same schema
                if (testDb != TestDB.ORACLE) {
                    org.jetbrains.exposed.v1.r2dbc.SchemaUtils.setSchema(schema)
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
    fun testNoChangesOnCreateMissingNullableColumns() {
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
            withTables(existingTable) {
                SchemaUtils.statementsRequiredToActualizeScheme(definedTable).also {
                    assertTrue(it.isEmpty())
                }
            }
        }
    }

    @Test
    fun testChangesOnCreateMissingNullableColumns() {
        val testerWithDefaults = object : Table("tester") {
            val defaultNullString = varchar("default_null_string", 8).nullable().default("NULL")
            val defaultNumber = integer("default_number").default(999).nullable()
            val defaultWord = varchar("default_word", 8).default("Hello").nullable()
        }

        val testerWithoutDefaults = object : Table("tester") {
            val defaultNullString = varchar("default_null_string", 8).nullable()
            val defaultNumber = integer("default_number").nullable()
            val defaultWord = varchar("default_word", 8).nullable()
        }

        listOf(
            testerWithDefaults to testerWithoutDefaults,
            testerWithoutDefaults to testerWithDefaults,
        ).forEach { (existingTable, definedTable) ->
            withTables(existingTable) {
                SchemaUtils.statementsRequiredToActualizeScheme(definedTable).also {
                    assertTrue(it.isNotEmpty())
                }
            }
        }

        listOf(
            testerWithDefaults to testerWithDefaults,
            testerWithoutDefaults to testerWithoutDefaults
        ).forEach { (existingTable, definedTable) ->
            withTables(existingTable) {
                SchemaUtils.statementsRequiredToActualizeScheme(definedTable).also {
                    assertTrue(it.isEmpty())
                }
            }
        }
    }

    @Test
    fun testFloatDefaultColumnValue() {
        val tester = object : Table("testFloatDefaultColumnValue") {
            val float = float("float_value").default(30.0f)
            val double = double("double_value").default(30.0)
            val floatExpression = float("float_expression_value").defaultExpression(floatLiteral(30.0f))
            val doubleExpression = double("double_expression_value").defaultExpression(doubleLiteral(30.0))
        }
        withTables(tester) {
            assertEqualLists(emptyList(), SchemaUtils.statementsRequiredToActualizeScheme(tester))
        }
    }

    @Test
    fun testColumnTypesWithDefinedSizeAndScale() {
        val originalTable = object : Table("tester") {
            val tax = decimal("tax", 3, 1)
            val address = varchar("address", 8)
            val zip = binary("zip", 1)
            val province = char("province", 1)
        }
        val newTable = object : Table("tester") {
            val tax = decimal("tax", 6, 3)
            val address = varchar("address", 16)
            val zip = binary("zip", 2)
            val province = char("province", 2)
        }

        val taxValue = 123.456.toBigDecimal()
        val addressValue = "A".repeat(16)
        val zipValue = "BB".toByteArray()
        val provinceValue = "CC"

        withTables(originalTable) { testDb ->
            assertTrue(SchemaUtils.statementsRequiredToActualizeScheme(originalTable, withLogs = false).isEmpty())

            expectException<IllegalArgumentException> {
                originalTable.insert {
                    it[tax] = taxValue
                    it[address] = addressValue
                    it[zip] = zipValue
                    it[province] = provinceValue
                }
            }

            val alterStatements = SchemaUtils.statementsRequiredToActualizeScheme(newTable, withLogs = false)
            val expectedSize = if (testDb in TestDB.ALL_POSTGRES_LIKE) {
                3
            } else {
                4
            }
            assertEquals(expectedSize, alterStatements.size)
            alterStatements.forEach { exec(it) }

            newTable.insert {
                it[tax] = taxValue
                it[address] = addressValue
                it[zip] = zipValue
                it[province] = provinceValue
            }
        }
    }
}

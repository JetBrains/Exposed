package org.jetbrains.exposed.v1.migration.jdbc

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nl.altindag.log.LogCaptor
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.dao.id.UIntIdTable
import org.jetbrains.exposed.v1.core.dao.id.ULongIdTable
import org.jetbrains.exposed.v1.core.vendors.PrimaryKeyMetadata
import org.jetbrains.exposed.v1.core.vendors.SQLiteDialect
import org.jetbrains.exposed.v1.core.vendors.inProperCase
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.exists
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.json.jsonb
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.currentDialectMetadataTest
import org.jetbrains.exposed.v1.tests.currentDialectTest
import org.jetbrains.exposed.v1.tests.shared.assertEqualCollections
import org.jetbrains.exposed.v1.tests.shared.assertEqualLists
import org.jetbrains.exposed.v1.tests.shared.assertEquals
import org.jetbrains.exposed.v1.tests.shared.assertTrue
import org.junit.Test
import kotlin.properties.Delegates
import kotlin.test.assertNull
import org.jetbrains.exposed.v1.datetime.date as kotlinDatetimeDate
import org.jetbrains.exposed.v1.javatime.date as javatimeDate

class DatabaseMigrationTests : DatabaseTestsBase() {
    private val columnTypeChangeUnsupportedDb = TestDB.ALL - TestDB.ALL_H2_V2

    @OptIn(InternalApi::class)
    @Test
    fun testCreateStatementsGeneratedForTablesThatDoNotExist() {
        val tester = object : Table("tester") {
            val bar = char("bar")
        }

        withDb {
            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(tester, withLogs = false)
            assertEquals(1, statements.size)
            assertEquals(
                "CREATE TABLE ${addIfNotExistsIfSupported()}${tester.nameInDatabaseCase()} " +
                    "(${"bar".inProperCase()} CHAR NOT NULL)",
                statements.first()
            )
        }
    }

    @Test
    fun testDropUnmappedColumnsStatementsIdentical() {
        val t1 = object : Table("foo") {
            val col1 = integer("col1")
            val col2 = integer("CoL2")
            val col3 = integer("\"CoL3\"")
        }

        val t2 = object : Table("foo") {
            val col1 = integer("col1")
            val col2 = integer("CoL2")
            val col3 = integer("\"CoL3\"")
        }

        withTables(t1) {
            val statements = MigrationUtils.dropUnmappedColumnsStatements(t2, withLogs = false)
            assertEqualCollections(statements, emptyList())
        }
    }

    @Test
    fun testDropUnmappedColumns() {
        val t1 = object : Table("foo") {
            val id = integer("id")
            val name = text("name")
        }

        val t2 = object : Table("foo") {
            val id = integer("id")
        }

        withTables(excludeSettings = listOf(TestDB.ORACLE), t1) {
            assertEqualCollections(MigrationUtils.statementsRequiredForDatabaseMigration(t1, withLogs = false), emptyList())

            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(t2, withLogs = false)
            assertEquals(1, statements.size)
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

        withTables(noPKTable) {
            val primaryKey: PrimaryKeyMetadata? = currentDialectMetadataTest.existingPrimaryKeys(singlePKTable)[singlePKTable]
            assertNull(primaryKey)

            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(singlePKTable, withLogs = false)
            if (currentDialectTest is SQLiteDialect) {
                assertTrue(statements.isEmpty())
            } else {
                val expected = "ALTER TABLE ${tableName.inProperCase()} ADD PRIMARY KEY (${noPKTable.bar.nameInDatabaseCase()})"
                assertEquals(expected, statements.single())
            }
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

                SchemaUtils.create(table)
                val actual = MigrationUtils.statementsRequiredForDatabaseMigration(table, withLogs = false)
                assertEqualLists(emptyList(), actual)
            } finally {
                SchemaUtils.drop(table)
            }
        }
    }

    @Test
    fun testCreateTableWithQuotedIdentifiers() {
        val identifiers = listOf("\"IdentifierTable\"", "\"IDentiFierCoLUmn\"")
        val quotedTable = object : Table(identifiers[0]) {
            val column1 = varchar(identifiers[1], 32)
        }

        withTables(quotedTable) {
            assertTrue(quotedTable.exists())

            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(quotedTable, withLogs = false)
            assertTrue(statements.isEmpty())
        }
    }

    @Test
    fun testNumericTypeLiteralsAsDefaultsDoNotTriggerMigrationStatements() {
        val tester = object : Table("tester") {
            val byte = byte("byte_column").defaultExpression(byteLiteral(Byte.MIN_VALUE))
            val ubyte = ubyte("ubyte_column").defaultExpression(ubyteLiteral(UByte.MAX_VALUE))
            val short = short("short_column").defaultExpression(shortLiteral(Short.MIN_VALUE))
            val ushort = ushort("ushort_column").defaultExpression(ushortLiteral(UShort.MAX_VALUE))
            val integer = integer("integer_column").defaultExpression(intLiteral(Int.MIN_VALUE))
            val uinteger = uinteger("uinteger_column").defaultExpression(uintLiteral(UInt.MAX_VALUE))
            val long = long("long_column").defaultExpression(longLiteral(Long.MIN_VALUE))
            val ulong = ulong("ulong_column").defaultExpression(ulongLiteral(Long.MAX_VALUE.toULong()))
            val float = float("float_column").defaultExpression(floatLiteral(3.14159F))
            val double = double("double_column").defaultExpression(doubleLiteral(3.1415926535))
            val decimal = decimal("decimal_column", 6, 3).defaultExpression(decimalLiteral(123.456.toBigDecimal()))
        }

        withTables(tester) {
            assertEqualLists(emptyList(), MigrationUtils.statementsRequiredForDatabaseMigration(tester, withLogs = false))
        }
    }

    @OptIn(InternalApi::class)
    @Test
    fun testNoColumnTypeChangeStatementsGenerated() {
        withDb(excludeSettings = columnTypeChangeUnsupportedDb) {
            try {
                SchemaUtils.create(MigrationTestsData.ColumnTypesTester)

                val columns = MigrationTestsData.ColumnTypesTester.columns.sortedBy { it.name.uppercase() }
                val columnsMetadata = connection.metadata {
                    requireNotNull(columns(MigrationTestsData.ColumnTypesTester)[MigrationTestsData.ColumnTypesTester])
                }.toSet().sortedBy { it.name.uppercase() }
                columnsMetadata.forEachIndexed { index, columnMetadataItem ->
                    val columnType = columns[index].columnType.sqlType()
                    val columnMetadataSqlType = columnMetadataItem.sqlType

                    assertTrue(currentDialectMetadataTest.areEquivalentColumnTypes(columnMetadataSqlType, columnMetadataItem.jdbcType, columnType))
                    assertTrue(currentDialectTest.areEquivalentColumnTypes(columnMetadataSqlType, columnMetadataItem.jdbcType, columnType))
                }

                val statements = MigrationUtils.statementsRequiredForDatabaseMigration(MigrationTestsData.ColumnTypesTester, withLogs = false)
                assertTrue(statements.isEmpty())
            } finally {
                SchemaUtils.drop(MigrationTestsData.ColumnTypesTester)
            }
        }
    }

    @OptIn(InternalApi::class)
    @Test
    fun testCorrectColumnTypeChangeStatementsGenerated() {
        withDb(excludeSettings = columnTypeChangeUnsupportedDb) {
            val columns = MigrationTestsData.ColumnTypesTester.columns.sortedBy { it.name.uppercase() }

            columns.forEach { oldColumn ->
                val oldColumnWithModifiedName = Column(
                    table = oldColumn.table,
                    name = "tester_col",
                    columnType = oldColumn.columnType as IColumnType<Any>
                )
                val oldTable = object : Table("tester") {
                    override val columns: List<Column<*>>
                        get() = listOf(oldColumnWithModifiedName)
                }

                withTables(oldTable) {
                    val columnsMetadata = connection.metadata {
                        requireNotNull(columns(oldTable)[oldTable])
                    }.toSet()
                    val oldColumnMetadataItem = columnsMetadata.single()

                    for (newColumn in columns) {
                        if (
                            currentDialectMetadataTest.areEquivalentColumnTypes(
                                oldColumnMetadataItem.sqlType,
                                oldColumnMetadataItem.jdbcType,
                                newColumn.columnType.sqlType()
                            )
                        ) {
                            continue
                        }

                        val newColumnWithModifiedName = Column(
                            table = newColumn.table,
                            name = "tester_col",
                            columnType = newColumn.columnType as IColumnType<Any>
                        )
                        val newTable = object : Table("tester") {
                            override val columns: List<Column<*>>
                                get() = listOf(newColumnWithModifiedName)
                        }

                        val statements = MigrationUtils.statementsRequiredForDatabaseMigration(newTable, withLogs = false)
                        assertEquals(1, statements.size)
                    }
                }
            }
        }
    }

    @OptIn(InternalApi::class)
    @Test
    fun testNoColumnTypeChangeStatementsGeneratedForArrayColumnType() {
        withTables(TestDB.ALL - setOf(TestDB.H2_V2, TestDB.H2_V2_PSQL), MigrationTestsData.ArraysTester) {
            val columnMetadata = connection.metadata {
                requireNotNull(columns(MigrationTestsData.ArraysTester)[MigrationTestsData.ArraysTester])
            }.toSet().sortedBy { it.name.uppercase() }
            val columns = MigrationTestsData.ArraysTester.columns.sortedBy { it.name.uppercase() }
            columnMetadata.forEachIndexed { index, columnMetadataItem ->
                val columnType = columns[index].columnType.sqlType()
                val columnMetadataSqlType = columnMetadataItem.sqlType

                assertTrue(currentDialectMetadataTest.areEquivalentColumnTypes(columnMetadataSqlType, columnMetadataItem.jdbcType, columnType))
                assertTrue(currentDialectTest.areEquivalentColumnTypes(columnMetadataSqlType, columnMetadataItem.jdbcType, columnType))
            }
            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(MigrationTestsData.ArraysTester, withLogs = false)
            assertTrue(statements.isEmpty())
        }
    }

    @OptIn(InternalApi::class)
    @Test
    fun testCorrectColumnTypeChangeStatementsGeneratedForArrayColumnType() {
        withDb(excludeSettings = TestDB.ALL - setOf(TestDB.H2_V2, TestDB.H2_V2_PSQL)) {
            val columns = MigrationTestsData.ArraysTester.columns.sortedBy { it.name.uppercase() }

            columns.forEach { oldColumn ->
                val oldColumnWithModifiedName = Column(
                    table = oldColumn.table,
                    name = "tester_col",
                    columnType = oldColumn.columnType as IColumnType<Any>
                )
                val oldTable = object : Table("tester") {
                    override val columns: List<Column<*>>
                        get() = listOf(oldColumnWithModifiedName)
                }

                withTables(oldTable) {
                    val columnsMetadata = connection.metadata {
                        requireNotNull(columns(oldTable)[oldTable])
                    }.toSet()
                    val oldColumnMetadataItem = columnsMetadata.single()

                    for (newColumn in columns) {
                        if (
                            currentDialectMetadataTest.areEquivalentColumnTypes(
                                oldColumnMetadataItem.sqlType,
                                oldColumnMetadataItem.jdbcType,
                                newColumn.columnType.sqlType()
                            )
                        ) {
                            continue
                        }

                        val newColumnWithModifiedName = Column(
                            table = newColumn.table,
                            name = "tester_col",
                            columnType = newColumn.columnType as IColumnType<Any>
                        )
                        val newTable = object : Table("tester") {
                            override val columns: List<Column<*>>
                                get() = listOf(newColumnWithModifiedName)
                        }

                        val statements = MigrationUtils.statementsRequiredForDatabaseMigration(newTable, withLogs = false)
                        assertEquals(1, statements.size)
                    }
                }
            }
        }
    }

    @Test
    fun testChangingIdTableType() {
        val intIdTable = object : IntIdTable("tester") {}
        val uintIdTable = object : UIntIdTable("tester") {}
        val longIdTable = object : LongIdTable("tester") {}
        val ulongIdTable = object : ULongIdTable("tester") {}

        val tables = listOf<IdTable<*>>(intIdTable, uintIdTable, longIdTable, ulongIdTable)

        withDb(excludeSettings = columnTypeChangeUnsupportedDb) {
            tables.forEach { oldTable ->
                for (newTable in tables) {
                    val oldIdColumn = (oldTable.id.columnType as EntityIDColumnType<*>).idColumn
                    val newIdColumn = (newTable.id.columnType as EntityIDColumnType<*>).idColumn

                    if (oldIdColumn.columnType == newIdColumn.columnType) {
                        continue
                    }

                    withTables(oldTable) {
                        assertTrue(MigrationUtils.statementsRequiredForDatabaseMigration(oldTable, withLogs = false).isEmpty())

                        val statements = MigrationUtils.statementsRequiredForDatabaseMigration(newTable, withLogs = false)

                        var expectedSize = 0
                        if (oldTable.ddl.any { it.contains("CHECK") } || newTable.ddl.any { it.contains("CHECK") }) {
                            expectedSize++ // Statement for adding or dropping the CHECK constraint
                        }
                        if (oldIdColumn.columnType.sqlType() != newIdColumn.columnType.sqlType()) {
                            expectedSize++ // Statement for changing the column type
                        }
                        assertEquals(expectedSize, statements.size)

                        statements.forEach(::exec)
                        newTable.insert {}
                    }
                }
            }
        }
    }

    @Serializable
    private class TestClient(val id: String, val name: String)

    @Test
    fun testMigrationGenerationForDatabaseGeneratedColumn() {
        val tester = object : Table("tester") {
            val doc = jsonb<TestClient>("doc", Json.Default)
            val id = text("id")
                .databaseGenerated()
                .withDefinition("GENERATED ALWAYS AS (doc ->> 'id') stored")
                .uniqueIndex()
        }

        withTables(excludeSettings = TestDB.ALL - TestDB.ALL_POSTGRES, tester) {
            assertEqualLists(MigrationUtils.statementsRequiredForDatabaseMigration(tester), emptyList())

            val testerWithAnotherDefinition = object : Table("tester") {
                val doc = jsonb<TestClient>("doc", Json.Default)
                val id = text("id")
                    .databaseGenerated()
                    .withDefinition("GENERATED ALWAYS AS (doc ->> 'name') stored")
                    .uniqueIndex()
            }

            assertEqualLists(MigrationUtils.statementsRequiredForDatabaseMigration(testerWithAnotherDefinition), emptyList())
        }
    }

    @Test
    fun testJavatimeCurrentDateAsDefaultExpression() {
        val testTable = object : LongIdTable("test_table") {
            val date = javatimeDate("date").index()
                .defaultExpression(org.jetbrains.exposed.v1.javatime.CurrentDate)
        }
        withTables(testTable) {
            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(testTable)
            assertTrue(statements.isEmpty())
        }
    }

    @Test
    fun testKotlinDatetimeCurrentDateAsDefaultExpression() {
        val testTable = object : LongIdTable("test_table") {
            val date = kotlinDatetimeDate("date").index()
                .defaultExpression(org.jetbrains.exposed.v1.datetime.CurrentDate)
        }
        withTables(testTable) {
            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(testTable)
            assertTrue(statements.isEmpty())
        }
    }

    @Test
    fun testMetadataRetrievalLogsAreSilent() {
        val logCaptor = LogCaptor.forName(exposedLogger.name)
        logCaptor.setLogLevelToDebug()

        withDb {
            // rely directly on DatabaseMetaData methods
            currentDialectMetadataTest.existingPrimaryKeys(MigrationTestsData.ColumnTypesTester)
            currentDialectMetadataTest.existingIndices(MigrationTestsData.ColumnTypesTester)
            currentDialectMetadataTest.allTablesNames()

            assertTrue(logCaptor.debugLogs.isEmpty())

            // rely directly on DatabaseMetaData methods - UNLESS MySQL
            currentDialectMetadataTest.columnConstraints()

            assertTrue(logCaptor.debugLogs.isEmpty())

            // rely on SQL string + connection execution
            connection.metadata { databaseDialectMode }
            connection.metadata { supportsLimitWithUpdateOrDelete() }
            currentDialectMetadataTest.existingCheckConstraints(MigrationTestsData.ColumnTypesTester)
            currentDialectMetadataTest.sequences()
            currentDialectMetadataTest.existingSequences(MigrationTestsData.ColumnTypesTester)

            assertTrue(logCaptor.debugLogs.isEmpty())
        }

        logCaptor.resetLogLevel()
        logCaptor.clearLogs()
        logCaptor.close()
    }
}

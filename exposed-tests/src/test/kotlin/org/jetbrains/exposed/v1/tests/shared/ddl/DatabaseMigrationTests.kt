package org.jetbrains.exposed.v1.tests.shared.ddl

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.dao.id.UIntIdTable
import org.jetbrains.exposed.v1.core.dao.id.ULongIdTable
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.core.vendors.MysqlDialect
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.core.vendors.PrimaryKeyMetadata
import org.jetbrains.exposed.v1.core.vendors.inProperCase
import org.jetbrains.exposed.v1.datetime.*
import org.jetbrains.exposed.v1.jdbc.exists
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.json.json
import org.jetbrains.exposed.v1.json.jsonb
import org.jetbrains.exposed.v1.migration.MigrationUtils
import org.jetbrains.exposed.v1.money.CurrencyColumnType
import org.jetbrains.exposed.v1.money.currency
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.currentDialectMetadataTest
import org.jetbrains.exposed.v1.tests.currentDialectTest
import org.jetbrains.exposed.v1.tests.shared.assertEqualCollections
import org.jetbrains.exposed.v1.tests.shared.assertEqualLists
import org.jetbrains.exposed.v1.tests.shared.assertEquals
import org.jetbrains.exposed.v1.tests.shared.assertFalse
import org.jetbrains.exposed.v1.tests.shared.assertTrue
import org.jetbrains.exposed.v1.tests.shared.ddl.EnumerationTests.Foo
import org.jetbrains.exposed.v1.tests.shared.ddl.EnumerationTests.PGEnum
import org.jetbrains.exposed.v1.tests.shared.expectException
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.*
import kotlin.properties.Delegates
import kotlin.test.assertNull

@OptIn(ExperimentalDatabaseMigrationApi::class)
@Suppress("LargeClass")
class DatabaseMigrationTests : DatabaseTestsBase() {

    private val columnTypeChangeUnsupportedDb = TestDB.ALL - TestDB.ALL_H2_V2

    private lateinit var sequence: Sequence

    @Before
    fun dropAllSequences() {
        withDb {
            sequence = Sequence(
                name = "my_sequence",
                startWith = 1,
                minValue = 1,
                maxValue = currentDialectTest.sequenceMaxValue
            )

            if (currentDialectTest.supportsCreateSequence) {
                val allSequences = currentDialectMetadataTest.sequences().map { name -> Sequence(name) }.toSet()
                allSequences.forEach { sequence ->
                    val dropStatements = sequence.dropStatement()
                    dropStatements.forEach { statement ->
                        exec(statement)
                    }
                }
            }
        }
    }

    @Test
    fun testMigrationScriptDirectoryAndContent() {
        val tableName = "tester"
        val noPKTable = object : Table(tableName) {
            val bar = integer("bar")
        }
        val singlePKTable = object : Table(tableName) {
            val bar = integer("bar")

            override val primaryKey = PrimaryKey(bar)
        }

        val scriptName = "V2__Add_primary_key"
        val scriptDirectory = "src/test/resources"

        withTables(excludeSettings = listOf(TestDB.SQLITE), noPKTable) {
            val script = MigrationUtils.generateMigrationScript(singlePKTable, scriptDirectory = scriptDirectory, scriptName = scriptName, withLogs = false)
            assertTrue(script.exists())
            assertEquals("src${File.separator}test${File.separator}resources${File.separator}$scriptName.sql", script.path)

            val expectedStatements: List<String> = MigrationUtils.statementsRequiredForDatabaseMigration(singlePKTable, withLogs = false)
            assertEquals(1, expectedStatements.size)

            val fileStatements: List<String> = script.bufferedReader().readLines().map { it.trimEnd(';') }
            expectedStatements.zip(fileStatements).forEach { (expected, actual) ->
                assertEquals(expected, actual)
            }

            assertTrue(File("$scriptDirectory/$scriptName.sql").delete())
        }
    }

    @Test
    fun testMigrationScriptOverwrittenIfAlreadyExists() {
        val tableName = "tester"
        val noPKTable = object : Table(tableName) {
            val bar = integer("bar")
        }
        val singlePKTable = object : Table(tableName) {
            val bar = integer("bar")

            override val primaryKey = PrimaryKey(bar)
        }

        val directory = "src/test/resources"
        val name = "V2__Test"

        withTables(excludeSettings = listOf(TestDB.SQLITE), noPKTable) {
            // Create initial script
            val initialScript = File("$directory/$name.sql")
            initialScript.createNewFile()
            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(noPKTable, withLogs = false)
            statements.forEach {
                initialScript.appendText(it)
            }

            // Generate script with the same name of initial script
            val newScript = MigrationUtils.generateMigrationScript(singlePKTable, scriptDirectory = directory, scriptName = name, withLogs = false)

            val expectedStatements: List<String> = MigrationUtils.statementsRequiredForDatabaseMigration(singlePKTable, withLogs = false)
            assertEquals(1, expectedStatements.size)

            val fileStatements: List<String> = newScript.bufferedReader().readLines().map { it.trimEnd(';') }
            expectedStatements.zip(fileStatements).forEach { (expected, actual) ->
                assertEquals(expected, actual)
            }

            assertTrue(File("$directory/$name.sql").delete())
        }
    }

    @Test
    fun testNoTablesPassedWhenGeneratingMigrationScript() {
        withDb {
            expectException<IllegalArgumentException> {
                MigrationUtils.generateMigrationScript(scriptDirectory = "src/test/resources", scriptName = "V2__Test", withLogs = false)
            }
        }
    }

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

        withTables(excludeSettings = listOf(TestDB.SQLITE, TestDB.ORACLE), t1) {
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

        withTables(excludeSettings = listOf(TestDB.SQLITE), noPKTable) {
            val primaryKey: PrimaryKeyMetadata? = currentDialectMetadataTest.existingPrimaryKeys(singlePKTable)[singlePKTable]
            assertNull(primaryKey)

            val expected = "ALTER TABLE ${tableName.inProperCase()} ADD PRIMARY KEY (${noPKTable.bar.nameInDatabaseCase()})"
            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(singlePKTable, withLogs = false)
            assertEquals(expected, statements.single())
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

                org.jetbrains.exposed.v1.jdbc.SchemaUtils.create(table)
                val actual = MigrationUtils.statementsRequiredForDatabaseMigration(table, withLogs = false)
                assertEqualLists(emptyList(), actual)
            } finally {
                org.jetbrains.exposed.v1.jdbc.SchemaUtils.drop(table)
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
    fun testDropExtraIndexOnSameColumn() {
        val testTableWithTwoIndices = object : Table("test_table") {
            val id = integer("id")
            val name = varchar("name", length = 42)

            override val primaryKey = PrimaryKey(id)
            val byName = index("test_table_by_name", false, name)
            val byName2 = index("test_table_by_name_2", false, name)
        }

        val testTableWithOneIndex = object : Table("test_table") {
            val id = integer("id")
            val name = varchar("name", length = 42)

            override val primaryKey = PrimaryKey(id)
            val byName = index("test_table_by_name", false, name)
        }

        // Oracle does not allow more than one index on a column
        withTables(excludeSettings = listOf(TestDB.ORACLE), testTableWithTwoIndices) {
            assertTrue(testTableWithTwoIndices.exists())

            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(testTableWithOneIndex, withLogs = false)
            assertEquals(1, statements.size)
        }
    }

    @Test
    fun testDropUnmappedIndex() {
        val testTableWithIndex = object : Table("test_table") {
            val id = integer("id")
            val name = varchar("name", length = 42)

            override val primaryKey = PrimaryKey(id)
            val byName = index("test_table_by_name", false, name)
        }

        val testTableWithoutIndex = object : Table("test_table") {
            val id = integer("id")
            val name = varchar("name", length = 42)

            override val primaryKey = PrimaryKey(id)
        }

        withTables(testTableWithIndex) {
            assertTrue(testTableWithIndex.exists())

            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(testTableWithoutIndex, withLogs = false)
            assertEquals(1, statements.size)
        }
    }

    @Test
    fun testAddAutoIncrementToExistingColumn() {
        withTables(excludeSettings = listOf(TestDB.SQLITE), tableWithoutAutoIncrement) { testDb ->
            assertEquals(0, MigrationUtils.statementsRequiredForDatabaseMigration(tableWithoutAutoIncrement, withLogs = false).size)

            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(tableWithAutoIncrement, withLogs = false)
            when (testDb) {
                TestDB.POSTGRESQL, TestDB.POSTGRESQLNG -> {
                    assertEquals(3, statements.size)
                    assertEquals(expectedCreateSequenceStatement("test_table_id_seq"), statements[0])
                    assertEquals("ALTER TABLE test_table ALTER COLUMN id SET DEFAULT nextval('test_table_id_seq')", statements[1])
                    assertEquals("ALTER SEQUENCE test_table_id_seq OWNED BY test_table.id", statements[2])
                }
                TestDB.SQLSERVER -> {
                    assertEquals(3, statements.size)
                    assertEquals("ALTER TABLE test_table ADD NEW_id BIGINT IDENTITY(1,1)", statements[0])
                    assertEquals("ALTER TABLE test_table DROP COLUMN id", statements[1])
                    assertEquals("EXEC sp_rename 'test_table.NEW_id', 'id', 'COLUMN'", statements[2])
                }
                TestDB.ORACLE, TestDB.H2_V2_ORACLE -> {
                    assertEquals(1, statements.size)
                    assertEquals(expectedCreateSequenceStatement("test_table_id_seq"), statements[0])
                }
                else -> {
                    assertEquals(1, statements.size)
                    val alterColumnWord = if (currentDialectTest is MysqlDialect) "MODIFY" else "ALTER"
                    assertTrue(statements[0].startsWith("ALTER TABLE test_table $alterColumnWord COLUMN id ", ignoreCase = true))
                }
            }
        }
    }

    @Test
    fun testAddAutoIncrementWithSequenceNameToExistingColumn() {
        withTables(excludeSettings = listOf(TestDB.SQLITE), tableWithoutAutoIncrement) {
            assertEquals(0, MigrationUtils.statementsRequiredForDatabaseMigration(tableWithoutAutoIncrement, withLogs = false).size)

            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(tableWithAutoIncrementSequenceName, withLogs = false)
            assertEquals(1, statements.size)
            if (currentDialectTest.supportsCreateSequence) {
                assertEquals(expectedCreateSequenceStatement(sequenceName), statements[0])
            } else {
                val alterColumnWord = if (currentDialectTest is MysqlDialect) "MODIFY" else "ALTER"
                assertTrue(statements[0].equals("ALTER TABLE TEST_TABLE $alterColumnWord COLUMN ID BIGINT AUTO_INCREMENT NOT NULL", ignoreCase = true))
            }
        }
    }

    @Test
    fun testAddAutoIncrementWithCustomSequenceToExistingColumn() {
        withTables(excludeSettings = listOf(TestDB.SQLITE), tableWithoutAutoIncrement) {
            if (currentDialectTest.supportsCreateSequence) {
                assertEquals(0, MigrationUtils.statementsRequiredForDatabaseMigration(tableWithoutAutoIncrement, withLogs = false).size)

                val statements = MigrationUtils.statementsRequiredForDatabaseMigration(tableWithAutoIncrementCustomSequence, withLogs = false)
                assertEquals(1, statements.size)
                assertEquals(expectedCreateSequenceStatement(sequence.name), statements[0])
            }
        }
    }

    @Test
    fun testDropAutoIncrementOnExistingColumn() {
        val tableWithAutoIncrement = object : IdTable<Long>("test_table") {
            override val id: Column<EntityID<Long>> = long("id").autoIncrement().entityId()

            override val primaryKey = PrimaryKey(id)
        }
        val tableWithoutAutoIncrement = object : IdTable<Long>("test_table") {
            override val id: Column<EntityID<Long>> = long("id").entityId()

            override val primaryKey = PrimaryKey(id)
        }

        withTables(excludeSettings = listOf(TestDB.SQLITE), tableWithAutoIncrement) { testDb ->
            assertEquals(0, MigrationUtils.statementsRequiredForDatabaseMigration(tableWithAutoIncrement, withLogs = false).size)

            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(tableWithoutAutoIncrement, withLogs = false)
            when (testDb) {
                TestDB.POSTGRESQL, TestDB.POSTGRESQLNG -> {
                    assertEquals(2, statements.size)
                    assertEquals("ALTER TABLE test_table ALTER COLUMN id TYPE BIGINT, ALTER COLUMN id DROP DEFAULT", statements[0])
                    assertEquals(expectedDropSequenceStatement("test_table_id_seq"), statements[1])
                }
                TestDB.ORACLE, TestDB.H2_V2_ORACLE -> {
                    assertEquals(1, statements.size)
                    assertTrue(statements[0].equals(expectedDropSequenceStatement("test_table_id_seq"), ignoreCase = true))
                }
                else -> {
                    assertEquals(1, statements.size)
                    val alterColumnWord = if (currentDialectTest is MysqlDialect) "MODIFY" else "ALTER"
                    assertTrue(statements[0].equals("ALTER TABLE test_table $alterColumnWord COLUMN id BIGINT", ignoreCase = true))
                }
            }
        }
    }

    @Test
    fun testAddSequenceNameToExistingAutoIncrementColumn() {
        val sequenceName = "custom_sequence"
        val tableWithAutoIncrement = object : IdTable<Long>("test_table") {
            override val id: Column<EntityID<Long>> = long("id").autoIncrement().entityId()

            override val primaryKey = PrimaryKey(id)
        }
        val tableWithAutoIncrementSequenceName = object : IdTable<Long>("test_table") {
            override val id: Column<EntityID<Long>> = long("id").autoIncrement(sequenceName).entityId()

            override val primaryKey = PrimaryKey(id)
        }

        withTables(excludeSettings = listOf(TestDB.SQLITE), tableWithAutoIncrement) { testDb ->
            if (currentDialectTest.supportsCreateSequence) {
                assertEquals(0, MigrationUtils.statementsRequiredForDatabaseMigration(tableWithAutoIncrement, withLogs = false).size)

                val statements = MigrationUtils.statementsRequiredForDatabaseMigration(tableWithAutoIncrementSequenceName, withLogs = false)
                assertEquals(expectedCreateSequenceStatement(sequenceName), statements[0])
                when (testDb) {
                    TestDB.POSTGRESQL, TestDB.POSTGRESQLNG -> {
                        assertEquals(3, statements.size)
                        assertEquals("ALTER TABLE test_table ALTER COLUMN id TYPE BIGINT, ALTER COLUMN id DROP DEFAULT", statements[1])
                        assertEquals(expectedDropSequenceStatement("test_table_id_seq"), statements[2])
                    }
                    TestDB.ORACLE, TestDB.H2_V2_ORACLE -> {
                        assertTrue(statements[1].equals(expectedDropSequenceStatement("test_table_id_seq"), ignoreCase = true))
                    }
                    else -> {
                        val alterColumnWord = if (currentDialectTest is MysqlDialect) "MODIFY" else "ALTER"
                        assertTrue(statements[1].startsWith("ALTER TABLE test_table $alterColumnWord COLUMN id BIGINT", ignoreCase = true))
                    }
                }
            }
        }
    }

    @Test
    fun testAddCustomSequenceToExistingAutoIncrementColumn() {
        withDb(excludeSettings = listOf(TestDB.SQLITE)) { testDb ->
            if (currentDialectTest.supportsCreateSequence) {
                try {
                    // MariaDB does not allow to create auto column without defining it as a key
                    val tableWithAutoIncrement = if (testDb == TestDB.MARIADB) {
                        object : IdTable<Long>("test_table") {
                            override val id: Column<EntityID<Long>> = long("id").autoIncrement().entityId()
                            override val primaryKey = PrimaryKey(id)
                        }
                    } else {
                        tableWithAutoIncrement
                    }

                    org.jetbrains.exposed.v1.jdbc.SchemaUtils.create(tableWithAutoIncrement)

                    assertEquals(0, MigrationUtils.statementsRequiredForDatabaseMigration(tableWithAutoIncrement, withLogs = false).size)

                    val statements = MigrationUtils.statementsRequiredForDatabaseMigration(tableWithAutoIncrementCustomSequence, withLogs = false)
                    assertEquals(expectedCreateSequenceStatement(sequence.name), statements[0])
                    when (testDb) {
                        TestDB.POSTGRESQL, TestDB.POSTGRESQLNG -> {
                            assertEquals(3, statements.size)
                            assertEquals("ALTER TABLE test_table ALTER COLUMN id TYPE BIGINT, ALTER COLUMN id DROP DEFAULT", statements[1])
                            assertEquals(expectedDropSequenceStatement("test_table_id_seq"), statements[2])
                        }
                        TestDB.ORACLE, TestDB.H2_V2_ORACLE -> {
                            assertEquals(2, statements.size)
                            assertTrue(statements[1].equals(expectedDropSequenceStatement("test_table_id_seq"), ignoreCase = true))
                        }
                        else -> {
                            assertEquals(2, statements.size)
                            val alterColumnWord = if (currentDialectTest is MysqlDialect) "MODIFY" else "ALTER"
                            assertTrue(statements[1].startsWith("ALTER TABLE test_table $alterColumnWord COLUMN id BIGINT", ignoreCase = true))
                        }
                    }
                } finally {
                    org.jetbrains.exposed.v1.jdbc.SchemaUtils.drop(tableWithAutoIncrement)
                }
            }
        }
    }

    @Test
    fun testDropAutoIncrementWithSequenceNameOnExistingColumn() {
        withDb(excludeSettings = listOf(TestDB.SQLITE)) { testDb ->
            if (currentDialectTest.supportsCreateSequence) {
                try {
                    org.jetbrains.exposed.v1.jdbc.SchemaUtils.create(tableWithAutoIncrementSequenceName)

                    assertEquals(0, MigrationUtils.statementsRequiredForDatabaseMigration(tableWithAutoIncrementSequenceName, withLogs = false).size)

                    val statements = MigrationUtils.statementsRequiredForDatabaseMigration(tableWithoutAutoIncrement, withLogs = false)
                    when (testDb) {
                        TestDB.H2_V1 -> {
                            assertEquals(0, statements.size)
                        }
                        in TestDB.ALL_POSTGRES -> {
                            // previous sequence used by column is altered but no longer dropped as not linked
                            assertEquals(0, statements.size)
                        }
                        else -> {
                            assertEquals(1, statements.size)
                            assertTrue(statements[0].equals(expectedDropSequenceStatement(sequenceName), ignoreCase = true))
                        }
                    }
                } finally {
                    org.jetbrains.exposed.v1.jdbc.SchemaUtils.drop(tableWithAutoIncrementSequenceName)
                }
            }
        }
    }

    @Test
    fun testDropSequenceNameOnExistingAutoIncrementColumn() {
        withDb(excludeSettings = listOf(TestDB.SQLITE)) { testDb ->
            if (currentDialectTest.supportsCreateSequence) {
                try {
                    org.jetbrains.exposed.v1.jdbc.SchemaUtils.create(tableWithAutoIncrementSequenceName)

                    assertEquals(0, MigrationUtils.statementsRequiredForDatabaseMigration(tableWithAutoIncrementSequenceName).size)

                    val statements = MigrationUtils.statementsRequiredForDatabaseMigration(tableWithAutoIncrement, withLogs = false)
                    when (testDb) {
                        TestDB.POSTGRESQL, TestDB.POSTGRESQLNG -> {
                            // previous sequence used by column is altered but no longer dropped as not linked
                            assertEquals(3, statements.size)
                            assertEquals(expectedCreateSequenceStatement("test_table_id_seq"), statements[0])
                            assertEquals("ALTER TABLE test_table ALTER COLUMN id SET DEFAULT nextval('test_table_id_seq')", statements[1])
                            assertEquals("ALTER SEQUENCE test_table_id_seq OWNED BY test_table.id", statements[2])
                        }
                        TestDB.SQLSERVER -> {
                            assertEquals(4, statements.size)
                            assertEquals("ALTER TABLE test_table ADD NEW_id BIGINT IDENTITY(1,1)", statements[0])
                            assertEquals("ALTER TABLE test_table DROP COLUMN id", statements[1])
                            assertEquals("EXEC sp_rename 'test_table.NEW_id', 'id', 'COLUMN'", statements[2])
                            assertEquals(expectedDropSequenceStatement(sequenceName), statements[3])
                        }
                        TestDB.ORACLE, TestDB.H2_V2_ORACLE -> {
                            assertEquals(2, statements.size)
                            assertEquals(expectedCreateSequenceStatement("test_table_id_seq"), statements[0])
                            assertTrue(statements[1].equals(expectedDropSequenceStatement(sequenceName), ignoreCase = true))
                        }
                        TestDB.H2_V1 -> {
                            assertEquals(1, statements.size)
                            assertEquals("ALTER TABLE TEST_TABLE ALTER COLUMN ID BIGINT AUTO_INCREMENT NOT NULL", statements[0])
                        }
                        TestDB.MARIADB -> {
                            assertEquals(2, statements.size)
                            assertEquals("ALTER TABLE test_table MODIFY COLUMN id BIGINT AUTO_INCREMENT NOT NULL", statements[0])
                            assertEquals(expectedDropSequenceStatement(sequenceName), statements[1])
                        }
                        else -> {
                            assertEquals(2, statements.size)
                            assertTrue(statements[0].startsWith("ALTER TABLE TEST_TABLE ALTER COLUMN ID", ignoreCase = true))
                            assertTrue(statements[1].equals(expectedDropSequenceStatement(sequenceName), ignoreCase = true))
                        }
                    }
                } finally {
                    org.jetbrains.exposed.v1.jdbc.SchemaUtils.drop(tableWithAutoIncrementSequenceName)
                }
            }
        }
    }

    @Test
    fun testAddCustomSequenceToExistingAutoIncrementColumnWithSequenceName() {
        withDb(excludeSettings = listOf(TestDB.SQLITE)) { testDb ->
            if (currentDialectTest.supportsCreateSequence) {
                try {
                    org.jetbrains.exposed.v1.jdbc.SchemaUtils.create(tableWithAutoIncrementSequenceName)

                    assertEquals(0, MigrationUtils.statementsRequiredForDatabaseMigration(tableWithAutoIncrementSequenceName, withLogs = false).size)

                    val statements = MigrationUtils.statementsRequiredForDatabaseMigration(tableWithAutoIncrementCustomSequence, withLogs = false)
                    when (testDb) {
                        TestDB.H2_V1 -> {
                            assertEquals(1, statements.size)
                            assertEquals(expectedCreateSequenceStatement(sequence.name), statements[0])
                        }
                        in TestDB.ALL_POSTGRES -> {
                            // previous sequence used by column is altered but no longer dropped as not linked
                            assertEquals(1, statements.size)
                            assertEquals(expectedCreateSequenceStatement(sequence.name), statements[0])
                        }
                        else -> {
                            assertEquals(2, statements.size)
                            assertEquals(expectedCreateSequenceStatement(sequence.name), statements[0])
                            assertTrue(statements[1].equals(expectedDropSequenceStatement(sequenceName), ignoreCase = true))
                        }
                    }
                } finally {
                    org.jetbrains.exposed.v1.jdbc.SchemaUtils.drop(tableWithAutoIncrementSequenceName)
                }
            }
        }
    }

    @Test
    fun testDropAutoIncrementWithCustomSequenceOnExistingColumn() {
        withDb(excludeSettings = listOf(TestDB.SQLITE)) { testDb ->
            if (currentDialectTest.supportsCreateSequence) {
                try {
                    org.jetbrains.exposed.v1.jdbc.SchemaUtils.create(tableWithAutoIncrementCustomSequence)

                    assertEquals(0, MigrationUtils.statementsRequiredForDatabaseMigration(tableWithAutoIncrementCustomSequence, withLogs = false).size)

                    val statements = MigrationUtils.statementsRequiredForDatabaseMigration(tableWithoutAutoIncrement, withLogs = false)
                    when (testDb) {
                        TestDB.H2_V1, in TestDB.ALL_POSTGRES -> {
                            // previous sequence used by column is altered but no longer dropped as not linked
                            assertEquals(0, statements.size)
                        }
                        else -> {
                            assertEquals(1, statements.size)
                            assertTrue(statements[0].equals(expectedDropSequenceStatement(sequence.name), ignoreCase = true))
                        }
                    }
                } finally {
                    org.jetbrains.exposed.v1.jdbc.SchemaUtils.drop(tableWithAutoIncrementCustomSequence)
                }
            }
        }
    }

    @Test
    fun testDropCustomSequenceOnExistingAutoIncrementColumn() {
        withDb(excludeSettings = listOf(TestDB.SQLITE)) { testDb ->
            if (currentDialectTest.supportsCreateSequence) {
                try {
                    org.jetbrains.exposed.v1.jdbc.SchemaUtils.create(tableWithAutoIncrementCustomSequence)

                    assertEquals(0, MigrationUtils.statementsRequiredForDatabaseMigration(tableWithAutoIncrementCustomSequence).size)

                    val statements = MigrationUtils.statementsRequiredForDatabaseMigration(tableWithAutoIncrement, withLogs = false)
                    when (testDb) {
                        TestDB.POSTGRESQL, TestDB.POSTGRESQLNG -> {
                            // previous sequence used by column is altered but no longer dropped as not linked
                            assertEquals(3, statements.size)
                            assertEquals(expectedCreateSequenceStatement("test_table_id_seq"), statements[0])
                            assertEquals("ALTER TABLE test_table ALTER COLUMN id SET DEFAULT nextval('test_table_id_seq')", statements[1])
                            assertEquals("ALTER SEQUENCE test_table_id_seq OWNED BY test_table.id", statements[2])
                        }
                        TestDB.SQLSERVER -> {
                            assertEquals(4, statements.size)
                            assertEquals("ALTER TABLE test_table ADD NEW_id BIGINT IDENTITY(1,1)", statements[0])
                            assertEquals("ALTER TABLE test_table DROP COLUMN id", statements[1])
                            assertEquals("EXEC sp_rename 'test_table.NEW_id', 'id', 'COLUMN'", statements[2])
                            assertEquals(expectedDropSequenceStatement(sequence.name), statements[3])
                        }
                        TestDB.ORACLE, TestDB.H2_V2_ORACLE -> {
                            assertEquals(2, statements.size)
                            assertEquals(expectedCreateSequenceStatement("test_table_id_seq"), statements[0])
                            assertTrue(statements[1].equals(expectedDropSequenceStatement(sequence.name), ignoreCase = true))
                        }
                        TestDB.H2_V1 -> {
                            assertEquals(1, statements.size)
                            assertEquals("ALTER TABLE TEST_TABLE ALTER COLUMN ID BIGINT AUTO_INCREMENT NOT NULL", statements[0])
                        }
                        TestDB.MARIADB -> {
                            assertEquals(2, statements.size)
                            assertEquals("ALTER TABLE test_table MODIFY COLUMN id BIGINT AUTO_INCREMENT NOT NULL", statements[0])
                            assertEquals(expectedDropSequenceStatement(sequence.name), statements[1])
                        }
                        else -> {
                            assertEquals(2, statements.size)
                            assertTrue(statements[0].startsWith("ALTER TABLE TEST_TABLE ALTER COLUMN ID", ignoreCase = true))
                            assertTrue(statements[1].equals(expectedDropSequenceStatement(sequence.name), ignoreCase = true))
                        }
                    }
                } finally {
                    org.jetbrains.exposed.v1.jdbc.SchemaUtils.drop(tableWithAutoIncrementCustomSequence)
                }
            }
        }
    }

    @Test
    fun testAddSequenceNameToExistingAutoIncrementColumnWithCustomSequence() {
        withDb(excludeSettings = listOf(TestDB.SQLITE)) { testDb ->
            if (currentDialectTest.supportsCreateSequence) {
                try {
                    org.jetbrains.exposed.v1.jdbc.SchemaUtils.create(tableWithAutoIncrementCustomSequence)

                    assertEquals(0, MigrationUtils.statementsRequiredForDatabaseMigration(tableWithAutoIncrementCustomSequence).size)

                    val statements = MigrationUtils.statementsRequiredForDatabaseMigration(tableWithAutoIncrementSequenceName, withLogs = false)
                    when (testDb) {
                        TestDB.H2_V1 -> {
                            assertEquals(1, statements.size)
                            assertEquals(expectedCreateSequenceStatement(sequenceName), statements[0])
                        }
                        in TestDB.ALL_POSTGRES -> {
                            // previous sequence used by column is altered but no longer dropped as not linked
                            assertEquals(1, statements.size)
                            assertEquals(expectedCreateSequenceStatement(sequenceName), statements[0])
                        }
                        else -> {
                            assertEquals(2, statements.size)
                            assertEquals(expectedCreateSequenceStatement(sequenceName), statements[0])
                            assertTrue(statements[1].equals(expectedDropSequenceStatement(sequence.name), ignoreCase = true))
                        }
                    }
                } finally {
                    org.jetbrains.exposed.v1.jdbc.SchemaUtils.drop(tableWithAutoIncrementCustomSequence)
                }
            }
        }
    }

    @Test
    fun testOnlySpecifiedTableDropsSequence() {
        val tableWithAutoIncrement = object : IntIdTable("test_table_auto") {}

        val tableWithoutAutoIncrement = object : IdTable<Int>("test_table_auto") {
            override val id: Column<EntityID<Int>> = integer("id").entityId()
            override val primaryKey = PrimaryKey(id)
        }

        val tableWithExplSequence = object : Table("test_table_expl_seq") {
            val counter = integer("counter").autoIncrement(sequence)
            override val primaryKey = PrimaryKey(counter)
        }

        val tableWithImplSequence = object : IntIdTable("test_table_impl_seq") {}

        withDb(TestDB.ALL_POSTGRES) {
            try {
                org.jetbrains.exposed.v1.jdbc.SchemaUtils.create(
                    tableWithAutoIncrement, // uses SERIAL column
                    tableWithExplSequence, // uses Sequence 'my_sequence'
                    tableWithImplSequence // uses SERIAL column
                )

                val autoSeq = tableWithAutoIncrement.sequences.single()
                val implicitSeq = tableWithImplSequence.sequences.single()
                assertTrue(autoSeq.exists())
                assertTrue(sequence.exists())
                assertTrue(implicitSeq.exists())

                val statements = MigrationUtils.statementsRequiredForDatabaseMigration(tableWithoutAutoIncrement)
                assertEquals(2, statements.size)
                assertEquals("ALTER TABLE test_table_auto ALTER COLUMN id TYPE INT, ALTER COLUMN id DROP DEFAULT", statements[0])
                assertEquals(expectedDropSequenceStatement("test_table_auto_id_seq"), statements[1])

                statements.forEach { exec(it) }
                assertFalse(autoSeq.exists())
                assertTrue(sequence.exists())
                assertTrue(implicitSeq.exists())
            } finally {
                org.jetbrains.exposed.v1.jdbc.SchemaUtils.drop(tableWithAutoIncrement, tableWithExplSequence, tableWithImplSequence)
            }
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

    @Test
    fun testNoColumnTypeChangeStatementsGenerated() {
        withDb(excludeSettings = columnTypeChangeUnsupportedDb) {
            try {
                org.jetbrains.exposed.v1.jdbc.SchemaUtils.create(columnTypesTester)

                val columns = columnTypesTester.columns.sortedBy { it.name.uppercase() }
                val columnsMetadata = connection.metadata {
                    requireNotNull(columns(columnTypesTester)[columnTypesTester])
                }.toSet().sortedBy { it.name.uppercase() }
                columnsMetadata.forEachIndexed { index, columnMetadataItem ->
                    val columnType = columns[index].columnType.sqlType()
                    val columnMetadataSqlType = columnMetadataItem.sqlType
                    assertTrue(currentDialectTest.areEquivalentColumnTypes(columnMetadataSqlType, columnMetadataItem.jdbcType, columnType))
                }

                val statements = MigrationUtils.statementsRequiredForDatabaseMigration(columnTypesTester, withLogs = false)
                assertTrue(statements.isEmpty())
            } finally {
                org.jetbrains.exposed.v1.jdbc.SchemaUtils.drop(columnTypesTester)
            }
        }
    }

    @Test
    fun testCorrectColumnTypeChangeStatementsGenerated() {
        withDb(excludeSettings = columnTypeChangeUnsupportedDb) {
            val columns = columnTypesTester.columns.sortedBy { it.name.uppercase() }

            columns.forEach { oldColumn ->
                val oldColumnWithModifiedName = Column(table = oldColumn.table, name = "tester_col", columnType = oldColumn.columnType as IColumnType<Any>)
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
                        if (currentDialectTest.areEquivalentColumnTypes(
                                oldColumnMetadataItem.sqlType,
                                oldColumnMetadataItem.jdbcType,
                                newColumn.columnType.sqlType()
                            )
                        ) {
                            continue
                        }

                        val newColumnWithModifiedName = Column(table = newColumn.table, name = "tester_col", columnType = newColumn.columnType as IColumnType<Any>)
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
    fun testNoColumnTypeChangeStatementsGeneratedForArrayColumnType() {
        withTables(TestDB.ALL - setOf(TestDB.H2_V2, TestDB.H2_V2_PSQL), arraysTester) {
            val columnMetadata = connection.metadata {
                requireNotNull(columns(arraysTester)[arraysTester])
            }.toSet().sortedBy { it.name.uppercase() }
            val columns = arraysTester.columns.sortedBy { it.name.uppercase() }
            columnMetadata.forEachIndexed { index, columnMetadataItem ->
                val columnType = columns[index].columnType.sqlType()
                val columnMetadataSqlType = columnMetadataItem.sqlType
                assertTrue(currentDialectTest.areEquivalentColumnTypes(columnMetadataSqlType, columnMetadataItem.jdbcType, columnType))
            }
            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(arraysTester, withLogs = false)
            assertTrue(statements.isEmpty())
        }
    }

    @Test
    fun testCorrectColumnTypeChangeStatementsGeneratedForArrayColumnType() {
        withDb(excludeSettings = TestDB.ALL - setOf(TestDB.H2_V2, TestDB.H2_V2_PSQL)) {
            val columns = arraysTester.columns.sortedBy { it.name.uppercase() }

            columns.forEach { oldColumn ->
                val oldColumnWithModifiedName = Column(table = oldColumn.table, name = "tester_col", columnType = oldColumn.columnType as IColumnType<Any>)
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
                        if (currentDialectTest.areEquivalentColumnTypes(
                                oldColumnMetadataItem.sqlType,
                                oldColumnMetadataItem.jdbcType,
                                newColumn.columnType.sqlType()
                            )
                        ) {
                            continue
                        }

                        val newColumnWithModifiedName = Column(table = newColumn.table, name = "tester_col", columnType = newColumn.columnType as IColumnType<Any>)
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
    fun testDifferentCheckConstraintForSameUnderlyingColumnType() {
        val oldTable = object : Table("tester") {
            val tester_col = byte("tester_col")
        }
        val newTable = object : Table("tester") {
            val tester_col = ubyte("tester_col")
        }

        // For H2 PostgreSQL, both `byte` and `ubyte` have the same column type of SMALLINT
        withTables(excludeSettings = TestDB.ALL - TestDB.H2_V2_PSQL, oldTable) {
            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(newTable, withLogs = false)
            assertEquals(2, statements.size)
            assertEquals(
                newTable.checkConstraints().single().createStatement().single(),
                statements[0]
            )
            assertEquals(
                oldTable.checkConstraints().single().dropStatement().single(),
                statements[1]
            )
            statements.forEach(::exec)
            newTable.insert {
                it[tester_col] = UByte.MAX_VALUE
            }
        }
    }

    @Test
    fun testAddMissingCheckConstraint() {
        val oldTable = object : Table("tester") {
            val tester_col = text("tester_col")
        }
        val newTable = object : Table("tester") {
            val tester_col = byte("tester_col")
        }

        withTables(excludeSettings = TestDB.ALL - TestDB.H2_V2_PSQL, oldTable) {
            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(newTable, withLogs = false)
            assertEquals(2, statements.size)
            assertEquals(
                newTable.checkConstraints().single().createStatement().single(),
                statements[1]
            )
            statements.forEach(::exec)
            newTable.insert {
                it[tester_col] = Byte.MAX_VALUE
            }
        }
    }

    @Test
    fun testDropUnmappedCheckConstraint() {
        val oldTable = object : Table("tester") {
            val tester_col = byte("tester_col")
        }
        val newTable = object : Table("tester") {
            val tester_col = text("tester_col")
        }

        withTables(excludeSettings = TestDB.ALL - TestDB.H2_V2_PSQL, oldTable) {
            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(newTable, withLogs = false)
            assertEquals(2, statements.size)
            assertEquals(
                oldTable.checkConstraints().single().dropStatement().single(),
                statements[1]
            )
            statements.forEach(::exec)
            newTable.insert {
                it[tester_col] = "Testing text"
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

    private fun expectedCreateSequenceStatement(sequenceName: String) =
        "CREATE SEQUENCE${" IF NOT EXISTS".takeIf { currentDialectTest.supportsIfNotExists } ?: ""} " +
            "$sequenceName START WITH 1 MINVALUE 1 MAXVALUE ${currentDialectTest.sequenceMaxValue}"

    private fun expectedDropSequenceStatement(sequenceName: String) =
        "DROP SEQUENCE${" IF EXISTS".takeIf { currentDialectTest.supportsIfNotExists } ?: ""} $sequenceName"

    private val sequenceName by lazy { "custom_sequence" }

    private val tableWithoutAutoIncrement by lazy {
        object : IdTable<Long>("test_table") {
            override val id: Column<EntityID<Long>> = long("id").entityId()
        }
    }

    private val tableWithAutoIncrement by lazy {
        object : IdTable<Long>("test_table") {
            override val id: Column<EntityID<Long>> = long("id").autoIncrement().entityId()
        }
    }

    private val tableWithAutoIncrementCustomSequence by lazy {
        object : IdTable<Long>("test_table") {
            override val id: Column<EntityID<Long>> = long("id").autoIncrement(sequence).entityId()
        }
    }

    private val tableWithAutoIncrementSequenceName by lazy {
        object : IdTable<Long>("test_table") {
            override val id: Column<EntityID<Long>> = long("id").autoIncrement(sequenceName).entityId()
        }
    }

    private enum class TestEnum { A, B, C }

    private val sqlType by lazy {
        when (currentDialectTest) {
            is H2Dialect, is MysqlDialect -> "ENUM('Bar', 'Baz')"
            is PostgreSQLDialect -> "RefEnum"
            else -> error("Unsupported case")
        }
    }

    private val columnTypesTester by lazy {
        object : Table("tester") {
            val byte = byte("byte_col")
            val ubyte = ubyte("ubyte_col")
            val short = short("short_col")
            val ushort = ushort("ushort_col")
            val integer = integer("integer_col")
            val uinteger = uinteger("uinteger_col")
            val long = long("long_col")
            val ulong = ulong("ulong_col")
            val float = float("float_col")
            val double = double("double_col")
            val decimal = decimal("decimal_col", 6, 2)
            val decimal2 = decimal("decimal_col_2", 3, 2)
            val char = char("char_col")
            val letter = char("letter_col", 1)
            val char2 = char("char_col_2", 2)
            val varchar = varchar("varchar_col", 14)
            val varchar2 = varchar("varchar_col_2", 28)
            val text = text("text_col")
            val mediumText = mediumText("mediumText_col")
            val largeText = largeText("largeText_col")

            val binary = binary("binary_col", 123)
            val binary2 = binary("binary_col_2", 456)
            val blob = blob("blob_col")
            val uuid = uuid("uuid_col")
            val bool = bool("boolean_col")
            val enum1 = enumeration("enum_col_1", TestEnum::class)
            val enum2 = enumeration<TestEnum>("enum_col_2")
            val enum3 = enumerationByName("enum_col_3", 25, TestEnum::class)
            val enum4 = enumerationByName("enum_col_4", 64, TestEnum::class)
            val enum5 = enumerationByName<TestEnum>("enum_col_5", 16)
            val enum6 = enumerationByName<TestEnum>("enum_col_6", 32)
            val customEnum = customEnumeration(
                "custom_enum_col",
                sqlType,
                { value -> Foo.valueOf(value as String) },
                { value ->
                    when (currentDialectTest) {
                        is PostgreSQLDialect -> PGEnum(sqlType, value)
                        else -> value.name
                    }
                }
            )
            val currency = currency("currency_col")
            val date = date("date_col")
            val datetime = datetime("datetime_col")
            val time = time("time_col")
            val timestamp = timestamp("timestamp_col")
            val timestampWithTimeZone = timestampWithTimeZone("timestampWithTimeZone_col")
            val duration = duration("duration_col")
            val intArrayJson = json<IntArray>("json_col", Json.Default)
            val intArrayJsonb = jsonb<IntArray>("jsonb_col", Json.Default)
        }
    }

    private val arraysTester by lazy {
        object : Table("tester") {
            val byteArray = array("byteArray", ByteColumnType())
            val ubyteArray = array("ubyteArray", UByteColumnType())
            val shortArray = array("shortArray", ShortColumnType(), 10)
            val ushortArray = array("ushortArray", UShortColumnType(), 10)
            val intArray = array<Int>("intArray", 20)
            val uintArray = array<UInt>("uintArray", 20)
            val longArray = array<Long>("longArray", 30)
            val ulongArray = array<ULong>("ulongArray", 30)
            val floatArray = array<Float>("floatArray", 40)
            val doubleArray = array<Double>("doubleArray", 50)
            val decimalArray = array("decimalArray", DecimalColumnType(6, 3), 60)
            val charArray = array("charArray", CharacterColumnType(), 70)
            val initialsArray = array("initialsArray", CharColumnType(2), 45)
            val varcharArray = array("varcharArray", VarCharColumnType(), 80)
            val textArray = array("textArray", TextColumnType(), 90)
            val mediumTextArray = array("mediumTextArray", MediumTextColumnType(), 100)
            val largeTextArray = array("largeTextArray", LargeTextColumnType(), 110)
            val binaryArray = array("binaryArray", BinaryColumnType(123), 120)
            val blobArray = array("blobArray", BlobColumnType(), 130)
            val uuidArray = array<UUID>("uuidArray", 140)
            val booleanArray = array<Boolean>("booleanArray", 150)
            val currencyArray = array("currencyArray", CurrencyColumnType(), 25)
            val dateArray = array("dateArray", KotlinLocalDateColumnType(), 366)
            val datetimeArray = array("datetimeArray", KotlinLocalDateTimeColumnType(), 10)
            val timeArray = array("timeArray", KotlinLocalTimeColumnType(), 14)
            val timestampArray = array("timestampArray", KotlinInstantColumnType(), 10)
            val timestampWithTimeZoneArray = array("timestampWithTimeZoneArray", KotlinOffsetDateTimeColumnType(), 10)
            val durationArray = array("durationArray", KotlinDurationColumnType(), 7)
        }
    }

    @Serializable
    class TestClient(val id: String, val name: String)

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
    fun testDropUnmappedIndices() {
        val dbTable = object : Table("testDropUnmappedIndices") {
            val indexOnlyInDb = integer("indexOnlyInDb").index("indexOnlyInDbIdx")
            val columnWithIndexOnlyInDb = integer("columnWithIndexOnlyInDb").index("columnWithIndexOnlyInDbIdx")
            val indexInCodeAndDB = integer("indexInCodeAndDB").index("indexInCodeAndDBIdx")
        }

        val tester = object : Table("testDropUnmappedIndices") {
            val indexOnlyInDb = integer("indexOnlyInDb")
            val indexInCodeAndDB = integer("indexInCodeAndDB").index("indexInCodeAndDBIdx")
            val indexOnlyInCode = integer("indexOnlyInCode").index("indexOnlyInCodeIdx")
        }

        withTables(dbTable) {
            val statements = MigrationUtils.dropUnmappedIndices(tester)
            assertEquals(2, statements.size)
            assertEquals(1, statements.map { it.lowercase() }.filter { it.contains(" indexOnlyInDbIdx".lowercase()) }.size)
            assertEquals(1, statements.map { it.lowercase() }.filter { it.contains(" columnWithIndexOnlyInDbIdx".lowercase()) }.size)
        }
    }

    @Test
    fun testDropUnmappedSequence() {
        val dbTable = object : Table("testDropUnmappedSequence") {
            val sequenceOnlyInDb = integer("sequenceOnlyInDb").autoIncrement()
            val columnWithSequenceOnlyInDb = integer("columnWithSequenceOnlyInDb").autoIncrement()
            val sequenceBothInDbAndCode = integer("sequenceBothInDbAndCode").autoIncrement()
        }

        val tester = object : Table("testDropUnmappedSequence") {
            val sequenceOnlyInDb = integer("sequenceOnlyInDb")
            val sequenceBothInDbAndCode = integer("sequenceBothInDbAndCode").autoIncrement()
            val sequenceOnlyInCode = integer("sequenceOnlyInCode").autoIncrement()
        }

        // It doesn't look like Sequence metadata works stable for all databases. For some databases it can't find existing sequences
        withTables(excludeSettings = TestDB.ALL - TestDB.POSTGRESQL, dbTable) {
            val statements = MigrationUtils.dropUnmappedSequences(tester)

            assertEquals(2, statements.size)
            assertEquals(1, statements.map { it.lowercase() }.filter { it.contains("_sequenceOnlyInDb_seq".lowercase()) }.size)
            assertEquals(1, statements.map { it.lowercase() }.filter { it.contains("_columnWithSequenceOnlyInDb_seq".lowercase()) }.size)
        }
    }
}

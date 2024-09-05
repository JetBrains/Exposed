package org.jetbrains.exposed.sql.tests.shared.ddl

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ExperimentalDatabaseMigrationApi
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Sequence
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.exists
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.inProperCase
import org.jetbrains.exposed.sql.tests.shared.assertEqualLists
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.jetbrains.exposed.sql.vendors.PrimaryKeyMetadata
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.properties.Delegates
import kotlin.test.assertNull

@OptIn(ExperimentalDatabaseMigrationApi::class)
class DatabaseMigrationTests : DatabaseTestsBase() {

    @Before
    fun dropAllSequences() {
        withDb {
            if (currentDialectTest.supportsCreateSequence) {
                val allSequences = currentDialectTest.sequences().map { name -> Sequence(name) }.toSet()
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

        withDb(excludeSettings = listOf(TestDB.SQLITE)) {
            try {
                SchemaUtils.create(noPKTable)

                val script = MigrationUtils.generateMigrationScript(singlePKTable, scriptDirectory = scriptDirectory, scriptName = scriptName, withLogs = false)
                assertTrue(script.exists())
                assertEquals("src/test/resources/$scriptName.sql", script.path)

                val expectedStatements: List<String> = MigrationUtils.statementsRequiredForDatabaseMigration(singlePKTable, withLogs = false)
                assertEquals(1, expectedStatements.size)

                val fileStatements: List<String> = script.bufferedReader().readLines().map { it.trimEnd(';') }
                expectedStatements.zip(fileStatements).forEach { (expected, actual) ->
                    assertEquals(expected, actual)
                }
            } finally {
                assertTrue(File("$scriptDirectory/$scriptName.sql").delete())
                SchemaUtils.drop(noPKTable)
            }
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

        withDb(excludeSettings = listOf(TestDB.SQLITE)) {
            try {
                SchemaUtils.create(noPKTable)

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
            } finally {
                assertTrue(File("$directory/$name.sql").delete())
                SchemaUtils.drop(noPKTable)
            }
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

        withDb(excludeSettings = listOf(TestDB.SQLITE)) {
            try {
                SchemaUtils.create(noPKTable)
                val primaryKey: PrimaryKeyMetadata? = currentDialectTest.existingPrimaryKeys(singlePKTable)[singlePKTable]
                assertNull(primaryKey)

                val expected = "ALTER TABLE ${tableName.inProperCase()} ADD PRIMARY KEY (${noPKTable.bar.nameInDatabaseCase()})"
                val statements = MigrationUtils.statementsRequiredForDatabaseMigration(singlePKTable, withLogs = false)
                assertEquals(expected, statements.single())
            } finally {
                SchemaUtils.drop(noPKTable)
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

        withDb {
            try {
                SchemaUtils.create(quotedTable)
                assertTrue(quotedTable.exists())

                val statements = MigrationUtils.statementsRequiredForDatabaseMigration(quotedTable, withLogs = false)
                assertTrue(statements.isEmpty())
            } finally {
                SchemaUtils.drop(quotedTable)
            }
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
        withTables(excludeSettings = listOf(TestDB.ORACLE), tables = arrayOf(testTableWithTwoIndices)) {
            try {
                SchemaUtils.create(testTableWithTwoIndices)
                assertTrue(testTableWithTwoIndices.exists())

                val statements = MigrationUtils.statementsRequiredForDatabaseMigration(testTableWithOneIndex, withLogs = false)
                assertEquals(1, statements.size)
            } finally {
                SchemaUtils.drop(testTableWithTwoIndices)
            }
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

        withTables(tables = arrayOf(testTableWithIndex)) {
            try {
                SchemaUtils.create(testTableWithIndex)
                assertTrue(testTableWithIndex.exists())

                val statements = MigrationUtils.statementsRequiredForDatabaseMigration(testTableWithoutIndex, withLogs = false)
                assertEquals(1, statements.size)
            } finally {
                SchemaUtils.drop(testTableWithIndex)
            }
        }
    }

    @Test
    fun testAddAutoIncrementToExistingColumn() {
        val tableWithoutAutoIncrement = object : IdTable<Long>("test_table") {
            override val id: Column<EntityID<Long>> = long("id").entityId()
        }
        val tableWithAutoIncrement = object : IdTable<Long>("test_table") {
            override val id: Column<EntityID<Long>> = long("id").autoIncrement().entityId()
        }

        withTables(excludeSettings = listOf(TestDB.SQLITE), tableWithoutAutoIncrement) { testDb ->
            assertEquals(0, MigrationUtils.statementsRequiredForDatabaseMigration(tableWithoutAutoIncrement, withLogs = false).size)

            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(tableWithAutoIncrement, withLogs = false)
            when (testDb) {
                TestDB.POSTGRESQL, TestDB.POSTGRESQLNG -> {
                    assertEquals(3, statements.size)
                    assertEquals("CREATE SEQUENCE IF NOT EXISTS test_table_id_seq START WITH 1 MINVALUE 1 MAXVALUE 9223372036854775807", statements[0])
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
                    assertEquals("CREATE SEQUENCE test_table_id_seq START WITH 1 MINVALUE 1 MAXVALUE 9223372036854775807", statements[0])
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
        val sequenceName = "custom_sequence"
        val tableWithoutAutoIncrement = object : IdTable<Long>("test_table") {
            override val id: Column<EntityID<Long>> = long("id").entityId()
        }
        val tableWithAutoIncrementSequenceName = object : IdTable<Long>("test_table") {
            override val id: Column<EntityID<Long>> = long("id").autoIncrement(sequenceName).entityId()
        }

        withTables(excludeSettings = listOf(TestDB.SQLITE), tableWithoutAutoIncrement) {
            assertEquals(0, MigrationUtils.statementsRequiredForDatabaseMigration(tableWithoutAutoIncrement, withLogs = false).size)

            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(tableWithAutoIncrementSequenceName, withLogs = false)
            assertEquals(1, statements.size)
            if (currentDialectTest.supportsCreateSequence) {
                assertEquals(
                    "CREATE SEQUENCE${" IF NOT EXISTS".takeIf { currentDialectTest.supportsIfNotExists } ?: ""} " +
                        "$sequenceName START WITH 1 MINVALUE 1 MAXVALUE 9223372036854775807",
                    statements[0]
                )
            } else {
                val alterColumnWord = if (currentDialectTest is MysqlDialect) "MODIFY" else "ALTER"
                assertTrue(statements[0].equals("ALTER TABLE TEST_TABLE $alterColumnWord COLUMN ID BIGINT AUTO_INCREMENT NOT NULL", ignoreCase = true))
            }
        }
    }

    @Test
    fun testAddAutoIncrementWithCustomSequenceToExistingColumn() {
        val tableWithoutAutoIncrement = object : IdTable<Long>("test_table") {
            override val id: Column<EntityID<Long>> = long("id").entityId()
        }
        val tableWithAutoIncrementCustomSequence = object : IdTable<Long>("test_table") {
            override val id: Column<EntityID<Long>> = long("id").autoIncrement(sequence).entityId()
        }

        withDb(excludeSettings = listOf(TestDB.SQLITE)) {
            if (currentDialectTest.supportsCreateSequence) {
                try {
                    SchemaUtils.create(tableWithoutAutoIncrement)

                    assertEquals(0, MigrationUtils.statementsRequiredForDatabaseMigration(tableWithoutAutoIncrement, withLogs = false).size)

                    val statements = MigrationUtils.statementsRequiredForDatabaseMigration(tableWithAutoIncrementCustomSequence, withLogs = false)
                    assertEquals(1, statements.size)
                    assertEquals(
                        "CREATE SEQUENCE${" IF NOT EXISTS".takeIf { currentDialectTest.supportsIfNotExists } ?: ""} " +
                            "${sequence.name} START WITH 4 INCREMENT BY 2 MINVALUE 1 MAXVALUE 100 CYCLE CACHE 20",
                        statements[0]
                    )
                } finally {
                    SchemaUtils.drop(tableWithoutAutoIncrement)
                }
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
                    assertEquals("ALTER TABLE test_table ALTER COLUMN id TYPE BIGINT", statements[0])
                    assertEquals("DROP SEQUENCE IF EXISTS test_table_id_seq", statements[1])
                }
                TestDB.ORACLE, TestDB.H2_V2_ORACLE -> {
                    assertEquals(1, statements.size)
                    assertTrue(statements[0].equals("DROP SEQUENCE TEST_TABLE_ID_SEQ", ignoreCase = true))
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

        withDb(excludeSettings = listOf(TestDB.SQLITE)) { testDb ->
            if (currentDialectTest.supportsCreateSequence) {
                try {
                    SchemaUtils.create(tableWithAutoIncrement)

                    assertEquals(0, MigrationUtils.statementsRequiredForDatabaseMigration(tableWithAutoIncrement, withLogs = false).size)

                    val statements = MigrationUtils.statementsRequiredForDatabaseMigration(tableWithAutoIncrementSequenceName, withLogs = false)
                    assertEquals(
                        "CREATE SEQUENCE${" IF NOT EXISTS".takeIf { currentDialectTest.supportsIfNotExists } ?: ""} " +
                            "$sequenceName START WITH 1 MINVALUE 1 MAXVALUE 9223372036854775807",
                        statements[0]
                    )
                    when (testDb) {
                        TestDB.POSTGRESQL, TestDB.POSTGRESQLNG -> {
                            assertEquals(3, statements.size)
                            assertEquals("ALTER TABLE test_table ALTER COLUMN id TYPE BIGINT, ALTER COLUMN id DROP DEFAULT", statements[1])
                            assertEquals("DROP SEQUENCE IF EXISTS test_table_id_seq", statements[2])
                        }
                        TestDB.ORACLE, TestDB.H2_V2_ORACLE -> {
                            assertTrue(statements[1].equals("DROP SEQUENCE TEST_TABLE_ID_SEQ", ignoreCase = true))
                        }
                        else -> {
                            val alterColumnWord = if (currentDialectTest is MysqlDialect) "MODIFY" else "ALTER"
                            assertTrue(statements[1].startsWith("ALTER TABLE test_table $alterColumnWord COLUMN id BIGINT", ignoreCase = true))
                        }
                    }
                } finally {
                    SchemaUtils.drop(tableWithAutoIncrement)
                }
            }
        }
    }

    @Test
    fun testAddCustomSequenceToExistingAutoIncrementColumn() {
        val tableWithAutoIncrement = object : IdTable<Long>("test_table") {
            override val id: Column<EntityID<Long>> = long("id").autoIncrement().entityId()
        }
        val tableWithAutoIncrementCustomSequence = object : IdTable<Long>("test_table") {
            override val id: Column<EntityID<Long>> = long("id").autoIncrement(sequence).entityId()
        }

        withDb(excludeSettings = listOf(TestDB.SQLITE)) { testDb ->
            if (currentDialectTest.supportsCreateSequence) {
                try {
                    SchemaUtils.create(tableWithAutoIncrement)

                    assertEquals(0, MigrationUtils.statementsRequiredForDatabaseMigration(tableWithAutoIncrement, withLogs = false).size)

                    val statements = MigrationUtils.statementsRequiredForDatabaseMigration(tableWithAutoIncrementCustomSequence, withLogs = false)
                    assertEquals(
                        "CREATE SEQUENCE${" IF NOT EXISTS".takeIf { currentDialectTest.supportsIfNotExists } ?: ""} " +
                            "${sequence.name} START WITH 4 INCREMENT BY 2 MINVALUE 1 MAXVALUE 100 CYCLE CACHE 20",
                        statements[0]
                    )
                    when (testDb) {
                        TestDB.POSTGRESQL, TestDB.POSTGRESQLNG -> {
                            assertEquals(3, statements.size)
                            assertEquals("ALTER TABLE test_table ALTER COLUMN id TYPE BIGINT, ALTER COLUMN id DROP DEFAULT", statements[1])
                            assertEquals("DROP SEQUENCE IF EXISTS test_table_id_seq", statements[2])
                        }
                        TestDB.ORACLE, TestDB.H2_V2_ORACLE -> {
                            assertEquals(2, statements.size)
                            assertTrue(statements[1].equals("DROP SEQUENCE test_table_id_seq", ignoreCase = true))
                        }
                        else -> {
                            assertEquals(2, statements.size)
                            val alterColumnWord = if (currentDialectTest is MysqlDialect) "MODIFY" else "ALTER"
                            assertTrue(statements[1].startsWith("ALTER TABLE test_table $alterColumnWord COLUMN id BIGINT", ignoreCase = true))
                        }
                    }
                } finally {
                    SchemaUtils.drop(tableWithAutoIncrement)
                }
            }
        }
    }

    @Test
    fun testDropAutoIncrementWithSequenceNameOnExistingColumn() {
        val sequenceName = "custom_sequence"
        val tableWithAutoIncrementSequenceName = object : IdTable<Long>("test_table") {
            override val id: Column<EntityID<Long>> = long("id").autoIncrement(sequenceName).entityId()
        }
        val tableWithoutAutoIncrement = object : IdTable<Long>("test_table") {
            override val id: Column<EntityID<Long>> = long("id").entityId()
        }

        withDb(excludeSettings = listOf(TestDB.SQLITE)) { testDb ->
            if (currentDialectTest.supportsCreateSequence) {
                try {
                    SchemaUtils.create(tableWithAutoIncrementSequenceName)

                    assertEquals(0, MigrationUtils.statementsRequiredForDatabaseMigration(tableWithAutoIncrementSequenceName, withLogs = false).size)

                    val statements = MigrationUtils.statementsRequiredForDatabaseMigration(tableWithoutAutoIncrement, withLogs = false)
                    when (testDb) {
                        TestDB.H2_V1 -> {
                            assertEquals(0, statements.size)
                        }
                        else -> {
                            assertEquals(1, statements.size)
                            assertTrue(
                                statements[0].equals(
                                    "DROP SEQUENCE${" IF EXISTS".takeIf { currentDialectTest.supportsIfNotExists } ?: ""} $sequenceName",
                                    ignoreCase = true
                                )
                            )
                        }
                    }
                } finally {
                    SchemaUtils.drop(tableWithAutoIncrementSequenceName)
                }
            }
        }
    }

    @Test
    fun testDropSequenceNameOnExistingAutoIncrementColumn() {
        val sequenceName = "custom_sequence"
        val tableWithAutoIncrementSequenceName = object : IdTable<Long>("test_table") {
            override val id: Column<EntityID<Long>> = long("id").autoIncrement(sequenceName).entityId()
        }
        val tableWithAutoIncrement = object : IdTable<Long>("test_table") {
            override val id: Column<EntityID<Long>> = long("id").autoIncrement().entityId()
        }

        withDb(excludeSettings = listOf(TestDB.SQLITE)) { testDb ->
            if (currentDialectTest.supportsCreateSequence) {
                try {
                    SchemaUtils.create(tableWithAutoIncrementSequenceName)

                    assertEquals(0, MigrationUtils.statementsRequiredForDatabaseMigration(tableWithAutoIncrementSequenceName).size)

                    val statements = MigrationUtils.statementsRequiredForDatabaseMigration(tableWithAutoIncrement, withLogs = false)
                    when (testDb) {
                        TestDB.POSTGRESQL, TestDB.POSTGRESQLNG -> {
                            assertEquals(4, statements.size)
                            assertEquals("CREATE SEQUENCE IF NOT EXISTS test_table_id_seq START WITH 1 MINVALUE 1 MAXVALUE 9223372036854775807", statements[0])
                            assertEquals("ALTER TABLE test_table ALTER COLUMN id SET DEFAULT nextval('test_table_id_seq')", statements[1])
                            assertEquals("ALTER SEQUENCE test_table_id_seq OWNED BY test_table.id", statements[2])
                            assertEquals("DROP SEQUENCE IF EXISTS $sequenceName", statements[3])
                        }
                        TestDB.SQLSERVER -> {
                            assertEquals(4, statements.size)
                            assertEquals("ALTER TABLE test_table ADD NEW_id BIGINT IDENTITY(1,1)", statements[0])
                            assertEquals("ALTER TABLE test_table DROP COLUMN id", statements[1])
                            assertEquals("EXEC sp_rename 'test_table.NEW_id', 'id', 'COLUMN'", statements[2])
                            assertEquals("DROP SEQUENCE $sequenceName", statements[3])
                        }
                        TestDB.ORACLE, TestDB.H2_V2_ORACLE -> {
                            assertEquals(2, statements.size)
                            assertEquals("CREATE SEQUENCE test_table_id_seq START WITH 1 MINVALUE 1 MAXVALUE 9223372036854775807", statements[0])
                            assertTrue(statements[1].equals("DROP SEQUENCE $sequenceName", ignoreCase = true))
                        }
                        TestDB.H2_V1 -> {
                            assertEquals(1, statements.size)
                            assertEquals("ALTER TABLE TEST_TABLE ALTER COLUMN ID BIGINT AUTO_INCREMENT NOT NULL", statements[0])
                        }
                        else -> {
                            assertEquals(2, statements.size)
                            assertTrue(statements[0].startsWith("ALTER TABLE TEST_TABLE ALTER COLUMN ID", ignoreCase = true))
                            assertTrue(
                                statements[1].equals(
                                    "DROP SEQUENCE${" IF EXISTS".takeIf { currentDialectTest.supportsIfNotExists } ?: ""} $sequenceName",
                                    ignoreCase = true
                                )
                            )
                        }
                    }
                } finally {
                    SchemaUtils.drop(tableWithAutoIncrementSequenceName)
                }
            }
        }
    }

    @Test
    fun testAddCustomSequenceToExistingAutoIncrementColumnWithSequenceName() {
        val sequenceName = "custom_sequence"
        val tableWithAutoIncrementSequenceName = object : IdTable<Long>("test_table") {
            override val id: Column<EntityID<Long>> = long("id").autoIncrement(sequenceName).entityId()
        }
        val tableWithAutoIncrementCustomSequence = object : IdTable<Long>("test_table") {
            override val id: Column<EntityID<Long>> = long("id").autoIncrement(sequence).entityId()
        }

        withDb(excludeSettings = listOf(TestDB.SQLITE)) { testDb ->
            if (currentDialectTest.supportsCreateSequence) {
                try {
                    SchemaUtils.create(tableWithAutoIncrementSequenceName)

                    assertEquals(0, MigrationUtils.statementsRequiredForDatabaseMigration(tableWithAutoIncrementSequenceName, withLogs = false).size)

                    val statements = MigrationUtils.statementsRequiredForDatabaseMigration(tableWithAutoIncrementCustomSequence, withLogs = false)
                    when (testDb) {
                        TestDB.H2_V1 -> {
                            assertEquals(1, statements.size)
                            assertEquals(
                                "CREATE SEQUENCE${" IF NOT EXISTS".takeIf { currentDialectTest.supportsIfNotExists } ?: ""} " +
                                    "${sequence.name} START WITH 4 INCREMENT BY 2 MINVALUE 1 MAXVALUE 100 CYCLE CACHE 20",
                                statements[0]
                            )
                        }
                        else -> {
                            assertEquals(2, statements.size)
                            assertEquals(
                                "CREATE SEQUENCE${" IF NOT EXISTS".takeIf { currentDialectTest.supportsIfNotExists } ?: ""} " +
                                    "${sequence.name} START WITH 4 INCREMENT BY 2 MINVALUE 1 MAXVALUE 100 CYCLE CACHE 20",
                                statements[0]
                            )
                            assertTrue(
                                statements[1].equals(
                                    "DROP SEQUENCE${" IF EXISTS".takeIf { currentDialectTest.supportsIfNotExists } ?: ""} $sequenceName",
                                    ignoreCase = true
                                )
                            )
                        }
                    }
                } finally {
                    SchemaUtils.drop(tableWithAutoIncrementSequenceName)
                }
            }
        }
    }

    @Test
    fun testDropAutoIncrementWithCustomSequenceOnExistingColumn() {
        val tableWithAutoIncrementCustomSequence = object : IdTable<Long>("test_table") {
            override val id: Column<EntityID<Long>> = long("id").autoIncrement(sequence).entityId()
        }
        val tableWithoutAutoIncrement = object : IdTable<Long>("test_table") {
            override val id: Column<EntityID<Long>> = long("id").entityId()
        }

        withDb(excludeSettings = listOf(TestDB.SQLITE)) { testDb ->
            if (currentDialectTest.supportsCreateSequence) {
                try {
                    SchemaUtils.create(tableWithAutoIncrementCustomSequence)

                    assertEquals(0, MigrationUtils.statementsRequiredForDatabaseMigration(tableWithAutoIncrementCustomSequence, withLogs = false).size)

                    val statements = MigrationUtils.statementsRequiredForDatabaseMigration(tableWithoutAutoIncrement, withLogs = false)
                    when (testDb) {
                        TestDB.H2_V1 -> {
                            assertEquals(0, statements.size)
                        }
                        else -> {
                            assertEquals(1, statements.size)
                            assertTrue(
                                statements[0].equals(
                                    "DROP SEQUENCE${" IF EXISTS".takeIf { currentDialectTest.supportsIfNotExists } ?: ""} ${sequence.name}",
                                    ignoreCase = true
                                )
                            )
                        }
                    }
                } finally {
                    SchemaUtils.drop(tableWithAutoIncrementCustomSequence)
                }
            }
        }
    }

    @Test
    fun testDropCustomSequenceOnExistingAutoIncrementColumn() {
        val tableWithAutoIncrementCustomSequence = object : IdTable<Long>("test_table") {
            override val id: Column<EntityID<Long>> = long("id").autoIncrement(sequence).entityId()
        }
        val tableWithAutoIncrement = object : IdTable<Long>("test_table") {
            override val id: Column<EntityID<Long>> = long("id").autoIncrement().entityId()
        }

        withDb(excludeSettings = listOf(TestDB.SQLITE)) { testDb ->
            if (currentDialectTest.supportsCreateSequence) {
                try {
                    SchemaUtils.create(tableWithAutoIncrementCustomSequence)

                    assertEquals(0, MigrationUtils.statementsRequiredForDatabaseMigration(tableWithAutoIncrementCustomSequence).size)

                    val statements = MigrationUtils.statementsRequiredForDatabaseMigration(tableWithAutoIncrement, withLogs = false)
                    when (testDb) {
                        TestDB.POSTGRESQL, TestDB.POSTGRESQLNG -> {
                            assertEquals(4, statements.size)
                            assertEquals("CREATE SEQUENCE IF NOT EXISTS test_table_id_seq START WITH 1 MINVALUE 1 MAXVALUE 9223372036854775807", statements[0])
                            assertEquals("ALTER TABLE test_table ALTER COLUMN id SET DEFAULT nextval('test_table_id_seq')", statements[1])
                            assertEquals("ALTER SEQUENCE test_table_id_seq OWNED BY test_table.id", statements[2])
                            assertEquals("DROP SEQUENCE IF EXISTS ${sequence.name}", statements[3])
                        }
                        TestDB.SQLSERVER -> {
                            assertEquals(4, statements.size)
                            assertEquals("ALTER TABLE test_table ADD NEW_id BIGINT IDENTITY(1,1)", statements[0])
                            assertEquals("ALTER TABLE test_table DROP COLUMN id", statements[1])
                            assertEquals("EXEC sp_rename 'test_table.NEW_id', 'id', 'COLUMN'", statements[2])
                            assertEquals("DROP SEQUENCE ${sequence.name}", statements[3])
                        }
                        TestDB.ORACLE, TestDB.H2_V2_ORACLE -> {
                            assertEquals(2, statements.size)
                            assertEquals("CREATE SEQUENCE test_table_id_seq START WITH 1 MINVALUE 1 MAXVALUE 9223372036854775807", statements[0])
                            assertTrue(statements[1].equals("DROP SEQUENCE ${sequence.name}", ignoreCase = true))
                        }
                        TestDB.H2_V1 -> {
                            assertEquals(1, statements.size)
                            assertEquals("ALTER TABLE TEST_TABLE ALTER COLUMN ID BIGINT AUTO_INCREMENT NOT NULL", statements[0])
                        }
                        else -> {
                            assertEquals(2, statements.size)
                            assertTrue(statements[0].startsWith("ALTER TABLE TEST_TABLE ALTER COLUMN ID", ignoreCase = true))
                            assertTrue(
                                statements[1].equals(
                                    "DROP SEQUENCE${" IF EXISTS".takeIf { currentDialectTest.supportsIfNotExists } ?: ""} ${sequence.name}",
                                    ignoreCase = true
                                )
                            )
                        }
                    }
                } finally {
                    SchemaUtils.drop(tableWithAutoIncrementCustomSequence)
                }
            }
        }
    }

    @Test
    fun testAddSequenceNameToExistingAutoIncrementColumnWithCustomSequence() {
        val sequenceName = "custom_sequence"
        val tableWithAutoIncrementCustomSequence = object : IdTable<Long>("test_table") {
            override val id: Column<EntityID<Long>> = long("id").autoIncrement(sequence).entityId()
        }
        val tableWithAutoIncrementSequenceName = object : IdTable<Long>("test_table") {
            override val id: Column<EntityID<Long>> = long("id").autoIncrement(sequenceName).entityId()
        }

        withDb(excludeSettings = listOf(TestDB.SQLITE)) { testDb ->
            if (currentDialectTest.supportsCreateSequence) {
                try {
                    SchemaUtils.create(tableWithAutoIncrementCustomSequence)

                    assertEquals(0, MigrationUtils.statementsRequiredForDatabaseMigration(tableWithAutoIncrementCustomSequence).size)

                    val statements = MigrationUtils.statementsRequiredForDatabaseMigration(tableWithAutoIncrementSequenceName, withLogs = false)
                    when (testDb) {
                        TestDB.H2_V1 -> {
                            assertEquals(1, statements.size)
                            assertEquals(
                                "CREATE SEQUENCE${" IF NOT EXISTS".takeIf { currentDialectTest.supportsIfNotExists } ?: ""} " +
                                    "$sequenceName START WITH 1 MINVALUE 1 MAXVALUE 9223372036854775807",
                                statements[0]
                            )
                        }
                        else -> {
                            assertEquals(2, statements.size)
                            assertEquals(
                                "CREATE SEQUENCE${" IF NOT EXISTS".takeIf { currentDialectTest.supportsIfNotExists } ?: ""} " +
                                    "$sequenceName START WITH 1 MINVALUE 1 MAXVALUE 9223372036854775807",
                                statements[0]
                            )
                            assertTrue(
                                statements[1].equals(
                                    "DROP SEQUENCE${" IF EXISTS".takeIf { currentDialectTest.supportsIfNotExists } ?: ""} ${sequence.name}",
                                    ignoreCase = true
                                )
                            )
                        }
                    }
                } finally {
                    SchemaUtils.drop(tableWithAutoIncrementCustomSequence)
                }
            }
        }
    }

    private val sequence = Sequence(
        name = "my_sequence",
        startWith = 4,
        incrementBy = 2,
        minValue = 1,
        maxValue = 100,
        cycle = true,
        cache = 20
    )
}

package org.jetbrains.exposed.v1.migration.r2dbc

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Sequence
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.vendors.MysqlDialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.exists
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.currentDialectMetadataTest
import org.jetbrains.exposed.v1.r2dbc.tests.currentDialectTest
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertFalse
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertTrue
import org.junit.Before
import org.junit.Test

class SequenceAutoIncrementTests : R2dbcDatabaseTestsBase() {
    @Before
    fun dropAllSequences() {
        withDb {
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
    fun testAddAutoIncrementToExistingColumn() {
        withTables(MigrationTestsData.TableWithoutAutoIncrement) { testDb ->
            assertNoStatementsRequiredForMigration(MigrationTestsData.TableWithoutAutoIncrement)

            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(MigrationTestsData.TableWithAutoIncrement, withLogs = false)
            when (testDb) {
                in TestDB.ALL_POSTGRES -> {
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
                in TestDB.ALL_ORACLE_LIKE -> {
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
        withTables(MigrationTestsData.TableWithoutAutoIncrement) {
            assertNoStatementsRequiredForMigration(MigrationTestsData.TableWithoutAutoIncrement)

            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(
                MigrationTestsData.TableWithAutoIncrementSequenceName,
                withLogs = false
            )
            assertEquals(1, statements.size)
            if (currentDialectTest.supportsCreateSequence) {
                assertEquals(expectedCreateSequenceStatement(MigrationTestsData.SEQUENCE_NAME), statements[0])
            } else {
                val alterColumnWord = if (currentDialectTest is MysqlDialect) "MODIFY" else "ALTER"
                assertTrue(statements[0].equals("ALTER TABLE TEST_TABLE $alterColumnWord COLUMN ID BIGINT AUTO_INCREMENT NOT NULL", ignoreCase = true))
            }
        }
    }

    @Test
    fun testAddAutoIncrementWithCustomSequenceToExistingColumn() {
        withTables(MigrationTestsData.TableWithoutAutoIncrement) {
            if (currentDialectTest.supportsCreateSequence) {
                assertNoStatementsRequiredForMigration(MigrationTestsData.TableWithoutAutoIncrement)

                val statements = MigrationUtils.statementsRequiredForDatabaseMigration(
                    MigrationTestsData.TableWithAutoIncrementCustomSequence,
                    withLogs = false
                )
                assertEquals(1, statements.size)
                assertEquals(expectedCreateSequenceStatement(MigrationTestsData.customSequence.name), statements[0])
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

        withTables(tableWithAutoIncrement) { testDb ->
            assertNoStatementsRequiredForMigration(tableWithAutoIncrement)

            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(tableWithoutAutoIncrement, withLogs = false)
            when (testDb) {
                in TestDB.ALL_POSTGRES -> {
                    assertEquals(2, statements.size)
                    assertEquals("ALTER TABLE test_table ALTER COLUMN id TYPE BIGINT, ALTER COLUMN id DROP DEFAULT", statements[0])
                    assertEquals(expectedDropSequenceStatement("test_table_id_seq"), statements[1])
                }
                in TestDB.ALL_ORACLE_LIKE -> {
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

        withTables(tableWithAutoIncrement) { testDb ->
            if (currentDialectTest.supportsCreateSequence) {
                assertNoStatementsRequiredForMigration(tableWithAutoIncrement)

                val statements = MigrationUtils.statementsRequiredForDatabaseMigration(tableWithAutoIncrementSequenceName, withLogs = false)
                assertEquals(expectedCreateSequenceStatement(sequenceName), statements[0])
                when (testDb) {
                    in TestDB.ALL_POSTGRES -> {
                        assertEquals(3, statements.size)
                        assertEquals("ALTER TABLE test_table ALTER COLUMN id TYPE BIGINT, ALTER COLUMN id DROP DEFAULT", statements[1])
                        assertEquals(expectedDropSequenceStatement("test_table_id_seq"), statements[2])
                    }
                    in TestDB.ALL_ORACLE_LIKE -> {
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
        withDb { testDb ->
            if (currentDialectTest.supportsCreateSequence) {
                try {
                    // MariaDB does not allow to create auto column without defining it as a key
                    val tableWithAutoIncrement = if (testDb == TestDB.MARIADB) {
                        object : IdTable<Long>("test_table") {
                            override val id: Column<EntityID<Long>> = long("id").autoIncrement().entityId()
                            override val primaryKey = PrimaryKey(id)
                        }
                    } else {
                        MigrationTestsData.TableWithAutoIncrement
                    }

                    SchemaUtils.create(tableWithAutoIncrement)
                    assertNoStatementsRequiredForMigration(tableWithAutoIncrement)

                    val statements = MigrationUtils.statementsRequiredForDatabaseMigration(
                        MigrationTestsData.TableWithAutoIncrementCustomSequence,
                        withLogs = false
                    )
                    assertEquals(expectedCreateSequenceStatement(MigrationTestsData.customSequence.name), statements[0])
                    when (testDb) {
                        in TestDB.ALL_POSTGRES -> {
                            assertEquals(3, statements.size)
                            assertEquals("ALTER TABLE test_table ALTER COLUMN id TYPE BIGINT, ALTER COLUMN id DROP DEFAULT", statements[1])
                            assertEquals(expectedDropSequenceStatement("test_table_id_seq"), statements[2])
                        }
                        in TestDB.ALL_ORACLE_LIKE -> {
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
                    SchemaUtils.drop(MigrationTestsData.TableWithAutoIncrement)
                }
            }
        }
    }

    @Test
    fun testDropAutoIncrementWithSequenceNameOnExistingColumn() {
        withDb { testDb ->
            if (currentDialectTest.supportsCreateSequence) {
                try {
                    SchemaUtils.create(MigrationTestsData.TableWithAutoIncrementSequenceName)
                    assertNoStatementsRequiredForMigration(MigrationTestsData.TableWithAutoIncrementSequenceName)

                    val statements = MigrationUtils.statementsRequiredForDatabaseMigration(
                        MigrationTestsData.TableWithoutAutoIncrement,
                        withLogs = false
                    )
                    when (testDb) {
                        in TestDB.ALL_POSTGRES -> {
                            // previous sequence used by column is altered but no longer dropped as not linked
                            assertEquals(0, statements.size)
                        }
                        else -> {
                            assertEquals(1, statements.size)
                            assertTrue(
                                statements[0].equals(expectedDropSequenceStatement(MigrationTestsData.SEQUENCE_NAME), ignoreCase = true)
                            )
                        }
                    }
                } finally {
                    SchemaUtils.drop(MigrationTestsData.TableWithAutoIncrementSequenceName)
                }
            }
        }
    }

    @Test
    fun testDropSequenceNameOnExistingAutoIncrementColumn() {
        withDb { testDb ->
            if (currentDialectTest.supportsCreateSequence) {
                try {
                    SchemaUtils.create(MigrationTestsData.TableWithAutoIncrementSequenceName)
                    assertNoStatementsRequiredForMigration(MigrationTestsData.TableWithAutoIncrementSequenceName)

                    val statements = MigrationUtils.statementsRequiredForDatabaseMigration(
                        MigrationTestsData.TableWithAutoIncrement,
                        withLogs = false
                    )
                    when (testDb) {
                        in TestDB.ALL_POSTGRES -> {
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
                            assertEquals(expectedDropSequenceStatement(MigrationTestsData.SEQUENCE_NAME), statements[3])
                        }
                        in TestDB.ALL_ORACLE_LIKE -> {
                            assertEquals(2, statements.size)
                            assertEquals(expectedCreateSequenceStatement("test_table_id_seq"), statements[0])
                            assertTrue(statements[1].equals(expectedDropSequenceStatement(MigrationTestsData.SEQUENCE_NAME), ignoreCase = true))
                        }
                        TestDB.MARIADB -> {
                            assertEquals(2, statements.size)
                            assertEquals("ALTER TABLE test_table MODIFY COLUMN id BIGINT AUTO_INCREMENT NOT NULL", statements[0])
                            assertEquals(expectedDropSequenceStatement(MigrationTestsData.SEQUENCE_NAME), statements[1])
                        }
                        else -> {
                            assertEquals(2, statements.size)
                            assertTrue(statements[0].startsWith("ALTER TABLE TEST_TABLE ALTER COLUMN ID", ignoreCase = true))
                            assertTrue(statements[1].equals(expectedDropSequenceStatement(MigrationTestsData.SEQUENCE_NAME), ignoreCase = true))
                        }
                    }
                } finally {
                    SchemaUtils.drop(MigrationTestsData.TableWithAutoIncrementSequenceName)
                }
            }
        }
    }

    @Test
    fun testAddCustomSequenceToExistingAutoIncrementColumnWithSequenceName() {
        withDb { testDb ->
            if (currentDialectTest.supportsCreateSequence) {
                try {
                    SchemaUtils.create(MigrationTestsData.TableWithAutoIncrementSequenceName)
                    assertNoStatementsRequiredForMigration(MigrationTestsData.TableWithAutoIncrementSequenceName)

                    val statements = MigrationUtils.statementsRequiredForDatabaseMigration(
                        MigrationTestsData.TableWithAutoIncrementCustomSequence,
                        withLogs = false
                    )
                    when (testDb) {
                        in TestDB.ALL_POSTGRES -> {
                            // previous sequence used by column is altered but no longer dropped as not linked
                            assertEquals(1, statements.size)
                            assertEquals(expectedCreateSequenceStatement(MigrationTestsData.customSequence.name), statements[0])
                        }
                        else -> {
                            assertEquals(2, statements.size)
                            assertEquals(expectedCreateSequenceStatement(MigrationTestsData.customSequence.name), statements[0])
                            assertTrue(
                                statements[1].equals(expectedDropSequenceStatement(MigrationTestsData.SEQUENCE_NAME), ignoreCase = true)
                            )
                        }
                    }
                } finally {
                    SchemaUtils.drop(MigrationTestsData.TableWithAutoIncrementSequenceName)
                }
            }
        }
    }

    @Test
    fun testDropAutoIncrementWithCustomSequenceOnExistingColumn() {
        withDb { testDb ->
            if (currentDialectTest.supportsCreateSequence) {
                try {
                    SchemaUtils.create(MigrationTestsData.TableWithAutoIncrementCustomSequence)
                    assertNoStatementsRequiredForMigration(MigrationTestsData.TableWithAutoIncrementCustomSequence)

                    val statements = MigrationUtils.statementsRequiredForDatabaseMigration(
                        MigrationTestsData.TableWithoutAutoIncrement,
                        withLogs = false
                    )
                    when (testDb) {
                        in TestDB.ALL_POSTGRES -> {
                            // previous sequence used by column is altered but no longer dropped as not linked
                            assertEquals(0, statements.size)
                        }
                        else -> {
                            assertEquals(1, statements.size)
                            assertTrue(
                                statements[0].equals(expectedDropSequenceStatement(MigrationTestsData.customSequence.name), ignoreCase = true)
                            )
                        }
                    }
                } finally {
                    SchemaUtils.drop(MigrationTestsData.TableWithAutoIncrementCustomSequence)
                }
            }
        }
    }

    @Test
    fun testDropCustomSequenceOnExistingAutoIncrementColumn() {
        withDb { testDb ->
            if (currentDialectTest.supportsCreateSequence) {
                try {
                    SchemaUtils.create(MigrationTestsData.TableWithAutoIncrementCustomSequence)
                    assertNoStatementsRequiredForMigration(MigrationTestsData.TableWithAutoIncrementCustomSequence)

                    val statements = MigrationUtils.statementsRequiredForDatabaseMigration(
                        MigrationTestsData.TableWithAutoIncrement,
                        withLogs = false
                    )
                    when (testDb) {
                        in TestDB.ALL_POSTGRES -> {
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
                            assertEquals(expectedDropSequenceStatement(MigrationTestsData.customSequence.name), statements[3])
                        }
                        in TestDB.ALL_ORACLE_LIKE -> {
                            assertEquals(2, statements.size)
                            assertEquals(expectedCreateSequenceStatement("test_table_id_seq"), statements[0])
                            assertTrue(
                                statements[1].equals(expectedDropSequenceStatement(MigrationTestsData.customSequence.name), ignoreCase = true)
                            )
                        }
                        TestDB.MARIADB -> {
                            assertEquals(2, statements.size)
                            assertEquals("ALTER TABLE test_table MODIFY COLUMN id BIGINT AUTO_INCREMENT NOT NULL", statements[0])
                            assertEquals(expectedDropSequenceStatement(MigrationTestsData.customSequence.name), statements[1])
                        }
                        else -> {
                            assertEquals(2, statements.size)
                            assertTrue(statements[0].startsWith("ALTER TABLE TEST_TABLE ALTER COLUMN ID", ignoreCase = true))
                            assertTrue(
                                statements[1].equals(expectedDropSequenceStatement(MigrationTestsData.customSequence.name), ignoreCase = true)
                            )
                        }
                    }
                } finally {
                    SchemaUtils.drop(MigrationTestsData.TableWithAutoIncrementCustomSequence)
                }
            }
        }
    }

    @Test
    fun testAddSequenceNameToExistingAutoIncrementColumnWithCustomSequence() {
        withDb { testDb ->
            if (currentDialectTest.supportsCreateSequence) {
                try {
                    SchemaUtils.create(MigrationTestsData.TableWithAutoIncrementCustomSequence)
                    assertNoStatementsRequiredForMigration(MigrationTestsData.TableWithAutoIncrementCustomSequence)

                    val statements = MigrationUtils.statementsRequiredForDatabaseMigration(
                        MigrationTestsData.TableWithAutoIncrementSequenceName,
                        withLogs = false
                    )
                    when (testDb) {
                        in TestDB.ALL_POSTGRES -> {
                            // previous sequence used by column is altered but no longer dropped as not linked
                            assertEquals(1, statements.size)
                            assertEquals(expectedCreateSequenceStatement(MigrationTestsData.SEQUENCE_NAME), statements[0])
                        }
                        else -> {
                            assertEquals(2, statements.size)
                            assertEquals(expectedCreateSequenceStatement(MigrationTestsData.SEQUENCE_NAME), statements[0])
                            assertTrue(
                                statements[1].equals(expectedDropSequenceStatement(MigrationTestsData.customSequence.name), ignoreCase = true)
                            )
                        }
                    }
                } finally {
                    SchemaUtils.drop(MigrationTestsData.TableWithAutoIncrementCustomSequence)
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

        val tableWithExplSequence by lazy {
            object : Table("test_table_expl_seq") {
                val counter = integer("counter").autoIncrement(MigrationTestsData.customSequence)
                override val primaryKey = PrimaryKey(counter)
            }
        }

        val tableWithImplSequence = object : IntIdTable("test_table_impl_seq") {}

        withDb(TestDB.ALL_POSTGRES) {
            try {
                SchemaUtils.create(
                    tableWithAutoIncrement, // uses SERIAL column
                    tableWithExplSequence, // uses Sequence 'my_sequence'
                    tableWithImplSequence // uses SERIAL column
                )

                val autoSeq = tableWithAutoIncrement.sequences.single()
                val implicitSeq = tableWithImplSequence.sequences.single()
                assertTrue(autoSeq.exists())
                assertTrue(MigrationTestsData.customSequence.exists())
                assertTrue(implicitSeq.exists())

                val statements = MigrationUtils.statementsRequiredForDatabaseMigration(tableWithoutAutoIncrement)
                assertEquals(2, statements.size)
                assertEquals("ALTER TABLE test_table_auto ALTER COLUMN id TYPE INT, ALTER COLUMN id DROP DEFAULT", statements[0])
                assertEquals(expectedDropSequenceStatement("test_table_auto_id_seq"), statements[1])

                statements.forEach { exec(it) }
                assertFalse(autoSeq.exists())
                assertTrue(MigrationTestsData.customSequence.exists())
                assertTrue(implicitSeq.exists())
            } finally {
                SchemaUtils.drop(tableWithAutoIncrement, tableWithExplSequence, tableWithImplSequence)
            }
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

    private fun expectedCreateSequenceStatement(sequenceName: String) =
        "CREATE SEQUENCE${" IF NOT EXISTS".takeIf { currentDialectTest.supportsIfNotExists } ?: ""} " +
            "$sequenceName START WITH 1 MINVALUE 1 MAXVALUE ${currentDialectTest.sequenceMaxValue}"

    private fun expectedDropSequenceStatement(sequenceName: String) =
        "DROP SEQUENCE${" IF EXISTS".takeIf { currentDialectTest.supportsIfNotExists } ?: ""} $sequenceName"

    private suspend fun R2dbcTransaction.assertNoStatementsRequiredForMigration(table: Table) {
        val statements = MigrationUtils.statementsRequiredForDatabaseMigration(table, withLogs = false)
        assertEquals(0, statements.size)
    }
}

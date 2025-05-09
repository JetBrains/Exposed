package org.jetbrains.exposed.v1.sql.tests.shared.ddl

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Sequence
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.autoIncColumnType
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.core.nextIntVal
import org.jetbrains.exposed.v1.core.nextLongVal
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.dao.UUIDEntity
import org.jetbrains.exposed.v1.dao.UUIDEntityClass
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.sql.tests.TestDB
import org.jetbrains.exposed.v1.sql.tests.currentDialectMetadataTest
import org.jetbrains.exposed.v1.sql.tests.currentDialectTest
import org.jetbrains.exposed.v1.sql.tests.inProperCase
import org.jetbrains.exposed.v1.sql.tests.shared.assertEquals
import org.jetbrains.exposed.v1.sql.tests.shared.assertFalse
import org.jetbrains.exposed.v1.sql.tests.shared.assertTrue
import org.junit.Assume
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SequencesTests : DatabaseTestsBase() {
    @Test
    fun createSequenceStatementTest() {
        withDb {
            if (currentDialectTest.supportsCreateSequence) {
                assertEquals(
                    "CREATE SEQUENCE " + addIfNotExistsIfSupported() + "${myseq.identifier} " +
                        "START WITH ${myseq.startWith} " +
                        "INCREMENT BY ${myseq.incrementBy} " +
                        "MINVALUE ${myseq.minValue} " +
                        "MAXVALUE ${myseq.maxValue} " +
                        "CYCLE " +
                        "CACHE ${myseq.cache}",
                    myseq.ddl
                )
            }
        }
    }

    @Test
    fun `test insert with sequences`() {
        withTables(Developer) {
            if (currentDialectTest.supportsSequenceAsGeneratedKeys) {
                try {
                    SchemaUtils.createSequence(myseq)

                    var developerId = Developer.insert {
                        it[id] = myseq.nextIntVal()
                        it[name] = "Hichem"
                    } get Developer.id

                    assertEquals(myseq.startWith, developerId.toLong())

                    developerId = Developer.insert {
                        it[id] = myseq.nextIntVal()
                        it[name] = "Andrey"
                    } get Developer.id

                    assertEquals(myseq.startWith!! + myseq.incrementBy!!, developerId.toLong())
                } finally {
                    org.jetbrains.exposed.v1.jdbc.SchemaUtils.dropSequence(myseq)
                }
            }
        }
    }

    @Test
    fun testInsertWithCustomSequence() {
        val tester = object : Table("tester") {
            val id = integer("id").autoIncrement(myseq)
            val name = varchar("name", 25)

            override val primaryKey = PrimaryKey(id, name)
        }
        withDb {
            if (currentDialectTest.supportsSequenceAsGeneratedKeys) {
                try {
                    SchemaUtils.create(tester)
                    assertTrue(myseq.exists())

                    var testerId = tester.insert {
                        it[name] = "Hichem"
                    } get tester.id

                    assertEquals(myseq.startWith, testerId.toLong())

                    testerId = tester.insert {
                        it[name] = "Andrey"
                    } get tester.id

                    assertEquals(myseq.startWith!! + myseq.incrementBy!!, testerId.toLong())
                } finally {
                    org.jetbrains.exposed.v1.jdbc.SchemaUtils.drop(tester)
                    assertFalse(myseq.exists())
                }
            }
        }
    }

    @Test
    fun `test insert int IdTable with sequences`() {
        withTables(DeveloperWithLongId) {
            if (currentDialectTest.supportsSequenceAsGeneratedKeys) {
                try {
                    SchemaUtils.createSequence(myseq)

                    var developerId = DeveloperWithLongId.insertAndGetId {
                        it[id] = myseq.nextLongVal()
                        it[name] = "Hichem"
                    }

                    assertEquals(myseq.startWith, developerId.value)

                    developerId = DeveloperWithLongId.insertAndGetId {
                        it[id] = myseq.nextLongVal()
                        it[name] = "Andrey"
                    }
                    assertEquals(myseq.startWith!! + myseq.incrementBy!!, developerId.value)
                } finally {
                    org.jetbrains.exposed.v1.jdbc.SchemaUtils.dropSequence(myseq)
                }
            }
        }
    }

    @Test
    fun testInsertInIdTableWithCustomSequence() {
        val tester = object : IdTable<Long>("tester") {
            override val id = long("id").autoIncrement(myseq).entityId()
            val name = varchar("name", 25)

            override val primaryKey = PrimaryKey(id, name)
        }
        withDb {
            if (currentDialectTest.supportsSequenceAsGeneratedKeys) {
                try {
                    SchemaUtils.create(tester)
                    assertTrue(myseq.exists())

                    var testerId = tester.insert {
                        it[name] = "Hichem"
                    } get tester.id

                    assertEquals(myseq.startWith, testerId.value)

                    testerId = tester.insert {
                        it[name] = "Andrey"
                    } get tester.id

                    assertEquals(myseq.startWith!! + myseq.incrementBy!!, testerId.value)
                } finally {
                    org.jetbrains.exposed.v1.jdbc.SchemaUtils.drop(tester)
                    assertFalse(myseq.exists())
                }
            }
        }
    }

    @Test
    fun `test insert LongIdTable with auto-increment with sequence`() {
        withDb {
            if (currentDialectTest.supportsSequenceAsGeneratedKeys) {
                try {
                    SchemaUtils.create(DeveloperWithAutoIncrementBySequence)
                    val developerId = DeveloperWithAutoIncrementBySequence.insertAndGetId {
                        it[name] = "Hichem"
                    }

                    assertNotNull(developerId)

                    val developerId2 = DeveloperWithAutoIncrementBySequence.insertAndGetId {
                        it[name] = "Andrey"
                    }
                    assertEquals(developerId.value + 1, developerId2.value)
                } finally {
                    org.jetbrains.exposed.v1.jdbc.SchemaUtils.drop(DeveloperWithAutoIncrementBySequence)
                }
            }
        }
    }

    @Test
    fun `test select with nextVal`() {
        withTables(Developer) {
            if (currentDialectTest.supportsCreateSequence) {
                try {
                    SchemaUtils.createSequence(myseq)
                    val nextVal = myseq.nextIntVal()
                    Developer.insert {
                        it[id] = nextVal
                        it[name] = "Hichem"
                    }

                    val firstValue = Developer.select(nextVal).single()[nextVal]
                    val secondValue = Developer.select(nextVal).single()[nextVal]

                    val expFirstValue = myseq.startWith!! + myseq.incrementBy!!
                    assertEquals(expFirstValue, firstValue.toLong())

                    val expSecondValue = expFirstValue + myseq.incrementBy!!
                    assertEquals(expSecondValue, secondValue.toLong())
                } finally {
                    org.jetbrains.exposed.v1.jdbc.SchemaUtils.dropSequence(myseq)
                }
            }
        }
    }

    @Test
    fun testManuallyCreatedSequenceExists() {
        withDb {
            if (currentDialectTest.supportsCreateSequence) {
                try {
                    SchemaUtils.createSequence(myseq)

                    assertTrue(myseq.exists())
                } finally {
                    org.jetbrains.exposed.v1.jdbc.SchemaUtils.dropSequence(myseq)
                }
            }
        }
    }

    @Test
    fun testExistingSequencesForAutoIncrementWithCustomSequence() {
        val tableWithExplicitSequenceName = object : IdTable<Long>() {
            override val id: Column<EntityID<Long>> = long("id").autoIncrement(myseq).entityId()
        }

        withDb {
            if (currentDialectTest.supportsSequenceAsGeneratedKeys) {
                try {
                    SchemaUtils.create(tableWithExplicitSequenceName)

                    val sequences = currentDialectMetadataTest.sequences()

                    assertTrue(sequences.isNotEmpty())
                    assertTrue(sequences.any { it == myseq.name.inProperCase() })
                } finally {
                    org.jetbrains.exposed.v1.jdbc.SchemaUtils.drop(tableWithExplicitSequenceName)
                }
            }
        }
    }

    @Test
    fun testExistingSequencesForAutoIncrementWithExplicitSequenceName() {
        val sequenceName = "id_seq"
        val tableWithExplicitSequenceName = object : IdTable<Long>() {
            override val id: Column<EntityID<Long>> = long("id").autoIncrement(sequenceName).entityId()
        }

        withDb {
            if (currentDialectTest.supportsSequenceAsGeneratedKeys) {
                try {
                    SchemaUtils.create(tableWithExplicitSequenceName)

                    val sequences = currentDialectMetadataTest.sequences()

                    assertTrue(sequences.isNotEmpty())
                    assertTrue(sequences.any { it == sequenceName.inProperCase() })
                } finally {
                    org.jetbrains.exposed.v1.jdbc.SchemaUtils.drop(tableWithExplicitSequenceName)
                }
            }
        }
    }

    @Test
    fun testExistingSequencesForAutoIncrementWithoutExplicitSequenceName() {
        val tableWithoutExplicitSequenceName = object : IdTable<Long>() {
            override val id: Column<EntityID<Long>> = long("id").autoIncrement().entityId()
        }

        withDb { testDb ->
            if (currentDialect.needsSequenceToAutoInc) {
                try {
                    SchemaUtils.create(tableWithoutExplicitSequenceName)

                    val sequences = currentDialectMetadataTest.sequences()

                    assertTrue(sequences.isNotEmpty())

                    val expected = tableWithoutExplicitSequenceName.id.autoIncColumnType!!.autoincSeq!!
                    assertTrue(sequences.any { it == if (testDb == TestDB.ORACLE) expected.inProperCase() else expected })
                } finally {
                    org.jetbrains.exposed.v1.jdbc.SchemaUtils.drop(tableWithoutExplicitSequenceName)
                }
            }
        }
    }

    @Test
    fun testNoCreateStatementForExistingSequence() {
        withDb {
            if (currentDialectTest.supportsSequenceAsGeneratedKeys) {
                val createSequencePrefix = "CREATE SEQUENCE"

                assertNotNull(SchemaUtils.createStatements(DeveloperWithAutoIncrementBySequence).find { it.startsWith(createSequencePrefix) })

                SchemaUtils.create(DeveloperWithAutoIncrementBySequence)

                // Remove table without removing sequence
                exec("DROP TABLE ${DeveloperWithAutoIncrementBySequence.nameInDatabaseCase()}")

                assertNull(SchemaUtils.createStatements(DeveloperWithAutoIncrementBySequence).find { it.startsWith(createSequencePrefix) })
                assertNull(
                    org.jetbrains.exposed.v1.jdbc.SchemaUtils.statementsRequiredToActualizeScheme(DeveloperWithAutoIncrementBySequence).find {
                        it.startsWith(createSequencePrefix)
                    }
                )

                // Clean up: create table and drop it for removing sequence
                SchemaUtils.create(DeveloperWithAutoIncrementBySequence)
                org.jetbrains.exposed.v1.jdbc.SchemaUtils.drop(DeveloperWithAutoIncrementBySequence)
            }
        }
    }

    @Test
    fun testExistingSequencesDetectedInIdentityTable() {
        val identityTable = object : IntIdTable("identity_table") {}

        withDb(TestDB.ALL_POSTGRES) {
            try {
                SchemaUtils.create(identityTable)

                val foundSequence = currentDialectMetadataTest.existingSequences(identityTable)[identityTable]?.single()
                assertNotNull(foundSequence)
                assertEquals(identityTable.sequences.single().identifier, foundSequence.identifier)
            } finally {
                org.jetbrains.exposed.v1.jdbc.SchemaUtils.drop(identityTable)
            }
        }
    }

    @Test
    fun testAutoIncrementColumnAccessWithEntity() {
        Assume.assumeTrue(TestDB.POSTGRESQL in TestDB.enabledDialects())

        TestDB.POSTGRESQL.connect()

        try {
            transaction {
                SchemaUtils.create(TesterTable)
            }

            val testerEntity = transaction {
                TesterEntity.new {
                    name = "test row"
                }
            }

            assertEquals(1, testerEntity.index)
        } finally {
            transaction {
                SchemaUtils.drop(TesterTable)
            }
        }
    }

    object TesterTable : UUIDTable("Tester") {
        val index = integer("index").autoIncrement()
        val name = text("name")
    }

    class TesterEntity(id: EntityID<UUID>) : UUIDEntity(id) {
        companion object : UUIDEntityClass<TesterEntity>(TesterTable)

        var index by TesterTable.index
        var name by TesterTable.name
    }

    private object Developer : Table() {
        val id = integer("id")
        val name = varchar("name", 25)

        override val primaryKey = PrimaryKey(id, name)
    }

    private object DeveloperWithLongId : LongIdTable() {
        val name = varchar("name", 25)
    }

    private object DeveloperWithAutoIncrementBySequence : IdTable<Long>() {
        override val id: Column<EntityID<Long>> = long("id").autoIncrement("id_seq").entityId()
        val name = varchar("name", 25)
    }

    private val myseq = Sequence(
        name = "my_sequence",
        startWith = 4,
        incrementBy = 2,
        minValue = 1,
        maxValue = 100,
        cycle = true,
        cache = 20
    )
}

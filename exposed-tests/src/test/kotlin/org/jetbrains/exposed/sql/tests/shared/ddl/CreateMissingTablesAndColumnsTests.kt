package org.jetbrains.exposed.sql.tests.shared.ddl

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.inProperCase
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.assertFailAndRollback
import org.jetbrains.exposed.sql.tests.shared.assertFalse
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.sql.SQLException
import java.util.*

class CreateMissingTablesAndColumnsTests : DatabaseTestsBase() {

    @Test
    fun testCreateMissingTablesAndColumns01() {
        val TestTable = object : Table("test_table") {
            val id = integer("id")
            val name = varchar("name", length = 42)
            val time = long("time").uniqueIndex()

            override val primaryKey = PrimaryKey(id)
        }

        withTables(excludeSettings = listOf(TestDB.H2_MYSQL), tables = *arrayOf(TestTable)) {
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


        withDb(TestDB.H2) {
            SchemaUtils.createMissingTablesAndColumns(initialTable)
            assertEquals("ALTER TABLE ${tableName.inProperCase()} ADD ${"id".inProperCase()} ${t.id.columnType.sqlType()}", t.id.ddl.first())
            assertEquals("ALTER TABLE ${tableName.inProperCase()} ADD CONSTRAINT pk_$tableName PRIMARY KEY (${"id".inProperCase()})", t.id.ddl[1])
            assertEquals(1, currentDialectTest.tableColumns(t)[t]!!.size)
            SchemaUtils.createMissingTablesAndColumns(t)
            assertEquals(2, currentDialectTest.tableColumns(t)[t]!!.size)
            SchemaUtils.drop(t)
        }

        withDb(TestDB.SQLITE) {
            try {
                SchemaUtils.createMissingTablesAndColumns(t)
                assertFalse(db.supportsAlterTableWithAddColumn)
            } catch (e: SQLException) {
                // SQLite doesn't support
            } finally {
                SchemaUtils.drop(t)
            }
        }

        withTables(excludeSettings = listOf(TestDB.H2, TestDB.H2_MYSQL, TestDB.SQLITE), tables = *arrayOf(initialTable)) {
            assertEquals("ALTER TABLE ${tableName.inProperCase()} ADD ${"id".inProperCase()} ${t.id.columnType.sqlType()} PRIMARY KEY", t.id.ddl)
            assertEquals(1, currentDialectTest.tableColumns(t)[t]!!.size)
            SchemaUtils.createMissingTablesAndColumns(t)
            assertEquals(2, currentDialectTest.tableColumns(t)[t]!!.size)
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

    object MultipleIndexesTable: Table("H2_MULTIPLE_INDEXES") {
        val value1 = varchar("value1", 255)
        val value2 = varchar("value2", 255)

        init {
            uniqueIndex("index1", value1, value2)
            uniqueIndex("index2", value2, value1)
        }
    }

    @Test fun testCreateTableWithReferenceMutipleTimes() {
        withTables(PlayerTable, SessionTable) {
            SchemaUtils.createMissingTablesAndColumns(PlayerTable, SessionTable)
            SchemaUtils.createMissingTablesAndColumns(PlayerTable, SessionTable)
        }
    }

    object PlayerTable: IntIdTable() {
        val username = varchar("username", 10).uniqueIndex().nullable()
    }

    object SessionTable: IntIdTable() {
        val playerId = integer("player_id").references(PlayerTable.id)
    }


    @Test fun createTableWithReservedIdentifierInColumnName() {
        withDb(TestDB.MYSQL) {
            addLogger(StdOutSqlLogger)
            SchemaUtils.createMissingTablesAndColumns(T1, T2)
            SchemaUtils.createMissingTablesAndColumns(T1, T2)

            assertTrue(T1.exists())
            assertTrue(T2.exists())
        }
    }

    object T1: Table("ARRAY") {
        val name = integer("name").uniqueIndex()
        val tmp = varchar("temp", 255)
    }
    object T2: Table("CHAIN") {
        val ref = integer("ref").references(T1.name)
    }
}
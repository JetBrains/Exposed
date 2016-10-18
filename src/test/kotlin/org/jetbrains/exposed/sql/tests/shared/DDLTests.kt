package org.jetbrains.exposed.sql.tests.shared

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IdTable
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.VendorDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.junit.Test
import java.sql.SQLException
import java.util.*
import javax.sql.rowset.serial.SerialBlob
import kotlin.test.assertFalse

class DDLTests : DatabaseTestsBase() {
    @Test fun tableExists01() {
        val TestTable = object : Table() {
            val id = integer("id").primaryKey()
            val name = varchar("name", length = 42)
        }

        withTables {
            assertEquals (false, TestTable.exists())
        }
    }

    @Test fun tableExists02() {
        val TestTable = object : Table() {
            val id = integer("id").primaryKey()
            val name = varchar("name", length = 42)
        }

        withTables(TestTable) {
            assertEquals (true, TestTable.exists())
        }
    }

    @Test fun testCreateMissingTablesAndColumns01() {
        val TestTable = object : Table("test") {
            val id = integer("id").primaryKey()
            val name = varchar("name", length = 42)
        }

        withDb {
            SchemaUtils.createMissingTablesAndColumns(TestTable)
            try {
                assertEquals (true, TestTable.exists())
            } finally {
                SchemaUtils.drop(TestTable)
            }
        }
    }

    @Test fun testCreateMissingTablesAndColumns02() {
        val TestTable = object : IdTable<String>("Users2") {
            override val id: Column<EntityID<String>> = varchar("id", 64).clientDefault { UUID.randomUUID().toString() }.primaryKey().entityId()

            val name = varchar("name", 255)
            val email = varchar("email", 255).uniqueIndex()
        }

        withDb(TestDB.H2) {
            SchemaUtils.createMissingTablesAndColumns(TestTable)
            try {
                assertEquals (true, TestTable.exists())
                SchemaUtils.createMissingTablesAndColumns(TestTable)
            } finally {
                SchemaUtils.drop(TestTable)
            }
        }
    }

    @Test fun unnamedTableWithQuotesSQL() {
        val TestTable = object : Table() {
            val id = integer("id").primaryKey()
            val name = varchar("name", length = 42)
        }

        withTables(TestTable) {
            val q = db.identityQuoteString
            assertEquals("CREATE TABLE IF NOT EXISTS $q${"unnamedTableWithQuotesSQL".inProperCase()}\$${"TestTable".inProperCase()}$1$q " +
                    "(${"id".inProperCase()} INT PRIMARY KEY, ${"name".inProperCase()} VARCHAR(42) NOT NULL)", TestTable.ddl)
        }
    }

    @Test fun namedEmptyTableWithoutQuotesSQL() {
        val TestTable = object : Table("test_named_table") {
        }

        withTables(excludeSettings = listOf(TestDB.MYSQL, TestDB.POSTGRESQL, TestDB.SQLITE), tables = TestTable) {
            assertEquals("CREATE TABLE IF NOT EXISTS ${"test_named_table".inProperCase()}", TestTable.ddl)
        }
    }

    @Test fun tableWithDifferentColumnTypesSQL01() {
        val TestTable = object : Table("test_table_with_different_column_types") {
            val id = integer("id").autoIncrement()
            val name = varchar("name", 42).primaryKey()
            val age = integer("age").nullable()
            // not applicable in H2 database
            //            val testCollate = varchar("testCollate", 2, "ascii_general_ci")
        }

         withTables(excludeSettings = listOf(TestDB.MYSQL), tables = TestTable) {
            assertEquals("CREATE TABLE IF NOT EXISTS ${"test_table_with_different_column_types".inProperCase()} " +
                    "(${"id".inProperCase()} ${currentDialect.dataTypeProvider.shortAutoincType()} NOT NULL, ${"name".inProperCase()} VARCHAR(42) PRIMARY KEY, " +
                    "${"age".inProperCase()} INT NULL)", TestTable.ddl)
        }
    }

    @Test fun tableWithDifferentColumnTypesSQL02() {
        val TestTable = object : Table("test_table_with_different_column_types") {
            val id = integer("id").primaryKey()
            val name = varchar("name", 42).primaryKey()
            val age = integer("age").nullable()
        }

        withTables(excludeSettings = listOf(TestDB.MYSQL), tables = TestTable) {
            assertEquals("CREATE TABLE IF NOT EXISTS ${"test_table_with_different_column_types".inProperCase()} " +
                    "(${"id".inProperCase()} INT, ${"name".inProperCase()} VARCHAR(42), ${"age".inProperCase()} INT NULL, " +
                    "CONSTRAINT pk_test_table_with_different_column_types PRIMARY KEY (${"id".inProperCase()}, ${"name".inProperCase()}))", TestTable.ddl)
        }
    }

    @Test fun testDefaults01() {
        val TestTable = object : Table("t") {
            val s = varchar("s", 100).default("test")
            val l = long("l").default(42)
        }

        withTables(TestTable) {
            assertEquals("CREATE TABLE IF NOT EXISTS ${"t".inProperCase()} (${"s".inProperCase()} VARCHAR(100) NOT NULL DEFAULT 'test', " +
                    "${"l".inProperCase()} BIGINT NOT NULL DEFAULT 42)", TestTable.ddl)
        }
    }

    @Test fun testIndices01() {
        val t = object : Table("t1") {
            val id = integer("id").primaryKey()
            val name = varchar("name", 255).index()
        }

        withTables(t) {
            val alter = SchemaUtils.createIndex(t.indices[0].first, t.indices[0].second)
            assertEquals("CREATE INDEX ${"t1_name".inProperCase()} ON ${"t1".inProperCase()} (${"name".inProperCase()})", alter)
        }
    }

    @Test fun testIndices02() {
        val t = object : Table("t2") {
            val id = integer("id").primaryKey()
            val lvalue = integer("lvalue")
            val rvalue = integer("rvalue");
            val name = varchar("name", 255).index()

            init {
                index (false, lvalue, rvalue)
            }
        }

        withTables(t) {
            val a1 = SchemaUtils.createIndex(t.indices[0].first, t.indices[0].second)
            assertEquals("CREATE INDEX ${"t2_name".inProperCase()} ON ${"t2".inProperCase()} (${"name".inProperCase()})", a1)

            val a2 = SchemaUtils.createIndex(t.indices[1].first, t.indices[1].second)
            assertEquals("CREATE INDEX ${"t2_lvalue_rvalue".inProperCase()} ON ${"t2".inProperCase()} " +
                    "(${"lvalue".inProperCase()}, ${"rvalue".inProperCase()})", a2)
        }
    }

    @Test fun testIndices03() {
        val t = object : Table("t1") {
            val id = integer("id").primaryKey()
            val name = varchar("name", 255).uniqueIndex()
        }

        withTables(t) {
            val alter = SchemaUtils.createIndex(t.indices[0].first, t.indices[0].second)
            assertEquals("CREATE UNIQUE INDEX ${"t1_name_unique".inProperCase()} ON ${"t1".inProperCase()} (${"name".inProperCase()})", alter)

        }
    }

    @Test fun testBlob() {
        val t = object: Table("t1") {
            val id = integer("id").autoIncrement().primaryKey()
            val b = blob("blob")
        }

        withTables(t) {
            val bytes = "Hello there!".toByteArray()
            val blob = if (currentDialect.dataTypeProvider.blobAsStream) {
                    SerialBlob(bytes)
                } else connection.createBlob().apply {
                    setBytes(1, bytes)
                }

            val id = t.insert {
                it[t.b] = blob
            } get (t.id)


            val readOn = t.select{t.id eq id}.first()[t.b]
            val text = readOn.binaryStream.reader().readText()

            assertEquals("Hello there!", text)
        }
    }

    @Test fun testBinary() {
        val t = object : Table() {
            val binary = binary("bytes", 10)
        }

        withTables(t) {
            t.insert { it[t.binary] = "Hello!".toByteArray() }

            val bytes = t.selectAll().single()[t.binary]

            assertEquals("Hello!", String(bytes))

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
            assertEquals("ALTER TABLE ${tableName.inProperCase()} ADD COLUMN ${"id".inProperCase()} ${t.id.columnType.sqlType()}", t.id.ddl.first())
            assertEquals("ALTER TABLE ${tableName.inProperCase()} ADD CONSTRAINT pk_$tableName PRIMARY KEY (${"id".inProperCase()})", t.id.ddl[1])
            assertEquals(1, currentDialect.tableColumns(t)[t]!!.size)
            SchemaUtils.createMissingTablesAndColumns(t)
            assertEquals(2, currentDialect.tableColumns(t)[t]!!.size)
        }

        withDb(TestDB.SQLITE) {
            try {
                SchemaUtils.createMissingTablesAndColumns(t)
                assertFalse(db.supportsAlterTableWithAddColumn)
            } catch (e: SQLException) {
                // SQLite doesn't support
            }
        }

        withTables(excludeSettings = listOf(TestDB.H2, TestDB.SQLITE), tables = initialTable) {
            assertEquals("ALTER TABLE ${tableName.inProperCase()} ADD COLUMN id ${t.id.columnType.sqlType()} PRIMARY KEY", t.id.ddl)
            assertEquals(1, currentDialect.tableColumns(t)[t]!!.size)
            SchemaUtils.createMissingTablesAndColumns(t)
            assertEquals(2, currentDialect.tableColumns(t)[t]!!.size)
        }
    }

    
    private abstract class EntityTable(name: String = "") : IdTable<String>(name) {
        override val id: Column<EntityID<String>> = varchar("id", 64).clientDefault { UUID.randomUUID().toString() }.primaryKey().entityId()
    }
    
    @Test fun complexTest01() {
        val User = object : EntityTable() {
            val name = varchar("name", 255)
            val email = varchar("email", 255)
        }

        val Repository = object : EntityTable() {
            val name = varchar("name", 255)
        }

        val UserToRepo = object : EntityTable() {
            val user = reference("user", User)
            val repo = reference("repo", Repository)
        }

        withTables(User, Repository, UserToRepo) {
            User.insert {
                it[User.name] = "foo"
                it[User.email] = "bar"
            }

            val userID = User.selectAll().single()[User.id]

            Repository.insert {
                it[Repository.name] = "foo"
            }
            val repo = Repository.selectAll().single()[Repository.id]

            UserToRepo.insert {
                it[UserToRepo.user] = userID
                it[UserToRepo.repo] = repo
            }

            assertEquals(1, UserToRepo.selectAll().count())
            UserToRepo.insert {
                it[UserToRepo.user] = userID
                it[UserToRepo.repo] = repo
            }

            assertEquals(2, UserToRepo.selectAll().count())
        }
    } 
    
}

private fun String.inProperCase(): String = TransactionManager.currentOrNull()?.let { tm ->
    (currentDialect as? VendorDialect)?.run {
        this@inProperCase.inProperCase
    }
} ?: this
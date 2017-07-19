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
import org.junit.Assert.assertTrue
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
        val TestTable = object : Table("test_table") {
            val id = integer("id").primaryKey()
            val name = varchar("name", length = 42)
        }

        withDb {
            SchemaUtils.createMissingTablesAndColumns(TestTable)
            assertTrue(TestTable.exists())
            SchemaUtils.drop(TestTable)
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
            assertTrue(TestTable.exists())
            try {
                SchemaUtils.createMissingTablesAndColumns(TestTable)
            } finally {
                SchemaUtils.drop(TestTable)
            }
        }
    }

    // Placed outside test function to shorten generated name
    val UnnamedTable = object : Table() {
        val id = integer("id").primaryKey()
        val name = varchar("name", length = 42)
    }

    @Test fun unnamedTableWithQuotesSQL() {
        withTables(UnnamedTable) {
            val q = db.identityQuoteString
            val tableName = if (db.dialect.needsQuotesWhenSymbolsInNames) { "$q${"UnnamedTable$1".inProperCase()}$q" } else { "UnnamedTable$1".inProperCase() }
            assertEquals("CREATE TABLE " + if (db.dialect.supportsIfNotExists) { "IF NOT EXISTS " } else { "" } + "$tableName " +
                    "(${"id".inProperCase()} ${db.dialect.dataTypeProvider.shortType()} PRIMARY KEY, ${"name".inProperCase()} VARCHAR(42) NOT NULL)", UnnamedTable.ddl)
        }
    }

    @Test fun namedEmptyTableWithoutQuotesSQL() {
        val TestTable = object : Table("test_named_table") {
        }

        withTables(excludeSettings = listOf(TestDB.MYSQL, TestDB.POSTGRESQL, TestDB.SQLITE, TestDB.ORACLE), tables = TestTable) {
            assertEquals("CREATE TABLE IF NOT EXISTS ${"test_named_table".inProperCase()}", TestTable.ddl)
        }
    }

    @Test fun tableWithDifferentColumnTypesSQL01() {
        val TestTable = object : Table("different_column_types") {
            val id = integer("id").autoIncrement()
            val name = varchar("name", 42).primaryKey()
            val age = integer("age").nullable()
            // not applicable in H2 database
            //            val testCollate = varchar("testCollate", 2, "ascii_general_ci")
        }

         withTables(excludeSettings = listOf(TestDB.MYSQL, TestDB.ORACLE), tables = TestTable) {
            assertEquals("CREATE TABLE " + if (db.dialect.supportsIfNotExists) { "IF NOT EXISTS " } else { "" } + "${"different_column_types".inProperCase()} " +
                    "(${"id".inProperCase()} ${db.dialect.dataTypeProvider.shortAutoincType()} NOT NULL, ${"name".inProperCase()} VARCHAR(42) PRIMARY KEY, " +
                    "${"age".inProperCase()} ${db.dialect.dataTypeProvider.shortType()} NULL)", TestTable.ddl)
        }
    }

    @Test fun tableWithDifferentColumnTypesSQL02() {
        val TestTable = object : Table("with_different_column_types") {
            val id = integer("id").primaryKey()
            val name = varchar("name", 42).primaryKey()
            val age = integer("age").nullable()
        }

        withTables(excludeSettings = listOf(TestDB.MYSQL), tables = TestTable) {
            assertEquals("CREATE TABLE " + if (db.dialect.supportsIfNotExists) { "IF NOT EXISTS " } else { "" } + "${"with_different_column_types".inProperCase()} " +
                    "(${"id".inProperCase()} ${db.dialect.dataTypeProvider.shortType()}, ${"name".inProperCase()} VARCHAR(42), ${"age".inProperCase()} ${db.dialect.dataTypeProvider.shortType()} NULL, " +
                    "CONSTRAINT pk_with_different_column_types PRIMARY KEY (${"id".inProperCase()}, ${"name".inProperCase()}))", TestTable.ddl)
        }
    }

    @Test fun testDefaults01() {
        val TestTable = object : Table("t") {
            val s = varchar("s", 100).default("test")
            val l = long("l").default(42)
            val c = char("c").default('X')
        }

        withTables(TestTable) {
            assertEquals("CREATE TABLE " + if (db.dialect.supportsIfNotExists) { "IF NOT EXISTS " } else { "" } +
                    "${"t".inProperCase()} (" +
                    "${"s".inProperCase()} VARCHAR(100) DEFAULT 'test' NOT NULL, " +
                    "${"l".inProperCase()} ${db.dialect.dataTypeProvider.longType()} DEFAULT 42 NOT NULL, " +
                    "${"c".inProperCase()} CHAR DEFAULT 'X' NOT NULL" +
                ")", TestTable.ddl)
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
            val rvalue = integer("rvalue")
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

    @Test fun testMultiColumnIndex() {
        val t = object : Table("t1") {
            val type = varchar("type", 255)
            val name = varchar("name", 255)
            init {
                index(false, name, type)
                uniqueIndex(type, name)
            }
        }

        withTables(t) {
            val indexAlter = SchemaUtils.createIndex(t.indices[0].first, t.indices[0].second)
            val uniqueAlter = SchemaUtils.createIndex(t.indices[1].first, t.indices[1].second)
            assertEquals("CREATE INDEX ${"t1_name_type".inProperCase()} ON ${"t1".inProperCase()} (${"name".inProperCase()}, ${"type".inProperCase()})", indexAlter)
            assertEquals("CREATE UNIQUE INDEX ${"t1_type_name_unique".inProperCase()} ON ${"t1".inProperCase()} (${"type".inProperCase()}, ${"name".inProperCase()})", uniqueAlter)
        }
    }

    @Test fun testBlob() {
        val t = object: Table("t1") {
            val id = integer("id").autoIncrement("t1_seq").primaryKey()
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
            assertEquals("ALTER TABLE ${tableName.inProperCase()} ADD ${"id".inProperCase()} ${t.id.columnType.sqlType()}", t.id.ddl.first())
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
            assertEquals("ALTER TABLE ${tableName.inProperCase()} ADD ${"id".inProperCase()} ${t.id.columnType.sqlType()} PRIMARY KEY", t.id.ddl)
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

    @Test fun testUUIDColumnType() {
        val Node = object: Table("node") {
            val uuid = uuid("uuid").primaryKey()
        }

        withTables(Node){
            val key: UUID = UUID.randomUUID()
            Node.insert { it[uuid] = key }
            val result = Node.select { Node.uuid.eq(key) }.toList()
            assertEquals(1, result.size)
            assertEquals(key, result.single()[Node.uuid])
        }
    }

    @Test fun testBooleanColumnType() {
        val BoolTable = object: Table("booleanTable") {
            val bool = bool("bool")
        }

        withTables(BoolTable){
            BoolTable.insert {
                it[bool] = true
            }
            val result = BoolTable.selectAll().toList()
            assertEquals(1, result.size)
            assertEquals(true, result.single()[BoolTable.bool])
        }
    }
    
}

private fun String.inProperCase(): String = TransactionManager.currentOrNull()?.let { tm ->
    (currentDialect as? VendorDialect)?.run {
        this@inProperCase.inProperCase
    }
} ?: this
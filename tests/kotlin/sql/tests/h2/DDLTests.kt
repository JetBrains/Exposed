package org.jetbrains.exposed.sql.tests.h2

import org.junit.Test
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.exists
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import kotlin.test.assertEquals

class DDLTests : DatabaseTestsBase() {
    @Test fun tableExists01() {
        val TestTable = object : Table("test") {
            val id = integer("id").primaryKey()
            val name = varchar("name", length = 42)
        }

        withDb {
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

    @Test fun unnamedTableWithQuotesSQL() {
        val TestTable = object : Table() {
            val id = integer("id").primaryKey()
            val name = varchar("name", length = 42)
        }

        withTables(TestTable) {
            assertEquals("CREATE TABLE IF NOT EXISTS \"unnamedTableWithQuotesSQL\$TestTable$1\" (id INT PRIMARY KEY NOT NULL, name VARCHAR(42) NOT NULL)", TestTable.ddl)
        }
    }

    @Test fun namedEmptyTableWithoutQuotesSQL() {
        val TestTable = object : Table("test_named_table") {
        }

        withTables(TestTable) {
            assertEquals("CREATE TABLE IF NOT EXISTS test_named_table", TestTable.ddl)
        }
    }

    @Test fun tableWithDifferentColumnTypesSQL() {
        val TestTable = object : Table("test_table_with_different_column_types") {
            val id = integer("id").autoIncrement()
            val name = varchar("name", 42).primaryKey()
            val age = integer("age").nullable()
            // not applicable in H2 database
            //            val testCollate = varchar("testCollate", 2, "ascii_general_ci")
        }

        withTables(TestTable) {
            assertEquals("CREATE TABLE IF NOT EXISTS test_table_with_different_column_types (id INT AUTO_INCREMENT NOT NULL, name VARCHAR(42) PRIMARY KEY NOT NULL, age INT NULL)", TestTable.ddl)
        }
    }

    @Test fun testDefaults01() {
        val TestTable = object : Table("t") {
            val s = varchar("s", 100).default("test")
            val l = long("l").default(42)
        }

        withTables(TestTable) {
            assertEquals("CREATE TABLE IF NOT EXISTS t (s VARCHAR(100) NOT NULL DEFAULT 'test', l BIGINT NOT NULL DEFAULT 42)", TestTable.ddl)
        }
    }

    @Test fun testIndices01() {
        val t = object : Table("t1") {
            val id = integer("id").primaryKey()
            val name = varchar("name", 255).index()
        }

        withTables(t) {
            val alter = createIndex(t.indices[0].first, t.indices[0].second)
            assertEquals("CREATE INDEX t1_name ON t1 (name)", alter)
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
            val a1 = createIndex(t.indices[0].first, t.indices[0].second)
            assertEquals("CREATE INDEX t2_name ON t2 (name)", a1)

            val a2 = createIndex(t.indices[1].first, t.indices[1].second)
            assertEquals("CREATE INDEX t2_lvalue_rvalue ON t2 (lvalue, rvalue)", a2)
        }
    }

    @Test fun testIndices03() {
        val t = object : Table("t1") {
            val id = integer("id").primaryKey()
            val name = varchar("name", 255).uniqueIndex()
        }

        withTables(t) {
            val alter = createIndex(t.indices[0].first, t.indices[0].second)
            assertEquals("CREATE UNIQUE INDEX t1_name_unique ON t1 (name)", alter)

        }
    }

    @Test fun testBlob() {
        val t = object: Table("t1") {
            val id = integer("id").autoIncrement().primaryKey()
            val b = blob("blob")
        }

        withTables(t) {
            val blob = connection.createBlob()!!
            blob.setBytes(1, "Hello there!".toByteArray())

            val id = t.insert {
                it[t.b] = blob
            } get (t.id)


            val readOn = t.select{t.id eq id}.first()[t.b]
            val text = readOn.binaryStream.reader().readText()

            assertEquals("Hello there!", text)
        }
    }

    @Test fun tablesWithCrossReferencesSQL() {
        val TestTableWithReference1 = object : Table("test_table_1") {
            val id = integer("id").primaryKey()
            val testTable2Id = integer("id_ref")
        }

        val TestTableWithReference2 = object : Table("test_table_2") {
            val id = integer("id").primaryKey()
            val testTable1Id = (integer("id_ref") references TestTableWithReference1.id).nullable()
        }

        with (TestTableWithReference1) {
            testTable2Id.references( TestTableWithReference2.id)
        }

        withDb {
            val statements = createStatements(TestTableWithReference1, TestTableWithReference2)
            assertEquals ("CREATE TABLE IF NOT EXISTS test_table_1 (id INT PRIMARY KEY NOT NULL, id_ref INT NOT NULL)", statements[0])
            assertEquals ("CREATE TABLE IF NOT EXISTS test_table_2 (id INT PRIMARY KEY NOT NULL, id_ref INT NULL)", statements[1])
            assertEquals ("ALTER TABLE test_table_1 ADD FOREIGN KEY (id_ref) REFERENCES test_table_2(id)", statements[2])
            assertEquals ("ALTER TABLE test_table_2 ADD FOREIGN KEY (id_ref) REFERENCES test_table_1(id)", statements[3])

            create(TestTableWithReference1, TestTableWithReference2)
        }
    }
}


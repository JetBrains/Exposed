package kotlin.sql.tests.h2

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.sql.*

public class DDLTests : DatabaseTestsBase() {
    Test fun tableExists01() {
        object TestTable : Table("test") {
            val id = integer("id").primaryKey()
            val name = varchar("name", length = 42)
        }

        withDb {
            assertEquals (false, TestTable.exists())
        }
    }

    Test fun tableExists02() {
        object TestTable : Table() {
            val id = integer("id").primaryKey()
            val name = varchar("name", length = 42)
        }

        withTables(TestTable) {
            assertEquals (true, TestTable.exists())
        }
    }

    Test fun unnamedTableWithQuotesSQL() {
        object TestTable : Table() {
            val id = integer("id").primaryKey()
            val name = varchar("name", length = 42)
        }

        withTables(TestTable) {
            assertEquals("CREATE TABLE IF NOT EXISTS \"unnamedTableWithQuotesSQL\$Test\" (id INT PRIMARY KEY NOT NULL, name VARCHAR(42) NOT NULL)", TestTable.ddl)
        }
    }

    Test fun namedEmptyTableWithoutQuotesSQL() {
        object TestTable : Table("test_named_table") {
        }

        withTables(TestTable) {
            assertEquals("CREATE TABLE IF NOT EXISTS test_named_table", TestTable.ddl)
        }
    }

    Test fun tableWithDifferentColumnTypesSQL() {
        object TestTable : Table("test_table_with_different_column_types") {
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

    Test fun tablesWithCrossReferencesSQL() {
        /*db.withSession {
            assertEquals("CREATE TABLE test_table_with_different_column_types (id INT PRIMARY KEY AUTO_INCREMENT NOT NULL, name VARCHAR(42) NOT NULL, age INT NULL)", TestTable.ddl)
        }*/
    }

    Test fun testDefaults01() {
        object TestTable : Table("t") {
            val s = varchar("s", 100).default("test")
            val l = long("l").default(42)
        }

        withTables(TestTable) {
            assertEquals("CREATE TABLE IF NOT EXISTS t (s VARCHAR(100) NOT NULL DEFAULT 'test', l BIGINT NOT NULL DEFAULT 42)", TestTable.ddl)
        }
    }

    Test fun testIndices01() {
        object t : Table("t1") {
            val id = integer("id").primaryKey()
            val name = varchar("name", 255).index()
        }

        withTables(t) {
            with (Session.get()) {
                val alter = index(t.indices[0].first, t.indices[0].second)
                assertEquals("CREATE INDEX t1_name ON t1 (name)", alter)
            }

        }
    }

    Test fun testIndices02() {
        object t : Table("t2") {
            val id = integer("id").primaryKey()
            val lvalue = integer("lvalue")
            val rvalue = integer("rvalue");
            val name = varchar("name", 255).index();

            {
                index (false, lvalue, rvalue)
            }
        }

        withTables(t) {
            with (Session.get()) {
                val a1 = index(t.indices[0].first, t.indices[0].second)
                assertEquals("CREATE INDEX t2_name ON t2 (name)", a1)

                val a2 = index(t.indices[1].first, t.indices[1].second)
                assertEquals("CREATE INDEX t2_lvalue_rvalue ON t2 (lvalue, rvalue)", a2)
            }
        }
    }

    Test fun testIndices03() {
        object t : Table("t1") {
            val id = integer("id").primaryKey()
            val name = varchar("name", 255).uniqueIndex()
        }

        withTables(t) {
            with (Session.get()) {
                val alter = index(t.indices[0].first, t.indices[0].second)
                assertEquals("CREATE UNIQUE INDEX t1_name ON t1 (name)", alter)
            }

        }
    }

    Test fun testBlob() {
        object t: Table("t1") {
            val id = integer("id").autoIncrement().primaryKey()
            val b = blob("blob")
        }

        withTables(t) {
            with (Session.get()) {
                val blob = connection.createBlob()!!
                blob.setBytes(1, "Hello there!".getBytes())

                val id = t.insert {
                    it[t.b] = blob
                } get (t.id)


                val readOn = t.select(t.id eq id).first()[t.b]
                val text = readOn.getBinaryStream().reader().readText()

                assertEquals("Hello there!", text)
            }
        }
    }
}

object TestTableWithReference1 : Table("test_table_1") {
    val id = integer("id").primaryKey()
    val testTable2Id = integer("id") references TestTableWithReference2.id
}

object TestTableWithReference2 : Table("test_table_1") {
    val id = integer("id").primaryKey()
    val testTable1Id = (integer("id") references TestTableWithReference1.id).nullable()
}

package kotlin.sql.tests.h2

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.sql.*

public class DDLTests : DatabaseTestsBase() {

    Test fun unnamedTableWithQuotesSQL() {
        object TestTable : Table() {
            val id = integer("id").primaryKey()
            val name = varchar("name", length = 42)
        }

        withTables(TestTable) {
            assertEquals("CREATE TABLE \"unnamedTableWithQuotesSQL\$Test\" (id INT PRIMARY KEY NOT NULL, name VARCHAR(42) NOT NULL)", TestTable.ddl)
        }
    }

    Test fun namedEmptyTableWithoutQuotesSQL() {
        object TestTable : Table("test_named_table") {
        }

        withTables(TestTable) {
            assertEquals("CREATE TABLE test_named_table", TestTable.ddl)
        }
    }

    Test fun tableWithDifferentColumnTypesSQL() {
        object TestTable : Table("test_table_with_different_column_types") {
            val id = integer("id").autoIncrement()
            val name = varchar("name", 42).primaryKey()
            val age = integer("age").nullable()
        }

        withTables(TestTable) {
            assertEquals("CREATE TABLE test_table_with_different_column_types (id INT AUTO_INCREMENT NOT NULL, name VARCHAR(42) PRIMARY KEY NOT NULL, age INT NULL)", TestTable.ddl)
        }
    }

    Test fun tablesWithCrossReferencesSQL() {
        /*db.withSession {
            assertEquals("CREATE TABLE test_table_with_different_column_types (id INT PRIMARY KEY AUTO_INCREMENT NOT NULL, name VARCHAR(42) NOT NULL, age INT NULL)", TestTable.ddl)
        }*/
    }

    Test fun testIndices01() {
        object t : Table("t") {
            val id = integer("id").primaryKey()
            val name = varchar("name", 255).index()
        }

        withTables(t) {
            with (Session.get()) {
                val alter = index(t.indices[0])
                assertEquals("CREATE INDEX ON t (name)", alter)
            }

        }
    }

    Test fun testIndices02() {
        object t : Table("t") {
            val id = integer("id").primaryKey()
            val lvalue = integer("lvalue")
            val rvalue = integer("rvalue");
            val name = varchar("name", 255).index();

            {
                index (lvalue, rvalue)
            }
        }

        withTables(t) {
            with (Session.get()) {
                val a1 = index(t.indices[0])
                assertEquals("CREATE INDEX ON t (name)", a1)

                val a2 = index(t.indices[1])
                assertEquals("CREATE INDEX ON t (lvalue, rvalue)", a2)
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

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
            assertEquals("CREATE TABLE \"unnamedTableWithQuotesSQL\$TestTable\" (id INT PRIMARY KEY NOT NULL, name VARCHAR(42) NOT NULL)", TestTable.ddl)
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
}

object TestTableWithReference1 : Table("test_table_1") {
    val id = integer("id").primaryKey()
    val testTable2Id = integer("id") references TestTableWithReference2.id
}

object TestTableWithReference2 : Table("test_table_1") {
    val id = integer("id").primaryKey()
    val testTable1Id = integer("id").nullable() references TestTableWithReference1.id
}

package org.jetbrains.exposed.sql.tests.h2

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.inProperCase
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transactionManager
import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class H2Tests : DatabaseTestsBase() {
    @Test
    fun testH2VersionIsCorrect() {
        val systemTestName = System.getProperty("exposed.test.name")
        withDb(TestDB.ALL_H2) { db ->
            val dialect = currentDialect
            if (dialect is H2Dialect) {
                val version = exec("SELECT H2VERSION();") {
                    it.next()
                    it.getString(1)
                }
                if (systemTestName == "h2") {
                    assertEquals("2", version?.first()?.toString())
                }
                if (systemTestName == "h2_v1") {
                    assertEquals("1", version?.first()?.toString())
                }
            }
        }
    }

    @Test
    fun insertInH2() {
        withDb(listOf(TestDB.H2_V2_MYSQL, TestDB.H2_V2)) {
            SchemaUtils.create(Testing)
            Testing.insert {
                it[id] = 1
                it[string] = "one"
            }

            assertEquals("one", Testing.selectAll().where { Testing.id.eq(1) }.single()[Testing.string])

            SchemaUtils.drop(Testing)
        }
    }

    @Test
    fun replaceAsInsertInH2() {
        withDb(listOf(TestDB.H2_V2_MYSQL, TestDB.H2_V2_MARIADB)) {
            SchemaUtils.create(Testing)
            Testing.replace {
                it[id] = 1
                it[string] = "one"
            }

            assertEquals("one", Testing.selectAll().where { Testing.id.eq(1) }.single()[Testing.string])

            SchemaUtils.drop(Testing)
        }
    }

    @Test
    fun closeAndUnregister() {
        withDb(TestDB.H2_V2) { testDB ->
            val originalManager = TransactionManager.manager
            val db = requireNotNull(testDB.db) { "testDB.db cannot be null" }
            try {
                TransactionManager.registerManager(
                    db,
                    WrappedTransactionManager(db.transactionManager)
                )
                Executors.newSingleThreadExecutor().apply {
                    submit { TransactionManager.closeAndUnregister(db) }
                        .get(1, TimeUnit.SECONDS)
                }.shutdown()
            } finally {
                TransactionManager.registerManager(db, originalManager)
            }
        }
    }

    @Test
    fun addAutoPrimaryKey() {
        val tableName = "Foo"
        val initialTable = object : Table(tableName) {
            val bar = text("bar")
        }
        val t = IntIdTable(tableName)

        withDb(listOf(TestDB.H2_V2, TestDB.H2_V2_MYSQL)) {
            try {
                SchemaUtils.createMissingTablesAndColumns(initialTable)
                assertEquals(
                    "ALTER TABLE ${tableName.inProperCase()} ADD ${"id".inProperCase()} ${t.id.columnType.sqlType()}",
                    t.id.ddl.first()
                )
                assertEquals(
                    "ALTER TABLE ${tableName.inProperCase()} ADD CONSTRAINT pk_$tableName PRIMARY KEY (${"id".inProperCase()})",
                    t.id.ddl[1]
                )
                assertEquals(1, currentDialectTest.tableColumns(t)[t]!!.size)
                SchemaUtils.createMissingTablesAndColumns(t)
                assertEquals(2, currentDialectTest.tableColumns(t)[t]!!.size)
            } finally {
                SchemaUtils.drop(t)
            }
        }
    }

    @Test
    fun testH2V1WithBigDecimalFunctionThatReturnsShort() {
        val testTable = object : Table("test_table") {
            val number = short("number")
        }

        withDb(TestDB.ALL_H2) {
            try {
                SchemaUtils.create(testTable)

                testTable.batchInsert(listOf<Short>(2, 4, 6, 8, 10)) { n ->
                    this[testTable.number] = n
                }

                val average = testTable.number.avg()
                val result = testTable.select(average).single()[average]
                assertEquals("6.00".toBigDecimal(), result)
            } finally {
                SchemaUtils.drop(testTable)
            }
        }
    }

    class WrappedTransactionManager(val transactionManager: TransactionManager) :
        TransactionManager by transactionManager

    object Testing : Table("H2_TESTING") {
        val id = integer("id").autoIncrement() // Column<Int>
        val string = varchar("string", 128)

        override val primaryKey = PrimaryKey(id)
    }
}

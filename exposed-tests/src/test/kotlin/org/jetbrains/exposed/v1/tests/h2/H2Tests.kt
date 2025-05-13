package org.jetbrains.exposed.v1.tests.h2

import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.avg
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.transactions.CoreTransactionManager
import org.jetbrains.exposed.v1.core.transactions.TransactionManagerApi
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transactionManager
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.currentDialectMetadataTest
import org.jetbrains.exposed.v1.tests.inProperCase
import org.jetbrains.exposed.v1.tests.shared.assertEquals
import org.jetbrains.exposed.v1.tests.shared.assertTrue
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertNotEquals

class H2Tests : DatabaseTestsBase() {
    @Test
    fun testH2VersionIsCorrect() {
        val systemTestName = System.getProperty("exposed.test.name")
        withDb(TestDB.ALL_H2) {
            val dialect = currentDialect
            if (dialect is H2Dialect) {
                val version = exec("SELECT H2VERSION();") {
                    it.next()
                    it.getString(1)
                }

                assertTrue(systemTestName == "h2_v2" || systemTestName == "h2_v1")
                if (systemTestName == "h2_v2") {
                    assertNotEquals("2.1.214", version)
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
        withTables(TestDB.ALL - listOf(TestDB.H2_V2_MYSQL, TestDB.H2_V2), Testing) {
            Testing.insert {
                it[id] = 1
                it[string] = "one"
            }

            assertEquals("one", Testing.selectAll().where { Testing.id.eq(1) }.single()[Testing.string])
        }
    }

    @Test
    fun replaceAsInsertInH2() {
        withTables(TestDB.ALL - listOf(TestDB.H2_V2_MYSQL, TestDB.H2_V2_MARIADB), Testing) {
            Testing.replace {
                it[id] = 1
                it[string] = "one"
            }

            assertEquals("one", Testing.selectAll().where { Testing.id.eq(1) }.single()[Testing.string])
        }
    }

    @Test
    fun closeAndUnregister() {
        withDb(TestDB.H2_V2) { testDB ->
            val originalManager = TransactionManager.manager
            val db = requireNotNull(testDB.db) { "testDB.db cannot be null" }
            try {
                @OptIn(InternalApi::class)
                CoreTransactionManager.registerDatabaseManager(db, WrappedTransactionManager(db.transactionManager))
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
                org.jetbrains.exposed.v1.jdbc.SchemaUtils.createMissingTablesAndColumns(initialTable)
                assertEquals(
                    "ALTER TABLE ${tableName.inProperCase()} ADD ${"id".inProperCase()} ${t.id.columnType.sqlType()}",
                    t.id.ddl.first()
                )
                assertEquals(
                    "ALTER TABLE ${tableName.inProperCase()} ADD CONSTRAINT pk_$tableName PRIMARY KEY (${"id".inProperCase()})",
                    t.id.ddl[1]
                )
                assertEquals(1, currentDialectMetadataTest.tableColumns(t)[t]!!.size)
                org.jetbrains.exposed.v1.jdbc.SchemaUtils.createMissingTablesAndColumns(t)
                assertEquals(2, currentDialectMetadataTest.tableColumns(t)[t]!!.size)
            } finally {
                org.jetbrains.exposed.v1.jdbc.SchemaUtils.drop(t)
            }
        }
    }

    @Test
    fun testH2V1WithBigDecimalFunctionThatReturnsShort() {
        val testTable = object : Table("test_table") {
            val number = short("number")
        }

        withTables(excludeSettings = TestDB.ALL - TestDB.ALL_H2, testTable) {
            testTable.batchInsert(listOf<Short>(2, 4, 6, 8, 10)) { n ->
                this[testTable.number] = n
            }

            val average = testTable.number.avg()
            val result = testTable.select(average).single()[average]
            assertEquals("6.00".toBigDecimal(), result)
        }
    }

    class WrappedTransactionManager(val transactionManager: TransactionManagerApi) :
        TransactionManagerApi by transactionManager

    object Testing : Table("H2_TESTING") {
        val id = integer("id").autoIncrement() // Column<Int>
        val string = varchar("string", 128)

        override val primaryKey = PrimaryKey(id)
    }
}

package org.jetbrains.exposed.r2dbc.sql.tests.h2

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.single
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.r2dbc.sql.SchemaUtils
import org.jetbrains.exposed.r2dbc.sql.batchInsert
import org.jetbrains.exposed.r2dbc.sql.insert
import org.jetbrains.exposed.r2dbc.sql.replace
import org.jetbrains.exposed.r2dbc.sql.select
import org.jetbrains.exposed.r2dbc.sql.selectAll
import org.jetbrains.exposed.r2dbc.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.r2dbc.sql.tests.TestDB
import org.jetbrains.exposed.r2dbc.sql.tests.currentDialectMetadataTest
import org.jetbrains.exposed.r2dbc.sql.tests.getString
import org.jetbrains.exposed.r2dbc.sql.tests.inProperCase
import org.jetbrains.exposed.r2dbc.sql.tests.shared.assertEquals
import org.jetbrains.exposed.r2dbc.sql.tests.shared.assertTrue
import org.jetbrains.exposed.r2dbc.sql.transactions.TransactionManager
import org.jetbrains.exposed.r2dbc.sql.transactions.transactionManager
import org.jetbrains.exposed.sql.InternalApi
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.avg
import org.jetbrains.exposed.sql.transactions.CoreTransactionManager
import org.jetbrains.exposed.sql.transactions.TransactionManagerApi
import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertNotEquals

class H2Tests : R2dbcDatabaseTestsBase() {
    @Test
    fun testH2VersionIsCorrect() {
        val systemTestName = System.getProperty("exposed.test.name")
        withDb(TestDB.ALL_H2_V2) {
            val dialect = currentDialect
            if (dialect is H2Dialect) {
                val version = exec("SELECT H2VERSION();") {
                    it.getString(1)
                }?.first()

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
                SchemaUtils.createMissingTablesAndColumns(initialTable)
                assertEquals(
                    "ALTER TABLE ${tableName.inProperCase()} ADD ${"id".inProperCase()} ${t.id.columnType.sqlType()}",
                    t.id.ddl.first()
                )
                assertEquals(
                    "ALTER TABLE ${tableName.inProperCase()} ADD CONSTRAINT pk_$tableName PRIMARY KEY (${"id".inProperCase()})",
                    t.id.ddl[1]
                )
                assertEquals(1, currentDialectMetadataTest.tableColumns(t)[t]!!.size)
                SchemaUtils.createMissingTablesAndColumns(t)
                assertEquals(2, currentDialectMetadataTest.tableColumns(t)[t]!!.size)
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

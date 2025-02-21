package org.jetbrains.exposed.sql.exposed.r2dbc.tests.h2

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.InternalApi
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectMetadataTest
import org.jetbrains.exposed.sql.tests.getString
import org.jetbrains.exposed.sql.tests.inProperCase
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.jetbrains.exposed.sql.transactions.CoreTransactionManager
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.TransactionManagerApi
import org.jetbrains.exposed.sql.transactions.transactionManager
import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class H2Tests : R2dbcDatabaseTestsBase() {
    @Test
    fun testH2VersionIsCorrect() = runTest {
        val systemTestName = System.getProperty("exposed.test.name")
        withDb(TestDB.ALL_H2_V2) {
            val dialect = currentDialect
            if (dialect is H2Dialect) {
                val version = exec("SELECT H2VERSION();") {
                    it.getString(0)
                }?.first()

                assertTrue(systemTestName == "h2_v2" || systemTestName == "h2_v1")
                if (systemTestName == "h2_v2") {
                    assertEquals("2", version?.first()?.toString())
                }
                if (systemTestName == "h2_v1") {
                    assertEquals("1", version?.first()?.toString())
                }
            }
        }
    }

    @Test
    fun closeAndUnregister() = runTest {
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
    fun addAutoPrimaryKey() = runTest {
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

    class WrappedTransactionManager(val transactionManager: TransactionManagerApi) :
        TransactionManagerApi by transactionManager
}

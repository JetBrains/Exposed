package org.jetbrains.exposed.v1.r2dbc.sql.tests.h2

import io.r2dbc.spi.IsolationLevel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.single
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.core.vendors.inProperCase
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.replace
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.currentDialectMetadataTest
import org.jetbrains.exposed.v1.r2dbc.tests.getString
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertTrue
import org.jetbrains.exposed.v1.r2dbc.transactions.R2dbcTransactionManager
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.r2dbc.transactions.transactionManager
import org.junit.jupiter.api.Test
import java.util.*
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

                assertTrue(systemTestName == "h2_v2")
                if (systemTestName == "h2_v2") {
                    assertNotEquals("2.1.214", version)
                    assertEquals("2", version?.first()?.toString())
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
                TransactionManager.registerManager(db, WrappedTransactionManager(db.transactionManager))
                Executors.newSingleThreadExecutor().apply {
                    submit { TransactionManager.closeAndUnregister(db) }
                        .get(1, TimeUnit.SECONDS)
                }.shutdown()
            } finally {
                TransactionManager.registerManager(db, originalManager)
            }
        }
    }

    @OptIn(InternalApi::class)
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
    fun testH2UUIDConversionWithBinary16ColumnType() {
        val testTable = object : UUIDTable("test_table") {
        }

        withDb(TestDB.ALL_H2_V2) {
            exec("CREATE TABLE test_table (id BINARY(16) NOT NULL, CONSTRAINT PK_TEST_TABLE PRIMARY KEY (id))")

            val uuid = UUID.randomUUID()

            testTable.insert { it[testTable.id] = uuid }

            val actualId = testTable.select(testTable.id).single()[testTable.id].value

            assertEquals(uuid, actualId)
        }
    }

    class WrappedTransactionManager(private val transactionManager: R2dbcTransactionManager) : R2dbcTransactionManager() {
        override val db get() = transactionManager.db
        override var defaultIsolationLevel
            get() = transactionManager.defaultIsolationLevel
            set(value) { transactionManager.defaultIsolationLevel = value }
        override var defaultMaxAttempts
            get() = transactionManager.defaultMaxAttempts
            set(value) { transactionManager.defaultMaxAttempts = value }
        override var defaultMinRetryDelay
            get() = transactionManager.defaultMinRetryDelay
            set(value) { transactionManager.defaultMinRetryDelay = value }
        override var defaultMaxRetryDelay
            get() = transactionManager.defaultMaxRetryDelay
            set(value) { transactionManager.defaultMaxRetryDelay = value }
        override var defaultReadOnly
            get() = transactionManager.defaultReadOnly
            set(value) { transactionManager.defaultReadOnly = value }
        override fun newTransaction(isolation: IsolationLevel?, readOnly: Boolean?, outerTransaction: R2dbcTransaction?) =
            transactionManager.newTransaction(isolation, readOnly, outerTransaction)
    }

    object Testing : Table("H2_TESTING") {
        val id = integer("id").autoIncrement() // Column<Int>
        val string = varchar("string", 128)

        override val primaryKey = PrimaryKey(id)
    }
}

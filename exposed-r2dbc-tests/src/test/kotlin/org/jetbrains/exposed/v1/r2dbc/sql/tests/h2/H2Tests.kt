package org.jetbrains.exposed.v1.r2dbc.sql.tests.h2

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.single
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.transactions.CoreTransactionManager
import org.jetbrains.exposed.v1.core.transactions.TransactionManagerApi
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.core.vendors.inProperCase
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.replace
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.currentDialectMetadataTest
import org.jetbrains.exposed.v1.r2dbc.tests.getString
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertTrue
import org.jetbrains.exposed.v1.r2dbc.transactions.transactionManager
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
            val originalManager = org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager.manager
            val db = requireNotNull(testDB.db) { "testDB.db cannot be null" }
            try {
                @OptIn(InternalApi::class)
                CoreTransactionManager.registerDatabaseManager(db, WrappedTransactionManager(db.transactionManager))
                Executors.newSingleThreadExecutor().apply {
                    submit { org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager.closeAndUnregister(db) }
                        .get(1, TimeUnit.SECONDS)
                }.shutdown()
            } finally {
                org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager.registerManager(db, originalManager)
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
                org.jetbrains.exposed.v1.r2dbc.SchemaUtils.createMissingTablesAndColumns(initialTable)
                assertEquals(
                    "ALTER TABLE ${tableName.inProperCase()} ADD ${"id".inProperCase()} ${t.id.columnType.sqlType()}",
                    t.id.ddl.first()
                )
                assertEquals(
                    "ALTER TABLE ${tableName.inProperCase()} ADD CONSTRAINT pk_$tableName PRIMARY KEY (${"id".inProperCase()})",
                    t.id.ddl[1]
                )
                assertEquals(1, currentDialectMetadataTest.tableColumns(t)[t]!!.size)
                org.jetbrains.exposed.v1.r2dbc.SchemaUtils.createMissingTablesAndColumns(t)
                assertEquals(2, currentDialectMetadataTest.tableColumns(t)[t]!!.size)
            } finally {
                org.jetbrains.exposed.v1.r2dbc.SchemaUtils.drop(t)
            }
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

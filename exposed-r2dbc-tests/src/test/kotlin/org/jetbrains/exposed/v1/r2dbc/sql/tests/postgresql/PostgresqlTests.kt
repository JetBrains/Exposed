package org.jetbrains.exposed.v1.r2dbc.sql.tests.postgresql

import io.r2dbc.spi.IsolationLevel
import io.r2dbc.spi.Row
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption.PostgreSQL
import org.jetbrains.exposed.v1.r2dbc.Query
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.any
import org.jetbrains.exposed.v1.r2dbc.tests.getString
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertFailAndRollback
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertFalse
import org.jetbrains.exposed.v1.r2dbc.transactions.inTopLevelSuspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.update
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals

class PostgresqlTests : R2dbcDatabaseTestsBase() {
    private val table = object : IntIdTable() {
        val name = varchar("name", 50)
    }

    @Test
    fun testForUpdateOptionsSyntax() {
        val id = 1

        suspend fun Query.city() = map { it[table.name] }.single()

        suspend fun select(option: ForUpdateOption): String {
            return table.selectAll().where { table.id eq id }.forUpdate(option).city()
        }

        withTables(excludeSettings = TestDB.ALL - TestDB.ALL_POSTGRES, table) {
            val name = "name"
            table.insert {
                it[table.id] = id
                it[table.name] = name
            }
            commit()

            val defaultForUpdateRes = table.selectAll().where { table.id eq id }.city()
            val forUpdateRes = select(ForUpdateOption.ForUpdate)
            val forUpdateOfTableRes = select(PostgreSQL.ForUpdate(ofTables = arrayOf(table)))
            val forShareRes = select(PostgreSQL.ForShare)
            val forShareNoWaitOfTableRes = select(PostgreSQL.ForShare(PostgreSQL.MODE.NO_WAIT, table))
            val forKeyShareRes = select(PostgreSQL.ForKeyShare)
            val forKeyShareSkipLockedRes = select(PostgreSQL.ForKeyShare(PostgreSQL.MODE.SKIP_LOCKED))
            val forNoKeyUpdateRes = select(PostgreSQL.ForNoKeyUpdate)
            val notForUpdateRes = table.selectAll().where { table.id eq id }.notForUpdate().city()

            assertEquals(name, defaultForUpdateRes)
            assertEquals(name, forUpdateRes)
            assertEquals(name, forUpdateOfTableRes)
            assertEquals(name, forShareRes)
            assertEquals(name, forShareNoWaitOfTableRes)
            assertEquals(name, forKeyShareRes)
            assertEquals(name, forKeyShareSkipLockedRes)
            assertEquals(name, forNoKeyUpdateRes)
            assertEquals(name, notForUpdateRes)
        }
    }

    @Test
    fun testPrimaryKeyCreatedInPostgresql() {
        val tableName = "tester"
        val tester1 = object : Table(tableName) {
            val age = integer("age")
        }

        val tester2 = object : Table(tableName) {
            val age = integer("age")

            override val primaryKey = PrimaryKey(age)
        }

        val tester3 = object : IntIdTable(tableName) {
            val age = integer("age")
        }

        suspend fun <T : Any> R2dbcTransaction.assertPrimaryKey(transform: (Row) -> T): Flow<T?>? {
            return exec(
                """
                SELECT ct.relname as TABLE_NAME, ci.relname AS PK_NAME
                FROM pg_catalog.pg_class ct
                JOIN pg_index i ON (ct.oid = i.indrelid AND indisprimary)
                JOIN pg_catalog.pg_class ci ON (ci.oid = i.indexrelid)
                WHERE ct.relname IN ('$tableName')
                """.trimIndent()
            ) { row ->
                transform(row)
            }
        }
        withDb(TestDB.ALL_POSTGRES) {
            val defaultPKName = "tester_pkey"
            org.jetbrains.exposed.v1.r2dbc.SchemaUtils.createMissingTablesAndColumns(tester1)
            assertFalse(assertPrimaryKey { it.getString(1)!! }?.any() == true)

            org.jetbrains.exposed.v1.r2dbc.SchemaUtils.createMissingTablesAndColumns(tester2)
            val defaultPK = assertPrimaryKey {
                it.getString("PK_NAME")!!
            }?.single()
            assertEquals(defaultPKName, defaultPK)

            assertFailAndRollback("Multiple primary keys are not allowed") {
                org.jetbrains.exposed.v1.r2dbc.SchemaUtils.createMissingTablesAndColumns(tester3)
            }

            org.jetbrains.exposed.v1.r2dbc.SchemaUtils.drop(tester1)
        }
    }

    private val uuid = UUID.fromString("b1dd54af-314f-4dac-9b8d-a6eacb825b61")

    object TestConflictTable : UUIDTable("test_conflict") {
        val value = integer("value")
    }

    // equivalent to exposed-tests/SuspendTransactionTests.kt/testClosedSuspendTransaction()
    @Test
    fun testClosedSuspendTransaction() {
        withTables(
            // Test is quite flaky by unknown yet reason. Locally it works without problem, but fails on CI.
            excludeSettings = TestDB.ALL - TestDB.POSTGRESQL,
            TestConflictTable,
            configure = {
                defaultMaxAttempts = 20
            }
        ) {
            inTopLevelSuspendTransaction(transactionIsolation = IsolationLevel.SERIALIZABLE) {
                TestConflictTable.insert {
                    it[id] = uuid
                    it[value] = 0
                }
            }

            val concurrentTransactions = 3

            List(concurrentTransactions) {
                coroutineScope {
                    launch(Dispatchers.IO) {
                        runExposedTransaction()
                    }
                }
            }.joinAll()
            val entry = TestConflictTable.selectAll().first()
            assertEquals(3, entry[TestConflictTable.value])
        }
    }

    private suspend fun runExposedTransaction() {
        suspendTransaction(null, transactionIsolation = IsolationLevel.SERIALIZABLE) {
            val current = TestConflictTable
                .selectAll()
                .where({ TestConflictTable.id eq uuid })
                .forUpdate()
                .single()[TestConflictTable.value]

            delay((100..300).random().toLong())

            TestConflictTable.update({ TestConflictTable.id eq uuid }) {
                it[value] = current + 1
            }
        }
    }
}

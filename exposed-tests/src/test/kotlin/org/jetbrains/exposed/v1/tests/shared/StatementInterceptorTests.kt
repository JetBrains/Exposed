package org.jetbrains.exposed.v1.tests.shared

import nl.altindag.log.LogCaptor
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.StatementContext
import org.jetbrains.exposed.v1.core.statements.StatementInterceptor
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.shared.dml.DMLTestsData
import org.jetbrains.exposed.v1.tests.shared.dml.withCitiesAndUsers
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test

private class DeleteWarningInterceptor : StatementInterceptor {
    override fun beforeExecution(transaction: Transaction, context: StatementContext) {
        if (context.statement.type == StatementType.DELETE) {
            exposedLogger.warn(DELETE_WARNING)
        }
    }

    companion object {
        const val DELETE_WARNING = "Delete operation detected"
    }
}

private class RollbackCheckInterceptor(
    private val query: String
) : StatementInterceptor {
    override fun afterRollback(transaction: Transaction) {
        transaction as JdbcTransaction
        val recordCount = transaction.exec(query) {
            it.next()
            it.getObject(1)
        }
        exposedLogger.info("$ROLLBACK_CHECK$recordCount")
    }

    companion object {
        const val ROLLBACK_CHECK = "Querying state post-rollback : "
    }
}

private class CommitDataInterceptor : StatementInterceptor {
    val debugStatsState = Key<DebugStats>()
    var preCommitCount = 0
    var postCommitCount = 0

    override fun beforeCommit(transaction: Transaction) {
        preCommitCount += transaction.getUserData(debugStatsState)?.count ?: 0
    }

    override fun afterCommit(transaction: Transaction) {
        postCommitCount += transaction.getUserData(debugStatsState)?.count ?: 0
    }

    override fun keepUserDataInTransactionStoreOnCommit(userData: Map<Key<*>, Any?>): Map<Key<*>, Any?> {
        return userData.filterValues { it is DebugStats }
    }

    data class DebugStats(val count: Int)
}

class StatementInterceptorTests : DatabaseTestsBase() {
    @Test
    fun testInterceptorWithOnlyLogs() {
        withCitiesAndUsers { cities, _, _ ->
            val logCaptor = LogCaptor.forName(exposedLogger.name)

            val testDeleteInterceptor = DeleteWarningInterceptor()

            registerInterceptor(testDeleteInterceptor).also {
                assertTrue(it)
            }

            val belgradeId = cities.insert { it[name] = "Belgrade" } get cities.id
            val amsterdamId = cities.insert { it[name] = "Amsterdam" } get cities.id

            assertTrue(logCaptor.warnLogs.isEmpty())

            cities.deleteWhere { cities.id eq belgradeId }

            assertTrue(logCaptor.warnLogs.single() == DeleteWarningInterceptor.DELETE_WARNING)
            logCaptor.clearLogs()

            unregisterInterceptor(testDeleteInterceptor).also {
                assertTrue(it)
            }

            cities.deleteWhere { cities.id eq amsterdamId }

            assertTrue(logCaptor.warnLogs.isEmpty())

            logCaptor.clearLogs()
            logCaptor.close()
        }
    }

    @Test
    fun testInterceptorWithOperations() {
        val cities = DMLTestsData.Cities
        withTables(cities, configure = { useNestedTransactions = true }) {
            val logCaptor = LogCaptor.forName(exposedLogger.name)

            val cityCount = cities.select(cities.id.count())
            val testRollbackInterceptor = RollbackCheckInterceptor(
                query = cityCount.prepareSQL(this)
            )

            cities.insert { it[name] = "Munich" }

            val originalCount = cityCount.single()[cities.id.count()]
            assertEquals(1, originalCount)

            transaction {
                registerInterceptor(testRollbackInterceptor).also {
                    assertTrue(it)
                }

                cities.insert { it[name] = "Belgrade" }
                cities.insert { it[name] = "Amsterdam" }

                assertEquals(originalCount + 2, cityCount.single()[cities.id.count()])
                assertTrue(logCaptor.infoLogs.isEmpty())

                rollback()

                unregisterInterceptor(testRollbackInterceptor).also {
                    assertTrue(it)
                }
            }

            val loggedInfo = logCaptor.infoLogs.single()
            assertTrue(loggedInfo.startsWith(RollbackCheckInterceptor.ROLLBACK_CHECK))
            assertEquals(
                originalCount,
                loggedInfo.substringAfter(RollbackCheckInterceptor.ROLLBACK_CHECK).toLong()
            )

            logCaptor.clearLogs()
            logCaptor.close()
        }
    }

    private object InsertInfoSqlLogger : SqlLogger {
        const val INFO_LOG = "INFO:"

        override fun log(context: StatementContext, transaction: Transaction) {
            if (context.statement.type == StatementType.INSERT) {
                exposedLogger.info("$INFO_LOG ${context.sql(transaction)}")
            }
        }
    }

    private object DeleteWarnSqlLogger : SqlLogger {
        const val WARN_LOG = "WARN:"

        override fun log(context: StatementContext, transaction: Transaction) {
            if (context.statement.type == StatementType.DELETE) {
                exposedLogger.warn("$WARN_LOG ${context.sql(transaction)}")
            }
        }
    }

    @Test
    fun testChangesToManualLogger() {
        withCitiesAndUsers { cities, _, _ ->
            val logCaptor = LogCaptor.forName(exposedLogger.name)

            // Logger left in on purpose as central to test.
            // Creates a CompositeSqlLogger that wraps a single logger instance
            val testLogger = addLogger(InsertInfoSqlLogger)

            val belgradeId = cities.insert { it[name] = "Belgrade" } get cities.id

            assertTrue(logCaptor.infoLogs.single().startsWith(InsertInfoSqlLogger.INFO_LOG))
            assertTrue(logCaptor.warnLogs.isEmpty())
            logCaptor.clearLogs()

            // The CompositeSqlLogger now wraps 2 logger instances
            testLogger.addLogger(DeleteWarnSqlLogger)

            cities.deleteWhere { cities.id eq belgradeId }

            assertTrue(logCaptor.infoLogs.isEmpty())
            assertTrue(logCaptor.warnLogs.single().startsWith(DeleteWarnSqlLogger.WARN_LOG))
            logCaptor.clearLogs()

            // The CompositeSqlLogger now wraps only a single instance again
            testLogger.removeLogger(InsertInfoSqlLogger)

            val amsterdamId = cities.insert { it[name] = "Amsterdam" } get cities.id
            cities.deleteWhere { cities.id eq amsterdamId }

            assertTrue(logCaptor.infoLogs.isEmpty())
            assertTrue(logCaptor.warnLogs.single().startsWith(DeleteWarnSqlLogger.WARN_LOG))

            logCaptor.clearLogs()
            logCaptor.close()
        }
    }

    @Test
    fun testChangesToImplicitDefaultLogger() {
        withCitiesAndUsers { cities, _, _ ->
            val logCaptor = LogCaptor.forName(exposedLogger.name)

            // the implicit defaultLogger is Slf4jSqlDebugLogger, which only logs at DEBUG level

            val belgradeId = cities.insert { it[name] = "Belgrade" } get cities.id

            assertTrue(logCaptor.debugLogs.isEmpty())

            logCaptor.setLogLevelToDebug()

            val amsterdamId = cities.insert { it[name] = "Amsterdam" } get cities.id

            assertTrue(logCaptor.debugLogs.single().startsWith("INSERT "))
            logCaptor.clearLogs()

            // the implicit defaultLogger now wraps an additional custom logger
            this.defaultLogger.addLogger(DeleteWarnSqlLogger)

            cities.deleteWhere { cities.id eq belgradeId }

            assertTrue(logCaptor.debugLogs.single().startsWith("DELETE "))
            assertTrue(logCaptor.warnLogs.single().startsWith(DeleteWarnSqlLogger.WARN_LOG))
            logCaptor.clearLogs()

            // the implicit defaultLogger no longer wraps the additional custom logger
            this.defaultLogger.removeLogger(DeleteWarnSqlLogger)

            cities.deleteWhere { cities.id eq amsterdamId }

            assertTrue(logCaptor.debugLogs.single().startsWith("DELETE "))
            assertTrue(logCaptor.warnLogs.isEmpty())

            logCaptor.resetLogLevel()
            logCaptor.clearLogs()
            logCaptor.close()
        }
    }

    @Test
    fun testConfiguredDefaultLogger() {
        Assumptions.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
        val logCaptor = LogCaptor.forName(exposedLogger.name)

        val tester = object : Table("tester") {
            val amount = integer("amount")
        }

        val dbH2 = TestDB.H2_V2.connect { sqlLogger = InsertInfoSqlLogger }

        transaction(dbH2) {
            SchemaUtils.create(tester)

            assertTrue(logCaptor.infoLogs.isEmpty())
        }

        transaction(dbH2) {
            // the defaultLogger is auto-enabled & configured to be InsertInfoSqlLogger
            tester.insert { it[amount] = 1 }
            tester.insert { it[amount] = 2 }

            assertEquals(2, logCaptor.infoLogs.size)
            assertTrue(logCaptor.infoLogs.all { it.startsWith(InsertInfoSqlLogger.INFO_LOG) })
            logCaptor.clearLogs()
        }

        transaction(dbH2) {
            // the defaultLogger is disabled manually for this entire transaction
            unregisterInterceptor(this.defaultLogger)

            tester.insert { it[amount] = 3 }
            tester.insert { it[amount] = 4 }

            assertTrue(logCaptor.infoLogs.isEmpty())
        }

        transaction(dbH2) {
            SchemaUtils.drop(tester)

            assertTrue(logCaptor.infoLogs.isEmpty())
        }

        logCaptor.clearLogs()
        logCaptor.close()
    }

    @Test
    fun testTransactionDataRestoredAfterCommit() {
        // Can't use withDB() etc because CurrentTestDBInterceptor competes & filters out our user data
        Assumptions.assumeTrue { TestDB.H2_V2 in TestDB.enabledDialects() }
        val db = TestDB.H2_V2.connect()
        val count = 5

        transaction(db) {
            debug = true

            val testCommitInterceptor = CommitDataInterceptor()

            registerInterceptor(testCommitInterceptor).also {
                assertTrue(it)
            }

            repeat(count) {
                exec("SELECT 1;")
            }

            // add to transaction's userData that should be held & restored across commits
            val expectedPreCommitCount = this.getOrCreate(testCommitInterceptor.debugStatsState) {
                CommitDataInterceptor.DebugStats(statementStats.values.sumOf { it.first }) // number of statements executed so far
            }.count
            assertEquals(count, expectedPreCommitCount)
            assertEquals(count, this.getUserData(testCommitInterceptor.debugStatsState)?.count)

            commit().also { statementStats.clear() }

            // restored userData is available directly inside the transaction in question
            assertEquals(count, this.getUserData(testCommitInterceptor.debugStatsState)?.count)

            assertEquals(count, testCommitInterceptor.preCommitCount)
            // interceptor afterCommit() should also have access to restored userData
            assertEquals(count, testCommitInterceptor.postCommitCount)

            unregisterInterceptor(testCommitInterceptor).also {
                assertTrue(it)
            }
        }

        TransactionManager.closeAndUnregister(db)
    }
}

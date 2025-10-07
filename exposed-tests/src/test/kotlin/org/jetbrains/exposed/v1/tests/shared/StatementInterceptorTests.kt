package org.jetbrains.exposed.v1.tests.shared

import nl.altindag.log.LogCaptor
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exposedLogger
import org.jetbrains.exposed.v1.core.statements.StatementContext
import org.jetbrains.exposed.v1.core.statements.StatementInterceptor
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.shared.dml.DMLTestsData
import org.jetbrains.exposed.v1.tests.shared.dml.withCitiesAndUsers
import org.junit.Test

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
}

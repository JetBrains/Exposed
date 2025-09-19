package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared

import kotlinx.coroutines.flow.single
import nl.altindag.log.LogCaptor
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exposedLogger
import org.jetbrains.exposed.v1.core.statements.StatementContext
import org.jetbrains.exposed.v1.core.statements.StatementInterceptor
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.dml.DMLTestsData
import org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.dml.withCitiesAndUsers
import org.jetbrains.exposed.v1.r2dbc.statements.SuspendStatementInterceptor
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertTrue
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
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
) : SuspendStatementInterceptor {
    override suspend fun afterRollback(transaction: R2dbcTransaction) {
        val recordCount = transaction.exec(query) {
            it.get(0)
        }?.single()
        exposedLogger.info("$ROLLBACK_CHECK$recordCount")
    }

    companion object {
        const val ROLLBACK_CHECK = "Querying state post-rollback : "
    }
}

class StatementInterceptorTests : R2dbcDatabaseTestsBase() {
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

            suspendTransaction {
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

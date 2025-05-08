package org.jetbrains.exposed.v1.dao

import org.jetbrains.exposed.v1.dao.id.IdTable
import org.jetbrains.exposed.v1.sql.AbstractQuery
import org.jetbrains.exposed.v1.sql.Key
import org.jetbrains.exposed.v1.sql.Transaction
import org.jetbrains.exposed.v1.sql.statements.*
import org.jetbrains.exposed.v1.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.v1.sql.targetTables
import org.jetbrains.exposed.v1.sql.transactions.transactionScope

private var isExecutedWithinEntityLifecycle by transactionScope { false }

internal fun <T> executeAsPartOfEntityLifecycle(body: () -> T): T {
    val currentExecutionState = isExecutedWithinEntityLifecycle
    return try {
        isExecutedWithinEntityLifecycle = true
        body()
    } finally {
        isExecutedWithinEntityLifecycle = currentExecutionState
    }
}

/**
 * Represents a [StatementInterceptor] specifically responsible for the statement lifecycle of [Entity] instances,
 * which is loaded whenever a [Transaction] instance is initialized.
 */
class EntityLifecycleInterceptor : GlobalStatementInterceptor {

    override fun keepUserDataInTransactionStoreOnCommit(userData: Map<Key<*>, Any?>): Map<Key<*>, Any?> {
        return userData.filterValues { it is EntityCache }
    }

    @Suppress("ComplexMethod")
    override fun beforeExecution(transaction: Transaction, context: StatementContext) {
        beforeExecution(transaction = transaction, context = context, childStatement = null)
    }

    private fun beforeExecution(transaction: Transaction, context: StatementContext, childStatement: Statement<*>?) {
        when (val statement = childStatement ?: context.statement) {
            is AbstractQuery<*> -> transaction.flushEntities(statement)

            is ReturningStatement -> {
                beforeExecution(transaction = transaction, context = context, childStatement = statement.mainStatement)
            }

            is DeleteStatement -> {
                transaction.flushCache()
                transaction.entityCache.removeTablesReferrers(statement.targetsSet.targetTables(), false)
                if (!isExecutedWithinEntityLifecycle) {
                    statement.targets.filterIsInstance<IdTable<*>>().forEach {
                        transaction.entityCache.data[it]?.clear()
                    }
                }
            }

            is UpsertStatement<*>, is BatchUpsertStatement -> {
                transaction.flushCache()
                transaction.entityCache.removeTablesReferrers(statement.targets, true)
                if (!isExecutedWithinEntityLifecycle) {
                    statement.targets.filterIsInstance<IdTable<*>>().forEach {
                        transaction.entityCache.data[it]?.clear()
                    }
                }
            }

            is InsertStatement<*> -> {
                transaction.flushCache()
                transaction.entityCache.removeTablesReferrers(listOf(statement.table), true)
            }

            is BatchUpdateStatement -> {
            }

            is UpdateStatement -> {
                transaction.flushCache()
                transaction.entityCache.removeTablesReferrers(statement.targetsSet.targetTables(), false)
                if (!isExecutedWithinEntityLifecycle) {
                    statement.targets.filterIsInstance<IdTable<*>>().forEach {
                        transaction.entityCache.data[it]?.clear()
                    }
                }
            }

            else -> {
                if (statement.type.group == StatementGroup.DDL) transaction.flushCache()
            }
        }
    }

    override fun afterExecution(transaction: Transaction, contexts: List<StatementContext>, executedStatement: PreparedStatementApi) {
        if (!isExecutedWithinEntityLifecycle || contexts.first().statement !is InsertStatement<*>) {
            transaction.alertSubscribers()
        }
    }

    override fun beforeCommit(transaction: Transaction) {
        val created = transaction.flushCache()
        transaction.alertSubscribers()
        val createdByHooks = transaction.flushCache()
        EntityCache.invalidateGlobalCaches(created + createdByHooks)
    }

    override fun beforeRollback(transaction: Transaction) {
        val entityCache = transaction.entityCache
        entityCache.clearReferrersCache()
        entityCache.data.clear()
        entityCache.inserts.clear()
    }

    private fun Transaction.flushEntities(query: AbstractQuery<*>) {
        // Flush data before executing query or results may be unpredictable
        val tables = query.targets.filterIsInstance(IdTable::class.java).toSet()
        entityCache.flush(tables)
    }
}

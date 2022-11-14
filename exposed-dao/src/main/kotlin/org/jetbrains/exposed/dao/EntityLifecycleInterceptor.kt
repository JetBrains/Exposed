package org.jetbrains.exposed.dao

import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Key
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.*
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.targetTables
import org.jetbrains.exposed.sql.transactions.transactionScope

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

class EntityLifecycleInterceptor : GlobalStatementInterceptor {

    override fun keepUserDataInTransactionStoreOnCommit(userData: Map<Key<*>, Any?>): Map<Key<*>, Any?> {
        return userData.filterValues { it is EntityCache }
    }

    override fun beforeExecution(transaction: Transaction, context: StatementContext) {
        when (val statement = context.statement) {
            is Query -> transaction.flushEntities(statement)

            is DeleteStatement -> {
                transaction.flushCache()
                transaction.entityCache.removeTablesReferrers(listOf(statement.table), false)
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
        if (!isExecutedWithinEntityLifecycle || contexts.first().statement !is InsertStatement<*>)
            transaction.alertSubscribers()
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

    private fun Transaction.flushEntities(query: Query) {
        // Flush data before executing query or results may be unpredictable
        val tables = query.targets.filterIsInstance(IdTable::class.java).toSet()
        entityCache.flush(tables)
    }
}

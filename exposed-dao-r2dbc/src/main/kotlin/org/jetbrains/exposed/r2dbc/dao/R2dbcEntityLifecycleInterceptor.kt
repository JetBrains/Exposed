package org.jetbrains.exposed.r2dbc.dao

import org.jetbrains.exposed.v1.core.AbstractQuery
import org.jetbrains.exposed.v1.core.Key
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.statements.*
import org.jetbrains.exposed.v1.core.targetTables
import org.jetbrains.exposed.v1.core.transactions.transactionScope
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.statements.GlobalSuspendStatementInterceptor
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcPreparedStatementApi

private var isExecutedWithinEntityLifecycle by transactionScope { false }

internal suspend fun <T> executeAsPartOfEntityLifecycle(body: suspend () -> T): T {
    val currentExecutionState = isExecutedWithinEntityLifecycle
    return try {
        isExecutedWithinEntityLifecycle = true
        body()
    } finally {
        isExecutedWithinEntityLifecycle = currentExecutionState
    }
}

class R2dbcEntityLifecycleInterceptor : GlobalSuspendStatementInterceptor {

    override fun keepUserDataInTransactionStoreOnCommit(userData: Map<Key<*>, Any?>): Map<Key<*>, Any?> {
        return userData.filterValues { it is R2dbcEntityCache }
    }

    @Suppress("ComplexMethod")
    override suspend fun beforeExecution(transaction: R2dbcTransaction, context: StatementContext) {
        beforeExecution(transaction = transaction, context = context, childStatement = null)
    }

    private suspend fun beforeExecution(transaction: R2dbcTransaction, context: StatementContext, childStatement: Statement<*>?) {
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

    override suspend fun afterExecution(
        transaction: R2dbcTransaction,
        contexts: List<StatementContext>,
        executedStatement: R2dbcPreparedStatementApi
    ) {
        if (!isExecutedWithinEntityLifecycle || contexts.first().statement !is InsertStatement<*>) {
            transaction.alertSubscribers()
        }
    }

    override suspend fun beforeCommit(transaction: R2dbcTransaction) {
        transaction.flushCache()
        transaction.alertSubscribers()
        transaction.flushCache()
        // TODO ALIGN_WITH_JDBC: call `EntityCache.invalidateGlobalCaches(created + createdByHooks)`
        //  once `ImmutableCachedEntityClass` exists in R2DBC.
    }

    override suspend fun beforeRollback(transaction: R2dbcTransaction) {
        val entityCache = transaction.entityCache
        entityCache.clearReferrersCache()

        // Clear writeValues and readValues for all entities before clearing the cache to prevent
        // stale data from being carried over into a new transaction. Ideally, at this stage,
        // values from writeValues should not have been transferred to readValues yet, but we clear
        // both for reliability to ensure complete cleanup.
        //
        // TODO ALIGN_WITH_JDBC: when ImmutableCachedEntityClass is ported, preserve its _readValues here.
        entityCache.data.values.forEach { entityMap ->
            entityMap.values.forEach { entity ->
                entity.writeValues.clear()
                entity._readValues = null
            }
        }
        entityCache.updates.values.forEach { entitySet ->
            entitySet.forEach { entity ->
                entity.writeValues.clear()
                entity._readValues = null
            }
        }

        entityCache.data.clear()
        entityCache.inserts.clear()
        entityCache.updates.clear()
    }

    private suspend fun R2dbcTransaction.flushEntities(query: AbstractQuery<*>) {
        // Flush data before executing query or results may be unpredictable
        val tables = query.targets.filterIsInstance(IdTable::class.java).toSet()
        entityCache.flush(tables)
    }
}

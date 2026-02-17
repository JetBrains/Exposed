package org.jetbrains.exposed.r2dbc.dao

import org.jetbrains.exposed.v1.core.AbstractQuery
import org.jetbrains.exposed.v1.core.Key
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.statements.*
import org.jetbrains.exposed.v1.core.targetTables
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.statements.GlobalSuspendStatementInterceptor
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcPreparedStatementApi

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
                transaction.entityCache.removeTablesReferrers(statement.targetsSet.targetTables().filterIsInstance<IdTable<*>>())
            }

            is UpsertStatement<*>, is BatchUpsertStatement -> {
                transaction.flushCache()
                transaction.entityCache.removeTablesReferrers(statement.targets.filterIsInstance<IdTable<*>>())
            }

            is InsertStatement<*> -> {
                transaction.flushCache()
                if (statement.table is IdTable<*>) {
                    transaction.entityCache.removeTablesReferrers(listOf(statement.table as IdTable<*>))
                }
            }

            is UpdateStatement -> {
                transaction.flushCache()
                transaction.entityCache.removeTablesReferrers(statement.targetsSet.targetTables().filterIsInstance<IdTable<*>>())
            }

            else -> {
                if (statement.type.group == StatementGroup.DDL) transaction.flushCache()
            }
        }
    }

    @Suppress("ForbiddenComment")
    override suspend fun afterExecution(
        transaction: R2dbcTransaction,
        contexts: List<StatementContext>,
        executedStatement: R2dbcPreparedStatementApi
    ) {
        // TODO: Implement alertSubscribers when subscriptions are implemented
    }

    @Suppress("ForbiddenComment")
    override suspend fun beforeCommit(transaction: R2dbcTransaction) {
        transaction.flushCache()
        // TODO: Implement alertSubscribers and EntityCache.invalidateGlobalCaches when subscriptions are implemented
    }

    override suspend fun beforeRollback(transaction: R2dbcTransaction) {
        val entityCache = transaction.entityCache
        // Clear referrers cache
        entityCache.referrers.clear()

        // Clear writeValues and readValues for all entities before clearing the cache to prevent
        // stale data from being carried over into a new transaction
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

// Extension functions for R2dbcTransaction
suspend fun R2dbcTransaction.flushCache(): List<R2dbcEntity<*>> {
    entityCache.flush()
    @Suppress("ForbiddenComment")
    // TODO: Return list of created entities when entity change tracking is implemented
    return emptyList()
}

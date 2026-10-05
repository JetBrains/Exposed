package org.jetbrains.exposed.v1.dao.r2dbc

import org.jetbrains.exposed.v1.core.AbstractQuery
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Key
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.statements.*
import org.jetbrains.exposed.v1.core.targetTables
import org.jetbrains.exposed.v1.core.transactions.transactionScope
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.statements.GlobalSuspendStatementInterceptor
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcPreparedStatementApi
import org.jetbrains.exposed.v1.r2dbc.withTransactionContext

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

/**
 * Represents a [GlobalSuspendStatementInterceptor] specifically responsible for the statement lifecycle of
 * [Entity] instances, which is loaded whenever an [R2dbcTransaction] instance is initialized.
 */
@ExperimentalR2dbcDaoApi
class EntityLifecycleInterceptor : GlobalSuspendStatementInterceptor {

    override fun keepUserDataInTransactionStoreOnCommit(userData: Map<Key<*>, Any?>): Map<Key<*>, Any?> {
        return userData.filterValues { it is EntityCache }
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
                transaction.flushChain()
                transaction.entityCache.removeTablesReferrers(statement.targetsSet.targetTables(), false)
                if (!isExecutedWithinEntityLifecycle) {
                    statement.targets.filterIsInstance<IdTable<*>>().forEach {
                        transaction.entityCache.data[it]?.clear()
                    }
                }
            }

            is UpsertStatement<*>, is BatchUpsertStatement -> {
                transaction.flushChain()
                transaction.entityCache.removeTablesReferrers(statement.targets, true)
                if (!isExecutedWithinEntityLifecycle) {
                    statement.targets.filterIsInstance<IdTable<*>>().forEach {
                        transaction.entityCache.data[it]?.clear()
                    }
                }
            }

            is InsertStatement<*> -> {
                transaction.flushChain()
                transaction.entityCache.removeTablesReferrers(listOf(statement.table), true)
            }

            is BatchUpdateStatement -> {
            }

            is UpdateStatement -> {
                transaction.flushChain()
                transaction.entityCache.removeTablesReferrers(statement.targetsSet.targetTables(), false)
                if (!isExecutedWithinEntityLifecycle) {
                    statement.targets.filterIsInstance<IdTable<*>>().forEach {
                        transaction.entityCache.data[it]?.clear()
                    }
                }
            }

            else -> {
                if (statement.type.group == StatementGroup.DDL) transaction.flushChain()
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

        // EXPOSED-1067: call `EntityCache.invalidateGlobalCaches(created + createdByHooks)`
        //  once `ImmutableCachedEntityClass` exists in R2DBC.
    }

    override suspend fun afterCommit(transaction: R2dbcTransaction) {
        transaction.entityCache.promoteUncommittedState()
    }

    override suspend fun beforeRollback(transaction: R2dbcTransaction) {
        val entityCache = transaction.entityCache
        entityCache.clearReferrersCache()

        // EXPOSED-1067: when ImmutableCachedEntityClass is ported, preserve its _readValues here.
        entityCache.discardUncommittedState()

        entityCache.inserts.clear()
        entityCache.updates.clear()
        entityCache.pendingInnerTableLinkUpdates.clear()
    }

    private suspend fun R2dbcTransaction.flushEntities(query: AbstractQuery<*>) {
        val tables = query.targets.filterIsInstance(IdTable::class.java).toSet()
        flushChain(tables)
    }
}

@OptIn(InternalApi::class)
private suspend fun R2dbcTransaction.flushChain(tables: Iterable<IdTable<*>>? = null) {
    for (scope in generateSequence(this) { it.outerTransaction }.toList().asReversed()) {
        if (scope === this) {
            scope.flushScope(tables)
        } else {
            withTransactionContext(scope) { scope.flushScope(tables) }
        }
    }
}

private suspend fun R2dbcTransaction.flushScope(tables: Iterable<IdTable<*>>?) {
    if (tables == null) entityCache.flush() else entityCache.flush(tables)
}

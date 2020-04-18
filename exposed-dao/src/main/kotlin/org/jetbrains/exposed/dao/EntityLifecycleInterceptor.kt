package org.jetbrains.exposed.dao

import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.*
import org.jetbrains.exposed.sql.targetTables
import org.jetbrains.exposed.sql.transactions.ITransaction

class EntityLifecycleInterceptor : GlobalStatementInterceptor {

    override fun beforeExecution(transaction: ITransaction, context: StatementContext) {
        if (transaction is DaoTransaction) {
            transaction as DaoTransaction
            when (val statement = context.statement) {
                is Query -> transaction.flushEntities(statement)

                is DeleteStatement -> {
                    transaction.flushCache()
                    transaction.removeTablesReferrers(listOf(statement.table))
                }

                is InsertStatement<*> -> {
                    transaction.flushCache()
                    transaction.removeTablesReferrers(listOf(statement.table))
                }

                is BatchUpdateStatement -> {
                }

                is UpdateStatement -> {
                    transaction.flushCache()
                    transaction.removeTablesReferrers(statement.targetsSet.targetTables())
                }

                else -> {
                    if (statement.type.group == StatementGroup.DDL)
                        transaction.flushCache()
                }
            }
        }
    }

    override fun beforeCommit(transaction: ITransaction) {
        if (transaction is DaoTransaction) {
            transaction as DaoTransaction
            val created = transaction.flushCache()
            transaction.alertSubscribers()
            val createdByHooks = transaction.flushCache()
            EntityCache.invalidateGlobalCaches(created + createdByHooks)
        }
    }

    override fun beforeRollback(transaction: ITransaction) {
        if (transaction is DaoTransaction) {
            transaction as DaoTransaction
            transaction.clearReferrersCache()
            transaction.clearData()
            transaction.clearInserts()
        }
    }

    private fun DaoTransaction.flushEntities(query: Query) {
        // Flush data before executing query or results may be unpredictable
        val tables = query.set.source.columns.map { it.table }.filterIsInstance(IdTable::class.java).toSet()
        this.flush(tables)
    }
}
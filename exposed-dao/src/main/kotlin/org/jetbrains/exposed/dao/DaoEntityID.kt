package org.jetbrains.exposed.dao

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.transactions.ITransactionManager

class DaoEntityID<T:Comparable<T>>(id: T?, table: IdTable<T>) : EntityID<T>(table, id) {
    override fun invokeOnNoValue() {
        val transaction: DaoTransaction = ITransactionManager.current() as DaoTransaction
        transaction.flushInserts(table)
    }
}

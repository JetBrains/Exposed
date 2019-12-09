package org.jetbrains.exposed.dao

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.transactions.TransactionManager

class DaoEntityID<T:Comparable<T>>(id: T?, table: IdTable<T>) : EntityID<T>(table, id) {
    override fun invokeOnNoValue() {
        TransactionManager.current().entityCache.flushInserts(table)
    }
}


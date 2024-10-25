package org.jetbrains.exposed.dao

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.transactions.TransactionManager

/**
 * Class representing a wrapper for a stored identity value of type [T], which is managed and cached
 * by an [EntityClass] using a data access object pattern.
 */
class DaoEntityID<T : Any>(id: T?, table: IdTable<T>) : EntityID<T>(table, id) {
    override fun invokeOnNoValue() {
        TransactionManager.current().entityCache.flushInserts(table)
    }
}

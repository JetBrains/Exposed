package org.jetbrains.exposed.v1.dao

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

/**
 * Class representing a wrapper for a stored identity value of type [T], which is managed and cached
 * by an [EntityClass] using a data access object pattern.
 */
class DaoEntityID<T : Any>(id: T?, table: IdTable<T>) : EntityID<T>(table, id) {
    override fun invokeOnNoValue() {
        TransactionManager.current().entityCache.flushInserts(table)
    }
}

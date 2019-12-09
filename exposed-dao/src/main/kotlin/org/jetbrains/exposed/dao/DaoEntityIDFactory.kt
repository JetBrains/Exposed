package org.jetbrains.exposed.dao

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.EntityIDFactory
import org.jetbrains.exposed.dao.id.IdTable

class DaoEntityIDFactory : EntityIDFactory {
    override fun <T : Comparable<T>> createEntityID(value: T, table: IdTable<T>): EntityID<T> {
        return DaoEntityID(value, table)
    }
}
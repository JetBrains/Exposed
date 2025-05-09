package org.jetbrains.exposed.v1.dao

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.EntityIDFactory
import org.jetbrains.exposed.v1.core.dao.id.IdTable

/**
 * Class representing a producer of [EntityID] instances, which are managed and cached by their respective
 * [EntityClass] instances using a data access object pattern.
 */
class DaoEntityIDFactory : EntityIDFactory {
    override fun <T : Any> createEntityID(value: T, table: IdTable<T>): EntityID<T> {
        return DaoEntityID(value, table)
    }
}

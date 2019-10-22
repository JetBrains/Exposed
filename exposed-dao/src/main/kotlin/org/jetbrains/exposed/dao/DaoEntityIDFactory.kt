package org.jetbrains.exposed.dao

class DaoEntityIDFactory : EntityIDFactory {
    override fun <T : Comparable<T>> createEntityID(value: T, table: IdTable<T>): EntityID<T> {
        return DaoEntityID(value, table)
    }
}
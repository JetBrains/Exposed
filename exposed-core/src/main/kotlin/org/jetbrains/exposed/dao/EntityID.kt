package org.jetbrains.exposed.dao

abstract class EntityID<T:Comparable<T>>(id: T?, val table: IdTable<T>) : Comparable<EntityID<T>> {
    var _value: Any? = id
    val value: T get() {
        if (_value == null) {
            invokeOnNoValue()
            assert(_value != null) { "Entity must be inserted" }
        }

        @Suppress("UNCHECKED_CAST")
        return _value!! as T
    }

    protected abstract fun invokeOnNoValue()

    override fun toString() = value.toString()

    override fun hashCode() = value.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other !is EntityID<*>) return false

        return other._value == _value && other.table == table
    }

    override fun compareTo(other: EntityID<T>): Int = value.compareTo(other.value)
}

class SimpleEntityID<T:Comparable<T>>(id: T?, table: IdTable<T>) : EntityID<T>(id, table) {
    override fun invokeOnNoValue() {}
}
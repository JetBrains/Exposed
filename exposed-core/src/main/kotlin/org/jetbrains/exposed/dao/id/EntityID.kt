package org.jetbrains.exposed.dao.id

open class EntityID<T:Comparable<T>> protected constructor(val table: IdTable<T>, id: T?) : Comparable<EntityID<T>> {
    constructor(id:T, table: IdTable<T>) : this(table, id)
    var _value: Any? = id
    val value: T get() {
        if (_value == null) {
            invokeOnNoValue()
            check(_value != null) { "Entity must be inserted" }
        }

        @Suppress("UNCHECKED_CAST")
        return _value!! as T
    }

    protected open fun invokeOnNoValue() {}

    override fun toString() = value.toString()

    override fun hashCode() = value.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other !is EntityID<*>) return false

        return other._value == _value && other.table == table
    }

    override fun compareTo(other: EntityID<T>): Int = value.compareTo(other.value)
}

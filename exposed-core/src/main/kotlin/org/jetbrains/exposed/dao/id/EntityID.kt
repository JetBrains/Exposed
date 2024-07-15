package org.jetbrains.exposed.dao.id

/**
 * Class representing a wrapper for a stored identity value of type [T].
 *
 * The class constructor could be useful, for example, if needing to manually provide an identity value to a column
 * default function or when manually inserting into identity columns using any DSL insert function.
 *
 * @param table The [IdTable] that stores the identity value.
 * @param id The value of type [T] to store.
 * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTestsData.YTable
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.InsertTests.testInsertWithPredefinedId
 */
open class EntityID<T : Comparable<T>> protected constructor(val table: IdTable<T>, id: T?) : Comparable<EntityID<T>> {
    constructor(id: T, table: IdTable<T>) : this(table, id)

    @Suppress("VariableNaming")
    var _value: Any? = id

    /** The identity value of type [T] wrapped by this [EntityID] instance. */
    val value: T
        get() {
            if (_value == null) {
                invokeOnNoValue()
                check(_value != null) { "Entity must be inserted" }
            }

            @Suppress("UNCHECKED_CAST")
            return _value!! as T
        }

    /** Performs steps when the internal [_value] is accessed without first being initialized. */
    protected open fun invokeOnNoValue() {}

    override fun toString() = value.toString()

    override fun hashCode() = value.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other !is EntityID<*>) return false

        return other._value == _value && other.table == table
    }

    override fun compareTo(other: EntityID<T>): Int = value.compareTo(other.value)
}

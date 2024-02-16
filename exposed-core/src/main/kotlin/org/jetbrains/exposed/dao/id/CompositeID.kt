package org.jetbrains.exposed.dao.id

import org.jetbrains.exposed.sql.Column

/** Class representing a mapping of each composite primary key column to its stored identity value. */
class CompositeID(
    idMapping: Map<out Column<*>, Comparable<*>?>
) : HashMap<Column<*>, Comparable<*>?>(idMapping), Comparable<CompositeID> {
    override fun toString(): String = "CompositeID(${entries.joinToString { "${it.key.name}=${it.value}" }})"

    override fun hashCode(): Int = entries.fold(0) { acc, entry ->
        (acc * 31) + entry.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other is EntityID<*>) return this == other._value
        if (other !is CompositeID) return false

        return super.equals(other)
    }

    override fun compareTo(other: CompositeID): Int {
        val compareSize = compareValues(other.size, size)
        if (compareSize != 0) return compareSize

        entries.forEach { (column, idValue) ->
            if (!other.containsKey(column)) return -1
            compareValues(idValue, other[column]).let {
                if (it != 0) return it
            }
        }
        return 0
    }
}

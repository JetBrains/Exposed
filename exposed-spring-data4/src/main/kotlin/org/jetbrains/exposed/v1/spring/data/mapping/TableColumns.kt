package org.jetbrains.exposed.v1.spring.data.mapping

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import java.util.IdentityHashMap
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.jvm.isAccessible

/**
 * The columns of a table object by their Kotlin property name (e.g. `createdAt`), which is how domain properties are mapped.
 * [Column.name] can't be used for this, because it is the SQL name (e.g. `created_at`).
 */
internal class TableColumns private constructor(val table: Table) {
    /** All columns by their Kotlin property name. */
    @Suppress("UNCHECKED_CAST")
    val columns: Map<String, Column<Any?>> = table::class.memberProperties
        .filter { it.returnType.isSubtypeOf(Column::class.starProjectedType) }
        .associate {
            it.isAccessible = true
            it.name to it.getter.call(table) as Column<Any?>
        }

    fun columnFor(property: String): Column<Any?>? = columns[property]

    companion object {
        // by identity, because tables are equal if their names are
        private val cache = IdentityHashMap<Table, TableColumns>()

        fun of(table: Table): TableColumns = synchronized(cache) { cache.getOrPut(table) { TableColumns(table) } }
    }
}

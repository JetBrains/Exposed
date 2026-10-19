package org.jetbrains.exposed.v1.spring.data.query

import org.jetbrains.exposed.v1.core.Alias
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnSet
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.spring.data.mapping.orderBy
import org.springframework.data.domain.Sort

/**
 * The table to select from for [sort]: the repository's [table], plus one `LEFT JOIN` per referenced table that a sort property
 * goes through (e.g. `customer.name`), under an alias, so the same table can be joined more than once.
 *
 * Only to-one paths can be sorted by (see [PropertyPathResolver.resolveSort]), so the joins never change the number of rows,
 * and counting, paging and scrolling work as without them.
 */
internal class SortedSource(table: Table, paths: PropertyPathResolver, private val sort: Sort) {
    private val aliases = LinkedHashMap<List<Hop>, Alias<Table>>()

    var columnSet: ColumnSet = table
        private set

    /** The (aliased) column of each sort property. */
    val columns: Map<String, Column<Any?>> = sort.associate { it.property to column(paths.resolveSort(it.property)) }

    fun select(selected: List<Expression<*>>): Query = columnSet.select(selected).apply { orderBy(sort, columns) }

    @Suppress("UNCHECKED_CAST")
    private fun column(path: ResolvedPath): Column<Any?> {
        var current: Alias<Table>? = null
        path.hops.indices.forEach { i ->
            val hop = path.hops[i]
            val parentKey = current?.get(hop.parentKey as Column<Any?>) ?: hop.parentKey
            current = aliases.getOrPut(path.hops.subList(0, i + 1).toList()) {
                hop.childTable.alias("sort_${aliases.size}").also { alias ->
                    columnSet = columnSet.join(alias, JoinType.LEFT, onColumn = parentKey, otherColumn = alias[hop.childKey as Column<Any?>])
                }
            }
        }
        return current?.get(path.leaf) ?: path.leaf
    }
}

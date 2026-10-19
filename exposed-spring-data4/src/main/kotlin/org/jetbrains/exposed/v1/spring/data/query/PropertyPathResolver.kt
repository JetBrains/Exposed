package org.jetbrains.exposed.v1.spring.data.query

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.spring.data.ExposedReferencedBy
import org.jetbrains.exposed.v1.spring.data.mapping.EntityMetadata
import org.jetbrains.exposed.v1.spring.data.mapping.ExposedMappings
import org.jetbrains.exposed.v1.spring.data.mapping.JoinRelation
import org.jetbrains.exposed.v1.spring.data.mapping.OwnedRelation
import org.jetbrains.exposed.v1.spring.data.mapping.columnFor
import org.jetbrains.exposed.v1.spring.data.mapping.isReferenceType
import org.jetbrains.exposed.v1.spring.data.mapping.propertyAnnotation
import org.jetbrains.exposed.v1.spring.data.mapping.toManyRelation
import org.springframework.data.core.PropertyPath
import org.springframework.data.core.PropertyReferenceException
import java.util.concurrent.ConcurrentHashMap

/**
 * A step from one table to another: the rows of [childTable] whose [childKey] matches [parentKey] of the current table.
 *
 * - to-one (a foreign key column of the current table, [toOne]): [parentKey] is that column, [childKey] the column it references
 * - to-many ([ExposedReferencedBy]): [parentKey] is the referenced column of the current table, [childKey] the foreign key column of [childTable]
 */
internal data class Hop(val parentKey: Column<*>, val childTable: Table, val childKey: Column<*>, val toOne: Boolean)

/** A property path of a derived query or sort: the [hops] to the table of the [leaf] column. */
internal class ResolvedPath(val hops: List<Hop>, val leaf: Column<Any?>) {
    /** Whether the value can be `NULL`: a nullable column, or a path through a nullable foreign key. */
    val nullable: Boolean get() = leaf.columnType.nullable || hops.any { it.parentKey.columnType.nullable }
}

/**
 * Resolves the property paths of derived queries and sorts, starting at the repository's table:
 *
 * - the last segment is the column of the property on the table reached so far (on the repository's table, only mapped properties
 *   of the entity), by its Kotlin name or the one named by `@ExposedColumn`
 * - a property annotated with [ExposedReferencedBy] is a to-many hop to the annotated table, through its foreign key to the current table;
 *   with a target, a second (to-one) hop goes on to the referenced table
 * - any other property is a to-one hop, if its column on the current table is a foreign key
 *
 * A to-one hop to the referenced column as the last segment (e.g. `customer.id`) is left out, the foreign key column is used directly.
 * Invalid paths fail with an [IllegalArgumentException], which is reported when the repository is created.
 */
internal class PropertyPathResolver(private val metadata: EntityMetadata<*, *>, private val mappings: ExposedMappings) {
    private val resolved = ConcurrentHashMap<String, ResolvedPath>()
    private val sorts = ConcurrentHashMap<String, ResolvedPath>()

    fun resolve(path: PropertyPath): ResolvedPath = resolved.getOrPut(path.toDotPath()) { doResolve(path) }

    /** The path of the sort property [property] (e.g. `customer.name`), which may only go through to-one hops. */
    fun resolveSort(property: String): ResolvedPath = sorts.getOrPut(property) {
        if ('.' !in property) {
            val column = requireNotNull(metadata.columnFor(property)) { "Unknown sort property '$property' for table ${metadata.table.tableName}" }
            return@getOrPut ResolvedPath(emptyList(), column)
        }
        val path = try {
            PropertyPath.from(property, metadata.domainType.java)
        } catch (e: PropertyReferenceException) {
            throw IllegalArgumentException("Unknown sort property '$property' for ${metadata.domainType.simpleName}", e)
        }
        resolve(path).also {
            require(it.hops.all(Hop::toOne)) { "Sorting by '$property' is not supported, the path goes through a to-many property" }
        }
    }

    /** The key to compare a foreign key [column] with, if [value] is the referenced object (e.g. `findByCustomer(customer: Customer)`). */
    fun parameterValue(column: Column<*>, value: Any?): Any? {
        val referee = column.referee ?: return value
        if (value == null || !isReferenceType(value.javaClass)) return value
        return mappings.writer(referee.table, value.javaClass).columnValue(value, referee)
    }

    private fun doResolve(root: PropertyPath): ResolvedPath {
        if (!root.hasNext()) return ResolvedPath(emptyList(), metadata.requireColumn(root.segment))

        val hops = mutableListOf<Hop>()
        var table: Table = metadata.table
        var path = root
        while (path.hasNext()) {
            val next = hops(table, path, root, isRoot = path === root)
            hops += next
            table = next.last().childTable
            path = path.next()!!
        }
        val leaf = requireNotNull(columnFor(table, path.owningType.type, path.segment)) {
            "No column found for '${path.segment}' of the path '${root.toDotPath()}' in table ${table.tableName}"
        }
        val last = hops.last()
        if (last.toOne && leaf == last.childKey) {
            @Suppress("UNCHECKED_CAST")
            return ResolvedPath(hops.dropLast(1), last.parentKey as Column<Any?>)
        }
        return ResolvedPath(hops, leaf)
    }

    private fun hops(table: Table, path: PropertyPath, root: PropertyPath, isRoot: Boolean): List<Hop> {
        val owner = path.owningType.type
        owner.propertyAnnotation<ExposedReferencedBy>(path.segment)?.let { annotation ->
            return when (val relation = toManyRelation(table, annotation, path.segment, "path '${root.toDotPath()}'")) {
                is OwnedRelation -> listOf(Hop(relation.parentKey, relation.table, relation.foreignKey, toOne = false))
                is JoinRelation -> listOf(
                    Hop(relation.parentKey, relation.table, relation.foreignKey, toOne = false),
                    Hop(relation.targetKey, relation.referee.table, relation.referee, toOne = true),
                )
            }
        }
        val column = if (isRoot) metadata.columnFor(path.segment) else columnFor(table, owner, path.segment)
        val target = column?.referee
        require(column != null && target != null) {
            "'${path.segment}' of the path '${root.toDotPath()}' is neither a foreign key column of table ${table.tableName} " +
                "nor annotated with @${ExposedReferencedBy::class.simpleName}"
        }
        return listOf(Hop(parentKey = column, childTable = target.table, childKey = target, toOne = true))
    }
}

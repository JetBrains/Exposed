package org.jetbrains.exposed.v1.spring.data.query

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.spring.data.ExposedReferencedBy
import org.jetbrains.exposed.v1.spring.data.mapping.EntityMetadata
import org.jetbrains.exposed.v1.spring.data.mapping.ExposedMappings
import org.jetbrains.exposed.v1.spring.data.mapping.JoinRelation
import org.jetbrains.exposed.v1.spring.data.mapping.OwnedRelation
import org.jetbrains.exposed.v1.spring.data.mapping.ToManyRelation
import org.jetbrains.exposed.v1.spring.data.mapping.columnFor
import org.jetbrains.exposed.v1.spring.data.mapping.isReferenceType
import org.springframework.data.core.PropertyPath
import org.springframework.data.core.PropertyReferenceException
import java.util.concurrent.ConcurrentHashMap

/**
 * A step from one table to another: the rows of [childTable] whose [childKey] matches [parentKey] of the current table.
 *
 * - to-one (a foreign key column of the current table, [toOne]): [parentKey] is that column, [childKey] the column it references
 * - to-many ([ExposedMappings.relationFor]): [parentKey] is the referenced column of the current table, [childKey] the foreign key column of [childTable]
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
 * - a to-many property (annotated with [ExposedReferencedBy] or inferred, see [ExposedMappings.relationFor]) is a to-many hop to the
 *   table of the relation, through its foreign key to the current table; for a join table, a second (to-one) hop goes on to the referenced table
 * - any other property is a to-one hop, if its column on the current table is a foreign key
 *
 * A to-one hop to the referenced column as the last segment (e.g. `customer.id`) is left out, the foreign key column is used directly.
 * [resolveToMany] resolves paths that end on a to-many property, for `IsEmpty` and `IsNotEmpty`.
 *
 * Invalid paths fail with an [IllegalArgumentException], which is reported when the repository is created. An error of
 * [ExposedMappings.relationFor] gets the path appended. A repository usually reports an ambiguous relation earlier and without
 * the path, because its entity reader resolves all relations of the aggregate first.
 */
internal class PropertyPathResolver(private val metadata: EntityMetadata<*, *>, private val mappings: ExposedMappings) {
    private val resolved = ConcurrentHashMap<String, ResolvedPath>()
    private val sorts = ConcurrentHashMap<String, ResolvedPath>()
    private val toMany = ConcurrentHashMap<String, List<Hop>>()

    fun resolve(path: PropertyPath): ResolvedPath = resolved.getOrPut(path.toDotPath()) { doResolve(path) }

    /**
     * The hops of a [path] that ends on a to-many property (e.g. `orders.items` for `IsEmpty`): the hops to the table owning the
     * property, plus a last to-many hop to the rows of the relation (the join table, for a many-to-many relation).
     * Fails with an [IllegalArgumentException] if the last segment is no to-many relation.
     */
    fun resolveToMany(path: PropertyPath): List<Hop> = toMany.getOrPut(path.toDotPath()) {
        val hops = mutableListOf<Hop>()
        var table: Table = metadata.table
        var segment = path
        while (segment.hasNext()) {
            hops += hops(table, segment, path, isRoot = segment === path).also { table = it.last().childTable }
            segment = segment.next()!!
        }
        val relation = relation(table, segment, path)
        requireNotNull(relation) {
            "'${segment.segment}' of the path '${path.toDotPath()}' is no to-many relation (a collection, inferred or annotated with " +
                "@${ExposedReferencedBy::class.simpleName}), so it can't be checked for emptiness"
        }
        hops + relationHops(relation).first()
    }

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

    private fun relation(table: Table, path: PropertyPath, root: PropertyPath): ToManyRelation? = try {
        mappings.relationFor(table, path.owningType.type, path.segment)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("${e.message} (path '${root.toDotPath()}')", e)
    }

    private fun relationHops(relation: ToManyRelation): List<Hop> = when (relation) {
        is OwnedRelation -> listOf(Hop(relation.parentKey, relation.table, relation.foreignKey, toOne = false))
        is JoinRelation -> listOf(
            Hop(relation.parentKey, relation.table, relation.foreignKey, toOne = false),
            Hop(relation.targetKey, relation.referee.table, relation.referee, toOne = true),
        )
    }

    private fun hops(table: Table, path: PropertyPath, root: PropertyPath, isRoot: Boolean): List<Hop> {
        val owner = path.owningType.type
        relation(table, path, root)?.let { return relationHops(it) }
        val column = if (isRoot) metadata.columnFor(path.segment) else columnFor(table, owner, path.segment)
        val target = column?.referee
        require(column != null && target != null) {
            "'${path.segment}' of the path '${root.toDotPath()}' is neither a foreign key column of table ${table.tableName} " +
                "nor a to-many relation (a collection, inferred or annotated with @${ExposedReferencedBy::class.simpleName})"
        }
        return listOf(Hop(parentKey = column, childTable = target.table, childKey = target, toOne = true))
    }
}

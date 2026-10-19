package org.jetbrains.exposed.v1.spring.data.query

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ForeignKeyConstraint
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.spring.data.ExposedReferencedBy
import org.jetbrains.exposed.v1.spring.data.mapping.EntityMetadata
import org.jetbrains.exposed.v1.spring.data.mapping.TableColumns
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.data.core.PropertyPath
import org.springframework.util.ReflectionUtils
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

/**
 * A step from one table to another: the rows of [childTable] whose [childKey] matches [parentKey] of the current table.
 *
 * - to-one (a foreign key column of the current table): [parentKey] is that column, [childKey] the column it references
 * - to-many ([ExposedReferencedBy]): [parentKey] is the referenced column of the current table, [childKey] the foreign key column of [childTable]
 */
internal class Hop(val parentKey: Column<*>, val childTable: Table, val childKey: Column<*>)

/** A property path of a derived query: the [hops] to the table of the [leaf] column, which the predicate is built on. */
internal class ResolvedPath(val hops: List<Hop>, val leaf: Column<Any?>) {
    /**
     * Wraps [predicate], built on [leaf], into one `IN` subquery per hop, from the inside out:
     * `orders.id IN (SELECT order_items.order_id FROM order_items WHERE order_items.name = ?)`.
     *
     * Subqueries instead of joins keep the query on the repository's table, so every root row is returned at most once,
     * and count, exists, delete and paging work unchanged.
     */
    fun wrap(predicate: Op<Boolean>): Op<Boolean> = hops.foldRight(predicate) { hop, inner ->
        hop.parentKey inSubQuery hop.childTable.select(hop.childKey).where(inner)
    }
}

/**
 * Resolves the property paths of derived queries, starting at the repository's table:
 *
 * - the last segment is the column with the same Kotlin name on the table reached so far
 *   (on the repository's table, only mapped properties of the entity)
 * - a property annotated with [ExposedReferencedBy] is a to-many hop to the annotated table, through its foreign key to the current table
 * - any other property is a to-one hop, if the current table has a foreign key column with the same Kotlin name
 *
 * Invalid paths fail with an [IllegalArgumentException], which is reported when the repository is created.
 */
internal class PropertyPathResolver(private val metadata: EntityMetadata<*, *>) {
    private val resolved = ConcurrentHashMap<String, ResolvedPath>()

    fun resolve(path: PropertyPath): ResolvedPath = resolved.getOrPut(path.toDotPath()) { doResolve(path) }

    private fun doResolve(root: PropertyPath): ResolvedPath {
        if (!root.hasNext()) return ResolvedPath(emptyList(), metadata.requireColumn(root.segment))

        val hops = mutableListOf<Hop>()
        var table: Table = metadata.table
        var path = root
        while (path.hasNext()) {
            val hop = hop(table, path, root)
            hops += hop
            table = hop.childTable
            path = path.next()!!
        }
        val leaf = requireNotNull(TableColumns.of(table).columnFor(path.segment)) {
            "No column found for '${path.segment}' of the path '${root.toDotPath()}' in table ${table.tableName}"
        }
        return ResolvedPath(hops, leaf)
    }

    private fun hop(table: Table, path: PropertyPath, root: PropertyPath): Hop {
        referencedBy(path)?.let { return toMany(table, it, path, root) }
        val column = TableColumns.of(table).columnFor(path.segment)
        val target = column?.referee
        require(column != null && target != null) {
            "'${path.segment}' of the path '${root.toDotPath()}' is neither a foreign key column of table ${table.tableName} " +
                "nor annotated with @${ExposedReferencedBy::class.simpleName}"
        }
        return Hop(parentKey = column, childTable = target.table, childKey = target)
    }

    private fun toMany(table: Table, annotation: ExposedReferencedBy, path: PropertyPath, root: PropertyPath): Hop {
        val child = requireNotNull(annotation.table.objectInstance) {
            "@${ExposedReferencedBy::class.simpleName} on '${path.segment}' must name a table object, but ${annotation.table.qualifiedName} is not one"
        }
        val candidates = child.foreignKeys.filter { it.targetTable === table && it.references.size == 1 }
        require(candidates.isNotEmpty()) {
            "Table ${child.tableName} has no foreign key to table ${table.tableName} (path '${root.toDotPath()}')"
        }
        val foreignKey: ForeignKeyConstraint = if (annotation.column.isEmpty()) {
            require(candidates.size == 1) {
                "Table ${child.tableName} has ${candidates.size} foreign keys to table ${table.tableName}, " +
                    "set the column of @${ExposedReferencedBy::class.simpleName} on '${path.segment}' (path '${root.toDotPath()}')"
            }
            candidates.single()
        } else {
            val column = requireNotNull(TableColumns.of(child).columnFor(annotation.column)) {
                "Table ${child.tableName} has no column '${annotation.column}' (@${ExposedReferencedBy::class.simpleName} on '${path.segment}')"
            }
            requireNotNull(candidates.singleOrNull { column in it.references }) {
                "Column '${annotation.column}' of table ${child.tableName} is not a foreign key to table ${table.tableName} " +
                    "(@${ExposedReferencedBy::class.simpleName} on '${path.segment}')"
            }
        }
        val (childKey, parentKey) = foreignKey.references.entries.single()
        return Hop(parentKey = parentKey, childTable = child, childKey = childKey)
    }

    /** The annotation on the Kotlin property, or on the Java field of [PropertyPath.getSegment] in the type owning it. */
    private fun referencedBy(path: PropertyPath): ExposedReferencedBy? {
        val owner = path.owningType.type
        owner.kotlin.memberProperties.firstOrNull { it.name == path.segment }
            ?.findAnnotation<ExposedReferencedBy>()
            ?.let { return it }
        return ReflectionUtils.findField(owner, path.segment)
            ?.let { AnnotatedElementUtils.findMergedAnnotation(it, ExposedReferencedBy::class.java) }
    }
}

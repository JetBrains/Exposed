package org.jetbrains.exposed.v1.spring.data.mapping

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ForeignKeyConstraint
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.spring.data.ExposedColumn
import org.jetbrains.exposed.v1.spring.data.ExposedReferencedBy
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.data.annotation.Transient
import org.springframework.data.mapping.model.SimpleTypeHolder
import org.springframework.util.ReflectionUtils
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KType
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.jvmErasure

/**
 * A to-many relation, declared with [ExposedReferencedBy] or inferred by [inferRelation]:
 * the rows of [table] whose [foreignKey] references [parentKey] of the owning table.
 */
internal sealed class ToManyRelation {
    abstract val parentKey: Column<Any?>
    abstract val table: Table
    abstract val foreignKey: Column<Any?>
}

/** One-to-many: the rows of [table] are the elements, owned by the parent. */
internal class OwnedRelation(
    override val parentKey: Column<Any?>,
    override val table: Table,
    override val foreignKey: Column<Any?>,
) : ToManyRelation()

/** Many-to-many: [table] is a join table, the elements are the rows referenced by its column [targetKey] (through [referee]). */
internal class JoinRelation(
    override val parentKey: Column<Any?>,
    override val table: Table,
    override val foreignKey: Column<Any?>,
    val targetKey: Column<Any?>,
    val referee: Column<Any?>,
) : ToManyRelation()

/**
 * Resolves the relation of [annotation] on [property] of a type mapped to [owner]. [where] describes the property for error messages.
 * Invalid declarations fail with an [IllegalArgumentException].
 */
@Suppress("UNCHECKED_CAST")
internal fun toManyRelation(owner: Table, annotation: ExposedReferencedBy, property: String, where: String): ToManyRelation {
    val name = "@${ExposedReferencedBy::class.simpleName}"
    val child = requireNotNull(annotation.table.objectInstance) {
        "$name on '$property' must name a table object, but ${annotation.table.qualifiedName} is not one ($where)"
    }
    val candidates = child.foreignKeys.filter { it.targetTable === owner && it.references.size == 1 }
    require(candidates.isNotEmpty()) { "Table ${child.tableName} has no foreign key to table ${owner.tableName} ($where)" }
    val foreignKey: ForeignKeyConstraint = if (annotation.column.isEmpty()) {
        require(candidates.size == 1) {
            "Table ${child.tableName} has ${candidates.size} foreign keys to table ${owner.tableName}, set the column of $name on '$property' ($where)"
        }
        candidates.single()
    } else {
        val column = requireNotNull(TableColumns.of(child).columnFor(annotation.column)) {
            "Table ${child.tableName} has no column '${annotation.column}' ($name on '$property', $where)"
        }
        requireNotNull(candidates.singleOrNull { column in it.references }) {
            "Column '${annotation.column}' of table ${child.tableName} is not a foreign key to table ${owner.tableName} ($name on '$property', $where)"
        }
    }
    val (childKey, parentKey) = foreignKey.references.entries.single()
    if (annotation.target.isEmpty()) return OwnedRelation(parentKey as Column<Any?>, child, childKey as Column<Any?>)

    val targetKey = requireNotNull(TableColumns.of(child).columnFor(annotation.target)) {
        "Table ${child.tableName} has no column '${annotation.target}' (target of $name on '$property', $where)"
    }
    val referee = requireNotNull(targetKey.referee) {
        "Column '${annotation.target}' of table ${child.tableName} is not a foreign key (target of $name on '$property', $where)"
    }
    require(targetKey != childKey) { "The target of $name on '$property' is the foreign key to the owning table ($where)" }
    return JoinRelation(parentKey as Column<Any?>, child, childKey as Column<Any?>, targetKey, referee as Column<Any?>)
}

/**
 * Infers the to-many relation of [property] (a collection of [element]) of a type mapped to [owner] from the foreign keys in [registry].
 * [where] describes the property for error messages.
 *
 * An owned candidate is a table with a single-column foreign key to [owner] and columns for the stored properties of [element].
 * A join candidate is such a table with another single-column foreign key to a table with matching columns.
 *
 * For a class, only properties with backing fields count as stored. All interface properties count.
 * The matcher skips to-many collections, `Map` and `@Transient` properties. It uses [ExposedColumn] names when present.
 *
 * Returns `null` if [element] is a simple type, so the property isn't a relation. Fails with an [IllegalArgumentException]
 * if there is no candidate or more than one, the message lists them and names the attribute of [ExposedReferencedBy] to set.
 */
internal fun inferRelation(owner: Table, property: String, element: Class<*>, registry: ExposedTableRegistry, where: String): ToManyRelation? {
    if (!isReferenceType(element)) return null
    val candidates = registry.incoming(owner).flatMap { foreignKey ->
        val child = foreignKey.childColumn.table
        val owned = if (fits(element, child)) listOf(Candidate(foreignKey, null)) else emptyList()
        val joins = child.foreignKeys
            .filter { it.references.size == 1 && it.childColumn !== foreignKey.childColumn && fits(element, it.parentColumn.table) }
            .map { Candidate(foreignKey, it) }
        owned + joins
    }
    val annotation = "@${ExposedReferencedBy::class.simpleName}"
    require(candidates.isNotEmpty()) {
        "$where is not mapped: no known table has a foreign key to ${owner.tableName} whose rows, or the rows they reference, " +
            "have a column for every property of ${element.simpleName}. Annotate '$property' with $annotation(<Table>::class), " +
            "or make sure the table is in a scanned package"
    }
    require(candidates.size == 1) { ambiguity(where, owner, annotation, candidates) }
    return candidates.single().toRelation()
}

/** A relation found by [inferRelation]: the rows of the table of [foreignKey], or for a join table the rows referenced by its [target]. */
private class Candidate(val foreignKey: ForeignKeyConstraint, val target: ForeignKeyConstraint?) {
    val table: Table get() = foreignKey.childColumn.table

    override fun toString(): String = if (target == null) {
        "${kotlinName(foreignKey.childColumn)} (one-to-many)"
    } else {
        "${kotlinName(foreignKey.childColumn)} with target ${kotlinName(target.childColumn)} (many-to-many to ${target.parentColumn.table.tableName})"
    }

    @Suppress("UNCHECKED_CAST")
    fun toRelation(): ToManyRelation {
        val parentKey = foreignKey.parentColumn as Column<Any?>
        val childKey = foreignKey.childColumn as Column<Any?>
        if (target == null) return OwnedRelation(parentKey, table, childKey)
        return JoinRelation(parentKey, table, childKey, target.childColumn as Column<Any?>, target.parentColumn as Column<Any?>)
    }
}

private fun ambiguity(where: String, owner: Table, annotation: String, candidates: List<Candidate>): String {
    val byTable = candidates.map { it.table }.distinctBy { System.identityHashCode(it) }.map { table -> table to candidates.filter { it.table === table } }
    val needsColumn = byTable.any { (_, inTable) -> inTable.map { it.foreignKey.childColumn }.distinct().size > 1 }
    val needsTarget = byTable.any { (_, inTable) -> inTable.groupBy { it.foreignKey.childColumn }.values.any { it.size > 1 } }
    val arguments = listOfNotNull(
        byTable.joinToString(" or ") { (table, _) -> "${table::class.simpleName}::class" },
        "column = \"...\"".takeIf { needsColumn },
        "target = \"...\"".takeIf { needsTarget },
    )
    val missing = listOfNotNull("table".takeIf { byTable.size > 1 }, "column".takeIf { needsColumn }, "target".takeIf { needsTarget })
    return "$where is ambiguous, these relations to ${owner.tableName} fit: ${candidates.joinToString()}. " +
        "Annotate it with $annotation(${arguments.joinToString()}) to choose one (set the ${missing.joinToString(" and ")})"
}

private val ForeignKeyConstraint.childColumn: Column<*> get() = references.keys.single()

private val ForeignKeyConstraint.parentColumn: Column<*> get() = references.values.single()

/** `Table.property`, the Kotlin names of the table object and the column, as used in [ExposedReferencedBy]. */
private fun kotlinName(column: Column<*>): String {
    val property = TableColumns.of(column.table).columns.entries.firstOrNull { it.value === column }?.key ?: column.name
    return "${column.table::class.simpleName}.$property"
}

/** Whether every property of [element] (except to-many and transient ones) has a column in [table]. */
private fun fits(element: Class<*>, table: Table): Boolean =
    elementProperties(element).all { runCatching { columnFor(table, element, it) }.getOrNull() != null }

private val elementProperties = ConcurrentHashMap<Class<*>, List<String>>()

/** The stored properties of [type]: all properties of an interface, those with a backing field of a class. */
private fun elementProperties(type: Class<*>): List<String> = elementProperties.getOrPut(type) {
    runCatching { type.kotlin.memberProperties.toList() }.getOrDefault(emptyList())
        .filter { type.isInterface || it.javaField != null }
        .filter { toManyElement(it.returnType) == null && !Map::class.java.isAssignableFrom(it.returnType.jvmErasure.java) }
        .filter { findPropertyAnnotation(type, it.name, Transient::class.java) == null }
        .map { it.name }
}

/** The element class of [type] if it is a `List`, `Set`, `Collection` or `Iterable`, otherwise `null`. */
internal fun toManyElement(type: KType): Class<*>? {
    val raw = type.jvmErasure
    if (raw != List::class && raw != Set::class && raw != Collection::class && raw != Iterable::class) return null
    return type.arguments.singleOrNull()?.type?.jvmErasure?.java
}

/**
 * The column of [table] for [property] of [owner]: the column named by [ExposedColumn], or the one with the same Kotlin name.
 * A name in [ExposedColumn] that isn't a column fails with an [IllegalArgumentException].
 */
internal fun columnFor(table: Table, owner: Class<*>, property: String): Column<Any?>? {
    val explicit = findPropertyAnnotation(owner, property, ExposedColumn::class.java)?.name ?: return TableColumns.of(table).columnFor(property)
    return requireNotNull(TableColumns.of(table).columnFor(explicit)) {
        "@${ExposedColumn::class.simpleName}(\"$explicit\") on ${owner.simpleName}.$property: table ${table.tableName} has no such column"
    }
}

/**
 * Whether a property of this type, stored in a foreign key column, holds the referenced row as an object (e.g. `customer: Customer`)
 * instead of the key value (e.g. `customer: Long`, an `EntityID` or a value class).
 */
internal fun isReferenceType(type: Class<*>): Boolean = !(
    type.isPrimitive ||
        type.isArray ||
        type.isEnum ||
        type == EntityID::class.java ||
        type.name.startsWith("java.") ||
        type.name.startsWith("kotlin.") ||
        SimpleTypeHolder.DEFAULT.isSimpleType(type) ||
        Collection::class.java.isAssignableFrom(type) ||
        Map::class.java.isAssignableFrom(type) ||
        runCatching { type.kotlin.isValue }.getOrDefault(false)
    )

/** The element class of the collection type [type] of a to-many property, and whether it is a `Set`. */
internal fun collectionElement(type: KType, where: String): Pair<Class<*>, Boolean> {
    val raw = type.jvmErasure
    val isSet = raw == Set::class
    require(isSet || raw == List::class || raw == Collection::class || raw == Iterable::class) {
        "$where must be a List, Set, Collection or Iterable to hold the rows of a to-many relation, but is $type"
    }
    val element = requireNotNull(type.arguments.singleOrNull()?.type) { "$where needs an element type, but is $type" }
    return element.jvmErasure.java to isSet
}

private data class AnnotationKey(val owner: Class<*>, val property: String, val type: Class<*>)

private val annotations = ConcurrentHashMap<AnnotationKey, Any>()
private val none = Any()

/** The annotation on the Kotlin property [property] of [owner] (also its getter), or on the Java field or getter. */
internal fun <A : Annotation> findPropertyAnnotation(owner: Class<*>, property: String, type: Class<A>): A? {
    val found = annotations.getOrPut(AnnotationKey(owner, property, type)) { lookUp(owner, property, type) ?: none }
    return if (found === none) null else type.cast(found)
}

private fun <A : Annotation> lookUp(owner: Class<*>, property: String, type: Class<A>): A? {
    val kotlinProperty = runCatching { owner.kotlin.memberProperties.firstOrNull { it.name == property } }.getOrNull()
    kotlinProperty?.let { p ->
        (p.annotations + p.getter.annotations).firstOrNull { type.isInstance(it) }?.let { return type.cast(it) }
        p.javaGetter?.let { AnnotatedElementUtils.findMergedAnnotation(it, type) }?.let { return it }
    }
    return ReflectionUtils.findField(owner, property)?.let { AnnotatedElementUtils.findMergedAnnotation(it, type) }
}

internal inline fun <reified A : Annotation> Class<*>.propertyAnnotation(property: String): A? = findPropertyAnnotation(this, property, A::class.java)

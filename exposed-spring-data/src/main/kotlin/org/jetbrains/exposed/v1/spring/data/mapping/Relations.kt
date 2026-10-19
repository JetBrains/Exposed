package org.jetbrains.exposed.v1.spring.data.mapping

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ForeignKeyConstraint
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.spring.data.ExposedColumn
import org.jetbrains.exposed.v1.spring.data.ExposedReferencedBy
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.data.mapping.model.SimpleTypeHolder
import org.springframework.util.ReflectionUtils
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KType
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.jvmErasure

/** A to-many relation declared with [ExposedReferencedBy]: the rows of [table] whose [foreignKey] references [parentKey] of the owning table. */
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

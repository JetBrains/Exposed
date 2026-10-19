package org.jetbrains.exposed.v1.spring.data.mapping

import org.jetbrains.exposed.v1.core.AbstractQuery
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.isAutoInc
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.statements.InsertStatement
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.spring.data.ExposedReferencedBy
import kotlin.reflect.KType
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.jvmErasure

/**
 * Writes what an object of [type] stored in [table] holds besides its plain column values:
 *
 * - **to-one references** (a [reference type][isReferenceType] in a foreign key column, e.g. `customer: Customer`): [cascade] saves
 *   referenced entities through their repository (inserted if they are new, otherwise updated), [dbValue] stores their key.
 * - **owned to-many rows** ([ExposedReferencedBy] without a target): [syncCollections] makes the rows of the relation match the collection,
 *   [deleteOwned] deletes them with their parent. Many-to-many relations are only read, so they are ignored here.
 *
 * Rows are matched by the primary key of their table, if the type maps all of its columns: missing rows are inserted, existing ones updated
 * and rows that are no longer in the collection deleted, so rows keep their identity. Without such a key, rows are matched by all their
 * values: only rows that aren't in the collection any more are deleted, and only new values inserted.
 */
internal class AggregateWriter(private val table: Table, private val type: Class<*>, private val mappings: ExposedMappings) {
    private val entity = mappings.persistentEntity(type)

    private val propertyTypes: Map<String, KType> =
        runCatching { type.kotlin.memberProperties.associate { it.name to it.returnType } }.getOrDefault(emptyMap())

    private inner class Reference(val property: ExposedPersistentProperty, val referee: Column<Any?>, val target: Class<*>) {
        private val refereeTable = referee.table
        private val cascades = refereeTable is IdTable<*> && referee == refereeTable.id && mappings.isEntityOf(refereeTable, target)

        fun save(value: Any): Any = if (cascades) mappings.repository(refereeTable as IdTable<*>, target).save(value) else value

        fun key(value: Any): Any? = if (cascades) {
            val metadata = mappings.resolver(refereeTable as IdTable<*>, target).metadata
            metadata.valueOf(value, metadata.idProperty)
        } else {
            mappings.writer(refereeTable, target).columnValue(value, referee)
        }
    }

    private class OwnedCollection(val property: ExposedPersistentProperty, val relation: OwnedRelation, val element: Class<*>)

    private val columns: Map<ExposedPersistentProperty, Column<Any?>> = entity
        .filter { type.propertyAnnotation<ExposedReferencedBy>(it.name) == null }
        .mapNotNull { property -> columnFor(table, type, property.name)?.let { property to it } }
        .toMap()

    private val references: Map<String, Reference> = columns.mapNotNull { (property, column) ->
        val referee = column.referee ?: return@mapNotNull null
        val target = propertyTypes[property.name]?.jvmErasure?.java ?: property.type
        if (!isReferenceType(target)) return@mapNotNull null
        @Suppress("UNCHECKED_CAST")
        property.name to Reference(property, referee as Column<Any?>, target)
    }.toMap()

    private val collections: List<OwnedCollection> = entity.mapNotNull { property ->
        val annotation = type.propertyAnnotation<ExposedReferencedBy>(property.name) ?: return@mapNotNull null
        val where = "${type.simpleName}.${property.name}"
        val relation = toManyRelation(table, annotation, property.name, where) as? OwnedRelation ?: return@mapNotNull null
        val elementType = requireNotNull(propertyTypes[property.name]) { "$where has no Kotlin type" }
        OwnedCollection(property, relation, collectionElement(elementType, where).first)
    }

    /** Whether the type has owned to-many rows, which have to be written and deleted with it. */
    val ownsRows: Boolean get() = collections.isNotEmpty()

    /** Saves the referenced entities of [obj] and returns [obj] with the saved ones (a copy, for immutable types). */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> cascade(obj: T): T {
        if (references.isEmpty()) return obj
        val accessor = entity.getPropertyAccessor(obj)
        references.values.forEach { reference ->
            accessor.getProperty(reference.property)?.let { accessor.setProperty(reference.property, reference.save(it)) }
        }
        return accessor.bean as T
    }

    /** The value to store for [property] with the [value]: the key for references, the value itself otherwise. */
    fun dbValue(property: String, value: Any?): Any? {
        val reference = references[property] ?: return value
        return value?.let { requireNotNull(reference.key(it)) { "The ${type.simpleName}.$property to store has no key: $it" } }
    }

    /** The value [obj] stores in [column], or `null` if no property is stored in it. */
    fun columnValue(obj: Any, column: Column<*>): Any? {
        val property = columns.entries.firstOrNull { it.value == column }?.key ?: return null
        return dbValue(property.name, entity.getPropertyAccessor(obj).getProperty(property))
    }

    /**
     * Makes the owned to-many rows of every relation match the collections of [obj], which is stored in a row whose key values
     * come from [keyOf], or from [obj] itself if [keyOf] returns `null`.
     */
    fun syncCollections(obj: Any, keyOf: (Column<*>) -> Any? = { null }) {
        if (collections.isEmpty()) return
        val accessor = entity.getPropertyAccessor(obj)
        collections.forEach { collection ->
            val key = keyOf(collection.relation.parentKey)?.unwrapEntityId() ?: columnValue(obj, collection.relation.parentKey)?.unwrapEntityId()
            val elements = accessor.getProperty(collection.property) as Iterable<*>?
            val parentKey = requireNotNull(key) {
                "${type.simpleName} has no value for ${collection.relation.parentKey.name}, its ${collection.property.name} can't be saved"
            }
            mappings.writer(collection.relation.table, collection.element).sync(collection.relation, parentKey, elements?.filterNotNull().orEmpty())
        }
    }

    /** Deletes the owned to-many rows (and theirs) of the parent rows, whose values of a column [parentRows] selects. */
    fun deleteOwned(parentRows: (Column<*>) -> AbstractQuery<*>) {
        collections.forEach { collection ->
            val relation = collection.relation
            val ofParents = { relation.foreignKey inSubQuery parentRows(relation.parentKey) }
            mappings.writer(relation.table, collection.element).deleteOwned { column -> relation.table.select(column).where(ofParents()) }
            relation.table.deleteWhere { ofParents() }
        }
    }

    /** Makes the rows of [relation] (stored in [table]) of the parent with the key [parentKey] match [elements]. */
    private fun sync(relation: OwnedRelation, parentKey: Any, elements: List<Any>) {
        val foreignKey = relation.foreignKey
        val existing = table.selectAll().where { foreignKey eq foreignKey.toDbValue(parentKey) }.toList()
        val desired = elements.map { element ->
            val saved = cascade(element)
            val accessor = entity.getPropertyAccessor(saved)
            val values = columns.entries.associate { (property, column) ->
                column to column.toDbValue(dbValue(property.name, accessor.getProperty(property)))
            } + (foreignKey to foreignKey.toDbValue(parentKey))
            saved to values
        }
        val identity = table.primaryKey?.columns?.toList()?.takeIf { key -> desired.isEmpty() || key.all { it in desired.first().second } }
        if (identity != null) syncByKey(identity, existing, desired) else syncByValues(foreignKey, existing, desired)
    }

    private fun syncByKey(identity: List<Column<*>>, existing: List<ResultRow>, desired: List<Pair<Any, Map<Column<Any?>, Any?>>>) {
        fun keyOf(values: (Column<*>) -> Any?) = identity.map { values(it).unwrapEntityId() }
        val existingByKey = existing.associateBy { row -> keyOf { row[it] } }
        val kept = HashSet<List<Any?>>()
        desired.forEach { (element, values) ->
            val key = keyOf { values[it] }
            val known = key.none { it == null }
            val updated = known && run {
                kept += key
                val changed = values.filterKeys { it !in identity }
                changed.isEmpty() && existingByKey.containsKey(key) || changed.isNotEmpty() && table.update({ matches(identity, key) }) { it.fill(changed) } > 0
            }
            val inserted: InsertStatement<*>? = if (updated) null else table.insert { it.fill(values) }
            val row = existingByKey[key]
            syncCollections(element) { column ->
                values[column] ?: row?.getOrNull(column) ?: inserted?.let { runCatching { it[column] }.getOrNull() }
            }
        }
        existing.filter { keyOf { column -> it[column] } !in kept }.forEach { row ->
            val where = matches(identity, keyOf { row[it] })
            deleteOwned { column -> table.select(column).where(where) }
            table.deleteWhere { where }
        }
    }

    private fun syncByValues(foreignKey: Column<Any?>, existing: List<ResultRow>, desired: List<Pair<Any, Map<Column<Any?>, Any?>>>) {
        check(collections.isEmpty()) {
            "The rows of ${table.tableName} have no primary key mapped by ${type.simpleName}, so its to-many properties can't be saved"
        }
        val compared = (columns.values + foreignKey).distinct()
        fun tuple(values: (Column<*>) -> Any?) = compared.map { values(it).unwrapEntityId() }
        val existingCounts = existing.groupingBy { row -> tuple { row[it] } }.eachCount()
        val desiredByTuple = desired.map { it.second }.groupBy { values -> tuple { values[it] } }
        (existingCounts.keys + desiredByTuple.keys).forEach { values ->
            val have = existingCounts[values] ?: 0
            val wanted = desiredByTuple[values].orEmpty()
            when {
                wanted.size > have -> wanted.drop(have).forEach { row -> table.insert { it.fill(row) } }
                wanted.size < have -> {
                    // identical rows can't be told apart, so all of them are deleted and the wanted number inserted again
                    table.deleteWhere { matches(compared, values) }
                    wanted.forEach { row -> table.insert { it.fill(row) } }
                }
            }
        }
    }

    private fun UpdateBuilder<*>.fill(values: Map<Column<Any?>, Any?>) {
        values.forEach { (column, value) ->
            // leave generated and default values to the database
            if (value != null || column.columnType.nullable && !column.columnType.isAutoInc) this[column] = value
        }
    }

    private companion object {
        @Suppress("UNCHECKED_CAST")
        fun matches(columns: List<Column<*>>, values: List<Any?>): Op<Boolean> = columns.zip(values).map { (column, value) ->
            val typed = column as Column<Any?>
            if (value == null) typed.isNull() else typed eq typed.toDbValue(value)
        }.reduce { acc, op -> acc and op }

        fun ResultRow.getOrNull(column: Column<*>): Any? = if (fieldIndex.containsKey(column)) this[column] else null
    }
}

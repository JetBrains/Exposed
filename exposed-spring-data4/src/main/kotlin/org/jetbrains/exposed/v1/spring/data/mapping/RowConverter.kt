package org.jetbrains.exposed.v1.spring.data.mapping

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.spring.data.ExposedColumn
import org.jetbrains.exposed.v1.spring.data.ExposedReferencedBy
import org.springframework.data.mapping.Parameter
import org.springframework.data.mapping.PreferredConstructor
import org.springframework.data.mapping.model.EntityInstantiators
import org.springframework.data.mapping.model.ParameterValueProvider
import org.springframework.data.projection.ProjectionFactory
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.KVisibility
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.jvm.kotlinFunction

/**
 * Turns [ResultRow]s into [R]. Instances are created and cached by [ProjectionResolver] and [ExposedMappings].
 *
 * Each property is read by a [ValueReader]. Referenced rows and to-many rows are loaded for all rows of [convertAll] at once,
 * with one query per relation (and chunk of keys), so converting many rows doesn't run a query per row.
 */
internal sealed class RowConverter<out R : Any>(private val targetType: Class<*>) {
    abstract val columns: List<Column<*>>
    abstract val narrowsSelect: Boolean
    protected open val requiredColumns: List<Column<*>> get() = columns

    /** The readers by property name, passed to [read] as a function of the property name. */
    protected open val readers: Map<String, ValueReader> get() = emptyMap()

    fun convert(row: ResultRow): R = convertAll(listOf(row)).single()

    open fun convertAll(rows: List<ResultRow>): List<R> {
        if (rows.isEmpty()) return emptyList()
        rows.forEach { row ->
            val missing = requiredColumns.filterNot { row.fieldIndex.containsKey(it) }
            check(missing.isEmpty()) {
                "Can't convert the row to ${targetType.simpleName}, these columns are not selected: ${missing.joinToString { it.name }}"
            }
        }
        val values = readers.mapValues { (_, reader) -> reader.prepare(rows) }
        return rows.map { row -> read(row) { property -> values.getValue(property)(row) } }
    }

    protected abstract fun read(row: ResultRow, value: (String) -> Any?): R
}

/** Returns the row itself. Selects every column of the table, but accepts rows with any columns. */
internal class RawRowConverter(override val columns: List<Column<*>>) : RowConverter<ResultRow>(ResultRow::class.java) {
    override val narrowsSelect: Boolean = false
    override val requiredColumns: List<Column<*>> = emptyList()

    override fun read(row: ResultRow, value: (String) -> Any?): ResultRow = row
}

/**
 * Creates the entity through the [ExposedMappingContext], with Spring Data's instantiation rules: the preferred constructor
 * (`@PersistenceCreator` or the primary constructor, with Kotlin default values) gets the values of its parameters,
 * and mapped properties that aren't constructor parameters are set afterwards. The created instance is passed to [afterConvert],
 * and its result is what [read] returns. How each property is read is decided by [readerFor].
 */
internal class EntityConverter<T : Any>(
    private val metadata: EntityMetadata<T, *>,
    instantiators: EntityInstantiators,
    mappings: ExposedMappings,
    private val afterConvert: (T) -> T = { it },
) : RowConverter<T>(metadata.domainType.java) {
    private val entity = metadata.persistentEntity
    private val instantiator = instantiators.getInstantiatorFor(entity)
    private val source = EntitySource(metadata, mappings)
    private val owner = metadata.domainType.java

    private val parameterReaders: Map<String, ValueReader> = run {
        val creator = checkNotNull(entity.instanceCreatorMetadata) { "${metadata.domainType.simpleName} has no constructor to create it with" }
        val kotlinParameters = (creator as? PreferredConstructor<*, *>)?.constructor?.kotlinFunction?.parameters.orEmpty()
        val optional = kotlinParameters.filter { it.isOptional }.mapNotNull { it.name }
        creator.parameters.mapNotNull { parameter ->
            val name = checkNotNull(parameter.name) { "${metadata.domainType.simpleName} has an unnamed constructor parameter" }
            val type = kotlinParameters.firstOrNull { it.name == name }?.type ?: metadata.propertyType(name)
            val reader = source.readerFor(owner, name, type)
            when {
                reader != null -> name to reader
                name in optional -> null
                else -> error("No column found for ${metadata.domainType.simpleName}.$name in table ${metadata.table.tableName}")
            }
        }.toMap()
    }

    private val populatedProperties: Map<ExposedPersistentProperty, ValueReader> = entity
        .filter { !entity.isCreatorArgument(it) && it.isWritable }
        .mapNotNull { property -> source.readerFor(owner, property.name, metadata.propertyType(property.name))?.let { property to it } }
        .toMap()

    override val readers: Map<String, ValueReader> = parameterReaders + populatedProperties.mapKeys { it.key.name }
    override val columns: List<Column<*>> = readers.values.flatMap { it.columns }.distinct()
    override val narrowsSelect: Boolean = false

    override fun read(row: ResultRow, value: (String) -> Any?): T = afterConvert(instantiate(value))

    private fun instantiate(value: (String) -> Any?): T {
        val provider = object : ParameterValueProvider<ExposedPersistentProperty> {
            @Suppress("UNCHECKED_CAST")
            override fun <V : Any> getParameterValue(parameter: Parameter<V, ExposedPersistentProperty>): V? =
                parameter.name?.takeIf { it in parameterReaders }?.let(value) as V?
        }
        val instance: T = instantiator.createInstance(entity, provider)
        if (populatedProperties.isEmpty()) return instance
        val accessor = entity.getPropertyAccessor(instance)
        populatedProperties.keys.forEach { property -> accessor.setProperty(property, value(property.name)) }
        return accessor.bean
    }
}

/**
 * Calls the primary constructor of the DTO [type] with the values of its parameters, read from [source]:
 * optional parameters without a column are left out, and every value must fit the entity property it comes from.
 */
internal class ConstructorConverter<R : Any>(
    type: KClass<R>,
    source: ColumnSource,
) : RowConverter<R>(type.java) {
    override val narrowsSelect: Boolean = true

    private val constructor = checkNotNull(type.primaryConstructor) { "${type.qualifiedName} needs a primary constructor" }.also {
        require(it.visibility == KVisibility.PUBLIC) { "The primary constructor of ${type.qualifiedName} must be public" }
    }

    private val parameterReaders: Map<KParameter, Pair<String, ValueReader>> = constructor.parameters.mapNotNull { parameter ->
        val name = checkNotNull(parameter.name) { "${type.simpleName} has an unnamed constructor parameter" }
        val reader = source.readerFor(type.java, name, parameter.type)
        when {
            reader != null -> parameter to (name to reader)
            parameter.isOptional -> null
            else -> error("No column found for ${type.simpleName}.$name in table ${source.table.tableName}")
        }
    }.toMap()

    override val readers: Map<String, ValueReader> = parameterReaders.values.toMap()
    override val columns: List<Column<*>> = readers.values.flatMap { it.columns }.distinct()

    override fun read(row: ResultRow, value: (String) -> Any?): R =
        constructor.callBy(parameterReaders.mapValues { (_, reader) -> value(reader.first) })
}

/** Creates a closed interface projection backed by a map of the projected property values, read from [source]. */
internal class InterfaceConverter<R : Any>(
    private val type: Class<R>,
    private val factory: ProjectionFactory,
    source: ColumnSource,
) : RowConverter<R>(type) {
    override val readers: Map<String, ValueReader> = factory.getProjectionInformation(type)
        .also { require(it.isClosed) { "Open projections are not supported: ${type.name}" } }
        .inputProperties
        .associate { descriptor ->
            val getter = checkNotNull(descriptor.readMethod) { "${type.simpleName}.${descriptor.name} has no getter" }
            val propertyType = getter.kotlinFunction?.returnType
                ?: type.kotlin.memberProperties.firstOrNull { it.name == descriptor.name }?.returnType
                ?: descriptor.propertyType.kotlin.starProjectedType
            val reader = checkNotNull(source.readerFor(type, descriptor.name, propertyType)) {
                "No column found for ${type.simpleName}.${descriptor.name} in table ${source.table.tableName}"
            }
            descriptor.name to reader
        }

    override val columns: List<Column<*>> = readers.values.flatMap { it.columns }.distinct()
    override val narrowsSelect: Boolean = true

    override fun read(row: ResultRow, value: (String) -> Any?): R = factory.createProjection(type, readers.keys.associateWith(value))
}

/**
 * How the properties of a projection are found in [table]. Properties of every projection can name their column with
 * `@ExposedColumn` and declare to-many relations with `@ExposedReferencedBy` themselves, see [readerFor].
 */
internal abstract class ColumnSource(val table: Table, val mappings: ExposedMappings) {
    /** The column of [property], if the projection doesn't name one. */
    abstract fun columnFor(property: String): Column<Any?>?

    /** The to-many relation of [property], if the projection doesn't declare one. */
    open fun referencedBy(property: String): ExposedReferencedBy? = null

    /** Checks that a plain column value of [property] fits [type]. */
    open fun checkType(owner: Class<*>, property: String, type: KType) = Unit
}

/** The properties of projections of the repository's entity are those of the entity: their columns, types and relations. */
internal class EntitySource(private val metadata: EntityMetadata<*, *>, mappings: ExposedMappings) : ColumnSource(metadata.table, mappings) {
    override fun columnFor(property: String): Column<Any?>? = metadata.columnFor(property)

    override fun referencedBy(property: String): ExposedReferencedBy? = metadata.domainType.java.propertyAnnotation<ExposedReferencedBy>(property)

    override fun checkType(owner: Class<*>, property: String, type: KType) {
        if (owner == metadata.domainType.java) return
        val source = checkNotNull(metadata.propertyType(property)) { "No entity property '$property' for ${owner.simpleName}.$property" }
        require(source.jvmErasure.isSubclassOf(type.jvmErasure)) {
            "${owner.simpleName}.$property has type $type, but the entity property has type $source"
        }
    }
}

/** Nested types (referenced rows, to-many rows) are mapped to the columns of their table by name. */
internal class TableSource(table: Table, mappings: ExposedMappings) : ColumnSource(table, mappings) {
    override fun columnFor(property: String): Column<Any?>? = TableColumns.of(table).columnFor(property)
}

/**
 * The reader of [property] of the projection [owner] with the type [type], or `null` if it has no column:
 *
 * - annotated with `@ExposedReferencedBy` (on [owner] or, through [ColumnSource.referencedBy], on the entity):
 *   the rows of the relation, as a collection of the element type
 * - a foreign key column and a [reference type][isReferenceType] (e.g. `customer: Customer`): the referenced row, converted to [type]
 * - otherwise the column value (e.g. `customer: Long` for the same foreign key column)
 */
internal fun ColumnSource.readerFor(owner: Class<*>, property: String, type: KType?): ValueReader? {
    val where = "${owner.simpleName}.$property"
    val referencedBy = owner.propertyAnnotation<ExposedReferencedBy>(property) ?: referencedBy(property)
    if (referencedBy != null) {
        val relation = toManyRelation(table, referencedBy, property, where)
        val (element, isSet) = collectionElement(requireNotNull(type) { "$where has no type" }, where)
        return when (relation) {
            is OwnedRelation -> {
                val converter = mappings.converter(relation.table, element)
                val order = relation.table.primaryKey?.columns?.toList().orEmpty()
                CollectionReader(relation.parentKey, isSet) { keys -> loadByKey(relation.foreignKey, keys, converter, order) }
            }
            is JoinRelation -> {
                val converter = mappings.converter(relation.referee.table, element)
                CollectionReader(relation.parentKey, isSet) { keys -> loadThroughJoinTable(relation, keys, converter) }
            }
        }
    }
    val explicit = owner.propertyAnnotation<ExposedColumn>(property) != null
    val column = (if (explicit) columnFor(table, owner, property) else columnFor(property)) ?: return null
    val referee = column.referee
    val target = type?.jvmErasure?.java
    if (referee != null && target != null && isReferenceType(target)) {
        return ReferenceReader(column, referee, mappings.converter(referee.table, target))
    }
    if (!explicit && referee == null && type != null) checkType(owner, property, type)
    return ColumnReader(column)
}

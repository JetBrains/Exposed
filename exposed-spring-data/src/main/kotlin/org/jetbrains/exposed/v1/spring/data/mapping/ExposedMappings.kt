package org.jetbrains.exposed.v1.spring.data.mapping

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.spring.data.event.EntityLifecycle
import org.jetbrains.exposed.v1.spring.data.repository.SimpleExposedRepository
import org.springframework.data.projection.ProjectionFactory
import org.springframework.data.projection.SpelAwareProxyProjectionFactory
import org.springframework.data.repository.core.support.PersistentEntityInformation
import java.util.concurrent.ConcurrentHashMap

/**
 * The mappings of all types to tables of one repository factory, shared by its repositories, so nested types can be read and written:
 *
 * - [resolver] and [repository]: the entity [type][Class] stored in an [IdTable], as if it had its own repository
 *   (with the same callbacks, events and auditing). Referenced entities are saved through [repository].
 * - [converter]: reads rows of any table as a nested type (an entity, a DTO or an interface projection)
 * - [writer]: writes the to-one references and the owned to-many rows of a type, see [AggregateWriter]
 */
internal class ExposedMappings(
    val mappingContext: ExposedMappingContext = ExposedMappingContext(),
    val projectionFactory: ProjectionFactory = SpelAwareProxyProjectionFactory(),
    private val lifecycle: (IdTable<Any>) -> EntityLifecycle<Any> = { EntityLifecycle(it) },
    private val keepCreatedOnUpdate: Boolean = false,
) {
    /** Tables are equal if their names are, so they are compared by identity here. */
    private class Key(val table: Table, val type: Class<*>) {
        override fun equals(other: Any?): Boolean = other is Key && other.table === table && other.type == type

        override fun hashCode(): Int = System.identityHashCode(table) * 31 + type.hashCode()
    }

    private val resolvers = ConcurrentHashMap<Key, ProjectionResolver<Any, Any>>()
    private val repositories = ConcurrentHashMap<Key, SimpleExposedRepository<Any, Any>>()
    private val converters = ConcurrentHashMap<Key, RowConverter<*>>()
    private val writers = ConcurrentHashMap<Key, AggregateWriter>()

    /** The converters being created on this thread, to break cycles of types referencing each other (e.g. `Employee.manager: Employee`). */
    private val creating = ThreadLocal.withInitial { mutableSetOf<Key>() }

    @Suppress("UNCHECKED_CAST")
    fun persistentEntity(type: Class<*>): ExposedPersistentEntity<Any> = mappingContext.getRequiredPersistentEntity(type) as ExposedPersistentEntity<Any>

    @Suppress("UNCHECKED_CAST")
    fun resolver(table: IdTable<*>, type: Class<*>): ProjectionResolver<Any, Any> = resolvers.getOrPut(Key(table, type)) {
        val idTable = table as IdTable<Any>
        ProjectionResolver(
            metadata = EntityMetadata(idTable, persistentEntity(type)),
            factory = projectionFactory,
            instantiators = mappingContext.instantiators,
            lifecycle = lifecycle(idTable),
            keepCreatedOnUpdate = keepCreatedOnUpdate,
            mappings = this,
        )
    }

    fun repository(table: IdTable<*>, type: Class<*>): SimpleExposedRepository<Any, Any> = repositories.getOrPut(Key(table, type)) {
        val resolver = resolver(table, type)
        SimpleExposedRepository(PersistentEntityInformation(resolver.metadata.persistentEntity), resolver)
    }

    /** Whether [type] is an entity stored in [table]: its id property is stored in the id column. */
    fun isEntityOf(table: Table, type: Class<*>): Boolean {
        if (table !is IdTable<*> || type.isInterface || !isReferenceType(type)) return false
        val id = persistentEntity(type).idProperty ?: return false
        return columnFor(table, type, id.name) == table.id
    }

    /** Reads rows of [table] as [type]: an entity of the table, a DTO or a closed interface projection, mapped by property names. */
    fun converter(table: Table, type: Class<*>): RowConverter<*> {
        val key = Key(table, type)
        converters[key]?.let { return it }
        val inProgress = creating.get()
        if (key in inProgress) return LazyConverter(type) { checkNotNull(converters[key]) { "No converter for ${type.name} of ${table.tableName}" } }
        inProgress += key
        try {
            return converters.getOrPut(key) { create(table, type) }
        } finally {
            inProgress -= key
        }
    }

    private fun create(table: Table, type: Class<*>): RowConverter<*> = when {
        type.isInterface -> InterfaceConverter(type, projectionFactory, TableSource(table, this))
        table is IdTable<*> && isEntityOf(table, type) -> resolver(table, type).entityConverter
        isReferenceType(type) -> ConstructorConverter(type.kotlin, TableSource(table, this))
        else -> error("Rows of table ${table.tableName} can't be converted to ${type.name}, it needs to be a class or an interface with properties")
    }

    fun writer(table: Table, type: Class<*>): AggregateWriter = writers.getOrPut(Key(table, type)) { AggregateWriter(table, type, this) }
}

/** Stands in for a converter that is still being created (a cycle of nested types), and delegates to it once it's needed. */
private class LazyConverter(type: Class<*>, target: () -> RowConverter<*>) : RowConverter<Any>(type) {
    private val target by lazy(target)

    override val columns: List<Column<*>> get() = target.columns
    override val narrowsSelect: Boolean get() = target.narrowsSelect
    override val requiredColumns: List<Column<*>> get() = emptyList()

    override fun convertAll(rows: List<ResultRow>): List<Any> = target.convertAll(rows)

    override fun read(row: ResultRow, value: (String) -> Any?): Any = error("Not used, see convertAll")
}

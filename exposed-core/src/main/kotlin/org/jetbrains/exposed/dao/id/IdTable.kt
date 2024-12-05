package org.jetbrains.exposed.dao.id

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.wrap
import java.util.*

/** Base class representing a producer of [EntityID] instances.  */
interface EntityIDFactory {
    /** Returns a new [EntityID] that holds a [value] of type [T], for the specified [table]. */
    fun <T : Any> createEntityID(value: T, table: IdTable<T>): EntityID<T>
}

/** Class responsible for locating and providing the appropriate functions to produce [EntityID] instances. */
object EntityIDFunctionProvider {
    private val factory: EntityIDFactory
    init {
        factory = ServiceLoader.load(EntityIDFactory::class.java, EntityIDFactory::class.java.classLoader).firstOrNull()
            ?: object : EntityIDFactory {
                override fun <T : Any> createEntityID(value: T, table: IdTable<T>): EntityID<T> {
                    return EntityID(value, table)
                }
            }
    }

    /** Returns a new [EntityID] that holds a [value] of type [T], for the specified [table]. */
    fun <T : Any> createEntityID(value: T, table: IdTable<T>) = factory.createEntityID(value, table)
}

/**
 * Base class for an identity table, which could be referenced from other tables.
 *
 * @param name Table name. By default, this will be resolved from any class name with a "Table" suffix removed (if present).
 */
abstract class IdTable<T : Any>(name: String = "") : Table(name) {
    /** The identity column of this [IdTable], for storing values of type [T] wrapped as [EntityID] instances. */
    abstract val id: Column<EntityID<T>>

    private val _idColumns = HashSet<Column<out Any>>()

    /** All base columns that make up this [IdTable]'s identifier column. */
    val idColumns: Set<Column<out Any>>
        get() = _idColumns.ifEmpty {
            val message = "Table definition must include id columns. Please use Column.entityId() or IdTable.addIdColumn()."
            exposedLogger.error(message)
            error(message)
        }

    /** Adds a column to [idColumns] so that it can be used as a component of the [id] property. */
    protected fun <S : Any> addIdColumn(newColumn: Column<EntityID<S>>) {
        if (_idColumns.isNotEmpty() && this !is CompositeIdTable) {
            val message = "CompositeIdTable should be used if multiple EntityID key columns are required"
            exposedLogger.error(message)
            error(message)
        }
        _idColumns.add(newColumn)
    }

    internal fun <S : Any> addIdColumnInternal(newColumn: Column<EntityID<S>>) { addIdColumn(newColumn) }
}

/**
 * Identity table with a primary key consisting of an auto-incrementing `Int` value.
 *
 * @param name Table name. By default, this will be resolved from any class name with a "Table" suffix removed (if present).
 * @param columnName Name for the primary key column. By default, "id" is used.
 */
open class IntIdTable(name: String = "", columnName: String = "id") : IdTable<Int>(name) {
    /** The identity column of this [IntIdTable], for storing 4-byte integers wrapped as [EntityID] instances. */
    final override val id: Column<EntityID<Int>> = integer(columnName).autoIncrement().entityId()
    final override val primaryKey = PrimaryKey(id)
}

/**
 * Identity table with a primary key consisting of an auto-incrementing `UInt` value.
 *
 * @param name Table name. By default, this will be resolved from any class name with a "Table" suffix removed (if present).
 * @param columnName Name for the primary key column. By default, "id" is used.
 */
open class UIntIdTable(name: String = "", columnName: String = "id") : IdTable<UInt>(name) {
    /** The identity column of this [IntIdTable], for storing 4-byte unsigned integers wrapped as [EntityID] instances. */
    final override val id: Column<EntityID<UInt>> = uinteger(columnName).autoIncrement().entityId()
    final override val primaryKey = PrimaryKey(id)
}

/**
 * Identity table with a primary key consisting of an auto-incrementing `Long` value.
 *
 * @param name Table name. By default, this will be resolved from any class name with a "Table" suffix removed (if present).
 * @param columnName Name for the primary key column. By default, "id" is used.
 */
open class LongIdTable(name: String = "", columnName: String = "id") : IdTable<Long>(name) {
    /** The identity column of this [LongIdTable], for storing 8-byte integers wrapped as [EntityID] instances. */
    final override val id: Column<EntityID<Long>> = long(columnName).autoIncrement().entityId()
    final override val primaryKey = PrimaryKey(id)
}

/**
 * Identity table with a primary key consisting of an auto-incrementing `ULong` value.
 *
 * @param name Table name. By default, this will be resolved from any class name with a "Table" suffix removed (if present).
 * @param columnName Name for the primary key column. By default, "id" is used.
 */
open class ULongIdTable(name: String = "", columnName: String = "id") : IdTable<ULong>(name) {
    /** The identity column of this [ULongIdTable], for storing 8-byte unsigned integers wrapped as [EntityID] instances. */
    final override val id: Column<EntityID<ULong>> = ulong(columnName).autoIncrement().entityId()
    final override val primaryKey = PrimaryKey(id)
}

/**
 * Identity table with a primary key consisting of an auto-generating [UUID] value.
 *
 * **Note** The specific UUID column type used depends on the database.
 * The stored identity value will be auto-generated on the client side just before insertion of a new row.
 *
 * @param name Table name. By default, this will be resolved from any class name with a "Table" suffix removed (if present).
 * @param columnName Name for the primary key column. By default, "id" is used.
 */
open class UUIDTable(name: String = "", columnName: String = "id") : IdTable<UUID>(name) {
    /** The identity column of this [UUIDTable], for storing UUIDs wrapped as [EntityID] instances. */
    final override val id: Column<EntityID<UUID>> = uuid(columnName).autoGenerate().entityId()
    final override val primaryKey = PrimaryKey(id)
}

/**
 * Identity table with a primary key consisting of a combination of columns.
 *
 * @param name Table name. By default, this will be resolved from any class name with a "Table" suffix removed (if present).
 */
open class CompositeIdTable(name: String = "") : IdTable<CompositeID>(name) {
    /** The identity column of this [CompositeIdTable], for storing references to all key columns wrapped as [EntityID] instances. */
    final override val id: Column<EntityID<CompositeID>> = compositeIdColumn()

    private fun compositeIdColumn(): Column<EntityID<CompositeID>> {
        val placeholder = Column(
            this,
            "composite_id",
            object : ColumnType<CompositeID>() {
                override fun sqlType(): String = ""
                override fun valueFromDB(value: Any): CompositeID? = null
            }
        )
        return Column(this, "composite_id", EntityIDColumnType(placeholder)).apply {
            default = null
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun mapIdComparison(
        toCompare: Any?,
        booleanOperator: (Column<*>, Expression<*>) -> Op<Boolean>
    ): Op<Boolean> {
        (toCompare as? EntityID<CompositeID>) ?: error("toCompare must be an EntityID<CompositeID> value")
        return idColumns.map { column ->
            val otherValue = if (column in toCompare.value.values) {
                toCompare.value[column as Column<EntityID<Any>>]
            } else {
                error("Comparison CompositeID is missing a key mapping for ${column.name}")
            }
            booleanOperator(column, column.wrap(otherValue.value as? EntityID<*> ?: otherValue))
        }.compoundAnd()
    }

    override fun mapIdOperator(
        booleanOperator: (Column<*>) -> Op<Boolean>
    ): Op<Boolean> = idColumns.map { booleanOperator(it) }.compoundAnd()
}

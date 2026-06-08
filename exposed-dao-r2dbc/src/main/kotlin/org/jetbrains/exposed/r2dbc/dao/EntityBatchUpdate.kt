package org.jetbrains.exposed.r2dbc.dao

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.statements.BatchUpdateStatement
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.statements.BatchUpdateSuspendExecutable
import java.util.*

/**
 * Class responsible for performing a batch update operation on multiple instances of an [Entity] class.
 *
 * @param klass The [EntityClass] associated with the entities to batch update.
 */
@ExperimentalR2dbcDaoApi
class EntityBatchUpdate(private val klass: EntityClass<*, Entity<*>>) {

    private val data = ArrayList<Pair<EntityID<*>, SortedMap<Column<*>, Any?>>>()

    /**
     * Adds the specified [entity] to the list of entities to batch update.
     *
     * The column-value mapping for this entity will initially be empty.
     * Columns to update should be assigned by using the `set()` operator on this [EntityBatchUpdate] instance.
     *
     * @throws IllegalStateException If the entity being added cannot be associated with the [EntityClass]
     * provided on instantiation of this [EntityBatchUpdate].
     */
    fun addBatch(entity: Entity<*>) {
        if (entity.klass.table != klass.table) {
            error(
                "Table ${entity.klass.table.tableName} for entity class ${entity.klass} differs from expected table " +
                    "${klass.table.tableName} for entity class $klass"
            )
        }
        data.add(entity.id to TreeMap())
    }

    operator fun set(column: Column<*>, value: Any?) {
        val values = data.last().second

        if (values.containsKey(column)) {
            error("$column is already initialized")
        }

        values[column] = value
    }

    /**
     * Executes the batch update SQL statement for each added entity in the provided [transaction]
     * and returns the number of updated rows.
     */
    suspend fun execute(transaction: R2dbcTransaction): Int {
        val updateSets = data.filterNot { it.second.isEmpty() }.groupBy { it.second.keys }
        return updateSets.values.fold(0) { acc, set ->
            acc + BatchUpdateSuspendExecutable(BatchUpdateStatement(klass.table)).let {
                it.statement.data.addAll(set)
                it.execute(transaction) ?: 0
            }
        }
    }
}

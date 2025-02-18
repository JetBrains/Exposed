package org.jetbrains.exposed.dao

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.JdbcTransaction
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.BatchUpdateBlockingExecutable
import org.jetbrains.exposed.sql.statements.BatchUpdateStatement
import java.util.*

/**
 * Class responsible for performing a batch update operation on multiple instances of an [Entity] class.
 *
 * @param klass The [EntityClass] associated with the entities to batch update.
 */
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
    fun execute(transaction: Transaction): Int {
        val updateSets = data.filterNot { it.second.isEmpty() }.groupBy { it.second.keys }
        return updateSets.values.fold(0) { acc, set ->
            acc + BatchUpdateBlockingExecutable(BatchUpdateStatement(klass.table)).let {
                it.statement.data.addAll(set)
                it.execute(transaction as JdbcTransaction)!!
            }
        }
    }
}

package org.jetbrains.exposed.v1.dao.r2dbc

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import java.util.IdentityHashMap

/** Distinguishes a column with no staged value from one staged as `null`. */
internal object NoStagedValue

/**
 * The column values one transaction holds for a single [Entity] beyond its committed snapshot: [dirty]
 * assignments still need a statement, [flushed] ones have had theirs but are not committed.
 */
internal class StagedValues {
    val dirty = LinkedHashMap<Column<Any?>, Any?>()
    val flushed = LinkedHashMap<Column<Any?>, Any?>()

    /** This scope's value for [column], or [NoStagedValue] if it holds none. */
    fun valueOrNone(column: Column<Any?>): Any? {
        if (dirty.containsKey(column)) return dirty[column]
        if (flushed.containsKey(column)) return flushed[column]
        return NoStagedValue
    }

    /** Takes every value [row] reports, leaving assignments that have not reached the database in place. */
    fun stageRow(row: ResultRow) {
        row.fieldIndex.keys.filterIsInstance<Column<*>>().forEach { column ->
            @Suppress("UNCHECKED_CAST")
            val typed = column as Column<Any?>
            if (row.hasValue(column) && !dirty.containsKey(typed)) {
                flushed[typed] = row[column]
            }
        }
    }
}

internal class StagedMemo(val owner: R2dbcTransaction, val values: StagedValues)

// TODO probably it would be better to create wrapper class over these 3 methods (acquire, release, releaseAll) too.
internal fun IdentityHashMap<Entity<*>, StagedValues>.acquire(
    entity: Entity<*>,
    owner: R2dbcTransaction
): StagedValues {
    this[entity]?.let { return it }

    val values = StagedValues()
    put(entity, values)
    entity.stagedMemo = if (entity.stagedScopeCount.incrementAndGet() == 1) {
        StagedMemo(owner, values)
    } else {
        null
    }
    return values
}

internal fun IdentityHashMap<Entity<*>, StagedValues>.release(entity: Entity<*>) {
    if (remove(entity) == null) return
    entity.stagedScopeCount.decrementAndGet()
    entity.stagedMemo = null
}

internal fun IdentityHashMap<Entity<*>, StagedValues>.releaseAll() {
    keys.forEach {
        it.stagedScopeCount.decrementAndGet()
        it.stagedMemo = null
    }
    clear()
}

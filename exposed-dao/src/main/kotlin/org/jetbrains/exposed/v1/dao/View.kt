package org.jetbrains.exposed.v1.dao

import org.jetbrains.exposed.v1.sql.Expression
import org.jetbrains.exposed.v1.sql.Op
import org.jetbrains.exposed.v1.sql.SizedIterable
import org.jetbrains.exposed.v1.sql.SortOrder
import org.jetbrains.exposed.v1.sql.vendors.ForUpdateOption
import kotlin.reflect.KProperty

/**
 * A [SizedIterable] of [Entity] instances that represent a subset of all managed entities that conform to the
 * provided [op] conditional expression.
 *
 * @param op The conditional expression to use when querying for matching entities.
 * @param factory The [EntityClass] to use when searching for matching entities.
 */
class View<out Target : Entity<*>>(val op: Op<Boolean>, val factory: EntityClass<*, Target>) : SizedIterable<Target> {
    override fun limit(count: Int): SizedIterable<Target> = factory.find(op).limit(count)
    override fun offset(start: Long): SizedIterable<Target> = factory.find(op).offset(start)
    override fun count(): Long = factory.find(op).count()
    override fun empty(): Boolean = factory.find(op).empty()
    override fun forUpdate(option: ForUpdateOption): SizedIterable<Target> = factory.find(op).forUpdate(option)
    override fun notForUpdate(): SizedIterable<Target> = factory.find(op).notForUpdate()

    override operator fun iterator(): Iterator<Target> = factory.find(op).iterator()
    operator fun getValue(o: Any?, desc: KProperty<*>): SizedIterable<Target> = factory.find(op)
    override fun copy(): SizedIterable<Target> = View(op, factory)
    override fun orderBy(vararg order: Pair<Expression<*>, SortOrder>): SizedIterable<Target> = factory.find(op).orderBy(*order)
}

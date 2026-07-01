package org.jetbrains.exposed.r2dbc.dao.relationships

import org.jetbrains.exposed.r2dbc.dao.Entity
import org.jetbrains.exposed.r2dbc.dao.EntityClass
import org.jetbrains.exposed.r2dbc.dao.ExperimentalR2dbcDaoApi
import org.jetbrains.exposed.v1.core.Column
import kotlin.reflect.KProperty

/**
 * Class representing a table relation between two [Entity] classes, which is responsible for
 * retrieving the parent entity referenced by the child entity. R2DBC counterpart of JDBC's `Reference`
 * from `References.kt`; the property delegate yields an [Accessor] for suspending lookups.
 *
 * @param reference The reference column defined on the child entity's associated table.
 * @param factory The [EntityClass] associated with the parent entity referenced by the child entity.
 */
@ExperimentalR2dbcDaoApi
class Reference<ID : Any, Parent : Entity<ID>, REF : Any>(
    val reference: Column<REF>,
    val factory: EntityClass<ID, @UnsafeVariance Parent>,
    /**
     * Composite-FK child→parent column map (mirrors JDBC's `Reference.references`). `null` for
     * single-column references — [Accessor] falls back to `reference.referee`.
     */
    val references: Map<Column<*>, Column<*>>? = null
) {
    /** Wires up the property delegate by returning an [Accessor] bound to the source [thisRef] entity. */
    operator fun <SRC : Entity<*>> provideDelegate(
        thisRef: SRC,
        property: KProperty<*>
    ): Accessor<ID, Parent, REF> {
        return Accessor(reference, factory, thisRef, references)
    }
}

/**
 * Class representing an optional table relation between two [Entity] classes, which is responsible for
 * retrieving the parent entity optionally referenced by the child entity. R2DBC counterpart of JDBC's
 * `OptionalReference`; the property delegate yields an [OptionalAccessor] for suspending lookups.
 *
 * @param reference The nullable reference column defined on the child entity's associated table.
 * @param factory The [EntityClass] associated with the parent entity optionally referenced by the child entity.
 */
@ExperimentalR2dbcDaoApi
class OptionalReference<ID : Any, Parent : Entity<ID>, REF : Any>(
    val reference: Column<REF?>,
    val factory: EntityClass<ID, @UnsafeVariance Parent>,
    /**
     * Composite-FK child→parent column map (mirrors JDBC's `OptionalReference.references`).
     */
    val references: Map<Column<*>, Column<*>>? = null
) {
    /** Wires up the property delegate by returning an [OptionalAccessor] bound to the source [thisRef] entity. */
    operator fun <SRC : Entity<*>> provideDelegate(
        thisRef: SRC,
        property: KProperty<*>
    ): OptionalAccessor<ID, Parent, REF> {
        return OptionalAccessor(reference, factory, thisRef, references)
    }
}

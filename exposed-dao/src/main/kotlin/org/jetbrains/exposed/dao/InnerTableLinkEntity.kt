package org.jetbrains.exposed.dao

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ResultRow

/**
 * Base class for an [Entity] instance identified by a [wrapped] entity comprised of any ID value.
 *
 * Instances of this base class should be used when needing to represent referenced entities in a many-to-many relation
 * from fields defined using `via`, which require additional columns in the intermediate table. These additional
 * columns should be added as constructor properties and the property-column mapping should be defined by
 * [getInnerTableLinkValue].
 *
 * @param WID ID type of the [wrapped] entity instance.
 * @property wrapped The referenced (parent) entity whose unique ID value identifies this [InnerTableLinkEntity] instance.
 * @sample org.jetbrains.exposed.sql.tests.shared.entities.ViaTests.ProjectWithApproval
 */
abstract class InnerTableLinkEntity<WID : Comparable<WID>>(val wrapped: Entity<WID>) : Entity<WID>(wrapped.id) {
    /**
     * Returns the initial column-property mapping for an [InnerTableLinkEntity] instance
     * before being flushed and inserted into the database.
     *
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.ViaTests.ProjectWithApproval
     */
    abstract fun getInnerTableLinkValue(column: Column<*>): Any?
}

/**
 * Base class representing the [EntityClass] that manages [InnerTableLinkEntity] instances and
 * maintains their relation to the provided [table] of the wrapped entity.
 *
 * This should be used, as a companion object to [InnerTableLinkEntity], when needing to represent referenced entities
 * in a many-to-many relation from fields defined using `via`, which require additional columns in the intermediate table.
 * These additional columns will be retrieved as part of a query's [ResultRow] and the column-property mapping to create
 * new instances should be defined by [createInstance].
 *
 * @param WID ID type of the wrapped entity instance.
 * @param E The [InnerTableLinkEntity] type that is managed by this class.
 * @param [table] The [IdTable] object that stores rows mapped to the wrapped entity of this class.
 * @sample org.jetbrains.exposed.sql.tests.shared.entities.ViaTests.ProjectWithApproval
 */
abstract class InnerTableLinkEntityClass<WID : Comparable<WID>, out E : InnerTableLinkEntity<WID>>(
    table: IdTable<WID>
) : EntityClass<WID, E>(table, null, null) {
    /**
     * Creates a new [InnerTableLinkEntity] instance by using the provided [row] to both create the wrapped entity
     * and any additional columns.
     *
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.ViaTests.ProjectWithApproval
     */
    abstract override fun createInstance(entityId: EntityID<WID>, row: ResultRow?): E
}

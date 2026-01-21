package org.jetbrains.exposed.v1.iface.base

/**
 * Marker interface for all interface-based entities.
 *
 * Generated entity implementations implement this interface.
 */
interface InterfaceEntity

/**
 * Base interface for entities with an ID.
 *
 * @param ID The type of the primary key.
 */
interface EntityWithId<ID : Comparable<ID>> : InterfaceEntity {
    /**
     * The primary key value.
     */
    val id: ID
}

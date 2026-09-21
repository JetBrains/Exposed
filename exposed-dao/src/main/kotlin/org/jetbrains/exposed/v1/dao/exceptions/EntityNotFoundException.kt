package org.jetbrains.exposed.v1.dao.exceptions

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.EntityClass

/** Thrown when no row with [id] exists in the table that [entity] maps to. */
class EntityNotFoundException(val id: EntityID<*>, val entity: EntityClass<*, *>) :
    Exception("Entity ${entity.klass.simpleName}, id=$id not found in the database")

package org.jetbrains.exposed.dao.exceptions

import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID

/**
 * An exception that provides information about an [entity] that could not be accessed
 * either within the scope of the current entity cache or as a result of a database search error.
 */
class EntityNotFoundException(val id: EntityID<*>, val entity: EntityClass<*, *>) :
    Exception("Entity ${entity.klass.simpleName}, id=$id not found in the database")

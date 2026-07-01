package org.jetbrains.exposed.r2dbc.dao.exceptions

import org.jetbrains.exposed.r2dbc.dao.EntityClass
import org.jetbrains.exposed.r2dbc.dao.ExperimentalR2dbcDaoApi
import org.jetbrains.exposed.v1.core.dao.id.EntityID

/**
 * An exception that provides information about an [entity] that could not be accessed
 * either within the scope of the current entity cache or as a result of a database search error.
 */
@ExperimentalR2dbcDaoApi
class EntityNotFoundException(val id: EntityID<*>, val entity: EntityClass<*, *>) :
    Exception("Entity ${entity.klass.simpleName}, id=${id._value} not found in the database")

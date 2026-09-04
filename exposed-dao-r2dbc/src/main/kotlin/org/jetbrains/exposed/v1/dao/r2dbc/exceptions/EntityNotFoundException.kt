package org.jetbrains.exposed.v1.dao.r2dbc.exceptions

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.r2dbc.EntityClass
import org.jetbrains.exposed.v1.dao.r2dbc.ExperimentalR2dbcDaoApi

/** Thrown when no row with [id] exists in the table that [entity] maps to. */
@ExperimentalR2dbcDaoApi
class EntityNotFoundException(val id: EntityID<*>, val entity: EntityClass<*, *>) :
    Exception("Entity ${entity.klass.simpleName}, id=${id._value} not found in the database")

package org.jetbrains.exposed.r2dbc.dao.exceptions

import org.jetbrains.exposed.r2dbc.dao.R2dbcEntityClass
import org.jetbrains.exposed.v1.core.dao.id.EntityID

class R2dbcEntityNotFoundException(val id: EntityID<*>, val entity: R2dbcEntityClass<*, *>) :
    Exception("Entity ${entity.klass.simpleName}, id=$id not found in the database")

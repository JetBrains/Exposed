package org.jetbrains.exposed.dao.exceptions

import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID

class EntityNotFoundException(val id: EntityID<*>, val entity: EntityClass<*, *>)
    : Exception("Entity ${entity.klass.simpleName}, id=$id not found in the database")

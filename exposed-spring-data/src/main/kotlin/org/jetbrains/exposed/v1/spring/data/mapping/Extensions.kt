package org.jetbrains.exposed.v1.spring.data.mapping

import org.jetbrains.exposed.v1.core.dao.id.EntityID

internal fun Any?.unwrapEntityId() = if (this is EntityID<*>) value else this


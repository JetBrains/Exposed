package org.jetbrains.exposed.r2dbc.dao

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable

/**
 * [EntityID] implementation for R2DBC DAOs.
 * This is the R2DBC equivalent of [org.jetbrains.exposed.v1.dao.DaoEntityID].
 */
class R2dbcDaoEntityID<T : Any>(id: T?, table: IdTable<T>) : EntityID<T>(table, id)

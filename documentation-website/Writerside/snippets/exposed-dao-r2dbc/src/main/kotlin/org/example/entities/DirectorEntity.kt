package org.example.entities

import org.example.tables.DirectorsTable
import org.jetbrains.exposed.v1.core.dao.id.CompositeID
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.r2dbc.CompositeEntity
import org.jetbrains.exposed.v1.dao.r2dbc.CompositeEntityClass

class DirectorEntity(id: EntityID<CompositeID>) : CompositeEntity(id) {
    companion object : CompositeEntityClass<DirectorEntity>(DirectorsTable)

    var genre by DirectorsTable.genre
}

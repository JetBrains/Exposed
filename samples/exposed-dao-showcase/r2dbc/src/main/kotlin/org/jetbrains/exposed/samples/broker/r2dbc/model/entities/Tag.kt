@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.r2dbc.model.entities

import org.jetbrains.exposed.samples.broker.r2dbc.model.tables.Tags
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntity
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntityClass

class Tag(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Tag>(Tags)

    var name by Tags.name
}

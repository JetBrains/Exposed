package org.example.entities

import org.example.tables.CitiesTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntity
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntityClass

class CityEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<CityEntity>(CitiesTable)

    val name by CitiesTable.name
}

package org.example.entities

import org.example.tables.CitiesTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.ImmutableEntityClass
import org.jetbrains.exposed.v1.dao.IntEntity

class CityEntity(id: EntityID<Int>) : IntEntity(id) {
    val name by CitiesTable.name

    companion object : ImmutableEntityClass<Int, CityEntity>(CitiesTable)
}

package org.example.entities

import org.example.tables.CitiesTable
import org.jetbrains.exposed.dao.ImmutableEntityClass
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.id.EntityID

class CityEntity(id: EntityID<Int>) : IntEntity(id) {
    val name by CitiesTable.name

    companion object : ImmutableEntityClass<Int, CityEntity>(CitiesTable)
}

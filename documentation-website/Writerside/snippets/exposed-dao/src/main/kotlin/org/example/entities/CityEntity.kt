package org.example.entities

import org.example.tables.CitiesTable
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class City(id: EntityID<Int>) : IntEntity(id) {
    val name by Countries.name

    companion object : ImmutableEntityClass<City>(CitiesTable)
}

package org.example.entities

import org.example.tables.UsersTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntity
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntityClass

class UserEntityWithOverride(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<UserEntityWithOverride>(UsersTable)

    var name by UsersTable.name
    val city by CityEntity referencedOn UsersTable.cityId

    override suspend fun delete() {
        println("Deleting user $name with ID: $id")
        super.delete()
    }
}

package org.example.entities

import org.example.tables.UsersTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass

class UserEntityWithOverride(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<UserEntityWithOverride>(UsersTable)

    var name by UsersTable.name
    var city by CityEntity referencedOn UsersTable.cityId

    override fun delete() {
        println("Deleting user $name with ID: $id")
        super.delete()
    }
}

package org.example.entities

import org.example.tables.UsersTable
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class UserEntityWithOverride(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<User>(UsersTable)

    var name by UsersTable.name

    override fun delete() {
        println("Deleting user $name with ID: $id")
        super.delete()
    }
}

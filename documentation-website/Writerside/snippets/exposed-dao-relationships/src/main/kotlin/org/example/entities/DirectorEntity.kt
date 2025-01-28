package org.example.entities

import org.example.tables.DirectorsTable
import org.example.tables.DirectorsCompositeIdTable
import org.example.tables.StarWarsFilmsWithCompositeRefTable
import org.jetbrains.exposed.dao.CompositeEntity
import org.jetbrains.exposed.dao.CompositeEntityClass
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.dao.id.EntityID

/*
    Important: This file is referenced by line number in `DAO-Relationships.topic`.
    If you add, remove, or modify any lines, ensure you update the corresponding
    line numbers in the `code-block` element of the referenced file.
*/

class DirectorEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<DirectorEntity>(DirectorsTable)

    var name by DirectorsTable.name
    var genre by DirectorsTable.genre
}

class DirectorCompositeIDEntity(id: EntityID<CompositeID>) : CompositeEntity(id) {
    companion object : CompositeEntityClass<DirectorCompositeIDEntity>(DirectorsCompositeIdTable)

    var genre by DirectorsCompositeIdTable.genre
    val films by StarWarsFilmWithCompositeRefEntity referrersOn StarWarsFilmsWithCompositeRefTable
}

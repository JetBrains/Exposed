package org.example.entities

import org.example.tables.StarWarsFilmActorsTable
import org.example.tables.StarWarsFilmRelationsTable
import org.example.tables.StarWarsFilmsTable
import org.example.tables.StarWarsFilmsWithCompositeRefTable
import org.example.tables.StarWarsFilmsWithDirectorTable
import org.example.tables.UserRatingsTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass

/*
    Important: This file is referenced by line number in `DAO-Relationships.topic`.
    If you add, remove, or modify any lines, ensure you update the corresponding
    line numbers in the `code-block` element of the referenced file.
*/

class StarWarsFilmEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<StarWarsFilmEntity>(StarWarsFilmsTable)

    var sequelId by StarWarsFilmsTable.sequelId
    var name by StarWarsFilmsTable.name
    var director by StarWarsFilmsTable.director
    val ratings by UserRatingEntity referrersOn UserRatingsTable.film // make sure to use val and referrersOn
    var actors by ActorEntity via StarWarsFilmActorsTable
}

class StarWarsFilmWithParentAndChildEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<StarWarsFilmWithParentAndChildEntity>(StarWarsFilmsWithDirectorTable)

    var name by StarWarsFilmsWithDirectorTable.name
    var director by DirectorEntity referencedOn StarWarsFilmsWithDirectorTable.director

    // Define hierarchical relationships
    var sequels by StarWarsFilmWithParentAndChildEntity.via(StarWarsFilmRelationsTable.parentFilm, StarWarsFilmRelationsTable.childFilm)
    var prequels by StarWarsFilmWithParentAndChildEntity.via(StarWarsFilmRelationsTable.childFilm, StarWarsFilmRelationsTable.parentFilm)
}

class StarWarsFilmWithCompositeRefEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<StarWarsFilmWithCompositeRefEntity>(StarWarsFilmsWithCompositeRefTable)

    var sequelId by StarWarsFilmsWithCompositeRefTable.sequelId
    var name by StarWarsFilmsWithCompositeRefTable.name
    var director by DirectorCompositeIDEntity referencedOn StarWarsFilmsWithCompositeRefTable
}

package org.example.examples

import org.example.entities.UserEntity
import org.example.entities.UserRatingEntity
import org.jetbrains.exposed.v1.dao.r2dbc.load
import org.jetbrains.exposed.v1.dao.r2dbc.with

class EagerLoadingExamples {
    suspend fun load() {
        // load a reference
        UserEntity.findById(1)?.load(UserEntity::ratings)

        // load references of references
        UserEntity.findById(1)?.load(UserEntity::ratings, UserRatingEntity::film)

        // load references on Collections
        UserEntity.all().with(UserEntity::ratings)
    }
}

@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.r2dbc.domain.user

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.samples.r2dbc.domain.BaseRepository
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update

interface UserRepository : BaseRepository {
    suspend fun save(user: User): UserId

    suspend fun findAll(): List<User>

    suspend fun findById(id: UserId): User?

    suspend fun update(id: UserId, settings: UserSettings): Boolean
}

class DSLUserRepository : UserRepository {
    override suspend fun save(user: User): UserId = dbQuery {
        val id = Users.insert {
            it[Users.fullName] = user.fullName
            it[Users.username] = user.username
            it[Users.settings] = user.settings
        } get Users.id

        UserId(id)
    }

    override suspend fun findAll(): List<User> = dbQuery {
        Users
            .selectAll()
            .map(::rowToUser)
            .toList()
    }

    override suspend fun findById(id: UserId): User? = dbQuery {
        Users
            .selectAll()
            .where { Users.id eq id.value }
            .singleOrNull()
            ?.let(::rowToUser)
    }

    override suspend fun update(id: UserId, settings: UserSettings): Boolean = dbQuery {
        val rowsUpdated = Users
            .update(
                where = { Users.id eq id.value }
            ) {
                it[Users.settings] = settings
            }

        rowsUpdated == 1
    }
}

@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.spring.service

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.samples.spring.domain.IssuesTable
import org.jetbrains.exposed.samples.spring.domain.User
import org.jetbrains.exposed.samples.spring.domain.UserId
import org.jetbrains.exposed.samples.spring.domain.UsersTable
import org.jetbrains.exposed.samples.spring.domain.toUser
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional
internal class UserService {

    // read user by user primary key
    suspend fun findUserById(id: UserId): User? {
        // Use Exposed dsl without `suspendTransaction { }`
        return UsersTable
            .selectAll()
            .where { UsersTable.id eq id.value }
            .firstOrNull()
            ?.toUser()
    }

    // read all users
    suspend fun findAllUsers(): List<User> {
        return UsersTable
            .selectAll()
            .map { it.toUser() }
            .toList()
    }

    // create user, along with any issues that should be assigned to them
    suspend fun create(request: UserCreateRequest): UserId {
        // All statements are executed in the same transaction, so if any issue cannot be created,
        // the transaction is rolled back and neither the user nor any of their issues are stored
        val id = UsersTable.insertAndGetId {
            it[name] = request.name
            it[age] = request.age
        }

        request.issueTitles.forEach { issueTitle ->
            IssuesTable.insert {
                it[title] = issueTitle
                it[assigneeId] = id
            }
        }

        return UserId(id.value)
    }

    // update user, returning whether a matching user was found
    suspend fun update(id: UserId, request: UserUpdateRequest): Boolean {
        val updatedRows = UsersTable.update(where = { UsersTable.id eq id.value }) {
            request.name?.let { name -> it[UsersTable.name] = name }
            request.age?.let { age -> it[UsersTable.age] = age }
        }

        return updatedRows > 0
    }

    // delete user and the issues assigned to them, returning whether a matching user was found
    suspend fun delete(id: UserId): Boolean {
        // Both statements are executed in the same transaction,
        // so the issues are only deleted if the user is successfully deleted too
        IssuesTable.deleteWhere { IssuesTable.assigneeId eq id.value }
        val deletedRows = UsersTable.deleteWhere { UsersTable.id eq id.value }

        return deletedRows > 0
    }
}

internal data class UserCreateRequest(
    val name: String,
    val age: Int,
    val issueTitles: List<String> = emptyList(),
)

internal data class UserUpdateRequest(
    val name: String? = null,
    val age: Int? = null,
)

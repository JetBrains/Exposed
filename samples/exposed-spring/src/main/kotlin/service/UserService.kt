@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.spring.service

import org.jetbrains.exposed.samples.spring.domain.User
import org.jetbrains.exposed.samples.spring.domain.UserEntity
import org.jetbrains.exposed.samples.spring.domain.UserId
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional
class UserService {

    fun findUserById(id: UserId): User? {
        return UserEntity
            .select { UserEntity.id eq id.value }
            .firstOrNull()?.let {
                User(
                    id = UserId(it[UserEntity.id].value),
                    name = it[UserEntity.name],
                    age = it[UserEntity.age],
                )
            }
    }

    fun create(request: UserCreateRequest): UserId {
        val id = UserEntity.insertAndGetId {
            it[name] = request.name
            it[age] = request.age
        }

        return UserId(id.value)
    }

    fun modify(id: Long, request: UserModifyRequest) {
        UserEntity.update({ UserEntity.id eq id }) {
            request.name?.let { name -> it[UserEntity.name] = name }
            request.age?.let { age -> it[UserEntity.age] = age }
        }
    }

    fun delete(id: UserId) {
        UserEntity.deleteWhere { UserEntity.id eq id.value }
    }
}

data class UserCreateRequest(
    val name: String,
    val age: Int,
)

data class UserModifyRequest(
    val name: String? = null,
    val age: Int? = null,
)

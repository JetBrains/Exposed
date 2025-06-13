package com.example.dao.impl

import com.example.bean.User
import com.example.dao.UserDao
import com.example.entity.UserEntity
import kotlinx.coroutines.flow.firstOrNull
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import org.springframework.stereotype.Repository

@Repository // not use final
internal class UserDaoImpl internal constructor(): UserDao {
    override suspend fun findUserById(id: Int): User {
        val user: User = UserEntity.selectAll().where { UserEntity.id eq id }.firstOrNull() ?.let { resultRow: ResultRow ->
            User(
                id = resultRow[UserEntity.id].value,
                account = resultRow[UserEntity.account],
                password = resultRow[UserEntity.password],
                nickname = resultRow[UserEntity.nickname],
            )
        } ?: User (
            id = 0,
            account = "default",
            password = "default",
            nickname = "default"
        )
        return user
    }
    override suspend fun insetUser(user: User): Boolean {
        val id: Int = UserEntity.insert { updateBuilder: UpdateBuilder<*> ->
            updateBuilder[UserEntity.account] = user.account
            updateBuilder[UserEntity.password] = user.password
            updateBuilder[UserEntity.nickname] = user.nickname
        }.getOrNull(column = UserEntity.id)?.value ?: 0
        return id > 0
    }

    override suspend fun deleteUserById(id: Int): Boolean {
        val rows: Int = UserEntity.deleteWhere { UserEntity.id eq id }
        return rows > 0
    }
    override suspend fun updateUser(user: User): Boolean {
        val rows: Int = UserEntity.update(where = { UserEntity.id eq user.id }) { updateStatement: UpdateStatement ->
            updateStatement[UserEntity.account] = user.account
            updateStatement[UserEntity.password] = user.password
            updateStatement[UserEntity.nickname] = user.nickname
        }
        return rows > 0
    }
}

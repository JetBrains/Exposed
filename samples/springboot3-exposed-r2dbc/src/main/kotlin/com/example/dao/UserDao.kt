package com.example.dao

import com.example.bean.User

internal interface UserDao {
    suspend fun findUserById(id: Int): User
    suspend fun insetUser(user: User): Boolean
    suspend fun deleteUserById(id: Int): Boolean
    suspend fun updateUser(user: User): Boolean
}

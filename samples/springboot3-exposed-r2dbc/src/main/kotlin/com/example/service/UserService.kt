package com.example.service

import com.example.bean.User
import kotlinx.coroutines.Deferred

internal interface UserService {
    suspend fun findUserById(id: Int): Deferred<User>
    suspend fun insetUser(user: User): Deferred<Boolean>
    suspend fun deleteUserById(id: Int): Deferred<Boolean>
    suspend fun updateUser(user: User): Deferred<Boolean>
}

package com.example.service.impl

import com.example.bean.User
import com.example.dao.UserDao
import com.example.service.UserService
import kotlinx.coroutines.Deferred
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransactionAsync
import org.springframework.stereotype.Service

@Service
internal final class UserServiceImpl internal constructor(
    private val userDao: UserDao
) : UserService {

    override suspend fun findUserById(id: Int): Deferred<User> = suspendTransactionAsync {
        return@suspendTransactionAsync userDao.findUserById(id = id)
    }

    override suspend fun insetUser(user: User): Deferred<Boolean> = suspendTransactionAsync {
        userDao.insetUser(user = user)
    }

    override suspend fun deleteUserById(id: Int): Deferred<Boolean> = suspendTransactionAsync {
        userDao.deleteUserById(id = id)
    }

    override suspend fun updateUser(user: User): Deferred<Boolean> = suspendTransactionAsync {
        userDao.updateUser(user = user)
    }
}

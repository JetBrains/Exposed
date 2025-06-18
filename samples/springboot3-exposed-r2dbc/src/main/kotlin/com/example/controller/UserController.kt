package com.example.controller

import com.example.bean.User
import com.example.service.UserService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@CrossOrigin
@RestController
@RequestMapping(value = ["/user"])
internal final class UserController private constructor(
    private val userService: UserService
) {
    private final val logger: Logger = LoggerFactory.getLogger(this.javaClass)

    @GetMapping(value = ["/findAll"])
    internal final suspend fun findAll(
        @RequestHeader headers: Map<String, String>
    ): List<User> {
        logger.info("findAll ${Thread.currentThread()}, headers: $headers")
        return userService.findAll().await()
    }

    @GetMapping(value = ["/findUserById"])
    internal final suspend fun findUserById(
        @RequestParam(value = "id") id: Int
    ): User {
        logger.info("${Thread.currentThread()} found user with id: $id")
        return userService.findUserById(id = id).await()
    }

    @PostMapping(value = ["/insert"])
    internal final suspend fun insert(
        @RequestBody user: User
    ): Boolean {
        return userService.insetUser(user = user).await()
    }

    @PutMapping(value = ["/update"])
    internal final suspend fun update(
        @RequestBody user: User
    ): Boolean {
        return userService.updateUser(user = user).await()
    }

    @GetMapping(value = ["/deleteById"])
    internal final suspend fun update(
        @RequestParam(value = "id") id: Int
    ): Boolean {
        return userService.deleteUserById(id = id).await()
    }
}

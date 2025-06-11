package com.example.controller

import com.example.bean.User
import com.example.service.UserService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(value = ["/user"])
private final class UserController private constructor(
    private val userService: UserService
) {
    private final val logger: Logger = LoggerFactory.getLogger(this.javaClass)
    @GetMapping(value = ["/findUserById"])
    private final suspend fun findUserById(
        @RequestParam(value = "id") id: Int
    ): User {
        logger.info("${Thread.currentThread()} found user with id: $id")
        return userService.findUserById(id = id).await()
    }
    @PostMapping(value = ["/insert"])
    private final suspend fun insert(
        @RequestBody user: User
    ): Boolean {
        return userService.insetUser(user = user).await()
    }
    @PutMapping(value = ["/update"])
    private final suspend fun update(
        @RequestBody user: User
    ): Boolean {
        return userService.updateUser(user = user).await()
    }
    @GetMapping(value = ["/deleteById"])
    private final suspend fun update(
        @RequestParam(value = "id") id: Int
    ): Boolean {
        return userService.deleteUserById(id = id).await()
    }
}

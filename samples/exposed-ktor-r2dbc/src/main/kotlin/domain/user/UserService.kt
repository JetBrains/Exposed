@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.r2dbc.domain.user

class UserService(
    private val userRepository: UserRepository
) {
    suspend fun createUser(user: User): User {
        val id = userRepository.save(user)
        return user.copy(id = id)
    }

    suspend fun getUsers(): List<User> {
        return userRepository.findAll()
    }

    suspend fun getUser(id: Int): User? {
        return userRepository.findById(UserId(id))
    }

    suspend fun editUserSettings(id: Int, settings: UserSettings): Boolean {
        return userRepository.update(UserId(id), settings)
    }
}

@file:Suppress("MagicNumber")

package org.example.tables

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.crypt.Argon2Hasher
import org.jetbrains.exposed.v1.crypt.BCryptHasher
import org.jetbrains.exposed.v1.crypt.Pbkdf2Hasher
import org.jetbrains.exposed.v1.crypt.hashed
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder

val hasher = BCryptHasher()

val bCryptHasher = BCryptHasher(strength = 12)

val argon2Hasher = Argon2Hasher(
    memory = 19_456,
    iterations = 2,
    parallelism = 1
)

val pbkdf2Hasher = Pbkdf2Hasher(
    algorithm = Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256
)

object Users : IntIdTable() {
    val email = varchar("email", 320)
    val password = text("password").hashed(hasher)
}

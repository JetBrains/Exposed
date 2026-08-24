package org.jetbrains.exposed.v1.crypt

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

/**
 * [Hasher] that uses the bcrypt algorithm, salting each value individually.
 *
 * Encoded values are always 60 characters long.
 *
 * @param strength Log rounds of hashing work to perform, between 4 and 31, where each increment doubles the
 * time taken. Defaults to the same value as Spring Security's `BCryptPasswordEncoder`.
 */
class BCryptHasher(
    strength: Int = DEFAULT_STRENGTH
) : PasswordEncoderHasher(BCryptPasswordEncoder(strength)) {
    private companion object {
        private const val DEFAULT_STRENGTH = 10
    }
}

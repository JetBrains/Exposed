package org.jetbrains.exposed.v1.crypt

import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm

/**
 * [Hasher] that uses the PBKDF2 algorithm, salting each value individually.
 *
 * Encoded values are the hex encoded salt and hash, 96 characters long with the default parameters, varying with
 * [saltLength] and the hash width of [algorithm].
 *
 * @param secret Optional secret, sometimes called a pepper, mixed into every hash. Unlike the salt it is not
 * stored alongside the hash, so it has to be kept and supplied identically in order to verify existing values.
 * @param saltLength Length in bytes of the randomly generated salt
 * @param iterations Number of hashing iterations to perform
 * @param algorithm Pseudorandom function to apply on each iteration
 */
class Pbkdf2Hasher(
    secret: CharSequence = "",
    saltLength: Int = DEFAULT_SALT_LENGTH,
    iterations: Int = DEFAULT_ITERATIONS,
    algorithm: SecretKeyFactoryAlgorithm = SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256
) : PasswordEncoderHasher(Pbkdf2PasswordEncoder(secret, saltLength, iterations, algorithm)) {
    private companion object {
        private const val DEFAULT_SALT_LENGTH = 16
        private const val DEFAULT_ITERATIONS = 310_000
    }
}

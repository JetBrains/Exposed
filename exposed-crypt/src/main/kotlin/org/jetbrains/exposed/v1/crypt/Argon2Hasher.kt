package org.jetbrains.exposed.v1.crypt

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder

/**
 * [Argon2Hasher] requires BouncyCastle (`org.bouncycastle:bcprov-jdk18on`) on the runtime classpath, which `exposed-crypt`
 * does not depend on; constructing it throws an [IllegalStateException] if BouncyCastle is missing.
 *
 * @param saltLength Length in bytes of the randomly generated salt
 * @param hashLength Length in bytes of the generated hash
 * @param parallelism Number of lanes used by the algorithm
 * @param memory Amount of memory in kibibytes used by the algorithm
 * @param iterations Number of passes over the memory
 * @sample org.jetbrains.exposed.v1.crypt.hashed
 */
class Argon2Hasher(
    saltLength: Int = DEFAULT_SALT_LENGTH,
    hashLength: Int = DEFAULT_HASH_LENGTH,
    parallelism: Int = DEFAULT_PARALLELISM,
    memory: Int = DEFAULT_MEMORY,
    iterations: Int = DEFAULT_ITERATIONS
) : PasswordEncoderHasher(newEncoder(saltLength, hashLength, parallelism, memory, iterations)) {
    private companion object {
        private const val DEFAULT_SALT_LENGTH = 16
        private const val DEFAULT_HASH_LENGTH = 32
        private const val DEFAULT_PARALLELISM = 1
        private const val DEFAULT_MEMORY = 16384
        private const val DEFAULT_ITERATIONS = 2

        private const val BOUNCY_CASTLE_CLASS = "org.bouncycastle.crypto.generators.Argon2BytesGenerator"

        @Suppress("LongParameterList")
        private fun newEncoder(
            saltLength: Int,
            hashLength: Int,
            parallelism: Int,
            memory: Int,
            iterations: Int
        ): Argon2PasswordEncoder {
            requireBouncyCastle("Argon2Hasher", BOUNCY_CASTLE_CLASS)
            return Argon2PasswordEncoder(saltLength, hashLength, parallelism, memory, iterations)
        }
    }
}

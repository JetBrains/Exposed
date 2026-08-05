package org.jetbrains.exposed.v1.crypt

import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder

/**
 * [Hasher] that uses the scrypt algorithm, salting each value individually.
 *
 * This requires BouncyCastle (`org.bouncycastle:bcprov-jdk18on`) on the runtime classpath, which `exposed-crypt`
 * does not depend on; constructing it throws an [IllegalStateException] if BouncyCastle is missing.
 *
 * @param cpuCost CPU cost of the algorithm, as a power of 2 greater than 1
 * @param memoryCost Memory cost of the algorithm
 * @param parallelization Parallelization of the algorithm
 * @param keyLength Length in bytes of the generated key
 * @param saltLength Length in bytes of the randomly generated salt
 */
class SCryptHasher(
    cpuCost: Int = DEFAULT_CPU_COST,
    memoryCost: Int = DEFAULT_MEMORY_COST,
    parallelization: Int = DEFAULT_PARALLELIZATION,
    keyLength: Int = DEFAULT_KEY_LENGTH,
    saltLength: Int = DEFAULT_SALT_LENGTH
) : PasswordEncoderHasher(newEncoder(cpuCost, memoryCost, parallelization, keyLength, saltLength)) {
    private companion object {
        private const val DEFAULT_CPU_COST = 65536
        private const val DEFAULT_MEMORY_COST = 8
        private const val DEFAULT_PARALLELIZATION = 1
        private const val DEFAULT_KEY_LENGTH = 32
        private const val DEFAULT_SALT_LENGTH = 16

        private const val BOUNCY_CASTLE_CLASS = "org.bouncycastle.crypto.generators.SCrypt"

        @Suppress("LongParameterList")
        private fun newEncoder(
            cpuCost: Int,
            memoryCost: Int,
            parallelization: Int,
            keyLength: Int,
            saltLength: Int
        ): SCryptPasswordEncoder {
            requireBouncyCastle("SCryptHasher", BOUNCY_CASTLE_CLASS)
            return SCryptPasswordEncoder(cpuCost, memoryCost, parallelization, keyLength, saltLength)
        }
    }
}

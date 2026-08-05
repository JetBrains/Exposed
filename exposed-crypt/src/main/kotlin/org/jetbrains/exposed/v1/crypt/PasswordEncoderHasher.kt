package org.jetbrains.exposed.v1.crypt

import org.springframework.security.crypto.password.PasswordEncoder

/**
 * [Hasher] that delegates to a Spring Security [passwordEncoder].
 *
 * This is the base of [BCryptHasher], [Argon2Hasher], [Pbkdf2Hasher], and [SCryptHasher], and can also be used
 * directly with any other implementation, including a `DelegatingPasswordEncoder` for a column whose existing
 * values were hashed by an algorithm that is being migrated away from:
 *
 * ```kotlin
 * val hasher = PasswordEncoderHasher(
 *     DelegatingPasswordEncoder(
 *         "argon2",
 *         mapOf("argon2" to Argon2PasswordEncoder(...), "bcrypt" to BCryptPasswordEncoder())
 *     )
 * )
 * ```
 *
 * To hash with something that is not a `PasswordEncoder` at all, implement [Hasher] directly.
 */
open class PasswordEncoderHasher(
    protected val passwordEncoder: PasswordEncoder
) : Hasher {
    override fun hash(plainText: String): Hashed = Hashed(
        this,
        checkNotNull(passwordEncoder.encode(plainText)) {
            "${passwordEncoder::class.simpleName} returned no hash for the given value"
        }
    )

    override fun matches(plainText: String, encodedValue: String): Boolean =
        passwordEncoder.matches(plainText, encodedValue)
}

internal fun requireBouncyCastle(hasher: String, className: String) {
    try {
        Class.forName(className)
    } catch (cause: ClassNotFoundException) {
        throw IllegalStateException(
            "$hasher requires BouncyCastle on the runtime classpath. " +
                "Add a dependency on org.bouncycastle:bcprov-jdk18on to use it.",
            cause
        )
    }
}

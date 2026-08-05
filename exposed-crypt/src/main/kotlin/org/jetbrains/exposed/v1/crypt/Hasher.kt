package org.jetbrains.exposed.v1.crypt

/**
 * Base class responsible for the one-way hashing of data.
 *
 * Unlike [Encryptor], a [Hasher] cannot recover the original value: the only operation available on a stored
 * hash is checking whether some plaintext produces it. Use this for values that never need to be read back,
 * such as passwords.
 *
 * [BCryptHasher], [Argon2Hasher], [Pbkdf2Hasher], and [SCryptHasher] cover the algorithms recommended by the
 * [OWASP password storage cheat sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).
 * To hash with something else, wrap any Spring Security `PasswordEncoder` in a [PasswordEncoderHasher], or
 * implement the two operations declared here:
 *
 * ```kotlin
 * class CustomHasher : Hasher {
 *     override fun hash(plainText: String): Hashed = Hashed(this, customLibrary.hash(plainText))
 *
 *     override fun matches(plainText: String, encodedValue: String): Boolean =
 *         customLibrary.verify(plainText, encodedValue)
 * }
 * ```
 */
interface Hasher {
    /** Hashes [plainText] into the value to be stored, salting it if the algorithm supports salting. */
    fun hash(plainText: String): Hashed

    /** Returns whether [plainText] hashes to [encodedValue], which is expected to be an already hashed value. */
    fun matches(plainText: String, encodedValue: String): Boolean
}

/**
 * A [hashed] column holds these rather than strings, which is what keeps a plaintext value from being stored in
 * one by accident, or compared against one in SQL. Assigning a [Hashed] read from the database back to a column
 * stores it unchanged, without hashing it a second time.
 *
 * Constructing one directly wraps [encodedValue] as it is, without hashing it. Do that to adopt hashes produced
 * elsewhere, such as when migrating existing values into a [hashed] column; to hash a plaintext value, use
 * [Hasher.hash].
 */
class Hashed(
    private val hasher: Hasher,
    /** The encoded hash, as stored in the database. */
    val encodedValue: String
) {
    /** Returns whether [plainText] hashes to this value, as determined by the [Hasher] that produced it. */
    fun matches(plainText: String): Boolean = hasher.matches(plainText, encodedValue)

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other !is Hashed -> false
        else -> encodedValue == other.encodedValue
    }

    override fun hashCode(): Int = encodedValue.hashCode()

    override fun toString(): String = "Hashed(***)"
}

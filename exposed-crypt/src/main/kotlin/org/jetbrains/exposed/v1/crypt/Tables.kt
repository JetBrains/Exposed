package org.jetbrains.exposed.v1.crypt

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnTransformer
import org.jetbrains.exposed.v1.core.ColumnWithTransform
import org.jetbrains.exposed.v1.core.Table

/**
 * Creates a character column, with the specified [name], for storing encrypted strings.
 *
 * @param name Name of the column
 * @param cipherTextLength Maximum expected length of encrypted value
 * @param encryptor [Encryptor] responsible for performing encryption and decryption of stored values
 * @sample org.jetbrains.exposed.v1.crypt.EncryptedColumnTests.testEncryptedColumnTypeWithAString
 */
fun Table.encryptedVarchar(name: String, cipherTextLength: Int, encryptor: Encryptor): Column<String> =
    registerColumn(name, EncryptedVarCharColumnType(encryptor, cipherTextLength))

/**
 * Creates a binary column, with the specified [name], for storing encrypted binary strings.
 *
 * @param name Name of the column
 * @param cipherByteLength Maximum expected length of encrypted value in bytes
 * @param encryptor [Encryptor] responsible for performing encryption and decryption of stored values
 * @sample org.jetbrains.exposed.v1.crypt.EncryptedColumnTests.testEncryptedColumnTypeWithAString
 */
fun Table.encryptedBinary(name: String, cipherByteLength: Int, encryptor: Encryptor): Column<ByteArray> =
    registerColumn(name, EncryptedBinaryColumnType(encryptor, cipherByteLength))

/**
 * Transforms this character column into one that stores one-way hashed values, using the provided [hasher].
 *
 * ```kotlin
 * val passwordHasher = BCryptHasher()
 *
 * object Users : IntIdTable() {
 *     val password = text("password").hashed(passwordHasher)
 * }
 *
 * Users.insert { it[password] = passwordHasher.hash("s3cret") }
 *
 * val user = Users.selectAll().where { Users.id eq id }.single()
 * val granted = user[Users.password].matches(submittedPassword)
 * ```
 *
 * Leaving [hasher] out leaves nothing to hash with either, as the default is not held anywhere the calling code
 * can reach. Ask the column for it instead, with [hash]:
 *
 * ```kotlin
 * object Users : IntIdTable() {
 *     val password = text("password").hashed()
 * }
 *
 * Users.insert { it[password] = Users.password.hash("s3cret") }
 * ```
 *
 * [hash] works whichever way the column was declared, and is worth preferring even when a hasher is at hand:
 * it always hashes with the one the column verifies with, which a separately held [Hasher] is not guaranteed
 * to be.
 *
 * A stored value can only be verified by the algorithm that produced it, so changing [hasher] on a column that
 * already holds data stops the values written before the change from matching.
 *
 * @param hasher [Hasher] responsible for hashing values and for verifying them against stored hashes. Defaults
 * to [BCryptHasher], the provided algorithm that needs nothing beyond `exposed-crypt` itself.
 * @return A new column holding [Hashed] values.
 */
fun Column<String>.hashed(hasher: Hasher = BCryptHasher()): Column<Hashed> =
    with(table) { this@hashed.transform(HashingTransformer(hasher)) }

/**
 * Transforms this nullable character column into one that stores one-way hashed values, using the provided
 * [hasher], and leaving `null` values untouched.
 *
 * @param hasher [Hasher] responsible for hashing values and for verifying them against stored hashes. Defaults
 * to [BCryptHasher], the provided algorithm that needs nothing beyond `exposed-crypt` itself.
 * @return A new nullable column holding [Hashed] values.
 * @see hashed
 */
@JvmName("hashedNullable")
fun Column<String?>.hashed(hasher: Hasher = BCryptHasher()): Column<Hashed?> =
    with(table) { this@hashed.transform(NullableHashingTransformer(hasher)) }

/**
 * Hashes [plainText] with the [Hasher] this column was declared with, ready to be stored in it.
 *
 * Using this rather than a separately held [Hasher] rules out hashing a value with one algorithm and storing it
 * in a column that verifies with another, which the column cannot detect and which makes every later
 * [Hashed.matches] fail. It is the only way to hash for a column declared without an explicit hasher:
 *
 * ```kotlin
 * object Users : IntIdTable() {
 *     val password = text("password").hashed()
 * }
 *
 * Users.insert { it[password] = Users.password.hash("s3cret") }
 * ```
 *
 * @param plainText Value to hash
 * @return The [Hashed] result of applying this column's [Hasher] to [plainText].
 */
fun Column<Hashed>.hash(plainText: String): Hashed = hasher.hash(plainText)

/**
 * Hashes [plainText] with the [Hasher] this nullable column was declared with, ready to be stored in it.
 *
 * @param plainText Value to hash
 * @return The [Hashed] result of applying this column's [Hasher] to [plainText].
 * @see hash
 */
@JvmName("hashNullable")
fun Column<Hashed?>.hash(plainText: String): Hashed = hasher.hash(plainText)

/**
 * The [Hasher] behind a column created by [hashed].
 *
 * Transforms nest, each one delegating to the column type it was applied to, so the hashing transformer is not
 * necessarily the outermost one and the whole chain has to be searched.
 */
private val Column<*>.hasher: Hasher
    get() = generateSequence(columnType as? ColumnWithTransform<*, *>) { it.delegate as? ColumnWithTransform<*, *> }
        .firstNotNullOfOrNull { it.transformer.hasherOrNull() }
        ?: error("Column $name does not hash its values. Declare it with hashed() to be able to hash for it.")

/** The [Hasher] this transformer hashes with, or `null` if it does not hash at all. */
private fun ColumnTransformer<*, *>.hasherOrNull(): Hasher? = when (this) {
    is HashingTransformer -> hasher
    is NullableHashingTransformer -> hasher
    else -> null
}

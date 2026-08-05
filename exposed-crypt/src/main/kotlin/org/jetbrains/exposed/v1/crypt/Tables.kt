package org.jetbrains.exposed.v1.crypt

import org.jetbrains.exposed.v1.core.Column
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
 * @param hasher [Hasher] responsible for hashing values and for verifying them against stored hashes
 * @return A new column holding [Hashed] values.
 */
fun Column<String>.hashed(hasher: Hasher): Column<Hashed> =
    with(table) { this@hashed.transform(HashingTransformer(hasher)) }

/**
 * Transforms this nullable character column into one that stores one-way hashed values, using the provided
 * [hasher], and leaving `null` values untouched.
 *
 * @param hasher [Hasher] responsible for hashing values and for verifying them against stored hashes
 * @return A new nullable column holding [Hashed] values.
 * @see hashed
 */
@JvmName("hashedNullable")
fun Column<String?>.hashed(hasher: Hasher): Column<Hashed?> =
    with(table) { this@hashed.transform(NullableHashingTransformer(hasher)) }

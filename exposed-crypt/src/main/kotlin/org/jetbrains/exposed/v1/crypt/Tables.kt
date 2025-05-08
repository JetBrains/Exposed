package org.jetbrains.exposed.v1.crypt

import org.jetbrains.exposed.v1.sql.Column
import org.jetbrains.exposed.v1.sql.Table

/**
 * Creates a character column, with the specified [name], for storing encrypted strings.
 *
 * @param name Name of the column
 * @param cipherTextLength Maximum expected length of encrypted value
 * @param encryptor [Encryptor] responsible for performing encryption and decryption of stored values
 * @sample org.jetbrains.exposed.crypt.EncryptedColumnTests.testEncryptedColumnTypeWithAString
 */
fun Table.encryptedVarchar(name: String, cipherTextLength: Int, encryptor: Encryptor): Column<String> =
    registerColumn(name, EncryptedVarCharColumnType(encryptor, cipherTextLength))

/**
 * Creates a binary column, with the specified [name], for storing encrypted binary strings.
 *
 * @param name Name of the column
 * @param cipherByteLength Maximum expected length of encrypted value in bytes
 * @param encryptor [Encryptor] responsible for performing encryption and decryption of stored values
 * @sample org.jetbrains.exposed.crypt.EncryptedColumnTests.testEncryptedColumnTypeWithAString
 */
fun Table.encryptedBinary(name: String, cipherByteLength: Int, encryptor: Encryptor): Column<ByteArray> =
    registerColumn(name, EncryptedBinaryColumnType(encryptor, cipherByteLength))

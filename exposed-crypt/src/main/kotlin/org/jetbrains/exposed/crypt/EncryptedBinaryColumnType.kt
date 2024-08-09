package org.jetbrains.exposed.crypt

import org.jetbrains.exposed.sql.BinaryColumnType
import org.jetbrains.exposed.sql.ColumnTransformer
import org.jetbrains.exposed.sql.ColumnWithTransform

/**
 * Binary column for storing encrypted binary strings of a specific [length], using the provided [encryptor].
 *
 * @sample org.jetbrains.exposed.crypt.encryptedBinary
 */
class EncryptedBinaryColumnType(
    private val encryptor: Encryptor,
    length: Int
) : ColumnWithTransform<ByteArray, ByteArray>(BinaryColumnType(length), ByteArrayEncryptionTransformer(encryptor))

class ByteArrayEncryptionTransformer(private val encryptor: Encryptor) : ColumnTransformer<ByteArray, ByteArray> {
    override fun unwrap(value: ByteArray) = encryptor.encrypt(String(value)).toByteArray()

    override fun wrap(value: ByteArray) = encryptor.decrypt(String(value)).toByteArray()
}

package org.jetbrains.exposed.v1.crypt

import org.jetbrains.exposed.v1.core.BinaryColumnType
import org.jetbrains.exposed.v1.core.ColumnTransformer
import org.jetbrains.exposed.v1.core.ColumnWithTransform

/**
 * Binary column for storing encrypted binary strings of a specific [length], using the provided [encryptor].
 *
 * @sample org.jetbrains.exposed.v1.crypt.encryptedBinary
 */
class EncryptedBinaryColumnType(
    private val encryptor: Encryptor,
    length: Int
) : ColumnWithTransform<ByteArray, ByteArray>(BinaryColumnType(length), ByteArrayEncryptionTransformer(encryptor))

class ByteArrayEncryptionTransformer(private val encryptor: Encryptor) : ColumnTransformer<ByteArray, ByteArray> {
    override fun unwrap(value: ByteArray) = encryptor.encrypt(String(value)).toByteArray()

    override fun wrap(value: ByteArray) = encryptor.decrypt(String(value)).toByteArray()
}

package org.jetbrains.exposed.crypt

import org.jetbrains.exposed.sql.BinaryColumnType

/**
 * Binary column for storing encrypted binary strings of a specific [length], using the provided [encryptor].
 *
 * @sample org.jetbrains.exposed.crypt.encryptedBinary
 */
class EncryptedBinaryColumnType(
    private val encryptor: Encryptor,
    length: Int
) : BinaryColumnType(length) {
    override fun nonNullValueToString(value: ByteArray): String {
        return super.nonNullValueToString(notNullValueToDB(value))
    }

    override fun notNullValueToDB(value: ByteArray): ByteArray = encryptor.encrypt(String(value)).toByteArray()

    override fun valueFromDB(value: Any): ByteArray {
        val encryptedByte = super.valueFromDB(value)
        return encryptor.decrypt(String(encryptedByte)).toByteArray()
    }

    override fun validateValueBeforeUpdate(value: ByteArray?) {
        if (value != null) {
            super.validateValueBeforeUpdate(notNullValueToDB(value))
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as EncryptedBinaryColumnType

        if (encryptor != other.encryptor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + encryptor.hashCode()
        return result
    }
}

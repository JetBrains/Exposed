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
    override fun notNullValueToDB(value: Any): Any {
        if (value !is ByteArray) {
            error("Unexpected value of type Byte: $value of ${value::class.qualifiedName}")
        }

        return encryptor.encrypt(String(value)).toByteArray()
    }

    override fun valueFromDB(value: Any): Any {
        val encryptedByte = super.valueFromDB(value)

        if (encryptedByte !is ByteArray) {
            error("Unexpected value of type Byte: $value of ${value::class.qualifiedName}")
        }

        return encryptor.decrypt(String(encryptedByte)).toByteArray()
    }

    override fun validateValueBeforeUpdate(value: Any?) {
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

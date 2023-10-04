package org.jetbrains.exposed.crypt

import org.jetbrains.exposed.sql.VarCharColumnType

/**
 * Character column for storing encrypted strings, using the provided [encryptor],
 * with the specified maximum [colLength].
 */
class EncryptedVarCharColumnType(
    private val encryptor: Encryptor,
    colLength: Int,
) : VarCharColumnType(colLength) {
    override fun notNullValueToDB(value: Any): Any {
        return encryptor.encrypt(value.toString())
    }

    override fun valueFromDB(value: Any): Any {
        val encryptedStr = super.valueFromDB(value)

        if (encryptedStr !is String) {
            error("Unexpected value of type String: $value of ${value::class.qualifiedName}")
        }

        return encryptor.decrypt(encryptedStr)
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

        other as EncryptedVarCharColumnType

        if (encryptor != other.encryptor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + encryptor.hashCode()
        return result
    }
}

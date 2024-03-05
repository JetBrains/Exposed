package org.jetbrains.exposed.crypt

import org.jetbrains.exposed.sql.VarCharColumnType

/**
 * Character column for storing encrypted strings, using the provided [encryptor],
 * with the specified maximum [colLength].
 *
 * @sample org.jetbrains.exposed.crypt.encryptedVarchar
 */
class EncryptedVarCharColumnType(
    private val encryptor: Encryptor,
    colLength: Int,
) : VarCharColumnType(colLength) {
    override fun nonNullValueToString(value: String): String {
        return super.nonNullValueToString(notNullValueToDB(value))
    }

    override fun notNullValueToDB(value: String): String {
        return encryptor.encrypt(value)
    }

    override fun valueFromDB(value: Any): String {
        val encryptedStr = super.valueFromDB(value)
        return encryptor.decrypt(encryptedStr)
    }

    override fun validateValueBeforeUpdate(value: String?) {
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

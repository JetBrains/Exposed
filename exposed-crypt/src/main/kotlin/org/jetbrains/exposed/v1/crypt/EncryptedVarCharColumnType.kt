package org.jetbrains.exposed.v1.crypt

import org.jetbrains.exposed.v1.sql.ColumnTransformer
import org.jetbrains.exposed.v1.sql.ColumnWithTransform
import org.jetbrains.exposed.v1.sql.VarCharColumnType

/**
 * Character column for storing encrypted strings, using the provided [encryptor],
 * with the specified maximum [colLength].
 *
 * @sample org.jetbrains.exposed.crypt.encryptedVarchar
 */
class EncryptedVarCharColumnType(
    private val encryptor: Encryptor,
    colLength: Int
) : ColumnWithTransform<String, String>(VarCharColumnType(colLength), StringEncryptionTransformer(encryptor))

class StringEncryptionTransformer(private val encryptor: Encryptor) : ColumnTransformer<String, String> {
    override fun unwrap(value: String) = encryptor.encrypt(value)

    override fun wrap(value: String) = encryptor.decrypt(value)
}

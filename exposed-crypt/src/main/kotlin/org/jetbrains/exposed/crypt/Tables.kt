package org.jetbrains.exposed.crypt

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

fun Table.encryptedVarchar(name: String, cipherTextLength: Int, encryptor: Encryptor): Column<String> =
    registerColumn(name, EncryptedVarCharColumnType(encryptor, cipherTextLength))

fun Table.encryptedBinary(name: String, cipherByteLength: Int, encryptor: Encryptor): Column<ByteArray> =
    registerColumn(name, EncryptedBinaryColumnType(encryptor, cipherByteLength))

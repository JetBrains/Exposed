package org.jetbrains.exposed.v1.core

import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect

internal class BinaryLiteralColumnType : BasicBinaryColumnType() {
    override fun nonNullValueToString(value: ByteArray): String {
        val literal = BlobColumnType().nonNullValueToString(ExposedBlob(value))
        return if (currentDialect is PostgreSQLDialect) "$literal::bytea" else literal
    }
}

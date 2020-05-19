package org.jetbrains.exposed.sql.statements.api

class ExposedBlob(val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExposedBlob) return false

        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int = bytes.contentHashCode()
}
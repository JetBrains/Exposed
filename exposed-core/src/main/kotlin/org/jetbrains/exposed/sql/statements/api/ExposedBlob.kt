package org.jetbrains.exposed.sql.statements.api

import java.io.InputStream

class ExposedBlob(val inputStream: InputStream) {
    constructor(bytes: ByteArray) : this (bytes.inputStream())

    val bytes get() = inputStream.readBytes().also { inputStream.reset() }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExposedBlob) return false


        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int = bytes.contentHashCode()
}

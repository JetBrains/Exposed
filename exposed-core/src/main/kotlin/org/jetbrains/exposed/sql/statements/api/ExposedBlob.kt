package org.jetbrains.exposed.sql.statements.api

import java.io.InputStream

class ExposedBlob(inputStream: InputStream) {
    constructor(bytes: ByteArray) : this (bytes.inputStream())

    var inputStream = inputStream
        private set

    val bytes get() = inputStream.readBytes().also {
        if (inputStream.markSupported())
            inputStream.reset()
        else
            inputStream = it.inputStream()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExposedBlob) return false

        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = bytes.contentHashCode()
}

package org.jetbrains.exposed.v1.core.statements.api

import org.jetbrains.exposed.v1.core.vendors.OracleDialect
import org.jetbrains.exposed.v1.core.vendors.currentDialectIfAvailable
import java.io.IOException
import java.io.InputStream

/** Represents a wrapper for an [inputStream] of bytes to be used in binary columns. */
class ExposedBlob(inputStream: InputStream) {
    constructor(bytes: ByteArray) : this (bytes.inputStream())

    /** The [InputStream] contained by this wrapper. */
    var inputStream = inputStream
        private set

    /** The `ByteArray` returned as a result of reading the contained [InputStream] completely. */
    val bytes: ByteArray
        get() = inputStream.readBytes().also {
            if (inputStream.markSupported()) {
                try {
                    inputStream.reset()
                } catch (_: IOException) {
                    if (currentDialectIfAvailable is OracleDialect) {
                        inputStream = it.inputStream()
                    }
                }
            } else {
                inputStream = it.inputStream()
            }
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExposedBlob) return false

        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = bytes.contentHashCode()

    /** Returns the hex-encoded string of the contained [InputStream] after being read. */
    fun hexString(): String = bytes.toHexString()

    /** Returns the hex-encoded string of a ByteArray. */
    private fun ByteArray.toHexString(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }
}

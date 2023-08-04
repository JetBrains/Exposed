package org.jetbrains.exposed.sql.statements.api

import org.jetbrains.exposed.sql.vendors.OracleDialect
import org.jetbrains.exposed.sql.vendors.currentDialectIfAvailable
import java.io.IOException
import java.io.InputStream

class ExposedBlob(inputStream: InputStream) {
    constructor(bytes: ByteArray) : this (bytes.inputStream())

    var inputStream = inputStream
        private set

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

    fun hexString(): String = bytes.toHexString()

    /** Returns the hex-encoded string of a ByteArray. */
    private fun ByteArray.toHexString(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }
}

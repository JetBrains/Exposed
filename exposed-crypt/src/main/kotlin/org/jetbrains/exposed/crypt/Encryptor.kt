package org.jetbrains.exposed.crypt

class Encryptor(
    val encryptFn: (String) -> String,
    val decryptFn: (String) -> String,
    val maxColLengthFn: (Int) -> Int
) {
    fun encrypt(str: String) = encryptFn(str)
    fun decrypt(str: String) = decryptFn(str)
    fun maxColLength(inputByteSize: Int) = maxColLengthFn(inputByteSize)
}

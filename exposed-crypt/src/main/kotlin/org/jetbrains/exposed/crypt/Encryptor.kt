package org.jetbrains.exposed.crypt

/**
 * Base cipher class responsible for the encryption and decryption of data.
 */
class Encryptor(
    /** Returns the function that converts a plaintext String to ciphertext. */
    val encryptFn: (String) -> String,
    /** Returns the function that converts ciphertext to a plaintext String. */
    val decryptFn: (String) -> String,
    /** Returns the function that converts the expected input length into the maximum encoded length to be stored. */
    val maxColLengthFn: (Int) -> Int
) {
    /** Returns an encrypted value using [encryptFn]. */
    fun encrypt(str: String) = encryptFn(str)

    /** Returns a decrypted value using [decryptFn]. */
    fun decrypt(str: String) = decryptFn(str)

    /**
     * Returns the maximum column length needed to store an encrypted value, using the specified [inputByteSize],
     * and determined by [maxColLengthFn].
     */
    fun maxColLength(inputByteSize: Int) = maxColLengthFn(inputByteSize)
}

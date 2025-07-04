package org.jetbrains.exposed.v1.crypt

/**
 * Base cipher class responsible for the encryption and decryption of data.
 *
 * @sample org.jetbrains.exposed.v1.crypt.Algorithms.AES_256_PBE_GCM
 */
class Encryptor(
    /** Encrypt a plaintext string to ciphertext. */
    val encryptFn: (String) -> String,
    /** Decrypt ciphertext to a plaintext string. */
    val decryptFn: (String) -> String,
    /** Convert the expected input length into the maximum encoded length to be stored. */
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

package org.jetbrains.exposed.v1.crypt

import org.springframework.security.crypto.encrypt.AesBytesEncryptor
import org.springframework.security.crypto.keygen.KeyGenerators
import org.springframework.security.crypto.util.EncodingUtils
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.ceil

/**
 * Symmetric-key block ciphers for performing encryption and decryption.
 *
 * @sample org.jetbrains.exposed.crypt.EncryptedColumnTests.testEncryptedColumnTypeWithAString
 */
object Algorithms {
    @Suppress("MagicNumber")
    private fun base64EncodedLength(byteSize: Int): Int = ceil(byteSize.toDouble() / 3).toInt() * 4
    private fun paddingLen(len: Int, blockSize: Int): Int = if (len % blockSize == 0) 0 else blockSize - len % blockSize
    private val base64Decoder = Base64.getDecoder()
    private val base64Encoder = Base64.getEncoder()

    private const val AES_256_GCM_BLOCK_LENGTH = 16
    private const val AES_256_GCM_TAG_LENGTH = 16

    /** Returns an [Encryptor] that uses AES encryption with its cipher algorithm set to GCM mode. */
    @Suppress("FunctionNaming")
    fun AES_256_PBE_GCM(password: CharSequence, salt: CharSequence): Encryptor {
        return AesBytesEncryptor(
            password.toString(),
            salt,
            KeyGenerators.secureRandom(AES_256_GCM_BLOCK_LENGTH),
            AesBytesEncryptor.CipherAlgorithm.GCM
        ).run {
            Encryptor(
                { base64Encoder.encodeToString(encrypt(it.toByteArray())) },
                { String(decrypt(base64Decoder.decode(it))) },
                { inputLen ->
                    base64EncodedLength(AES_256_GCM_BLOCK_LENGTH + inputLen + AES_256_GCM_TAG_LENGTH)
                }
            )
        }
    }

    private const val AES_256_CBC_BLOCK_LENGTH = 16

    /** Returns an [Encryptor] that uses AES encryption with its cipher algorithm set to CBC mode. */
    @Suppress("FunctionNaming")
    fun AES_256_PBE_CBC(password: CharSequence, salt: CharSequence): Encryptor {
        return AesBytesEncryptor(
            password.toString(),
            salt,
            KeyGenerators.secureRandom(AES_256_CBC_BLOCK_LENGTH)
        ).run {
            Encryptor(
                { base64Encoder.encodeToString(encrypt(it.toByteArray())) },
                { String(decrypt(base64Decoder.decode(it))) },
                { inputLen ->
                    val paddingSize = (AES_256_CBC_BLOCK_LENGTH - inputLen % AES_256_CBC_BLOCK_LENGTH)
                    base64EncodedLength(AES_256_CBC_BLOCK_LENGTH + inputLen + paddingSize)
                }
            )
        }
    }

    private const val BLOW_FISH_BLOCK_LENGTH = 8

    /** Returns an [Encryptor] that uses a Blowfish algorithm. */
    @Suppress("FunctionNaming")
    fun BLOW_FISH(key: CharSequence): Encryptor {
        val ks = SecretKeySpec(key.toString().toByteArray(), "Blowfish")

        return Encryptor(
            { plainText ->
                val cipher = Cipher.getInstance("Blowfish")
                cipher.init(Cipher.ENCRYPT_MODE, ks)

                val encryptedBytes = cipher.doFinal(plainText.toByteArray())
                base64Encoder.encodeToString(encryptedBytes)
            },
            { encryptedText ->
                val cipher = Cipher.getInstance("Blowfish")
                cipher.init(Cipher.DECRYPT_MODE, ks)

                val decryptedBytes = cipher.doFinal(base64Decoder.decode(encryptedText))
                String(decryptedBytes)
            },
            { base64EncodedLength(it + paddingLen(it, BLOW_FISH_BLOCK_LENGTH)) }
        )
    }

    private const val TRIPLE_DES_KEY_LENGTH = 24
    private const val TRIPLE_DES_BLOCK_LENGTH = 8

    /** Returns an [Encryptor] that uses a Triple DES algorithm. */
    @Suppress("FunctionNaming", "UseRequire")
    fun TRIPLE_DES(secretKey: CharSequence): Encryptor {
        if (secretKey.toString().toByteArray().size != TRIPLE_DES_KEY_LENGTH) {
            throw IllegalArgumentException("secretKey must have 24 bytes")
        }
        val ks = SecretKeySpec(secretKey.toString().toByteArray(), "TripleDES")

        val ivGenerator = KeyGenerators.secureRandom(TRIPLE_DES_BLOCK_LENGTH)

        return Encryptor(
            { plainText ->
                val cipher = Cipher.getInstance("TripleDES/CBC/PKCS5Padding")
                val iv = ivGenerator.generateKey()
                cipher.init(Cipher.ENCRYPT_MODE, ks, IvParameterSpec(iv))

                val encryptedBytes = cipher.doFinal(plainText.toByteArray())
                base64Encoder.encodeToString(EncodingUtils.concatenate(iv, encryptedBytes))
            },
            { encryptedText ->
                val cipher = Cipher.getInstance("TripleDES/CBC/PKCS5Padding")
                val decodedBytes = base64Decoder.decode(encryptedText.toByteArray())

                val iv = EncodingUtils.subArray(decodedBytes, 0, TRIPLE_DES_BLOCK_LENGTH)
                cipher.init(Cipher.DECRYPT_MODE, ks, IvParameterSpec(iv))

                val decryptedBytes = cipher.doFinal(EncodingUtils.subArray(decodedBytes, TRIPLE_DES_BLOCK_LENGTH, decodedBytes.size))
                String(decryptedBytes)
            },
            { base64EncodedLength(TRIPLE_DES_BLOCK_LENGTH + it + paddingLen(it, TRIPLE_DES_BLOCK_LENGTH)) }
        )
    }
}

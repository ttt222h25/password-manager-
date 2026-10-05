package com.localvault.core

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class WrongPasswordException : Exception("Wrong master password")

class InvalidVaultFileException(message: String) : Exception(message)

/** An AES key derived from the master password, plus the KDF parameters needed to derive it again. */
class VaultKey internal constructor(
    internal val secret: SecretKey,
    val salt: ByteArray,
    val iterations: Int,
)

/**
 * Encrypted file layout (all integers big-endian):
 *
 * ```
 * "LVLT" | version (1 byte) | PBKDF2 iterations (4) | salt length (1) | salt | IV length (1) | IV | AES-GCM ciphertext+tag
 * ```
 *
 * The header is passed to AES-GCM as additional authenticated data, so any change to it
 * (or to the ciphertext) makes decryption fail.
 */
object VaultCrypto {
    const val DEFAULT_ITERATIONS = 600_000
    private const val MIN_ITERATIONS = 1_000
    private const val MAX_ITERATIONS = 10_000_000

    private val MAGIC = byteArrayOf('L'.code.toByte(), 'V'.code.toByte(), 'L'.code.toByte(), 'T'.code.toByte())
    private const val FORMAT_VERSION: Byte = 1
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128

    private val random = SecureRandom()

    /** Derives a brand-new key (with a fresh random salt) from [password]. */
    fun newKey(password: CharArray, iterations: Int = DEFAULT_ITERATIONS): VaultKey {
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "Unsupported iteration count" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        return VaultKey(deriveSecret(password, salt, iterations), salt, iterations)
    }

    fun encrypt(key: VaultKey, plaintext: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val header = header(key.salt, key.iterations, iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key.secret, GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(header)
        return header + cipher.doFinal(plaintext)
    }

    /**
     * Decrypts [data] with [password]. Returns the key (so later saves don't need to run the slow KDF
     * again) together with the plaintext.
     */
    fun decrypt(password: CharArray, data: ByteArray): Pair<VaultKey, ByteArray> {
        val buf = ByteBuffer.wrap(data)
        try {
            val magic = ByteArray(MAGIC.size).also { buf.get(it) }
            if (!magic.contentEquals(MAGIC)) throw InvalidVaultFileException("Not a vault file")
            val version = buf.get()
            if (version != FORMAT_VERSION) throw InvalidVaultFileException("Unsupported vault version $version")
            val iterations = buf.getInt()
            if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) throw InvalidVaultFileException("Corrupt vault header")
            val salt = ByteArray(buf.get().toInt() and 0xFF).also { buf.get(it) }
            val iv = ByteArray(buf.get().toInt() and 0xFF).also { buf.get(it) }
            if (salt.size != SALT_BYTES || iv.size != IV_BYTES) throw InvalidVaultFileException("Corrupt vault header")
            val headerLength = buf.position()
            val ciphertext = ByteArray(buf.remaining()).also { buf.get(it) }

            val key = VaultKey(deriveSecret(password, salt, iterations), salt, iterations)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key.secret, GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(data, 0, headerLength)
            val plaintext = try {
                cipher.doFinal(ciphertext)
            } catch (e: AEADBadTagException) {
                throw WrongPasswordException()
            }
            return key to plaintext
        } catch (e: java.nio.BufferUnderflowException) {
            throw InvalidVaultFileException("Vault file is truncated")
        }
    }

    private fun header(salt: ByteArray, iterations: Int, iv: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { stream ->
            stream.write(MAGIC)
            stream.writeByte(FORMAT_VERSION.toInt())
            stream.writeInt(iterations)
            stream.writeByte(salt.size)
            stream.write(salt)
            stream.writeByte(iv.size)
            stream.write(iv)
        }
        return out.toByteArray()
    }

    private fun deriveSecret(password: CharArray, salt: ByteArray, iterations: Int): SecretKey {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val bytes = factory.generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES").also { bytes.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }
}

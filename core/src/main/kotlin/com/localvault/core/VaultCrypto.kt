package com.localvault.core

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class WrongPasswordException : Exception("Wrong password")

class InvalidVaultFileException(message: String) : Exception(message)

/**
 * Two encrypted file formats, both AES-256-GCM with the header passed as additional authenticated
 * data (so changing any byte makes decryption fail). Integers are big-endian.
 *
 * Version 1, password based (backup files):
 * ```
 * "LVLT" | 1 | PBKDF2 iterations (4) | salt length (1) | salt | IV length (1) | IV | ciphertext+tag
 * ```
 *
 * Version 2, key based (the vault on the phone, keyed by the Android Keystore):
 * ```
 * "LVLT" | 2 | IV length (1) | IV | ciphertext+tag
 * ```
 */
object VaultCrypto {
    const val DEFAULT_ITERATIONS = 600_000
    private const val MIN_ITERATIONS = 1_000
    private const val MAX_ITERATIONS = 10_000_000

    private val MAGIC = byteArrayOf('L'.code.toByte(), 'V'.code.toByte(), 'L'.code.toByte(), 'T'.code.toByte())
    private const val PASSWORD_FORMAT: Byte = 1
    private const val KEY_FORMAT: Byte = 2
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private val random = SecureRandom()

    fun encryptWithPassword(password: CharArray, plaintext: ByteArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "Unsupported iteration count" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val header = header { stream ->
            stream.writeByte(PASSWORD_FORMAT.toInt())
            stream.writeInt(iterations)
            stream.writeByte(salt.size)
            stream.write(salt)
            stream.writeByte(iv.size)
            stream.write(iv)
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(header)
        return header + cipher.doFinal(plaintext)
    }

    /** @throws WrongPasswordException if [password] is wrong or the file was changed. */
    fun decryptWithPassword(password: CharArray, data: ByteArray): ByteArray = parse(data) { buf ->
        if (buf.get() != PASSWORD_FORMAT) throw InvalidVaultFileException("Not a backup file")
        val iterations = buf.getInt()
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) throw InvalidVaultFileException("Corrupt file header")
        val salt = ByteArray(buf.get().toInt() and 0xFF).also { buf.get(it) }
        val iv = ByteArray(buf.get().toInt() and 0xFF).also { buf.get(it) }
        if (salt.size != SALT_BYTES || iv.size != IV_BYTES) throw InvalidVaultFileException("Corrupt file header")
        val headerLength = buf.position()
        val ciphertext = ByteArray(buf.remaining()).also { buf.get(it) }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(data, 0, headerLength)
        try {
            cipher.doFinal(ciphertext)
        } catch (e: AEADBadTagException) {
            throw WrongPasswordException()
        }
    }

    fun encryptWithKey(key: SecretKey, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // Let the provider pick the random IV: the Android Keystore refuses caller-chosen IVs.
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val header = header { stream ->
            stream.writeByte(KEY_FORMAT.toInt())
            stream.writeByte(iv.size)
            stream.write(iv)
        }
        cipher.updateAAD(header)
        return header + cipher.doFinal(plaintext)
    }

    /** @throws InvalidVaultFileException if the file is damaged or was encrypted with another key. */
    fun decryptWithKey(key: SecretKey, data: ByteArray): ByteArray = parse(data) { buf ->
        if (buf.get() != KEY_FORMAT) throw InvalidVaultFileException("Unsupported vault version")
        val iv = ByteArray(buf.get().toInt() and 0xFF).also { buf.get(it) }
        if (iv.size != IV_BYTES) throw InvalidVaultFileException("Corrupt vault header")
        val headerLength = buf.position()
        val ciphertext = ByteArray(buf.remaining()).also { buf.get(it) }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(data, 0, headerLength)
        try {
            cipher.doFinal(ciphertext)
        } catch (e: AEADBadTagException) {
            throw InvalidVaultFileException("Vault file is damaged")
        }
    }

    /** Checks the magic bytes, then hands the rest to [body]; a short file becomes InvalidVaultFileException. */
    private fun <T> parse(data: ByteArray, body: (ByteBuffer) -> T): T {
        val buf = ByteBuffer.wrap(data)
        try {
            val magic = ByteArray(MAGIC.size).also { buf.get(it) }
            if (!magic.contentEquals(MAGIC)) throw InvalidVaultFileException("Not a Vault file")
            return body(buf)
        } catch (e: BufferUnderflowException) {
            throw InvalidVaultFileException("File is truncated")
        }
    }

    private fun header(fields: (DataOutputStream) -> Unit): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { stream ->
            stream.write(MAGIC)
            fields(stream)
        }
        return out.toByteArray()
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKey {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val bytes = factory.generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES").also { bytes.fill(0) }
        } catch (e: GeneralSecurityException) {
            throw IllegalStateException("Key derivation failed", e)
        } finally {
            spec.clearPassword()
        }
    }
}

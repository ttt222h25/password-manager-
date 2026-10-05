package com.localvault.core

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.crypto.SecretKey

/**
 * Reads and writes the vault file, encrypted with a device key (on Android, a key that lives in the
 * phone's secure hardware and can't be copied out). Backups are encrypted with a password instead,
 * so they can be restored on another phone. Nothing here touches the network.
 */
class VaultStore(
    private val file: File,
    private val keyProvider: () -> SecretKey,
    private val backupIterations: Int = VaultCrypto.DEFAULT_ITERATIONS,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Loads the vault, creating one with the default folders the first time. */
    fun load(): Vault {
        if (!file.isFile) {
            return Vault.withDefaultFolders().also(::save)
        }
        return decode(VaultCrypto.decryptWithKey(keyProvider(), file.readBytes()))
    }

    fun save(vault: Vault) {
        val plaintext = encode(vault)
        try {
            writeAtomically(VaultCrypto.encryptWithKey(keyProvider(), plaintext))
        } finally {
            plaintext.fill(0)
        }
    }

    /** A backup file containing [vault], encrypted with [password]. */
    fun exportBackup(vault: Vault, password: CharArray): ByteArray {
        val plaintext = encode(vault)
        try {
            return VaultCrypto.encryptWithPassword(password, plaintext, backupIterations)
        } finally {
            plaintext.fill(0)
        }
    }

    /**
     * Replaces the vault with the contents of a backup (including backups made by the old
     * master-password version of the app, which used the same file format).
     *
     * @throws WrongPasswordException if [password] does not open the backup.
     * @throws InvalidVaultFileException if [backup] is not a Vault backup.
     */
    fun importBackup(backup: ByteArray, password: CharArray): Vault {
        val vault = decode(VaultCrypto.decryptWithPassword(password, backup))
        save(vault)
        return vault
    }

    private fun encode(vault: Vault): ByteArray =
        json.encodeToString(Vault.serializer(), vault).encodeToByteArray()

    private fun decode(plaintext: ByteArray): Vault {
        try {
            return json.decodeFromString(Vault.serializer(), plaintext.decodeToString())
        } catch (e: SerializationException) {
            throw InvalidVaultFileException("Vault contents are unreadable")
        } finally {
            plaintext.fill(0)
        }
    }

    /** Writes to a temp file first and then renames it, so a crash never leaves a half-written vault. */
    private fun writeAtomically(bytes: ByteArray) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(bytes)
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}

package com.localvault.core

import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** An unlocked vault: the decrypted contents plus the key needed to save changes. */
class UnlockedVault internal constructor(
    internal val key: VaultKey,
    val vault: Vault,
) {
    internal fun withVault(vault: Vault) = UnlockedVault(key, vault)
}

/** Reads and writes the encrypted vault file. Nothing here touches the network. */
class VaultStore(
    private val file: File,
    private val iterations: Int = VaultCrypto.DEFAULT_ITERATIONS,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun exists(): Boolean = file.isFile

    /** Creates a new vault (with the default folders) protected by [password]. */
    fun create(password: CharArray): UnlockedVault {
        val unlocked = UnlockedVault(VaultCrypto.newKey(password, iterations), Vault.withDefaultFolders())
        write(unlocked)
        return unlocked
    }

    /** @throws WrongPasswordException if [password] is wrong. */
    fun unlock(password: CharArray): UnlockedVault = decode(password, file.readBytes())

    /** Encrypts and saves [vault], returning the new unlocked state. */
    fun save(current: UnlockedVault, vault: Vault): UnlockedVault {
        val updated = current.withVault(vault)
        write(updated)
        return updated
    }

    /** Re-encrypts the vault under [newPassword] (new salt, new key). */
    fun changePassword(current: UnlockedVault, newPassword: CharArray): UnlockedVault {
        val updated = UnlockedVault(VaultCrypto.newKey(newPassword, iterations), current.vault)
        write(updated)
        return updated
    }

    /** The raw encrypted file, suitable as a backup. It stays encrypted with the master password. */
    fun exportEncrypted(): ByteArray = file.readBytes()

    /**
     * Replaces the current vault with a backup made by [exportEncrypted]. [password] is the master
     * password the backup was made with; it becomes the master password from now on.
     *
     * @throws WrongPasswordException if [password] does not open the backup.
     * @throws InvalidVaultFileException if [backup] is not a vault backup.
     */
    fun importEncrypted(backup: ByteArray, password: CharArray): UnlockedVault {
        val unlocked = decode(password, backup)
        writeBytes(backup)
        return unlocked
    }

    private fun decode(password: CharArray, data: ByteArray): UnlockedVault {
        val (key, plaintext) = VaultCrypto.decrypt(password, data)
        try {
            val vault = json.decodeFromString(Vault.serializer(), plaintext.decodeToString())
            return UnlockedVault(key, vault)
        } catch (e: kotlinx.serialization.SerializationException) {
            throw InvalidVaultFileException("Vault contents are unreadable")
        } finally {
            plaintext.fill(0)
        }
    }

    private fun write(unlocked: UnlockedVault) {
        val plaintext = json.encodeToString(Vault.serializer(), unlocked.vault).encodeToByteArray()
        try {
            writeBytes(VaultCrypto.encrypt(unlocked.key, plaintext))
        } finally {
            plaintext.fill(0)
        }
    }

    /** Writes to a temp file first and then renames it, so a crash never leaves a half-written vault. */
    private fun writeBytes(bytes: ByteArray) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(bytes)
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}

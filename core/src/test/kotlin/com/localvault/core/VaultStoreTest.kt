package com.localvault.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class VaultStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val deviceKey = newKey()

    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private val vaultFile get() = File(tmp.root, "vault.enc")

    // Low iteration count keeps the tests fast; the app uses VaultCrypto.DEFAULT_ITERATIONS.
    private fun store(file: File = vaultFile, key: SecretKey = deviceKey) =
        VaultStore(file, keyProvider = { key }, backupIterations = 1_000)

    private fun account(folderId: String, label: String = "Main") = Account(
        id = Vault.newId(),
        folderId = folderId,
        label = label,
        username = "player@example.com",
        password = "hunter2!",
        notes = "2FA on phone",
    )

    private fun vaultWithAccounts(): Vault {
        val vault = Vault.withDefaultFolders()
        val steam = vault.folders.first { it.name == "Steam" }
        return vault.upsertAccount(account(steam.id, "Main")).upsertAccount(account(steam.id, "Alt"))
    }

    @Test
    fun firstLoadCreatesDefaultFolders() {
        assertFalse(vaultFile.exists())
        val vault = store().load()
        assertTrue(vaultFile.exists())
        assertEquals(Vault.DEFAULT_FOLDER_NAMES, vault.folders.map { it.name })
        assertEquals(vault, store().load())
    }

    @Test
    fun savedAccountsSurviveReopening() {
        val vault = vaultWithAccounts()
        store().save(vault)
        assertEquals(vault, store().load())
    }

    @Test
    fun fileDoesNotContainPlaintext() {
        store().save(vaultWithAccounts())
        val raw = vaultFile.readBytes().decodeToString()
        assertFalse(raw.contains("hunter2"))
        assertFalse(raw.contains("Steam"))
        assertFalse(raw.contains("player@example.com"))
    }

    @Test(expected = InvalidVaultFileException::class)
    fun otherKeyCannotOpenVault() {
        store().save(vaultWithAccounts())
        store(key = newKey()).load()
    }

    @Test(expected = InvalidVaultFileException::class)
    fun tamperedVaultIsRejected() {
        store().save(vaultWithAccounts())
        val bytes = vaultFile.readBytes()
        bytes[bytes.size - 5] = (bytes[bytes.size - 5].toInt() xor 1).toByte()
        vaultFile.writeBytes(bytes)
        store().load()
    }

    @Test
    fun backupRestoresOnAnotherPhone() {
        val vault = vaultWithAccounts()
        val backup = store().exportBackup(vault, "backup-pw".toCharArray())
        assertFalse(backup.decodeToString().contains("hunter2"))

        val otherPhoneFile = File(tmp.root, "other/vault.enc")
        val otherPhone = store(otherPhoneFile, newKey())
        otherPhone.load()
        assertEquals(vault, otherPhone.importBackup(backup, "backup-pw".toCharArray()))
        assertEquals(vault, otherPhone.load())
    }

    @Test
    fun backupWithWrongPasswordKeepsExistingVault() {
        val original = vaultWithAccounts()
        store().save(original)
        val backup = store().exportBackup(Vault.withDefaultFolders(), "backup-pw".toCharArray())
        try {
            store().importBackup(backup, "wrong".toCharArray())
            throw AssertionError("import should fail")
        } catch (expected: WrongPasswordException) {
        }
        assertEquals(original, store().load())
    }

    @Test(expected = WrongPasswordException::class)
    fun tamperedBackupIsRejected() {
        val backup = store().exportBackup(vaultWithAccounts(), "backup-pw".toCharArray())
        backup[12] = (backup[12].toInt() xor 1).toByte() // inside the salt
        store().importBackup(backup, "backup-pw".toCharArray())
    }

    @Test(expected = InvalidVaultFileException::class)
    fun garbageIsNotABackup() {
        store().importBackup("hello world".toByteArray(), "pw".toCharArray())
    }

    @Test(expected = InvalidVaultFileException::class)
    fun vaultFileIsNotABackup() {
        store().save(vaultWithAccounts())
        store().importBackup(vaultFile.readBytes(), "pw".toCharArray())
    }

    @Test
    fun restoresBackupMadeByMasterPasswordVersion() {
        // Exported by Vault 1.0.1 (master-password version) with master password "old-master".
        val oldBackup = Base64.getDecoder().decode(
            "TFZMVAEAAAPoEHornZScNmhvJdxfAR7t+UwMK6Goz+4gp44Fg6AcqZGqqdKoA2irI5xM5qwFTUejUa2BB/mAoAZDzj" +
            "LNUe5eJ0v0W4nRZkAyS328OUbvn+wXu1Bs5aQWyezXCjDdmCBi5pqARkalnjtbKsm77FTyBJE3Pr+vHQSXgXq/MqH9" +
            "k07avmEjQE3jqeRDrZpLyfNTfJCiLEvSwcLFuBDkF245F8O5miOIo2886ZfzAgD5RFb7Zz7grXHvNzpyoFOHoEQKqS" +
            "2zC7GjBu6u3WS87ygV/wEwleDrVzxg+hgx1i3xeXlq3CxbX1mURPSbDItjWKuL9bKZiKRK4oaHGzJmhFtU4fb5tZmB" +
            "EM/Zy/aNtFQuCs8ISq0hmOqYhDYiWWgpstKbrSd35OwYIEm2I1zv8jsJwgG5rmZNq5RdfMdqd8gWBqJToMktseKCun" +
            "CQ1P1i4La44W6k74yXKz6f2b8Pfdd9Km3IwTvkzpLagi9iW1GqZOY9sVc5gGPZXDyoxSQD6sc9CqHJIuCjhU/KvBK4" +
            "0rgS4QFfT/G+mPtTaQutSlrj2kCIH+VTUBAdNvjtUf8VBtE3WB3UdzeqK9/ExS3WHVi3ybYibNSYH/DQtbcIMKZ8hC" +
            "Zd9cjpa7BOdsV+PY5PwyTiMBgUBRouiudYYUGgcyrjCUWgpx0nT+PB1DEtiP3yHv15L000iarhptw70bEnw3gRGE9k" +
            "HIhzS/GdVaFXyXsjJJ2CHtilgvHX",
        )
        val vault = store().importBackup(oldBackup, "old-master".toCharArray())
        val steam = vault.folders.first { it.name == "Steam" }
        assertEquals(
            listOf(Account("acc-1", steam.id, "Main", "gamer@example.com", "hunter2!", "notes here", 42L)),
            vault.accountsIn(steam.id),
        )
        assertEquals(vault, store().load())
    }
}

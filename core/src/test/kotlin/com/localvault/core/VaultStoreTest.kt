package com.localvault.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VaultStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    // Low iteration count keeps the tests fast; the app uses VaultCrypto.DEFAULT_ITERATIONS.
    private fun store(file: File = File(tmp.root, "vault.enc")) = VaultStore(file, iterations = 1_000)

    private fun account(folderId: String, label: String = "Main") = Account(
        id = Vault.newId(),
        folderId = folderId,
        label = label,
        username = "player@example.com",
        password = "hunter2!",
        notes = "2FA on phone",
    )

    @Test
    fun createMakesDefaultFolders() {
        val store = store()
        assertFalse(store.exists())
        val unlocked = store.create("master".toCharArray())
        assertTrue(store.exists())
        assertEquals(Vault.DEFAULT_FOLDER_NAMES, unlocked.vault.folders.map { it.name })
    }

    @Test
    fun savedAccountsSurviveLockAndUnlock() {
        val store = store()
        var unlocked = store.create("master".toCharArray())
        val steam = unlocked.vault.folders.first { it.name == "Steam" }
        val main = account(steam.id, "Main")
        val alt = account(steam.id, "Alt")
        unlocked = store.save(unlocked, unlocked.vault.upsertAccount(main).upsertAccount(alt))

        val reopened = store().unlock("master".toCharArray())
        assertEquals(listOf(alt, main), reopened.vault.accountsIn(steam.id))
    }

    @Test
    fun fileDoesNotContainPlaintext() {
        val file = File(tmp.root, "vault.enc")
        val store = store(file)
        val unlocked = store.create("master".toCharArray())
        store.save(unlocked, unlocked.vault.upsertAccount(account(unlocked.vault.folders[0].id)))
        val raw = file.readBytes().decodeToString()
        assertFalse(raw.contains("hunter2"))
        assertFalse(raw.contains("Steam"))
        assertFalse(raw.contains("player@example.com"))
    }

    @Test(expected = WrongPasswordException::class)
    fun wrongPasswordIsRejected() {
        store().create("master".toCharArray())
        store().unlock("not-master".toCharArray())
    }

    @Test(expected = WrongPasswordException::class)
    fun tamperedCiphertextIsRejected() {
        val file = File(tmp.root, "vault.enc")
        store(file).create("master".toCharArray())
        val bytes = file.readBytes()
        bytes[bytes.size - 5] = (bytes[bytes.size - 5].toInt() xor 1).toByte()
        file.writeBytes(bytes)
        store(file).unlock("master".toCharArray())
    }

    @Test(expected = WrongPasswordException::class)
    fun tamperedHeaderIsRejected() {
        val file = File(tmp.root, "vault.enc")
        store(file).create("master".toCharArray())
        val bytes = file.readBytes()
        bytes[12] = (bytes[12].toInt() xor 1).toByte() // inside the salt
        file.writeBytes(bytes)
        store(file).unlock("master".toCharArray())
    }

    @Test(expected = InvalidVaultFileException::class)
    fun garbageIsNotAVault() {
        store().importEncrypted("hello world".toByteArray(), "master".toCharArray())
    }

    @Test
    fun changePasswordReEncrypts() {
        val store = store()
        val unlocked = store.create("old".toCharArray())
        store.changePassword(unlocked, "new".toCharArray())
        assertEquals(unlocked.vault, store().unlock("new".toCharArray()).vault)
        try {
            store().unlock("old".toCharArray())
            throw AssertionError("old password still works")
        } catch (expected: WrongPasswordException) {
        }
    }

    @Test
    fun exportThenImportOnAnotherDevice() {
        val phoneA = store(File(tmp.root, "a/vault.enc"))
        var unlocked = phoneA.create("master".toCharArray())
        unlocked = phoneA.save(unlocked, unlocked.vault.addFolder("Epic Games"))
        val backup = phoneA.exportEncrypted()

        val phoneB = store(File(tmp.root, "b/vault.enc"))
        phoneB.create("other".toCharArray())
        val imported = phoneB.importEncrypted(backup, "master".toCharArray())
        assertEquals(unlocked.vault, imported.vault)
        assertArrayEquals(backup, File(tmp.root, "b/vault.enc").readBytes())
        assertEquals(unlocked.vault, store(File(tmp.root, "b/vault.enc")).unlock("master".toCharArray()).vault)
    }

    @Test
    fun importWithWrongPasswordKeepsExistingVault() {
        val source = store(File(tmp.root, "a/vault.enc"))
        source.create("master".toCharArray())
        val target = store(File(tmp.root, "b/vault.enc"))
        val original = target.create("mine".toCharArray())
        try {
            target.importEncrypted(source.exportEncrypted(), "wrong".toCharArray())
            throw AssertionError("import should fail")
        } catch (expected: WrongPasswordException) {
        }
        assertEquals(original.vault, target.unlock("mine".toCharArray()).vault)
    }
}

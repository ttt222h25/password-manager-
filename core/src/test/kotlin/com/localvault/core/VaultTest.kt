package com.localvault.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultTest {
    private fun account(folderId: String, label: String, username: String = "") = Account(
        id = Vault.newId(), folderId = folderId, label = label, username = username, password = "pw",
    )

    @Test
    fun deleteFolderRemovesItsAccounts() {
        var vault = Vault.withDefaultFolders()
        val steam = vault.folders[0]
        val discord = vault.folders[1]
        vault = vault.upsertAccount(account(steam.id, "Main")).upsertAccount(account(discord.id, "Main"))
        vault = vault.deleteFolder(steam.id)
        assertEquals(null, vault.folder(steam.id))
        assertEquals(1, vault.accounts.size)
        assertEquals(discord.id, vault.accounts[0].folderId)
    }

    @Test
    fun upsertReplacesExistingAccount() {
        val vault = Vault.withDefaultFolders()
        val original = account(vault.folders[0].id, "Main")
        val edited = original.copy(password = "new")
        val result = vault.upsertAccount(original).upsertAccount(edited)
        assertEquals(listOf(edited), result.accounts)
    }

    @Test
    fun renameFolder() {
        val vault = Vault.withDefaultFolders()
        val renamed = vault.renameFolder(vault.folders[4].id, "  Epic Games ")
        assertEquals("Epic Games", renamed.folders[4].name)
    }

    @Test
    fun searchMatchesLabelUsernameAndFolderName() {
        var vault = Vault.withDefaultFolders()
        val steam = vault.folders.first { it.name == "Steam" }
        val gmail = vault.folders.first { it.name == "Gmail" }
        val smurf = account(steam.id, "Smurf")
        val work = account(gmail.id, "Work", username = "me@company.com")
        vault = vault.upsertAccount(smurf).upsertAccount(work)
        assertEquals(listOf(smurf), vault.search("smu"))
        assertEquals(listOf(work), vault.search("COMPANY"))
        assertEquals(listOf(smurf), vault.search("steam"))
        assertTrue(vault.search("   ").isEmpty())
    }

    @Test
    fun generatedPasswordsContainEveryGroup() {
        repeat(200) {
            val pw = PasswordGenerator.generate(length = 12)
            assertEquals(12, pw.length)
            assertTrue(pw.any { it.isLowerCase() })
            assertTrue(pw.any { it.isUpperCase() })
            assertTrue(pw.any { it.isDigit() })
            assertTrue(pw.any { !it.isLetterOrDigit() })
        }
        assertNotEquals(PasswordGenerator.generate(), PasswordGenerator.generate())
    }

    @Test
    fun generatorCanSkipGroups() {
        val pw = PasswordGenerator.generate(length = 30, uppercase = false, digits = false, symbols = false)
        assertTrue(pw.all { it.isLowerCase() })
    }
}

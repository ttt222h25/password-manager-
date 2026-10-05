package com.localvault.core

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Folder(
    val id: String,
    val name: String,
)

@Serializable
data class Account(
    val id: String,
    val folderId: String,
    val label: String,
    val username: String,
    val password: String,
    val notes: String = "",
    val updatedAt: Long = 0L,
)

/** The whole decrypted vault. Immutable: every edit returns a new copy. */
@Serializable
data class Vault(
    val folders: List<Folder> = emptyList(),
    val accounts: List<Account> = emptyList(),
) {
    fun folder(id: String): Folder? = folders.firstOrNull { it.id == id }

    fun account(id: String): Account? = accounts.firstOrNull { it.id == id }

    fun accountsIn(folderId: String): List<Account> =
        accounts.filter { it.folderId == folderId }.sortedBy { it.label.lowercase() }

    fun search(query: String): List<Account> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return accounts.filter { account ->
            account.label.lowercase().contains(q) ||
                account.username.lowercase().contains(q) ||
                account.notes.lowercase().contains(q) ||
                (folder(account.folderId)?.name?.lowercase()?.contains(q) ?: false)
        }.sortedBy { it.label.lowercase() }
    }

    fun addFolder(name: String): Vault =
        copy(folders = folders + Folder(id = newId(), name = name.trim()))

    fun renameFolder(id: String, name: String): Vault =
        copy(folders = folders.map { if (it.id == id) it.copy(name = name.trim()) else it })

    /** Deletes the folder together with every account inside it. */
    fun deleteFolder(id: String): Vault =
        copy(
            folders = folders.filterNot { it.id == id },
            accounts = accounts.filterNot { it.folderId == id },
        )

    /** Inserts the account, or replaces the existing one with the same id. */
    fun upsertAccount(account: Account): Vault {
        val exists = accounts.any { it.id == account.id }
        return copy(
            accounts = if (exists) {
                accounts.map { if (it.id == account.id) account else it }
            } else {
                accounts + account
            },
        )
    }

    fun deleteAccount(id: String): Vault = copy(accounts = accounts.filterNot { it.id == id })

    companion object {
        val DEFAULT_FOLDER_NAMES = listOf("Steam", "Discord", "Gmail", "Microsoft", "Other")

        fun withDefaultFolders(): Vault =
            Vault(folders = DEFAULT_FOLDER_NAMES.map { Folder(id = newId(), name = it) })

        fun newId(): String = UUID.randomUUID().toString()
    }
}

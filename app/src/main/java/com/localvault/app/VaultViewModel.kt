package com.localvault.app

import android.app.Application
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localvault.core.Account
import com.localvault.core.InvalidVaultFileException
import com.localvault.core.Vault
import com.localvault.core.VaultStore
import com.localvault.core.WrongPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface Screen {
    data object Unlock : Screen
    data object Home : Screen
    data class FolderDetail(val folderId: String) : Screen
    data class EditAccount(val accountId: String?, val folderId: String) : Screen
    data object Settings : Screen
}

class VaultViewModel(private val app: Application) : AndroidViewModel(app) {
    private val store = VaultStore(File(app.filesDir, "vault.enc"), keyProvider = DeviceKey::get)

    /** The decrypted vault while unlocked; null while locked. */
    private var current by mutableStateOf<Vault?>(null)
    private val backStack = mutableStateListOf<Screen>()

    /** True while slow work runs (opening the vault, making or restoring a backup). */
    var busy by mutableStateOf(false)
        private set

    /** Error shown on the unlock screen. */
    var error by mutableStateOf<String?>(null)
        private set

    /** The vault file exists but couldn't be opened; the unlock screen then offers a restore. */
    var loadFailed by mutableStateOf(false)
        private set

    /** The phone has no screen lock, so Vault opened without any check. */
    var noScreenLock by mutableStateOf(false)
        private set

    /** One-off message shown in a snackbar. */
    var message by mutableStateOf<String?>(null)

    /**
     * Set while the system file picker is open, so leaving the app for the picker doesn't lock the
     * vault. Any other time the app goes to the background it locks.
     */
    var externalActivityInProgress = false

    /** Whether the unlock screen should open the fingerprint/PIN prompt by itself. */
    private var autoPrompt = true

    private var clipboardClearJob: Job? = null

    val vault: Vault get() = current ?: Vault()

    val screen: Screen
        get() = when {
            current == null -> Screen.Unlock
            backStack.isEmpty() -> Screen.Home
            else -> backStack.last()
        }

    val canGoBack: Boolean get() = current != null && backStack.isNotEmpty()

    fun navigate(to: Screen) {
        backStack.add(to)
    }

    fun back() {
        if (backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex)
    }

    /** Returns true once after each lock, so the prompt opens automatically but never loops. */
    fun consumeAutoPrompt(): Boolean = autoPrompt.also { autoPrompt = false }

    fun showError(text: String) {
        error = text.ifEmpty { null }
    }

    /** Called after the phone's lock check passed (or when the phone has no screen lock). */
    fun unlock(phoneHasScreenLock: Boolean) {
        noScreenLock = !phoneHasScreenLock
        runSlow(
            work = { store.load() },
            onSuccess = {
                loadFailed = false
                current = it
            },
            onFailure = { e ->
                loadFailed = true
                error = "Your saved passwords couldn't be opened (${e.message}). " +
                    "If you have a backup file, restore it below."
            },
        )
    }

    fun lock() {
        current = null
        backStack.clear()
        error = null
        autoPrompt = true
    }

    fun addFolder(name: String) {
        if (name.isBlank()) return
        update { it.addFolder(name) }
    }

    fun renameFolder(id: String, name: String) {
        if (name.isBlank()) return
        update { it.renameFolder(id, name) }
    }

    fun deleteFolder(id: String) {
        update { it.deleteFolder(id) }
        backStack.removeAll { it is Screen.FolderDetail && it.folderId == id }
        message = "Folder deleted"
    }

    fun saveAccount(account: Account) {
        update { it.upsertAccount(account.copy(updatedAt = System.currentTimeMillis())) }
        back()
        message = "Saved"
    }

    fun deleteAccount(id: String) {
        update { it.deleteAccount(id) }
        back()
        message = "Account deleted"
    }

    /** Writes an encrypted backup of the vault, locked with [password], to [uri]. */
    fun exportBackup(uri: Uri, password: String) {
        val vault = current ?: return
        runSlow(
            work = {
                val bytes = store.exportBackup(vault, password.toCharArray())
                val out = app.contentResolver.openOutputStream(uri, "wt")
                    ?: throw IllegalStateException("Could not open file")
                out.use { it.write(bytes) }
            },
            onSuccess = { message = "Backup saved. Keep the backup password somewhere safe." },
            onFailure = { message = "Backup failed: ${it.message}" },
        )
    }

    /** Restores a backup file. [password] is the password the backup was made with. */
    fun importBackup(uri: Uri, password: String) {
        runSlow(
            work = {
                val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Could not open file")
                store.importBackup(bytes, password.toCharArray())
            },
            onSuccess = {
                loadFailed = false
                error = null
                current = it
                backStack.clear()
                message = "Backup restored"
            },
            onFailure = { e ->
                message = when (e) {
                    is WrongPasswordException -> "Wrong password for that backup"
                    is InvalidVaultFileException -> "That file is not a Vault backup"
                    else -> "Restore failed: ${e.message}"
                }
            },
        )
    }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = app.getSystemService(ClipboardManager::class.java)
        val clip = ClipData.newPlainText(label, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Hides the value from the clipboard preview and keyboard suggestions.
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        } else {
            // Android 13+ shows its own "Copied" confirmation.
            message = "$label copied (clears in 30 s)"
        }
        clipboard.setPrimaryClip(clip)

        clipboardClearJob?.cancel()
        clipboardClearJob = viewModelScope.launch {
            delay(CLIPBOARD_CLEAR_MS)
            val current = runCatching { clipboard.primaryClip?.getItemAt(0)?.text?.toString() }.getOrNull()
            // Only clear if it still holds what we copied (or we can't tell because we're in the background).
            if (current == null || current == text) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    clipboard.clearPrimaryClip()
                } else {
                    clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            }
        }
    }

    private fun update(transform: (Vault) -> Vault) {
        val vault = current ?: return
        val updated = transform(vault)
        try {
            // AES on a small file takes milliseconds.
            store.save(updated)
            current = updated
        } catch (e: Exception) {
            message = "Could not save: ${e.message}"
        }
    }

    private fun <T> runSlow(work: () -> T, onSuccess: (T) -> Unit, onFailure: (Throwable) -> Unit) {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) { runCatching(work) }
            busy = false
            result.onSuccess(onSuccess).onFailure(onFailure)
        }
    }

    private companion object {
        const val CLIPBOARD_CLEAR_MS = 30_000L
    }
}

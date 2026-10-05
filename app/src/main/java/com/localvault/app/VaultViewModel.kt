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
import com.localvault.core.UnlockedVault
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
    data object Setup : Screen
    data object Unlock : Screen
    data object Home : Screen
    data class FolderDetail(val folderId: String) : Screen
    data class EditAccount(val accountId: String?, val folderId: String) : Screen
    data object Settings : Screen
}

class VaultViewModel(private val app: Application) : AndroidViewModel(app) {
    private val store = VaultStore(File(app.filesDir, "vault.enc"))

    private var hasVault by mutableStateOf(store.exists())
    private var unlocked by mutableStateOf<UnlockedVault?>(null)
    private val backStack = mutableStateListOf<Screen>()

    /** True while the slow master-password check (key derivation) runs. */
    var busy by mutableStateOf(false)
        private set

    /** Error shown inline on the setup / unlock / settings screens. */
    var error by mutableStateOf<String?>(null)
        private set

    /** One-off message shown in a snackbar. */
    var message by mutableStateOf<String?>(null)

    /**
     * Set while the system file picker is open, so leaving the app for the picker doesn't lock the
     * vault. Any other time the app goes to the background it locks.
     */
    var externalActivityInProgress = false

    private var clipboardClearJob: Job? = null

    val vault: Vault get() = unlocked?.vault ?: Vault()

    val screen: Screen
        get() = when {
            unlocked == null -> if (hasVault) Screen.Unlock else Screen.Setup
            backStack.isEmpty() -> Screen.Home
            else -> backStack.last()
        }

    val canGoBack: Boolean get() = unlocked != null && backStack.isNotEmpty()

    fun navigate(to: Screen) {
        error = null
        backStack.add(to)
    }

    fun back() {
        error = null
        if (backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex)
    }

    fun createVault(password: String, confirm: String) {
        error = when {
            password.length < 8 -> "Use at least 8 characters"
            password != confirm -> "The two passwords don't match"
            else -> null
        }
        if (error != null) return
        runSlow(
            work = { store.create(password.toCharArray()) },
            onSuccess = {
                hasVault = true
                unlocked = it
            },
        )
    }

    fun unlock(password: String) {
        if (password.isEmpty()) return
        runSlow(work = { store.unlock(password.toCharArray()) }, onSuccess = { unlocked = it })
    }

    fun lock() {
        unlocked = null
        backStack.clear()
        error = null
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

    fun changePassword(current: String, new: String, confirm: String) {
        error = when {
            new.length < 8 -> "Use at least 8 characters for the new password"
            new != confirm -> "The new passwords don't match"
            else -> null
        }
        if (error != null) return
        runSlow(
            work = {
                // Re-derive from the typed current password to prove the person holding the phone knows it.
                store.unlock(current.toCharArray())
                store.changePassword(unlocked ?: throw IllegalStateException("Vault is locked"), new.toCharArray())
            },
            onSuccess = {
                unlocked = it
                message = "Master password changed"
            },
            wrongPasswordText = "Current master password is wrong",
        )
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val out = app.contentResolver.openOutputStream(uri, "wt") ?: throw IllegalStateException("Could not open file")
                    out.use { it.write(store.exportEncrypted()) }
                }
            }
            message = if (result.isSuccess) "Backup saved" else "Backup failed: ${result.exceptionOrNull()?.message}"
        }
    }

    /** Restores a backup file. [password] is the master password the backup was made with. */
    fun importBackup(uri: Uri, password: String) {
        runSlow(
            work = {
                val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Could not open file")
                store.importEncrypted(bytes, password.toCharArray())
            },
            onSuccess = {
                hasVault = true
                unlocked = it
                backStack.clear()
                message = "Backup restored"
            },
            wrongPasswordText = "Wrong master password for that backup",
            useSnackbar = true,
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
        val current = unlocked ?: return
        try {
            // AES on a small file takes milliseconds; the slow KDF isn't involved here.
            unlocked = store.save(current, transform(current.vault))
        } catch (e: Exception) {
            message = "Could not save: ${e.message}"
        }
    }

    private fun <T> runSlow(
        work: () -> T,
        onSuccess: (T) -> Unit,
        wrongPasswordText: String = "Wrong master password",
        useSnackbar: Boolean = false,
    ) {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) { runCatching(work) }
            busy = false
            result.onSuccess(onSuccess).onFailure { e ->
                val text = when (e) {
                    is WrongPasswordException -> wrongPasswordText
                    is InvalidVaultFileException -> "That file is not a Vault backup (${e.message})"
                    else -> "Something went wrong: ${e.message}"
                }
                if (useSnackbar) message = text else error = text
            }
        }
    }

    private companion object {
        const val CLIPBOARD_CLEAR_MS = 30_000L
    }
}

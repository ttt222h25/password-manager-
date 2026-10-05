package com.localvault.app.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import com.localvault.app.PhoneLock
import com.localvault.app.Screen
import com.localvault.app.VaultViewModel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import java.time.LocalDate

@Composable
fun VaultApp(vm: VaultViewModel) {
    val activity = LocalContext.current as FragmentActivity
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        snapshotFlow { vm.message }.filterNotNull().collect {
            vm.message = null
            snackbar.showSnackbar(it)
        }
    }

    val requestUnlock = {
        if (PhoneLock.isSet(activity)) {
            PhoneLock.prompt(activity, onSuccess = { vm.unlock(phoneHasScreenLock = true) }, onError = vm::showError)
        } else {
            vm.unlock(phoneHasScreenLock = false)
        }
    }

    // Backup files go through Android's own file picker, so the app needs no storage permission.
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var choosingBackupPassword by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf<String?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        vm.externalActivityInProgress = false
        pendingImport = uri
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        vm.externalActivityInProgress = false
        val password = exportPassword
        exportPassword = null
        if (uri != null && password != null) vm.exportBackup(uri, password)
    }
    val startImport = {
        vm.externalActivityInProgress = true
        importLauncher.launch(arrayOf("*/*"))
    }

    BackHandler(enabled = vm.canGoBack) { vm.back() }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            when (val screen = vm.screen) {
                Screen.Unlock -> {
                    LaunchedEffect(Unit) {
                        // Open the fingerprint/PIN prompt by itself once the app is on screen again.
                        activity.lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
                        if (vm.consumeAutoPrompt()) requestUnlock()
                    }
                    UnlockScreen(
                        error = vm.error,
                        busy = vm.busy,
                        showRestore = vm.loadFailed,
                        onUnlock = requestUnlock,
                        onRestoreBackup = startImport,
                    )
                }

                Screen.Home -> HomeScreen(
                    vault = vm.vault,
                    noScreenLock = vm.noScreenLock,
                    onOpenFolder = { vm.navigate(Screen.FolderDetail(it.id)) },
                    onOpenAccount = { vm.navigate(Screen.EditAccount(it.id, it.folderId)) },
                    onAddFolder = vm::addFolder,
                    onCopy = vm::copyToClipboard,
                    onSettings = { vm.navigate(Screen.Settings) },
                    onLock = vm::lock,
                )

                is Screen.FolderDetail -> {
                    val folder = vm.vault.folder(screen.folderId)
                    if (folder == null) {
                        LaunchedEffect(screen) { vm.back() }
                    } else {
                        FolderScreen(
                            folder = folder,
                            accounts = vm.vault.accountsIn(folder.id),
                            onBack = vm::back,
                            onAddAccount = { vm.navigate(Screen.EditAccount(null, folder.id)) },
                            onOpenAccount = { vm.navigate(Screen.EditAccount(it.id, it.folderId)) },
                            onRename = { vm.renameFolder(folder.id, it) },
                            onDelete = { vm.deleteFolder(folder.id) },
                            onCopy = vm::copyToClipboard,
                        )
                    }
                }

                is Screen.EditAccount -> {
                    val existing = screen.accountId?.let { vm.vault.account(it) }
                    if (screen.accountId != null && existing == null) {
                        LaunchedEffect(screen) { vm.back() }
                    } else {
                        EditAccountScreen(
                            existing = existing,
                            folders = vm.vault.folders,
                            initialFolderId = screen.folderId,
                            onSave = vm::saveAccount,
                            onDelete = { vm.deleteAccount(it.id) },
                            onBack = vm::back,
                        )
                    }
                }

                Screen.Settings -> SettingsScreen(
                    busy = vm.busy,
                    noScreenLock = vm.noScreenLock,
                    onBack = vm::back,
                    onExport = { choosingBackupPassword = true },
                    onImport = startImport,
                    onLock = vm::lock,
                )
            }

            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp),
            )

            if (vm.busy) BusyOverlay()
        }
    }

    if (choosingBackupPassword) {
        NewBackupPasswordDialog(
            onConfirm = { password ->
                choosingBackupPassword = false
                exportPassword = password
                vm.externalActivityInProgress = true
                exportLauncher.launch("vault-backup-${LocalDate.now()}.vault")
            },
            onDismiss = { choosingBackupPassword = false },
        )
    }

    pendingImport?.let { uri ->
        BackupPasswordDialog(
            onConfirm = { password ->
                pendingImport = null
                vm.importBackup(uri, password)
            },
            onDismiss = { pendingImport = null },
        )
    }
}

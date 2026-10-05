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
import androidx.compose.ui.unit.dp
import com.localvault.app.Screen
import com.localvault.app.VaultViewModel
import kotlinx.coroutines.flow.filterNotNull
import java.time.LocalDate

@Composable
fun VaultApp(vm: VaultViewModel) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        snapshotFlow { vm.message }.filterNotNull().collect {
            vm.message = null
            snackbar.showSnackbar(it)
        }
    }

    // Backup file pickers (Android's own file picker; the app needs no storage permission).
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        vm.externalActivityInProgress = false
        pendingImport = uri
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        vm.externalActivityInProgress = false
        if (uri != null) vm.exportBackup(uri)
    }
    val startImport = {
        vm.externalActivityInProgress = true
        importLauncher.launch(arrayOf("*/*"))
    }
    val startExport = {
        vm.externalActivityInProgress = true
        exportLauncher.launch("vault-backup-${LocalDate.now()}.vault")
    }

    BackHandler(enabled = vm.canGoBack) { vm.back() }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            when (val screen = vm.screen) {
                Screen.Setup -> SetupScreen(
                    error = vm.error,
                    busy = vm.busy,
                    onCreate = vm::createVault,
                    onRestoreBackup = startImport,
                )

                Screen.Unlock -> UnlockScreen(error = vm.error, busy = vm.busy, onUnlock = vm::unlock)

                Screen.Home -> HomeScreen(
                    vault = vm.vault,
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
                    error = vm.error,
                    busy = vm.busy,
                    onBack = vm::back,
                    onChangePassword = vm::changePassword,
                    onExport = startExport,
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

@file:OptIn(ExperimentalMaterial3Api::class)

package com.localvault.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    busy: Boolean,
    noScreenLock: Boolean,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onLock: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (noScreenLock) NoScreenLockWarning()

            Section("Backup") {
                Text(
                    "Your passwords are stored only on this phone. If the phone is lost, reset or the app is " +
                        "uninstalled, they are gone. Export a backup now and then and keep a copy somewhere safe " +
                        "(computer, USB stick). You choose a password for each backup file, and you'll need it " +
                        "to restore that backup.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onExport, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(VaultIcons.Download, contentDescription = null)
                    Text("  Export backup")
                }
                OutlinedButton(onClick = onImport, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(VaultIcons.Upload, contentDescription = null)
                    Text("  Restore from backup")
                }
            }

            Section("Privacy") {
                Text(
                    "• Vault opens with your phone's own fingerprint, face, PIN or pattern.\n" +
                        "• It locks every time you leave the app.\n" +
                        "• The app has no internet permission, so Android itself stops it from going online.\n" +
                        "• Your passwords are encrypted (AES-256) with a key kept in the phone's secure " +
                        "hardware. The key can't be copied off the phone.\n" +
                        "• Screenshots are blocked and copied passwords are cleared from the clipboard after 30 seconds.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Button(onClick = onLock, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Lock, contentDescription = null)
                Text("  Lock now")
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

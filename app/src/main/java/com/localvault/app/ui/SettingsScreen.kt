@file:OptIn(ExperimentalMaterial3Api::class)

package com.localvault.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    error: String?,
    busy: Boolean,
    onBack: () -> Unit,
    onChangePassword: (current: String, new: String, confirm: String) -> Unit,
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section("Backup") {
                Text(
                    "Your passwords are stored only on this phone. If the phone is lost, reset or the app is " +
                        "uninstalled, they are gone. Export a backup now and then and keep a copy somewhere safe " +
                        "(computer, USB stick). The backup file is encrypted with your master password.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                    Icon(VaultIcons.Download, contentDescription = null)
                    Text("  Export backup")
                }
                OutlinedButton(onClick = onImport, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(VaultIcons.Upload, contentDescription = null)
                    Text("  Restore from backup")
                }
            }

            ChangePasswordSection(error = error, busy = busy, onChangePassword = onChangePassword)

            Section("Privacy") {
                Text(
                    "• This app has no internet permission — Android itself stops it from going online.\n" +
                        "• Everything is encrypted with AES-256 using a key made from your master password.\n" +
                        "• The vault locks every time you leave the app.\n" +
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
private fun ChangePasswordSection(
    error: String?,
    busy: Boolean,
    onChangePassword: (current: String, new: String, confirm: String) -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    Section("Change master password") {
        PasswordField(value = current, onValueChange = { current = it }, label = "Current master password")
        PasswordField(value = new, onValueChange = { new = it }, label = "New master password")
        PasswordField(
            value = confirm,
            onValueChange = { confirm = it },
            label = "Type new password again",
            imeAction = ImeAction.Done,
        )
        ErrorText(error)
        OutlinedButton(
            onClick = { onChangePassword(current, new, confirm) },
            enabled = !busy && current.isNotEmpty() && new.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Change master password") }
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

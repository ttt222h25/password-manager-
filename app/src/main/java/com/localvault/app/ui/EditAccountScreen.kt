@file:OptIn(ExperimentalMaterial3Api::class)

package com.localvault.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.localvault.core.Account
import com.localvault.core.Folder
import com.localvault.core.PasswordGenerator
import com.localvault.core.Vault

@Composable
fun EditAccountScreen(
    existing: Account?,
    folders: List<Folder>,
    initialFolderId: String,
    onSave: (Account) -> Unit,
    onDelete: (Account) -> Unit,
    onBack: () -> Unit,
) {
    var label by rememberSaveable { mutableStateOf(existing?.label ?: "") }
    var username by rememberSaveable { mutableStateOf(existing?.username ?: "") }
    var password by rememberSaveable { mutableStateOf(existing?.password ?: "") }
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }
    var folderId by rememberSaveable { mutableStateOf(existing?.folderId ?: initialFolderId) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var folderMenuOpen by rememberSaveable { mutableStateOf(false) }

    val canSave = username.isNotBlank() || password.isNotEmpty()
    val save = {
        onSave(
            Account(
                id = existing?.id ?: Vault.newId(),
                folderId = folderId,
                label = label.trim().ifEmpty { username.trim().ifEmpty { "Account" } },
                username = username.trim(),
                password = password,
                notes = notes.trim(),
            ),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New account" else "Edit account") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = { showDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete account") }
                    }
                    TextButton(onClick = save, enabled = canSave) { Text("Save") }
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box {
                OutlinedButton(onClick = { folderMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(VaultIcons.Folder, contentDescription = null)
                        Text(
                            "  Folder: " + (folders.firstOrNull { it.id == folderId }?.name ?: "—"),
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                }
                DropdownMenu(expanded = folderMenuOpen, onDismissRequest = { folderMenuOpen = false }) {
                    folders.forEach { folder ->
                        DropdownMenuItem(
                            text = { Text(folder.name) },
                            onClick = {
                                folderId = folder.id
                                folderMenuOpen = false
                            },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Name (e.g. Main, Alt, School)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username or email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            PasswordField(value = password, onValueChange = { password = it }, label = "Password")
            OutlinedButton(onClick = { password = PasswordGenerator.generate() }) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Text("  Generate strong password")
            }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (recovery codes, security questions…)") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Tip: change the real password on the website first, then save it here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = save, enabled = canSave, modifier = Modifier.fillMaxWidth()) { Text("Save") }
        }
    }

    if (showDelete && existing != null) {
        ConfirmDialog(
            title = "Delete \"${existing.label}\"?",
            text = "This account and its password will be removed. This cannot be undone.",
            confirmText = "Delete",
            onConfirm = {
                showDelete = false
                onDelete(existing)
            },
            onDismiss = { showDelete = false },
        )
    }
}

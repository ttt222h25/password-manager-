@file:OptIn(ExperimentalMaterial3Api::class)

package com.localvault.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.localvault.core.Account
import com.localvault.core.Folder
import com.localvault.core.Vault

@Composable
fun HomeScreen(
    vault: Vault,
    noScreenLock: Boolean,
    onOpenFolder: (Folder) -> Unit,
    onOpenAccount: (Account) -> Unit,
    onAddFolder: (String) -> Unit,
    onCopy: (String, String) -> Unit,
    onSettings: () -> Unit,
    onLock: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var showAddFolder by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vault") },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
                    IconButton(onClick = onLock) { Icon(Icons.Filled.Lock, contentDescription = "Lock") }
                },
            )
        },
        floatingActionButton = {
            if (query.isBlank()) {
                ExtendedFloatingActionButton(
                    onClick = { showAddFolder = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("New folder") },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (noScreenLock) {
                item { NoScreenLockWarning() }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search all accounts") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Clear search") }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotBlank()) {
                val results = vault.search(query)
                if (results.isEmpty()) {
                    item { EmptyText("No accounts match \"$query\"") }
                }
                items(results, key = { it.id }) { account ->
                    AccountCard(
                        account = account,
                        folderName = vault.folder(account.folderId)?.name,
                        onClick = { onOpenAccount(account) },
                        onCopy = onCopy,
                    )
                }
            } else {
                if (vault.folders.isEmpty()) {
                    item { EmptyText("No folders yet. Tap \"New folder\" to make one.") }
                }
                items(vault.folders, key = { it.id }) { folder ->
                    FolderRow(folder = folder, count = vault.accountsIn(folder.id).size, onClick = { onOpenFolder(folder) })
                }
            }
        }
    }

    if (showAddFolder) {
        TextInputDialog(
            title = "New folder",
            initial = "",
            confirmText = "Add",
            label = "Folder name (e.g. Epic Games)",
            onConfirm = {
                onAddFolder(it)
                showAddFolder = false
            },
            onDismiss = { showAddFolder = false },
        )
    }
}

@Composable
private fun FolderRow(folder: Folder, count: Int, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            FolderIcon(folder.name, size = 44.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(folder.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (count == 1) "1 account" else "$count accounts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
fun FolderScreen(
    folder: Folder,
    accounts: List<Account>,
    onBack: () -> Unit,
    onAddAccount: () -> Unit,
    onOpenAccount: (Account) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onCopy: (String, String) -> Unit,
) {
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FolderIcon(folder.name, size = 32.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(folder.name)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { showRename = true }) { Icon(Icons.Filled.Edit, contentDescription = "Rename folder") }
                    IconButton(onClick = { showDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete folder") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddAccount) { Icon(Icons.Filled.Add, contentDescription = "Add account") }
        },
    ) { padding ->
        if (accounts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyText("No ${folder.name} accounts yet.\nTap + to add one.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(accounts, key = { it.id }) { account ->
                    AccountCard(account = account, onClick = { onOpenAccount(account) }, onCopy = onCopy)
                }
            }
        }
    }

    if (showRename) {
        TextInputDialog(
            title = "Rename folder",
            initial = folder.name,
            confirmText = "Rename",
            onConfirm = {
                onRename(it)
                showRename = false
            },
            onDismiss = { showRename = false },
        )
    }
    if (showDelete) {
        ConfirmDialog(
            title = "Delete \"${folder.name}\"?",
            text = if (accounts.isEmpty()) {
                "This folder is empty."
            } else {
                "This also deletes the ${accounts.size} account(s) saved in it. This cannot be undone."
            },
            confirmText = "Delete",
            onConfirm = {
                showDelete = false
                onDelete()
            },
            onDismiss = { showDelete = false },
        )
    }
}

@Composable
fun NoScreenLockWarning() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            "Your phone has no screen lock, so anyone holding it can open Vault. Set a PIN, pattern or " +
                "fingerprint in Android Settings → Security, and Vault will ask for it.",
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
fun EmptyText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(32.dp),
    )
}

package com.localvault.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localvault.core.Account

@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) VaultIcons.VisibilityOff else VaultIcons.Visibility,
                    contentDescription = if (visible) "Hide password" else "Show password",
                )
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

/** One saved login: label, username and password, with show/hide and copy buttons. */
@Composable
fun AccountCard(
    account: Account,
    onClick: () -> Unit,
    onCopy: (label: String, value: String) -> Unit,
    folderName: String? = null,
) {
    var showPassword by remember(account.id) { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp, end = 4.dp)) {
            if (folderName != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FolderIcon(folderName, size = 28.dp)
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(account.label, style = MaterialTheme.typography.titleMedium)
                        Text(folderName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else {
                Text(account.label, style = MaterialTheme.typography.titleMedium)
            }
            if (account.username.isNotEmpty()) {
                FieldRow(
                    text = account.username,
                    monospace = false,
                ) {
                    IconButton(onClick = { onCopy("Username", account.username) }) {
                        Icon(VaultIcons.ContentCopy, contentDescription = "Copy username")
                    }
                }
            }
            if (account.password.isNotEmpty()) {
                FieldRow(
                    text = if (showPassword) account.password else "••••••••••",
                    monospace = showPassword,
                ) {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) VaultIcons.VisibilityOff else VaultIcons.Visibility,
                            contentDescription = if (showPassword) "Hide password" else "Show password",
                        )
                    }
                    IconButton(onClick = { onCopy("Password", account.password) }) {
                        Icon(VaultIcons.ContentCopy, contentDescription = "Copy password")
                    }
                }
            }
            if (account.notes.isNotBlank()) {
                Text(
                    account.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 12.dp, bottom = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun FieldRow(text: String, monospace: Boolean, actions: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = if (monospace) FontFamily.Monospace else null,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    label: String = "Name",
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmText, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Asks for the password a backup file was made with. */
@Composable
fun BackupPasswordDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Enter the password you chose when you made this backup. For a backup from the old " +
                        "version of Vault, that's your old master password. Restoring replaces everything " +
                        "currently saved in the app.",
                )
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Backup password",
                    imeAction = ImeAction.Done,
                    onDone = { if (password.isNotEmpty()) onConfirm(password) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = password.isNotEmpty()) { Text("Restore") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Asks for a new password to lock a backup file with. */
@Composable
fun NewBackupPasswordDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val submit = {
        error = when {
            password.length < 8 -> "Use at least 8 characters"
            password != confirm -> "The two passwords don't match"
            else -> null
        }
        if (error == null) onConfirm(password)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Backup password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "The backup file is locked with this password so it's safe to keep on a computer or USB " +
                        "stick. You'll need it to restore the backup. Write it down; it can't be recovered.",
                )
                PasswordField(value = password, onValueChange = { password = it }, label = "Backup password")
                PasswordField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = "Type it again",
                    imeAction = ImeAction.Done,
                    onDone = submit,
                )
                ErrorText(error)
            }
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = password.isNotEmpty()) { Text("Choose where to save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun BusyOverlay() {
    Surface(color = Color.Black.copy(alpha = 0.4f), modifier = Modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

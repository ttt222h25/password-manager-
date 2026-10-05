package com.localvault.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
private fun LockLayout(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        content()
    }
}

@Composable
fun SetupScreen(
    error: String?,
    busy: Boolean,
    onCreate: (password: String, confirm: String) -> Unit,
    onRestoreBackup: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    LockLayout(
        title = "Create your master password",
        subtitle = "This one password unlocks all your other passwords. It is never stored anywhere and " +
            "cannot be recovered — if you forget it, your saved passwords are lost. Write it down somewhere safe.",
    ) {
        PasswordField(value = password, onValueChange = { password = it }, label = "Master password")
        PasswordField(
            value = confirm,
            onValueChange = { confirm = it },
            label = "Type it again",
            imeAction = ImeAction.Done,
            onDone = { onCreate(password, confirm) },
        )
        ErrorText(error)
        Button(
            onClick = { onCreate(password, confirm) },
            enabled = !busy && password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create vault") }
        OutlinedButton(onClick = onRestoreBackup, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Restore from a backup file")
        }
    }
}

@Composable
fun UnlockScreen(error: String?, busy: Boolean, onUnlock: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    LockLayout(title = "Vault is locked", subtitle = "Enter your master password") {
        PasswordField(
            value = password,
            onValueChange = { password = it },
            label = "Master password",
            imeAction = ImeAction.Done,
            onDone = { onUnlock(password) },
        )
        ErrorText(error)
        Button(
            onClick = { onUnlock(password) },
            enabled = !busy && password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Unlock") }
    }
}

@Composable
fun ErrorText(error: String?) {
    if (error != null) {
        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
}

package com.localvault.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.localvault.app.ui.VaultApp
import com.localvault.app.ui.VaultTheme

class MainActivity : ComponentActivity() {
    private val viewModel: VaultViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Blocks screenshots and screen recording, and hides the app content in the recent-apps view.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            VaultTheme {
                VaultApp(viewModel)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Lock as soon as the app leaves the screen (home button, app switch, screen off).
        if (!isChangingConfigurations && !viewModel.externalActivityInProgress) {
            viewModel.lock()
        }
    }
}

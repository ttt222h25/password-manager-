package com.localvault.app

import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Unlocks Vault with the phone's own screen lock: fingerprint, face, PIN, pattern or password. */
object PhoneLock {
    private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** False when the phone has no screen lock at all, so there is nothing to check against. */
    fun isSet(context: Context): Boolean {
        val canAuthenticate = BiometricManager.from(context).canAuthenticate(AUTHENTICATORS)
        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) return true
        return context.getSystemService(KeyguardManager::class.java).isDeviceSecure
    }

    fun prompt(activity: FragmentActivity, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                onError(if (cancelled) "" else errString.toString())
            }
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Vault")
            .setSubtitle("Use your phone's fingerprint, face, PIN or pattern")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info)
    }
}

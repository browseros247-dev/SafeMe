package com.safeme.app.protect

import android.content.Context
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.safeme.app.ui.theme.findActivity

/**
 * Biometric (Touch ID / Face ID) support for App Lock.
 *
 * Uses [BiometricManager.Authenticators.BIOMETRIC_WEAK], matching the
 * reference implementation. The prompt requires a FragmentActivity host.
 */
object AppLockBiometrics {

    const val TAG = "SafeMeAppLockBio"

    /** True when the device can currently authenticate with a biometric. */
    fun isAvailable(context: Context): Boolean = try {
        val manager = BiometricManager.from(context.applicationContext)
        manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    } catch (t: Throwable) {
        Log.w(TAG, "biometric availability check failed", t)
        false
    }

    /**
     * Launch the system biometric prompt. [onSuccess] fires on the main thread
     * when authentication succeeds; user-cancellation is reported via
     * [onCancelled]. Never throws — failures are logged and ignored so the
     * lock gate stays up.
     */
    fun launch(
        context: Context,
        title: String,
        subtitle: String,
        negativeText: String,
        onSuccess: () -> Unit,
        onCancelled: () -> Unit = {},
    ) {
        val activity = context.findActivity() as? FragmentActivity ?: run {
            Log.w(TAG, "no FragmentActivity host for biometric prompt")
            return
        }
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED
                    ) {
                        Log.w(TAG, "biometric error $errorCode: $errString")
                    }
                    onCancelled()
                }

                override fun onAuthenticationFailed() {
                    // Wrong biometric — prompt stays open for retry.
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .setNegativeButtonText(negativeText)
            .build()
        try {
            prompt.authenticate(info)
        } catch (t: Throwable) {
            Log.w(TAG, "biometric prompt launch failed", t)
            onCancelled()
        }
    }
}

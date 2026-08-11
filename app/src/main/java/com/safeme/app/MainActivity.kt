package com.safeme.app

import android.content.res.Configuration
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.safeme.app.R
import com.safeme.app.data.ThemePref
import com.safeme.app.protect.A11yProtectionGuard
import com.safeme.app.protect.A11yProtectionUtils
import com.safeme.app.ui.screens.applock.AppLockGateController
import com.safeme.app.ui.screens.applock.AppLockGateHost
import com.safeme.app.ui.screens.permissions.OnboardingNavHost
import com.safeme.app.ui.theme.SafeMeApp
import com.safeme.app.ui.theme.ThemePrefHolder

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Match the window background to the stored theme before the first
        // frame. The theme XML's values-night variant only follows the
        // system, so a light system + dark app pref (or vice versa) would
        // otherwise flash the wrong color during process start.
        val darkWindow = when (ThemePrefHolder.pref) {
            ThemePref.DARK -> true
            ThemePref.LIGHT -> false
            ThemePref.SYSTEM ->
                (resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
        }
        window.setBackgroundDrawableResource(
            if (darkWindow) R.color.bg_night else R.color.bg
        )
        enableEdgeToEdge()
        setContent {
            SafeMeApp {
                Box(modifier = Modifier.fillMaxSize()) {
                    OnboardingNavHost()
                    // Full-screen unlock gate — sits above the whole app.
                    AppLockGateHost()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Auto-lock on return from background (applies the chosen delay).
        AppLockGateController.onAppForeground()
    }

    override fun onStop() {
        AppLockGateController.onAppBackground()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Cheap insurance while the app is foreground: heal any disabled
        // protected service and re-arm the watcher (no-ops when off).
        A11yProtectionUtils.selfHealAllAsync(this)
        A11yProtectionGuard.getInstance().ensureWatching(this)
    }
}
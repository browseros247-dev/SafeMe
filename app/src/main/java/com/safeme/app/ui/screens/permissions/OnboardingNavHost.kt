package com.safeme.app.ui.screens.permissions

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import com.safeme.app.R
import com.safeme.app.data.markOnboardingComplete
import com.safeme.app.data.onboardingComplete
import com.safeme.app.ui.screens.welcome.WelcomeScreen
import com.safeme.app.ui.theme.LocalAppColors
import com.safeme.app.ui.theme.findActivity
import com.safeme.app.ui.util.isAccessibilityEnabled

private const val KEY_NOTIFICATIONS = "notifications"
private const val KEY_BATTERY = "battery"
private const val KEY_ACCESSIBILITY = "accessibility"

/**
 * Returns true when every permission required for SafeMe to function is
 * already granted to the system: notifications (API 33+) and the SafeMe
 * accessibility service. Battery optimization is optional and not required.
 */
fun requiredPermissionsGranted(context: Context): Boolean =
    hasNotificationsPermission(context) && isAccessibilityEnabled(context)

fun hasNotificationsPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= 33) {
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }
    return true
}

@Composable
fun OnboardingNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val context = LocalContext.current
    // Resolve the hosting activity defensively; never crash on a null or
    // wrapped context (e.g. previews or unusual host configurations).
    val activity = (LocalActivity.current as? ComponentActivity)
        ?: context.findActivity() as? ComponentActivity
    val vm: OnboardingViewModel =
        if (activity != null) {
            viewModel(viewModelStoreOwner = activity)
        } else {
            viewModel()
        }
    val scope = rememberCoroutineScope()
    val onboardedState by context.onboardingComplete().collectAsState(initial = null)
    val onboarded = onboardedState
    val colors = LocalAppColors.current

    // If onboarding was never completed but all required permissions are
    // already granted, skip the permission screens entirely and go straight to
    // the main app (smoother first-run experience).
    val canSkip = remember(context) { requiredPermissionsGranted(context) }
    LaunchedEffect(onboarded, canSkip) {
        if (onboarded == false && canSkip) {
            context.markOnboardingComplete()
        }
    }

    if (onboarded == null) {
        Box(modifier = modifier.fillMaxSize().background(colors.background))
        return
    }

    val showMain = onboarded == true || (onboarded == false && canSkip)

    NavHost(
        navController = navController,
        startDestination = if (showMain) "main" else "welcome",
        modifier = modifier
    ) {
        composable("welcome") {
            WelcomeScreen(onGetStarted = { navController.navigate("permissions") })
        }

        composable("permissions") {
            val granted by vm.granted.collectAsState()
            val notificationLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted) {
                    vm.markGranted(KEY_NOTIFICATIONS)
                    navController.navigate("permbattery")
                } else {
                    Toast.makeText(
                        context, R.string.perm_required_toast, Toast.LENGTH_SHORT
                    ).show()
                }
            }
            PermissionScreen(
                title = context.getString(R.string.perm_notifications_title),
                subtitle = context.getString(R.string.perm_notifications_sub),
                icon = BellIcon,
                iconTint = colors.brandDark,
                iconBg = colors.brandSoft,
                required = true,
                step = 1,
                totalSteps = 3,
                granted = KEY_NOTIFICATIONS in granted,
                onBack = { navController.popBackStack() },
                onGrant = {
                    if (Build.VERSION.SDK_INT >= 33) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        vm.markGranted(KEY_NOTIFICATIONS)
                        navController.navigate("permbattery")
                    }
                }
            )
        }

        composable("permbattery") {
            var pendingReturn by rememberSaveable { mutableStateOf(false) }
            val granted by vm.granted.collectAsState()
            fun onGranted() {
                vm.markGranted(KEY_BATTERY)
                navController.navigate("perma11y")
            }
            fun grant() {
                pendingReturn = true
                try {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                } catch (e: ActivityNotFoundException) {
                    context.startActivity(
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    )
                }
            }
            OnResumeEffect {
                if (pendingReturn) {
                    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                    if (pm.isIgnoringBatteryOptimizations(context.packageName)) {
                        pendingReturn = false
                        onGranted()
                    } else {
                        pendingReturn = false
                    }
                }
            }
            PermissionScreen(
                title = context.getString(R.string.perm_battery_title),
                subtitle = context.getString(R.string.perm_battery_sub),
                icon = BatteryIcon,
                iconTint = colors.success,
                iconBg = colors.iconGreenBg,
                required = false,
                step = 2,
                totalSteps = 3,
                granted = KEY_BATTERY in granted,
                onBack = { navController.popBackStack() },
                onGrant = ::grant,
                skipLabel = context.getString(R.string.perm_skip),
                onSkip = {
                    Toast.makeText(
                        context, R.string.perm_skipped_toast, Toast.LENGTH_SHORT
                    ).show()
                    navController.navigate("perma11y")
                }
            )
        }

        composable("perma11y") {
            var pendingReturn by rememberSaveable { mutableStateOf(false) }
            val granted by vm.granted.collectAsState()
            fun finishOnboarding() {
                vm.markGranted(KEY_ACCESSIBILITY)
                if (KEY_NOTIFICATIONS in vm.granted.value && KEY_ACCESSIBILITY in vm.granted.value) {
                    scope.launch { context.markOnboardingComplete() }
                    navController.navigate("main") {
                        popUpTo(0) { inclusive = true }
                    }
                    Toast.makeText(
                        context, R.string.perm_welcome_toast, Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        context, R.string.perm_required_toast, Toast.LENGTH_SHORT
                    ).show()
                }
            }
            fun grant() {
                if (isAccessibilityEnabled(context)) {
                    finishOnboarding()
                } else {
                    pendingReturn = true
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
            OnResumeEffect {
                if (pendingReturn) {
                    pendingReturn = false
                    if (isAccessibilityEnabled(context)) {
                        finishOnboarding()
                    } else {
                        Toast.makeText(
                            context, R.string.perm_required_toast, Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            PermissionScreen(
                title = context.getString(R.string.perm_a11y_title),
                subtitle = context.getString(R.string.perm_a11y_sub),
                icon = A11yPersonIcon,
                iconTint = colors.iconDarkFg,
                iconBg = colors.iconDarkBg,
                required = true,
                step = 3,
                totalSteps = 3,
                granted = KEY_ACCESSIBILITY in granted,
                onBack = { navController.popBackStack() },
                onGrant = ::grant
            )
        }

        composable("main") {
            com.safeme.app.ui.screens.main.MainScreen()
        }
    }
}

@Composable
private fun OnResumeEffect(onResume: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

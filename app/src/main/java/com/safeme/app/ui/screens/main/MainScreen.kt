package com.safeme.app.ui.screens.main

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.safeme.app.R
import com.safeme.app.ui.components.BottomNavBar
import com.safeme.app.ui.screens.antitamper.AccessibilityProtectionScreen
import com.safeme.app.ui.screens.antitamper.AntiTamperScreen
import com.safeme.app.ui.screens.blocking.BlockingScreen
import com.safeme.app.ui.screens.blockscreen.BlockScreen
import com.safeme.app.ui.screens.focus.FocusScreen
import com.safeme.app.ui.screens.home.HomeScreen
import com.safeme.app.ui.screens.keywords.KeywordManagerScreen
import com.safeme.app.ui.screens.profile.ProfileScreen
import com.safeme.app.ui.screens.schedule.ScheduleScreen
import com.safeme.app.ui.screens.titleblock.TitleBlockScreen
import com.safeme.app.ui.screens.vpn.DnsVpnScreen
import com.safeme.app.ui.theme.LocalAppColors

private val MainTabRoutes = setOf("home", "block", "focus", "schedule", "profile")

@Composable
fun MainScreen() {
    val colors = LocalAppColors.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in MainTabRoutes
    val context = LocalContext.current

    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = colors.background,
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(
                    currentRoute = currentRoute,
                    onNavigate = navigateToTab,
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            // Every screen applies its own statusBarsPadding(), so consuming
            // the Scaffold's top inset here too would double-pad and push all
            // content down by a full status-bar height (leaving a dead strip at
            // the top and pushing the bottom of each screen off-screen). Only
            // the bottom padding (bottom bar / gesture inset) comes from the
            // Scaffold.
            modifier = Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding()),
            enterTransition = { slideInHorizontally(animationSpec = tween(250), initialOffsetX = { it }) + fadeIn(animationSpec = tween(250)) },
            exitTransition = { fadeOut(animationSpec = tween(200)) },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(250), initialOffsetX = { -it }) + fadeIn(animationSpec = tween(250)) },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(200), targetOffsetX = { it }) + fadeOut(animationSpec = tween(200)) },
        ) {
            composable("home") {
                HomeScreen(
                    onReviewShield = { navigateToTab("block") },
                    onStartFocus = { navigateToTab("focus") },
                    onAddKeyword = { navController.navigate("keywords") },
                    onNewSchedule = { navController.navigate("scheduleedit") },
                    onBackup = { navController.navigate("backup") },
                    onHistory = { navController.navigate("history") },
                    onOpenAccessibility = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                )
            }
            composable("block") {
                BlockingScreen(
                    onOpenBlockScreen = { navController.navigate("blockscreen") },
                    onOpenVpn = { navController.navigate("vpn") },
                    onOpenAntiTamper = { navController.navigate("antitamper") },
                    onOpenKeywords = {
                        navController.navigate("keywords?type=keywords&tab=blocklist")
                    },
                    onOpenWebsites = {
                        navController.navigate("keywords?type=websites&tab=blocked")
                    },
                    onOpenTitleBlock = { navController.navigate("titleblock") },
                )
            }
            composable("blockscreen") {
                BlockScreen(onBack = { navController.popBackStack() })
            }
            composable("vpn") {
                DnsVpnScreen(onBack = { navController.popBackStack() })
            }
            composable("antitamper") {
                AntiTamperScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAccessibilityProtection = { navController.navigate("selfprotect") },
                )
            }
            composable("selfprotect") {
                AccessibilityProtectionScreen(onBack = { navController.popBackStack() })
            }
            composable("focus") {
                FocusScreen(
                    onStartFocus = { navController.navigate("focusactive") },
                    onManageWhitelist = { navController.navigate("focuswhitelist") },
                )
            }
            composable("schedule") {
                ScheduleScreen(
                    onNewSchedule = { navController.navigate("scheduleedit") },
                    onEditSchedule = { navController.navigate("scheduleedit") }
                )
            }
            composable("profile") {
                ProfileScreen(
                    onOpen = { target ->
                        when (target) {
                            "permissions" -> navController.navigate("permissions")
                            "backup" -> navController.navigate("backup")
                            "troubleshoot" -> navController.navigate("troubleshoot")
                            "crash" -> navController.navigate("crash")
                            "relay" -> navController.navigate("relay")
                            "about" -> navController.navigate("about")
                        }
                    }
                )
            }
            composable(
                route = "keywords?type={type}&tab={tab}",
                arguments = listOf(
                    navArgument("type") { defaultValue = "keywords" },
                    navArgument("tab") { defaultValue = "blocklist" },
                ),
            ) { entry ->
                KeywordManagerScreen(
                    initialType = entry.arguments?.getString("type") ?: "keywords",
                    initialTab = entry.arguments?.getString("tab") ?: "blocklist",
                    onBack = { navController.popBackStack() },
                )
            }
            composable("scheduleedit") { PlaceholderScreen(R.string.home_scheduleedit_title) }
            composable("backup") { PlaceholderScreen(R.string.home_backup_title) }
            composable("history") { PlaceholderScreen(R.string.home_history_title) }
            composable("focusactive") { PlaceholderScreen(R.string.foc_active_title) }
            composable("focuswhitelist") { PlaceholderScreen(R.string.foc_whitelist_placeholder) }
            composable("permissions") { PlaceholderScreen(R.string.prof_permissions_title) }
            composable("troubleshoot") { PlaceholderScreen(R.string.prof_troubleshoot_title) }
            composable("crash") { PlaceholderScreen(R.string.prof_crash_title) }
            composable("relay") { PlaceholderScreen(R.string.prof_relay_title) }
            composable("about") { PlaceholderScreen(R.string.prof_about_title) }
            composable("titleblock") {
                TitleBlockScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(titleRes: Int) {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(titleRes),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = colors.ink,
        )
    }
}

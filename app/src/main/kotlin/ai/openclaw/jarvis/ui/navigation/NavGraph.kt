package ai.openclaw.jarvis.ui.navigation

import ai.openclaw.jarvis.policy.ui.PendingApprovalsViewModel
import ai.openclaw.jarvis.ui.screens.CapabilityDashboardScreen
import ai.openclaw.jarvis.ui.screens.DebugScreen
import ai.openclaw.jarvis.ui.screens.HistoryScreen
import ai.openclaw.jarvis.ui.screens.HomeScreen
import ai.openclaw.jarvis.ui.screens.OpenClawDiagnosticsScreen
import ai.openclaw.jarvis.ui.screens.PendingActionTakeoverScreen
import ai.openclaw.jarvis.ui.screens.PendingApprovalsScreen
import ai.openclaw.jarvis.ui.screens.ProtocolDebugScreen
import ai.openclaw.jarvis.ui.screens.SettingsScreen
import ai.openclaw.jarvis.ui.screens.SuggestionsScreen
import ai.openclaw.jarvis.ui.screens.SystemScreen
import ai.openclaw.jarvis.ui.theme.BlueprintBackground
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

object Routes {
    // Top-level tabs
    const val HOME         = "home"
    const val SUGGESTIONS  = "suggestions"
    const val HISTORY      = "history"
    const val SYSTEM       = "system"

    // Pushed destinations (above the bottom nav)
    const val SETTINGS              = "settings"
    const val APPROVALS             = "approvals"
    const val DEBUG                 = "debug"
    const val PROTOCOL              = "protocol"
    const val CAPABILITIES          = "capabilities"
    const val PENDING_TAKEOVER      = "pending_takeover"
    const val OPENCLAW_DIAGNOSTICS  = "openclaw_diagnostics"
}

/** Log every navigation event: source control, from route, target route. */
private fun navLog(source: String, from: String?, to: String) {
    Log.d("NAV", "$source | ${from ?: "start"} → $to")
}

/**
 * Top-level scaffold + nav. The bottom nav is rooted at the four spec
 * tabs; everything else is pushed above it (Settings, Approvals,
 * Debug, Protocol, full Capability dashboard, and the pending-action
 * takeover route).
 *
 * Navigation rules:
 *   - Top-left menu  → NavigationDrawerSheet (never Settings directly)
 *   - Top-right gear → Settings hub
 *   - BottomNav tabs → their respective screens (System ≠ Settings)
 *   - System screen  → has an "Open Settings" button for Settings
 */
@Composable
fun JarvisNavGraph(
    navController: NavHostController = rememberNavController(),
    approvalsVm: PendingApprovalsViewModel = hiltViewModel(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomNav = currentRoute in TOP_LEVEL_TABS

    var showNavDrawer by remember { mutableStateOf(false) }

    val approvals by approvalsVm.approvals.collectAsStateWithLifecycle()
    LaunchedEffect(approvals.isNotEmpty()) {
        if (approvals.isNotEmpty() && currentRoute != Routes.PENDING_TAKEOVER) {
            navLog("AutoTakeover", currentRoute, Routes.PENDING_TAKEOVER)
            navController.navigate(Routes.PENDING_TAKEOVER) { launchSingleTop = true }
        }
    }

    Scaffold(
        containerColor = BlueprintBackground,
        bottomBar = { if (showBottomNav) JarvisBottomNav(navController) },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            NavHost(
                navController    = navController,
                startDestination = Routes.HOME,
                modifier         = Modifier
                    .fillMaxSize()
                    .background(BlueprintBackground),
            ) {
                // ── Tabs ──────────────────────────────────────────────────────
                composable(Routes.HOME) {
                    HomeScreen(
                        onMenuClick       = {
                            navLog("TopBar.Menu", Routes.HOME, "Drawer")
                            showNavDrawer = true
                        },
                        onSettingsClick   = {
                            navLog("TopBar.Gear", Routes.HOME, Routes.SETTINGS)
                            navController.navigate(Routes.SETTINGS)
                        },
                        onOpenSuggestions = {
                            navLog("Home.Suggestions", Routes.HOME, Routes.SUGGESTIONS)
                            navController.navigate(Routes.SUGGESTIONS)
                        },
                        onApproveCurrentAction = {
                            navLog("Home.Approve", Routes.HOME, Routes.APPROVALS)
                            navController.navigate(Routes.APPROVALS)
                        },
                        onOpenDiagnostics = {
                            navLog("Home.Diagnostics", Routes.HOME, Routes.OPENCLAW_DIAGNOSTICS)
                            navController.navigate(Routes.OPENCLAW_DIAGNOSTICS)
                        },
                    )
                }

                composable(Routes.SUGGESTIONS) { SuggestionsScreen() }
                composable(Routes.HISTORY)     { HistoryScreen() }

                composable(Routes.SYSTEM) {
                    SystemScreen(
                        onOpenSettings = {
                            navLog("System.OpenSettings", Routes.SYSTEM, Routes.SETTINGS)
                            navController.navigate(Routes.SETTINGS)
                        },
                        onOpenApprovals = {
                            navLog("System.Approvals", Routes.SYSTEM, Routes.APPROVALS)
                            navController.navigate(Routes.APPROVALS)
                        },
                        onOpenDebug = {
                            navLog("System.Debug", Routes.SYSTEM, Routes.DEBUG)
                            navController.navigate(Routes.DEBUG)
                        },
                        onOpenProtocol = {
                            navLog("System.Protocol", Routes.SYSTEM, Routes.PROTOCOL)
                            navController.navigate(Routes.PROTOCOL)
                        },
                    )
                }

                // ── Above-tab destinations ────────────────────────────────────
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack            = { navController.popBackStack() },
                        onOpenApprovals   = {
                            navLog("Settings.Approvals", Routes.SETTINGS, Routes.APPROVALS)
                            navController.navigate(Routes.APPROVALS)
                        },
                        onOpenDiagnostics = {
                            navLog("Settings.Diagnostics", Routes.SETTINGS, Routes.OPENCLAW_DIAGNOSTICS)
                            navController.navigate(Routes.OPENCLAW_DIAGNOSTICS)
                        },
                    )
                }

                composable(Routes.OPENCLAW_DIAGNOSTICS) {
                    OpenClawDiagnosticsScreen(
                        onBack = { navController.popBackStack() },
                        onEditConnection = {
                            navLog("Diagnostics.EditConnection", Routes.OPENCLAW_DIAGNOSTICS, Routes.SETTINGS)
                            navController.popBackStack()
                            navController.navigate(Routes.SETTINGS)
                        },
                    )
                }

                composable(Routes.APPROVALS) {
                    PendingApprovalsScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.DEBUG) {
                    DebugScreen(
                        onBack                    = { navController.popBackStack() },
                        onNavigateToProtocol      = {
                            navLog("Debug.Protocol", Routes.DEBUG, Routes.PROTOCOL)
                            navController.navigate(Routes.PROTOCOL)
                        },
                        onNavigateToCapabilities  = {
                            navLog("Debug.Capabilities", Routes.DEBUG, Routes.CAPABILITIES)
                            navController.navigate(Routes.CAPABILITIES)
                        },
                    )
                }

                composable(Routes.PROTOCOL) {
                    ProtocolDebugScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.CAPABILITIES) {
                    CapabilityDashboardScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.PENDING_TAKEOVER) {
                    PendingActionTakeoverScreen(onResolved = { navController.popBackStack() })
                }
            }
        }
    }

    // Navigation drawer sheet — shown over any tab, dismissed on any selection
    if (showNavDrawer) {
        NavigationDrawerSheet(
            currentRoute          = currentRoute,
            onDismiss             = { showNavDrawer = false },
            onNavigateHome        = {
                navLog("Drawer.Home", currentRoute, Routes.HOME)
                showNavDrawer = false
                navController.navigate(Routes.HOME) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true; restoreState = true
                }
            },
            onNavigateSuggestions = {
                navLog("Drawer.Suggestions", currentRoute, Routes.SUGGESTIONS)
                showNavDrawer = false
                navController.navigate(Routes.SUGGESTIONS) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true; restoreState = true
                }
            },
            onNavigateHistory     = {
                navLog("Drawer.History", currentRoute, Routes.HISTORY)
                showNavDrawer = false
                navController.navigate(Routes.HISTORY) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true; restoreState = true
                }
            },
            onNavigateSystem      = {
                navLog("Drawer.System", currentRoute, Routes.SYSTEM)
                showNavDrawer = false
                navController.navigate(Routes.SYSTEM) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true; restoreState = true
                }
            },
            onNavigateSettings    = {
                navLog("Drawer.Settings", currentRoute, Routes.SETTINGS)
                showNavDrawer = false
                navController.navigate(Routes.SETTINGS)
            },
            onNavigateDiagnostics = {
                navLog("Drawer.Diagnostics", currentRoute, Routes.OPENCLAW_DIAGNOSTICS)
                showNavDrawer = false
                navController.navigate(Routes.OPENCLAW_DIAGNOSTICS)
            },
        )
    }
}

private val TOP_LEVEL_TABS = setOf(
    Routes.HOME, Routes.SUGGESTIONS, Routes.HISTORY, Routes.SYSTEM,
)

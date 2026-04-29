package ai.openclaw.jarvis.ui.navigation

import ai.openclaw.jarvis.policy.ui.PendingApprovalsViewModel
import ai.openclaw.jarvis.ui.screens.CapabilityDashboardScreen
import ai.openclaw.jarvis.ui.screens.DebugScreen
import ai.openclaw.jarvis.ui.screens.HistoryScreen
import ai.openclaw.jarvis.ui.screens.HomeScreen
import ai.openclaw.jarvis.ui.screens.PendingActionTakeoverScreen
import ai.openclaw.jarvis.ui.screens.PendingApprovalsScreen
import ai.openclaw.jarvis.ui.screens.ProtocolDebugScreen
import ai.openclaw.jarvis.ui.screens.SettingsScreen
import ai.openclaw.jarvis.ui.screens.SuggestionsScreen
import ai.openclaw.jarvis.ui.screens.SystemScreen
import ai.openclaw.jarvis.ui.theme.BlueprintBackground
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
    const val SETTINGS         = "settings"
    const val APPROVALS        = "approvals"
    const val DEBUG            = "debug"
    const val PROTOCOL         = "protocol"
    const val CAPABILITIES     = "capabilities"
    const val PENDING_TAKEOVER = "pending_takeover"
}

/**
 * Top-level scaffold + nav. The bottom nav is rooted at the four spec
 * tabs; everything else is pushed above it (Settings, Approvals,
 * Debug, Protocol, full Capability dashboard, and the pending-action
 * takeover route).
 */
@Composable
fun JarvisNavGraph(
    navController: NavHostController = rememberNavController(),
    approvalsVm: PendingApprovalsViewModel = hiltViewModel(),
    onScreenshotProjectionRequest: ((android.content.Intent) -> Unit)? = null,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomNav = currentRoute in TOP_LEVEL_TABS

    // Auto-takeover: the moment a pending approval lands, push the
    // full-screen takeover route. Idempotent — if the user is already
    // there, this is a no-op.
    val approvals by approvalsVm.approvals.collectAsStateWithLifecycle()
    LaunchedEffect(approvals.isNotEmpty()) {
        if (approvals.isNotEmpty() && currentRoute != Routes.PENDING_TAKEOVER) {
            navController.navigate(Routes.PENDING_TAKEOVER) {
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        containerColor = BlueprintBackground,
        bottomBar = { if (showBottomNav) JarvisBottomNav(navController) },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier
                    .fillMaxSize()
                    .background(BlueprintBackground),
            ) {
                // ── Tabs ──────────────────────────────────────────────
                composable(Routes.HOME) {
                    HomeScreen(
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onOpenSuggestions = { navController.navigate(Routes.SUGGESTIONS) },
                        onApproveCurrentAction = { navController.navigate(Routes.APPROVALS) },
                        onScreenshotProjectionRequest = onScreenshotProjectionRequest,
                    )
                }
                composable(Routes.SUGGESTIONS) { SuggestionsScreen() }
                composable(Routes.HISTORY) { HistoryScreen() }
                composable(Routes.SYSTEM) {
                    SystemScreen(
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onOpenApprovals = { navController.navigate(Routes.APPROVALS) },
                        onOpenDebug = { navController.navigate(Routes.DEBUG) },
                        onOpenProtocol = { navController.navigate(Routes.PROTOCOL) },
                    )
                }

                // ── Above-tab destinations ────────────────────────────
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenApprovals = { navController.navigate(Routes.APPROVALS) },
                    )
                }
                composable(Routes.APPROVALS) {
                    PendingApprovalsScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.DEBUG) {
                    DebugScreen(
                        onBack = { navController.popBackStack() },
                        onNavigateToProtocol = { navController.navigate(Routes.PROTOCOL) },
                        onNavigateToCapabilities = { navController.navigate(Routes.CAPABILITIES) },
                    )
                }
                composable(Routes.PROTOCOL) {
                    ProtocolDebugScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.CAPABILITIES) {
                    CapabilityDashboardScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.PENDING_TAKEOVER) {
                    PendingActionTakeoverScreen(
                        onResolved = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

private val TOP_LEVEL_TABS = setOf(
    Routes.HOME, Routes.SUGGESTIONS, Routes.HISTORY, Routes.SYSTEM,
)

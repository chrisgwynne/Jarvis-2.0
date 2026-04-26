package ai.openclaw.jarvis.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ai.openclaw.jarvis.ui.screens.CapabilityDashboardScreen
import ai.openclaw.jarvis.ui.screens.DebugScreen
import ai.openclaw.jarvis.ui.screens.MainScreen
import ai.openclaw.jarvis.ui.screens.ProtocolDebugScreen
import ai.openclaw.jarvis.ui.screens.SettingsScreen

object Routes {
    const val MAIN          = "main"
    const val SETTINGS      = "settings"
    const val DEBUG         = "debug"
    const val PROTOCOL      = "protocol"
    const val CAPABILITIES  = "capabilities"
}

@Composable
fun JarvisNavGraph(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Routes.MAIN,
    ) {
        composable(Routes.MAIN) {
            MainScreen(
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToDebug    = { navController.navigate(Routes.DEBUG) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.DEBUG) {
            DebugScreen(
                onBack = { navController.popBackStack() },
                onNavigateToProtocol = { navController.navigate(Routes.PROTOCOL) },
                onNavigateToCapabilities = { navController.navigate(Routes.CAPABILITIES) },
            )
        }
        composable(Routes.PROTOCOL) {
            ProtocolDebugScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.CAPABILITIES) {
            CapabilityDashboardScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}

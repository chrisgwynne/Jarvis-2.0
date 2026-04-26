package ai.openclaw.jarvis.ui.navigation

import ai.openclaw.jarvis.ui.theme.BlueprintBorder
import ai.openclaw.jarvis.ui.theme.BlueprintSurface
import ai.openclaw.jarvis.ui.theme.CobaltBright
import ai.openclaw.jarvis.ui.theme.TextDim
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

/**
 * 4-tab bottom navigation per the spec — Home / Suggestions / History /
 * System. "Capabilities" deliberately lives inside System rather than as
 * a top-level tab; the spec calls that out.
 */
@Composable
fun JarvisBottomNav(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavigationBar(
        containerColor = BlueprintSurface,
        contentColor = CobaltBright,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
    ) {
        TABS.forEach { tab ->
            val selected = backStackEntry?.destination?.hierarchy?.any { it.route == tab.route } == true ||
                currentRoute == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (currentRoute == tab.route) return@NavigationBarItem
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = CobaltBright,
                    selectedTextColor = CobaltBright,
                    indicatorColor = BlueprintBorder,
                    unselectedIconColor = TextDim,
                    unselectedTextColor = TextDim,
                ),
            )
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.HOME, "Home", Icons.Filled.Home),
    Tab(Routes.SUGGESTIONS, "Suggestions", Icons.Filled.Lightbulb),
    Tab(Routes.HISTORY, "History", Icons.Filled.History),
    Tab(Routes.SYSTEM, "System", Icons.Filled.Build),
)

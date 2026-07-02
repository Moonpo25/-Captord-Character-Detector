package com.captord.screenrecorder.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.captord.screenrecorder.ui.gallery.GalleryScreen
import com.captord.screenrecorder.ui.home.HomeScreen
import com.captord.screenrecorder.ui.settings.SettingsScreen

object CaptordRoutes {
    const val HOME = "home"
    const val GALLERY = "gallery"
    const val SETTINGS = "settings"
}

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(CaptordRoutes.HOME, "Home", Icons.Filled.Home),
    TopLevelDestination(CaptordRoutes.GALLERY, "Gallery", Icons.Filled.VideoLibrary),
    TopLevelDestination(CaptordRoutes.SETTINGS, "Settings", Icons.Filled.Settings),
)

@Composable
fun CaptordNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                topLevelDestinations.forEach { destination ->
                    val selected = currentDestination
                        ?.hierarchy
                        ?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.label,
                            )
                        },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = CaptordRoutes.HOME,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(CaptordRoutes.HOME) { HomeScreen() }
            composable(CaptordRoutes.GALLERY) { GalleryScreen() }
            composable(CaptordRoutes.SETTINGS) { SettingsScreen() }
        }
    }
}

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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.captord.screenrecorder.feature.gallery.GalleryViewModel
import com.captord.screenrecorder.feature.recorder.RecordingViewModel
import com.captord.screenrecorder.ui.gallery.GalleryScreen
import com.captord.screenrecorder.ui.gallery.PreviewScreen
import com.captord.screenrecorder.ui.home.HomeScreen
import com.captord.screenrecorder.ui.settings.SettingsScreen
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object CaptordRoutes {
    const val HOME = "home"
    const val GALLERY = "gallery"
    const val SETTINGS = "settings"
    const val PREVIEW = "preview/{encodedPath}"

    fun preview(path: String): String {
        val encoded = URLEncoder.encode(path, StandardCharsets.UTF_8.toString())
        return "preview/$encoded"
    }
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
fun CaptordNavHost(
    onRequestStartRecording: () -> Unit,
    recordingViewModel: RecordingViewModel = viewModel(),
    galleryViewModel: GalleryViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = currentDestination?.route?.startsWith("preview") != true

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showBottomBar) {
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
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = CaptordRoutes.HOME,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(CaptordRoutes.HOME) {
                HomeScreen(
                    viewModel = recordingViewModel,
                    galleryViewModel = galleryViewModel,
                    onRequestStart = onRequestStartRecording,
                    onOpenGallery = {
                        navController.navigate(CaptordRoutes.GALLERY) {
                            launchSingleTop = true
                        }
                    },
                    onOpenPreview = { path ->
                        navController.navigate(CaptordRoutes.preview(path))
                    },
                )
            }
            composable(CaptordRoutes.GALLERY) {
                GalleryScreen(
                    onOpenPreview = { path ->
                        navController.navigate(CaptordRoutes.preview(path))
                    },
                    galleryViewModel = galleryViewModel,
                )
            }
            composable(CaptordRoutes.SETTINGS) { SettingsScreen() }
            composable(
                route = CaptordRoutes.PREVIEW,
                arguments = listOf(navArgument("encodedPath") { type = NavType.StringType }),
            ) { entry ->
                val encoded = entry.arguments?.getString("encodedPath").orEmpty()
                PreviewScreen(
                    encodedPath = encoded,
                    onBack = { navController.popBackStack() },
                    galleryViewModel = galleryViewModel,
                )
            }
        }
    }
}

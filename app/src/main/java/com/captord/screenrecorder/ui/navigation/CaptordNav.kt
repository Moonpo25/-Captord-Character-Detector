package com.captord.screenrecorder.ui.navigation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.captord.screenrecorder.feature.gallery.GalleryViewModel
import com.captord.screenrecorder.feature.recorder.RecordingViewModel
import com.captord.screenrecorder.ui.components.FloatingNavToolbar
import com.captord.screenrecorder.ui.gallery.GalleryScreen
import com.captord.screenrecorder.ui.gallery.PreviewScreen
import com.captord.screenrecorder.ui.home.HomeScreen
import com.captord.screenrecorder.ui.settings.SettingsScreen
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object CaptordRoutes {
    const val MAIN = "main"
    const val PREVIEW = "preview/{encodedPath}"
}

@Composable
fun CaptordNavHost(
    onRequestStartRecording: () -> Unit,
    recordingViewModel: RecordingViewModel,
    galleryViewModel: GalleryViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        modifier = modifier.fillMaxSize(),
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = CaptordRoutes.MAIN,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(CaptordRoutes.MAIN) {
                MainContainerScreen(
                    onRequestStartRecording = onRequestStartRecording,
                    recordingViewModel = recordingViewModel,
                    galleryViewModel = galleryViewModel,
                    onOpenPreview = { path ->
                        val encoded = URLEncoder.encode(path, StandardCharsets.UTF_8.toString())
                        navController.navigate("preview/$encoded")
                    }
                )
            }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainContainerScreen(
    onRequestStartRecording: () -> Unit,
    recordingViewModel: RecordingViewModel,
    galleryViewModel: GalleryViewModel,
    onOpenPreview: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()
    val galleryBadgeCount by galleryViewModel.badgeCount.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 0,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> HomeScreen(
                    viewModel = recordingViewModel,
                    galleryViewModel = galleryViewModel,
                    onRequestStart = onRequestStartRecording,
                    onOpenGallery = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(1)
                        }
                    },
                    onOpenPreview = onOpenPreview,
                )
                1 -> GalleryScreen(
                    onOpenPreview = onOpenPreview,
                    galleryViewModel = galleryViewModel,
                    onRequestStart = onRequestStartRecording,
                    isActive = pagerState.settledPage == 1,
                )
                2 -> SettingsScreen()
            }
        }

        FloatingNavToolbar(
            currentPage = pagerState.currentPage,
            onPageSelected = { page ->
                coroutineScope.launch {
                    pagerState.animateScrollToPage(page)
                }
            },
            galleryBadgeCount = galleryBadgeCount,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
}

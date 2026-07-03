/*
 * OmegaPlayer Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/

package com.arslandaim.omegaplayer

import android.os.Bundle
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.arslandaim.omegaplayer.ui.home.HomeScreen
import com.arslandaim.omegaplayer.ui.locker.LockerScreen
import com.arslandaim.omegaplayer.ui.navigation.Screen
import com.arslandaim.omegaplayer.ui.player.PlayerScreen
import com.arslandaim.omegaplayer.ui.settings.SettingsScreen
import com.arslandaim.omegaplayer.ui.theme.OmegaPlayerTheme
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import androidx.lifecycle.viewmodel.compose.viewModel
import com.arslandaim.omegaplayer.viewmodel.VideoViewModel
import com.arslandaim.omegaplayer.viewmodel.AudioViewModel
import com.arslandaim.omegaplayer.viewmodel.ThemeViewModel
import com.arslandaim.omegaplayer.viewmodel.LockerViewModel
import com.arslandaim.omegaplayer.ui.player.AudioPlayerScreen
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.launch

@OptIn(ExperimentalSharedTransitionApi::class)
class MainActivity : FragmentActivity() {
    private var isPlayerActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val videoViewModel: VideoViewModel = viewModel()
            val audioViewModel: AudioViewModel = viewModel()
            val themeViewModel: ThemeViewModel = viewModel()
            val lockerViewModel: LockerViewModel = viewModel()
            val appTheme by themeViewModel.theme.collectAsState()
            
            val isDarkTheme = when (appTheme) {
                com.arslandaim.omegaplayer.data.AppTheme.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                com.arslandaim.omegaplayer.data.AppTheme.LIGHT -> false
                com.arslandaim.omegaplayer.data.AppTheme.DARK -> true
            }

            // Global Security: Lock on app stop
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                        lockerViewModel.lock()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            OmegaPlayerTheme(appTheme = appTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    
                    DisposableEffect(Unit) {
                        val consumer = androidx.core.util.Consumer<Intent> { intent ->
                            navController.handleDeepLink(intent)
                        }
                        addOnNewIntentListener(consumer)
                        onDispose { removeOnNewIntentListener(consumer) }
                    }

                    SharedTransitionLayout {
                        NavHost(
                            navController = navController,
                            startDestination = Screen.Main.route
                        ) {
                            composable(
                                route = Screen.Main.route,
                                arguments = listOf(navArgument("tab") { 
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                }),
                                deepLinks = listOf(
                                    navDeepLink { uriPattern = "omegaplayer://main?tab={tab}" }
                                )
                            ) { backStackEntry ->
                                val tab = backStackEntry.arguments?.getString("tab")
                                isPlayerActive = false
                                
                                val homeTab = when(tab) {
                                    "videos" -> com.arslandaim.omegaplayer.ui.home.MediaTab.VIDEOS
                                    "audios" -> com.arslandaim.omegaplayer.ui.home.MediaTab.AUDIOS
                                    else -> null
                                }

                                MainScreen(
                                    videoViewModel, 
                                    audioViewModel,
                                    lockerViewModel,
                                    navController,
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this@composable,
                                    initialTab = homeTab
                                )
                            }
                            composable(
                                route = Screen.Player.route,
                                arguments = listOf(
                                    navArgument("videoUri") { type = NavType.StringType },
                                    navArgument("from") { 
                                        type = NavType.StringType
                                        nullable = true
                                        defaultValue = null
                                    }
                                ),
                                enterTransition = {
                                    androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing)) + 
                                    androidx.compose.animation.scaleIn(initialScale = 0.92f, animationSpec = androidx.compose.animation.core.tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                                },
                                exitTransition = {
                                    androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing)) + 
                                    androidx.compose.animation.scaleOut(targetScale = 0.92f, animationSpec = androidx.compose.animation.core.tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                                },
                                popEnterTransition = {
                                    androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing)) +
                                    androidx.compose.animation.scaleIn(initialScale = 0.92f, animationSpec = androidx.compose.animation.core.tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                                },
                                popExitTransition = {
                                    androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing)) + 
                                    androidx.compose.animation.scaleOut(targetScale = 0.92f, animationSpec = androidx.compose.animation.core.tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                                }
                            ) { backStackEntry ->
                                val encodedUri = backStackEntry.arguments?.getString("videoUri") ?: ""
                                val decodedUri = URLDecoder.decode(encodedUri, StandardCharsets.UTF_8.toString())
                                isPlayerActive = true
                                PlayerScreen(
                                    videoUri = decodedUri, 
                                    viewModel = videoViewModel,
                                    isDarkTheme = isDarkTheme,
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this@composable,
                                    onBack = { 
                                        // Standard robust back: just pop back to return to exactly where we were
                                        if (!navController.popBackStack()) {
                                            // Fallback for deep links: navigate to home
                                            navController.navigate(Screen.Main.createRoute("videos")) {
                                                popUpTo(Screen.Main.route) { inclusive = false }
                                            }
                                        }
                                    }
                                )
                            }
                            composable(
                                route = Screen.AudioPlayer.route,
                                arguments = listOf(navArgument("audioUri") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val encodedUri = backStackEntry.arguments?.getString("audioUri") ?: ""
                                val decodedUri = URLDecoder.decode(encodedUri, StandardCharsets.UTF_8.toString())
                                AudioPlayerScreen(
                                    audioUri = decodedUri,
                                    viewModel = audioViewModel,
                                    onBack = { 
                                        // Standard robust back: just pop back to return to exactly where we were
                                        if (!navController.popBackStack()) {
                                            // Fallback: navigate back to the main screen's audio tab
                                            navController.navigate(Screen.Main.createRoute("audios")) {
                                                popUpTo(Screen.Main.route) { inclusive = false }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MainScreen(
    videoViewModel: VideoViewModel,
    audioViewModel: AudioViewModel,
    lockerViewModel: LockerViewModel,
    navController: NavHostController,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    initialTab: com.arslandaim.omegaplayer.ui.home.MediaTab? = null
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val hazeState = remember { HazeState() }

    LaunchedEffect(initialTab) {
        if (initialTab != null) {
            pagerState.scrollToPage(0)
        }
    }

    // Security: Lock when swiping away
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != 1) {
            lockerViewModel.lock()
        }
    }

    Scaffold(
        // This tells the scaffold not to consume navigation bar insets globally.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(
                modifier = Modifier.hazeChild(
                    state = hazeState,
                    style = HazeDefaults.style(
                        backgroundColor = Color(0xFFFFFFFF),
//                        blurRadius = 20.dp,
//                        noiseFactor = 0.8f
                    )
                ),
//                containerColor = Color.Transparent,
                windowInsets = WindowInsets.navigationBars
            ) {
                NavigationBarItem(
                    icon = {
                        val scale by animateFloatAsState(
                            targetValue = if (pagerState.currentPage == 0) 1.25f else 1.0f,
                            animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                            label = "HomeScale"
                        )
                        val iconColor by animateColorAsState(
                            targetValue = if (pagerState.currentPage == 0) Color(0xFF121212) else Color.Gray,
                            label = "HomeColor"
                        )
                        Box(modifier = Modifier.scale(scale).offset(y = 1.dp)) {
                            Icon(Icons.Default.Home, contentDescription = "Home", tint = iconColor)
                        }
                    },
                    label = { Text("Home", modifier = Modifier.offset(y = 2.dp)) },
                    selected = pagerState.currentPage == 0,
                    alwaysShowLabel = false, // Makes text visible ONLY when clicked
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } }
                )
                NavigationBarItem(
                    modifier = Modifier.background(Color.Transparent),
                    icon = {
                        val scale by animateFloatAsState(
                            targetValue = if (pagerState.currentPage == 1) 1.25f else 1.0f,
                            animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                            label = "LockerScale"
                        )
                        val iconColor by animateColorAsState(
                            targetValue = if (pagerState.currentPage == 1) Color(0xFF121212) else Color.Gray,
                            label = "LockerColor"
                        )
                        Box(modifier = Modifier.scale(scale).offset(y = 1.dp)) {
                            Icon(Icons.Default.Lock, contentDescription = "Locker", tint = iconColor)
                        }
                    },
                    label = { Text("Locker", modifier = Modifier.offset(y = 2.dp)) },
                    selected = pagerState.currentPage == 1,
                    alwaysShowLabel = false, // Makes text visible ONLY when clicked
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } }
                )
                NavigationBarItem(
                    icon = {
                        val scale by animateFloatAsState(
                            targetValue = if (pagerState.currentPage == 2) 1.25f else 1.0f,
                            animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                            label = "SettingsScale"
                        )
                        val iconColor by animateColorAsState(
                            targetValue = if (pagerState.currentPage == 2) Color(0xFF121212) else Color.Gray,
                            label = "SettingsColor"
                        )
                        Box(modifier = Modifier.scale(scale).offset(y = 1.dp)) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = iconColor)
                        }
                    },
                    label = { Text("Settings", modifier = Modifier.offset(y = 2.dp)) },
                    selected = pagerState.currentPage == 2,
                    alwaysShowLabel = false, // Makes text visible ONLY when clicked
                    onClick = { scope.launch { pagerState.animateScrollToPage(2) } }
                )
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .haze(state = hazeState),
            beyondViewportPageCount = 1
        ) { page ->
            when (page) {
                0 -> HomeScreen(
                    viewModel = videoViewModel,
                    audioViewModel = audioViewModel,
                    lockerViewModel = lockerViewModel,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    onVideoClick = { videoUri -> navController.navigate(Screen.Player.createRoute(videoUri)) },
                    onAudioClick = { audioUri ->
                        val encodedUri = URLEncoder.encode(audioUri, StandardCharsets.UTF_8.toString())
                        navController.navigate(Screen.AudioPlayer.createRoute(encodedUri))
                    },
                    onSettingsClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                    onLockerClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    bottomPadding = padding.calculateBottomPadding(),
                    isFocused = pagerState.currentPage == 0,
                    initialTab = initialTab
                )
                1 -> LockerScreen(
                    viewModel = lockerViewModel,
                    onBack = { scope.launch { pagerState.animateScrollToPage(0) } },
                    onVideoClick = { videoUri ->
                        val encodedUri = URLEncoder.encode(videoUri, StandardCharsets.UTF_8.toString())
                        navController.navigate(Screen.Player.createRoute(encodedUri, "locker"))
                    },
                    bottomPadding = padding.calculateBottomPadding(),
                    isFocused = pagerState.currentPage == 1
                )
                2 -> SettingsScreen(
                    viewModel = lockerViewModel,
                    onLockerClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    onBack = { scope.launch { pagerState.animateScrollToPage(0) } },
                    bottomPadding = padding.calculateBottomPadding(),
                    isFocused = pagerState.currentPage == 2
                )
            }
        }
    }
}

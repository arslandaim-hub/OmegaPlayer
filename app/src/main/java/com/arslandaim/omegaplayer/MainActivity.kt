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
import androidx.fragment.app.FragmentActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.arslandaim.omegaplayer.ui.feature.library.HomeScreen
import com.arslandaim.omegaplayer.ui.feature.library.HistoryScreen
import com.arslandaim.omegaplayer.ui.feature.locker.LockerScreen
import com.arslandaim.omegaplayer.ui.navigation.Screen
import com.arslandaim.omegaplayer.ui.feature.player.PlayerScreen
import com.arslandaim.omegaplayer.ui.feature.settings.SettingsScreen
import com.arslandaim.omegaplayer.ui.theme.OmegaPlayerTheme
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.arslandaim.omegaplayer.viewmodel.VideoViewModel
import com.arslandaim.omegaplayer.viewmodel.AudioViewModel
import com.arslandaim.omegaplayer.viewmodel.ThemeViewModel
import com.arslandaim.omegaplayer.viewmodel.LockerViewModel
import com.arslandaim.omegaplayer.viewmodel.StorageViewModel
import com.arslandaim.omegaplayer.ui.feature.player.AudioPlayerScreen
import com.arslandaim.omegaplayer.media.PlaybackConnection
import com.arslandaim.omegaplayer.ui.common.NowPlayingBar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.launch

@OptIn(ExperimentalSharedTransitionApi::class)
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private var isPlayerActive = false

    @Inject
    lateinit var playbackConnection: PlaybackConnection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val videoViewModel: VideoViewModel = hiltViewModel()
            val audioViewModel: AudioViewModel = hiltViewModel()
            val storageViewModel: StorageViewModel = hiltViewModel()
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val lockerViewModel: LockerViewModel = hiltViewModel()
            val appTheme by themeViewModel.theme.collectAsState()
            val dynamicColor by themeViewModel.dynamicColor.collectAsState()
            
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

            OmegaPlayerTheme(appTheme = appTheme, dynamicColor = dynamicColor) {
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
                                    "videos" -> com.arslandaim.omegaplayer.ui.feature.library.MediaTab.VIDEOS
                                    "audios" -> com.arslandaim.omegaplayer.ui.feature.library.MediaTab.AUDIOS
                                    else -> null
                                }

                                MainScreen(
                                    videoViewModel, 
                                    audioViewModel,
                                    storageViewModel,
                                    lockerViewModel,
                                    playbackConnection,
                                    navController,
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this@composable,
                                    isDarkTheme = isDarkTheme,
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
                            composable(Screen.History.route) {
                                HistoryScreen(
                                    viewModel = videoViewModel,
                                    onBack = { navController.popBackStack() },
                                    onMediaClick = { uri, type ->
                                        if (type == "video") {
                                            navController.navigate(Screen.Player.createRoute(uri))
                                        } else {
                                            navController.navigate(Screen.AudioPlayer.createRoute(uri))
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
    storageViewModel: StorageViewModel,
    lockerViewModel: LockerViewModel,
    playbackConnection: PlaybackConnection,
    navController: NavHostController,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    isDarkTheme: Boolean,
    initialTab: com.arslandaim.omegaplayer.ui.feature.library.MediaTab? = null
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
        bottomBar = {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .hazeChild(
                        state = hazeState,
                        style = HazeDefaults.style(
                            backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = if (isDarkTheme) 0.5f else 0.7f),
                        )
                    )
            ) {
                NowPlayingBar(
                    playbackConnection = playbackConnection,
                    onBarClick = {
                        val currentItem = playbackConnection.currentMediaItem.value
                        val uri = currentItem?.localConfiguration?.uri?.toString()
                        if (uri != null) {
                            val encodedUri = URLEncoder.encode(uri, StandardCharsets.UTF_8.toString())
                            navController.navigate(Screen.AudioPlayer.createRoute(encodedUri))
                        }
                    }
                )
                ModernNavigationBar(
                    selectedTab = pagerState.currentPage,
                    onTabSelected = { page ->
                        scope.launch { pagerState.animateScrollToPage(page) }
                    }
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
                    storageViewModel = storageViewModel,
                    lockerViewModel = lockerViewModel,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    onVideoClick = { videoUri ->
                        val encodedUri = URLEncoder.encode(videoUri, StandardCharsets.UTF_8.toString())
                        navController.navigate(Screen.Player.createRoute(encodedUri)) 
                    },
                    onAudioClick = { audioUri ->
                        val encodedUri = URLEncoder.encode(audioUri, StandardCharsets.UTF_8.toString())
                        navController.navigate(Screen.AudioPlayer.createRoute(encodedUri))
                    },
                    onSettingsClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                    onLockerClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    onViewAllHistoryClick = { navController.navigate(Screen.History.route) },
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

@Composable
fun ModernNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .height(68.dp)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(28.dp)
            )
            .clip(RoundedCornerShape(28.dp)),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        )
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val tabWidth = maxWidth / 3
            
            // Sliding Background Indicator
            val indicatorOffset by animateDpAsState(
                targetValue = tabWidth * selectedTab,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
                label = "indicator"
            )

            Box(
                modifier = Modifier
                    .offset { IntOffset(indicatorOffset.roundToPx(), 0) }
                    .width(tabWidth)
                    .fillMaxHeight()
                    .padding(8.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tabs = listOf(
                    Triple("Home", Icons.Default.Home, 0),
                    Triple("Locker", Icons.Default.Lock, 1),
                    Triple("Settings", Icons.Default.Settings, 2)
                )

                tabs.forEach { (label, icon, index) ->
                    val isSelected = selectedTab == index
                    
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.2f else 1.0f,
                        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
                        label = "iconScale"
                    )

                    val color by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "iconColor"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable {
                                if (!isSelected) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onTabSelected(index)
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            modifier = Modifier
                                .size(26.dp)
                                .scale(scale),
                            tint = color
                        )
                        
                        AnimatedVisibility(
                            visible = isSelected,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = color,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

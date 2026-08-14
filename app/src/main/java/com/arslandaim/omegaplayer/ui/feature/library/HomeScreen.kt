/*
 * OmegaPlayer Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/

package com.arslandaim.omegaplayer.ui.feature.library

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import coil.size.Precision
import com.arslandaim.omegaplayer.data.LockedVideo
import com.arslandaim.omegaplayer.data.LockerDatabase
import com.arslandaim.omegaplayer.data.VideoModel
import com.arslandaim.omegaplayer.data.AudioModel
import com.arslandaim.omegaplayer.data.Playlist
import com.arslandaim.omegaplayer.data.PlaylistItem
import com.arslandaim.omegaplayer.data.RecentPlayback
import com.arslandaim.omegaplayer.viewmodel.AudioViewModel
import com.arslandaim.omegaplayer.viewmodel.VideoViewModel
import com.arslandaim.omegaplayer.viewmodel.LockerViewModel
import androidx.compose.ui.graphics.drawscope.Stroke as CanvasStroke
import com.arslandaim.omegaplayer.viewmodel.StorageStats
import com.arslandaim.omegaplayer.viewmodel.StorageViewModel
import com.arslandaim.omegaplayer.ui.feature.locker.MoveToLockerResult
import com.arslandaim.omegaplayer.ui.feature.locker.bulkPrepareMoveToLocker
import com.arslandaim.omegaplayer.ui.feature.locker.bulkPrepareAudioMoveToLocker
import com.arslandaim.omegaplayer.ui.feature.locker.prepareMoveToLocker
import com.arslandaim.omegaplayer.ui.feature.locker.prepareAudioMoveToLocker
import com.arslandaim.omegaplayer.ui.common.ModernLoadingDialog
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun RecentPlaybackItem(
    item: RecentPlayback,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(180.dp)
            .height(110.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Placeholder/Icon Background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.mediaType == "video") Icons.Default.PlayCircle else Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).alpha(0.1f),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (item.mediaType == "video") "Video" else item.artist ?: "Audio",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // Progress Bar
                val progress = item.position.toFloat() / item.duration.coerceAtLeast(1L)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape),
                    color = Color(0xFFFF6600),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
fun PlayingVisualizer(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "visualizer")
    val color = MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier.height(20.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(3) { index ->
            val heightScale by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 400 + (index * 150),
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$index"
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(heightScale)
                    .background(color, RoundedCornerShape(2.dp))
            )
        }
    }
}

@Composable
fun ModernOmegaIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF87CEEB), // Light Blue
                        Color(0xFFFF6600)  // Orange
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Ω",
            style = MaterialTheme.typography.titleLarge.copy(
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 24.sp,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.offset(y = (-1).dp) // Visual centering adjustment
        )
    }
}

enum class MediaTab { VIDEOS, AUDIOS, PLAYLISTS }

@Composable
fun StorageVisualization(stats: StorageStats, modifier: Modifier = Modifier) {
    val videoColor = Color(0xFFFF6600) // Orange
    val audioColor = Color(0xFF87CEEB) // Blue
    val otherColor = Color(0xFF71717A) // Zinc
    val freeColor = Color(0xFFE4E4E7).copy(alpha = 0.3f)

    val total = stats.totalBytes.toFloat()
    if (total <= 0f) return

    val videoWeight = (stats.videoBytes / total)
    val audioWeight = (stats.audioBytes / total)
    val otherWeight = (stats.otherBytes / total)
    val freeWeight = (stats.freeBytes / total)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Storage",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${stats.formatSize(stats.totalBytes - stats.freeBytes)} / ${stats.formatSize(stats.totalBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Linear Storage Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(freeColor)
            ) {
                if (videoWeight > 0) Box(modifier = Modifier.fillMaxHeight().weight(videoWeight).background(videoColor))
                if (audioWeight > 0) Box(modifier = Modifier.fillMaxHeight().weight(audioWeight).background(audioColor))
                if (otherWeight > 0) Box(modifier = Modifier.fillMaxHeight().weight(otherWeight).background(otherColor))
                if (freeWeight > 0) Box(modifier = Modifier.fillMaxHeight().weight(freeWeight).background(Color.Transparent))
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StorageLegendItemSmall("Videos", videoColor)
                StorageLegendItemSmall("Audios", audioColor)
                StorageLegendItemSmall("Other", otherColor)
                StorageLegendItemSmall("Free", Color(0xFFE4E4E7))
            }
        }
    }
}

@Composable
fun StorageLegendItemSmall(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreen(
    viewModel: VideoViewModel,
    audioViewModel: AudioViewModel,
    storageViewModel: StorageViewModel,
    lockerViewModel: LockerViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onVideoClick: (String) -> Unit,
    onAudioClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onLockerClick: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    isFocused: Boolean = true,
    initialTab: MediaTab? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var selectedTab by rememberSaveable { mutableStateOf(initialTab ?: MediaTab.VIDEOS) }
    var isGridView by rememberSaveable { mutableStateOf(false) }

    val pagerState = rememberPagerState(
        initialPage = selectedTab.ordinal,
        pageCount = { MediaTab.entries.size }
    )

    LaunchedEffect(pagerState.currentPage) {
        selectedTab = MediaTab.entries[pagerState.currentPage]
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab.ordinal != pagerState.currentPage) {
            pagerState.animateScrollToPage(selectedTab.ordinal)
        }
    }

    // Data loading logic
    LaunchedEffect(Unit) {
        viewModel.fetchVideos(context)
        audioViewModel.fetchAudios(context)
    }

    // Update selectedTab only if initialTab is explicitly changed via navigation
    // and it differs from the current selectedTab to avoid resetting user choice.
    LaunchedEffect(initialTab) {
        if (initialTab != null && initialTab != selectedTab) {
            selectedTab = initialTab
        }
    }

    // Video Data
    val videos by viewModel.videos.collectAsState()
    val isLoadingVideos by viewModel.isLoading.collectAsState()
    val videoFolders by viewModel.folders.collectAsStateWithLifecycle()
    val selectedVideoFolder by viewModel.selectedFolder.collectAsStateWithLifecycle()
    val videosInFolder by viewModel.videosInSelectedFolder.collectAsStateWithLifecycle()
    val recentPlayback by viewModel.recentPlayback.collectAsStateWithLifecycle()

    // Audio Data
    val audios by audioViewModel.audios.collectAsStateWithLifecycle()
    val isLoadingAudios by audioViewModel.isLoading.collectAsStateWithLifecycle()
    val audioFolders by audioViewModel.folders.collectAsStateWithLifecycle()
    val selectedAudioFolder by audioViewModel.selectedFolder.collectAsStateWithLifecycle()
    val audiosInFolder by audioViewModel.audiosInSelectedFolder.collectAsStateWithLifecycle()
    val playlists by audioViewModel.playlists.collectAsStateWithLifecycle()

    // Storage Stats
    val storageStats by storageViewModel.storageStats.collectAsStateWithLifecycle()
    LaunchedEffect(videos, audios) {
        storageViewModel.updateStorageStats(videos, audios)
    }
    
    val isLoading = if (selectedTab == MediaTab.VIDEOS) isLoadingVideos else isLoadingAudios
    
    val dao = remember { LockerDatabase.getDatabase(context).lockerDao() }
    
    val lockerSettings by lockerViewModel.settings.collectAsStateWithLifecycle()
    var showSetPinDialog by remember { mutableStateOf(false) }

    var videoPendingMove by remember { mutableStateOf<VideoModel?>(null) }
    var audioPendingMove by remember { mutableStateOf<AudioModel?>(null) }
    var folderVideosPendingMove by remember { mutableStateOf<List<VideoModel>>(emptyList()) }
    var folderAudiosPendingMove by remember { mutableStateOf<List<AudioModel>>(emptyList()) }
    var folderOriginPendingMove by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var mediaPendingPlaylist by remember { mutableStateOf<Pair<String, String>?>(null) } // URI to Type
    var selectedPlaylistForDetails by remember { mutableStateOf<Playlist?>(null) }
    val playlistItems by (if (selectedPlaylistForDetails != null) audioViewModel.getPlaylistItems(selectedPlaylistForDetails!!.id) else flowOf(emptyList<PlaylistItem>())).collectAsStateWithLifecycle(initialValue = emptyList())
    
    val currentSelectedFolder = when (selectedTab) {
        MediaTab.VIDEOS -> selectedVideoFolder
        MediaTab.AUDIOS -> selectedAudioFolder
        else -> null
    }
    val currentFolders = when (selectedTab) {
        MediaTab.VIDEOS -> videoFolders
        MediaTab.AUDIOS -> audioFolders
        else -> emptyMap()
    }

    fun checkPinAndProceed(action: () -> Unit) {
        if (lockerSettings == null) {
            showSetPinDialog = true
        } else {
            action()
        }
    }

    // Robust Back Handling
    val isCurrentFolderOpen = remember(selectedTab, selectedVideoFolder, selectedAudioFolder, selectedPlaylistForDetails) {
        (selectedTab == MediaTab.VIDEOS && selectedVideoFolder != null) || 
        (selectedTab == MediaTab.AUDIOS && selectedAudioFolder != null) ||
        (selectedTab == MediaTab.PLAYLISTS && selectedPlaylistForDetails != null)
    }

    BackHandler(enabled = isFocused && isCurrentFolderOpen) {
        if (selectedTab == MediaTab.VIDEOS) {
            viewModel.setSelectedFolder(null)
        } else if (selectedTab == MediaTab.AUDIOS) {
            audioViewModel.setSelectedFolder(null)
        } else {
            selectedPlaylistForDetails = null
        }
    }

    val filteredFolders = remember(currentFolders, searchQuery, currentSelectedFolder) {
        if (currentSelectedFolder != null) emptyMap()
        else if (searchQuery.isEmpty()) currentFolders
        else currentFolders.filterKeys { it.contains(searchQuery, ignoreCase = true) }
    }

    val filteredVideos = remember(videosInFolder, searchQuery, selectedVideoFolder, selectedTab) {
        if (selectedVideoFolder == null || selectedTab != MediaTab.VIDEOS) emptyList()
        else if (searchQuery.isEmpty()) videosInFolder
        else videosInFolder.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }
    
    val filteredAudios = remember(audiosInFolder, searchQuery, selectedAudioFolder, selectedTab) {
        if (selectedAudioFolder == null || selectedTab != MediaTab.AUDIOS) emptyList()
        else if (searchQuery.isEmpty()) audiosInFolder
        else audiosInFolder.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            scope.launch {
                isProcessing = true
                // This is to handle single video move completion
                videoPendingMove?.let { video: VideoModel ->
                    val lockerDir = File(context.filesDir, "locker")
                    val destFile = File(lockerDir, video.name)
                    dao.insertLockedVideo(
                        LockedVideo(
                            originalPath = video.path,
                            lockerPath = destFile.absolutePath,
                            name = video.name,
                            duration = video.duration,
                            originFolderName = File(video.path).parentFile?.name,
                            isAudio = false
                        )
                    )
                    Toast.makeText(context, "Video moved to locker", Toast.LENGTH_SHORT).show()
                    viewModel.clearVideoCache(context, video.id)
                    viewModel.refreshVideos(context)
                }

                // This is to handle single audio move completion
                audioPendingMove?.let { audio: AudioModel ->
                    val lockerDir = File(context.filesDir, "locker")
                    val destFile = File(lockerDir, audio.name)
                    dao.insertLockedVideo(
                        LockedVideo(
                            originalPath = audio.path,
                            lockerPath = destFile.absolutePath,
                            name = audio.name,
                            duration = audio.duration,
                            originFolderName = File(audio.path).parentFile?.name,
                            isAudio = true
                        )
                    )
                    Toast.makeText(context, "Audio moved to locker", Toast.LENGTH_SHORT).show()
                    audioViewModel.refreshAudios(context)
                }
                
                // This is to handle folder move completion (Videos)
                if (folderVideosPendingMove.isNotEmpty()) {
                    val lockerDir = File(context.filesDir, "locker")
                    folderVideosPendingMove.forEach { video ->
                        val destFile = File(lockerDir, video.name)
                        dao.insertLockedVideo(
                            LockedVideo(
                                originalPath = video.path,
                                lockerPath = destFile.absolutePath,
                                name = video.name,
                                duration = video.duration,
                                originFolderName = folderOriginPendingMove,
                                isAudio = false
                            )
                        )
                        viewModel.clearVideoCache(context, video.id)
                    }
                    Toast.makeText(context, "Moved ${folderVideosPendingMove.size} videos to locker", Toast.LENGTH_SHORT).show()
                    viewModel.refreshVideos(context)
                }

                // This is to handle folder move completion (Audios)
                if (folderAudiosPendingMove.isNotEmpty()) {
                    val lockerDir = File(context.filesDir, "locker")
                    folderAudiosPendingMove.forEach { audio ->
                        val destFile = File(lockerDir, audio.name)
                        dao.insertLockedVideo(
                            LockedVideo(
                                originalPath = audio.path,
                                lockerPath = destFile.absolutePath,
                                name = audio.name,
                                duration = audio.duration,
                                originFolderName = folderOriginPendingMove,
                                isAudio = true
                            )
                        )
                    }
                    Toast.makeText(context, "Moved ${folderAudiosPendingMove.size} audios to locker", Toast.LENGTH_SHORT).show()
                    audioViewModel.refreshAudios(context)
                }

                // If it was just a deletion (not a move to locker), refresh
                if (videoPendingMove == null && audioPendingMove == null && 
                    folderVideosPendingMove.isEmpty() && folderAudiosPendingMove.isEmpty()) {
                    
                    // Stop playback if the current item was deleted
                    viewModel.activeVideoUri.value?.let { viewModel.stopIfPlaying(Uri.parse(it)) }
                    audioViewModel.activeAudioUri.value?.let { audioViewModel.stopIfPlaying(Uri.parse(it)) }
                    
                    viewModel.refreshVideos(context)
                    audioViewModel.refreshAudios(context)
                }
                
                videoPendingMove = null
                audioPendingMove = null
                folderVideosPendingMove = emptyList()
                folderAudiosPendingMove = emptyList()
                folderOriginPendingMove = null
                isProcessing = false
            }
        } else {
            // Cleanup on cancel when needed
            videoPendingMove = null
            audioPendingMove = null
            folderVideosPendingMove = emptyList()
            folderAudiosPendingMove = emptyList()
            folderOriginPendingMove = null
            isProcessing = false
            Toast.makeText(context, "Move cancelled", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun checkPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    var hasPermission by remember { mutableStateOf(checkPermission()) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            hasPermission = permissions.values.all { it }
            if (hasPermission) {
                viewModel.fetchVideos(context)
                audioViewModel.fetchAudios(context)
            }
        }
    )

    // Notification Permission for Android 13+
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { /* Log or handle */ }
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            viewModel.fetchVideos(context)
            audioViewModel.fetchAudios(context)
        }
    }

    var selectedVideoForLocker by remember { mutableStateOf<VideoModel?>(null) }
    var selectedAudioForLocker by remember { mutableStateOf<AudioModel?>(null) }
    var selectedVideoForDelete by remember { mutableStateOf<VideoModel?>(null) }
    var selectedAudioForDelete by remember { mutableStateOf<AudioModel?>(null) }
    var folderToDelete by remember { mutableStateOf<String?>(null) }
    var folderToMoveToLocker by remember { mutableStateOf<String?>(null) }

    if (showSetPinDialog) {
        AlertDialog(
            onDismissRequest = { showSetPinDialog = false },
            icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFFF6600)) },
            title = { Text("PIN Required") },
            text = { Text("Please set a security PIN in the Locker tab before moving items to the private vault.") },
            confirmButton = {
                Button(onClick = {
                    showSetPinDialog = false
                    onLockerClick()
                }) {
                    Text("Set PIN Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSetPinDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }

    if (folderToDelete != null) {
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = {
                Text(
                    text = if (selectedTab == MediaTab.VIDEOS) "Delete Video Folder" else "Delete Audio Folder",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete folder '${folderToDelete}' and all its ${if (selectedTab == MediaTab.VIDEOS) "videos" else "audios"}?",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val folderName = folderToDelete!!
                        folderToDelete = null
                        scope.launch {
                            isProcessing = true
                            if (selectedTab == MediaTab.VIDEOS) {
                                val videos = viewModel.getVideosInFolder(folderName)
                                if (videos.isNotEmpty()) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        val deleteUriList = videos.map { it.uri }
                                        val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, deleteUriList)
                                        deleteLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                                    } else {
                                        val uris = videos.map { it.uri }
                                        viewModel.stopIfPlaying(uris)
                                        videos.forEach { context.contentResolver.delete(it.uri, null, null) }
                                        viewModel.refreshVideos(context)
                                        isProcessing = false
                                    }
                                } else {
                                    isProcessing = false
                                }
                            } else {
                                val audios = audioViewModel.getAudiosInFolder(folderName)
                                if (audios.isNotEmpty()) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        val deleteUriList = audios.map { it.uri }
                                        val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, deleteUriList)
                                        deleteLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                                    } else {
                                        val uris = audios.map { it.uri }
                                        audioViewModel.stopIfPlaying(uris)
                                        audios.forEach { context.contentResolver.delete(it.uri, null, null) }
                                        audioViewModel.refreshAudios(context)
                                        isProcessing = false
                                    }
                                } else {
                                    isProcessing = false
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { folderToDelete = null }) { Text("Cancel") } }
        )
    }

    if (selectedVideoForDelete != null) {
        AlertDialog(
            onDismissRequest = { selectedVideoForDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = {
                Text(
                    text = "Delete Video",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete '${selectedVideoForDelete?.name}'?",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val video = selectedVideoForDelete!!
                        selectedVideoForDelete = null
                        scope.launch {
                            isProcessing = true
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, listOf(video.uri))
                                deleteLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                            } else {
                                viewModel.stopIfPlaying(video.uri)
                                context.contentResolver.delete(video.uri, null, null)
                                viewModel.refreshVideos(context)
                                isProcessing = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { selectedVideoForDelete = null }) { Text("Cancel") } }
        )
    }

    if (selectedAudioForDelete != null) {
        AlertDialog(
            onDismissRequest = { selectedAudioForDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = {
                Text(
                    text = "Delete Audio",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete '${selectedAudioForDelete?.name}'?",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val audio = selectedAudioForDelete!!
                        selectedAudioForDelete = null
                        scope.launch {
                            isProcessing = true
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, listOf(audio.uri))
                                deleteLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                            } else {
                                audioViewModel.stopIfPlaying(audio.uri)
                                context.contentResolver.delete(audio.uri, null, null)
                                audioViewModel.refreshAudios(context)
                                isProcessing = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { selectedAudioForDelete = null }) { Text("Cancel") } }
        )
    }

    if (folderToMoveToLocker != null) {
        AlertDialog(
            onDismissRequest = { folderToMoveToLocker = null },
            title = { Text(if (selectedTab == MediaTab.VIDEOS) "Move Video Folder to Locker" else "Move Audio Folder to Locker") },
            text = { Text("Move all ${if (selectedTab == MediaTab.VIDEOS) "videos" else "audios"} in '${folderToMoveToLocker}' to private vault?") },
            confirmButton = {
                Button(onClick = {
                    val folderName = folderToMoveToLocker!!
                    folderToMoveToLocker = null
                    scope.launch {
                        isProcessing = true
                        if (selectedTab == MediaTab.VIDEOS) {
                            val videos = viewModel.getVideosInFolder(folderName)
                            val result = bulkPrepareMoveToLocker(context, videos)
                            
                            when (result) {
                                is MoveToLockerResult.Success -> {
                                    videos.forEach { video ->
                                        val lockerDir = File(context.filesDir, "locker")
                                        val destFile = File(lockerDir, video.name)
                                        dao.insertLockedVideo(
                                            LockedVideo(
                                                originalPath = video.path,
                                                lockerPath = destFile.absolutePath,
                                                name = video.name,
                                                duration = video.duration,
                                                originFolderName = folderName,
                                                isAudio = false
                                            )
                                        )
                                        viewModel.clearVideoCache(context, video.id)
                                    }
                                    Toast.makeText(context, "Moved ${videos.size} videos to locker", Toast.LENGTH_SHORT).show()
                                    viewModel.refreshVideos(context)
                                    isProcessing = false
                                }
                                is MoveToLockerResult.RequiresUserConsent -> {
                                    folderVideosPendingMove = videos
                                    folderOriginPendingMove = folderName
                                    deleteLauncher.launch(IntentSenderRequest.Builder(result.intentSender).build())
                                }
                                is MoveToLockerResult.Error -> {
                                    isProcessing = false
                                    Toast.makeText(context, "Failed to move folder", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            val audios = audioViewModel.getAudiosInFolder(folderName)
                            val result = bulkPrepareAudioMoveToLocker(context, audios)
                            
                            when (result) {
                                is MoveToLockerResult.Success -> {
                                    audios.forEach { audio ->
                                        val lockerDir = File(context.filesDir, "locker")
                                        val destFile = File(lockerDir, audio.name)
                                        dao.insertLockedVideo(
                                            LockedVideo(
                                                originalPath = audio.path,
                                                lockerPath = destFile.absolutePath,
                                                name = audio.name,
                                                duration = audio.duration,
                                                originFolderName = folderName,
                                                isAudio = true
                                            )
                                        )
                                    }
                                    Toast.makeText(context, "Moved ${audios.size} audios to locker", Toast.LENGTH_SHORT).show()
                                    audioViewModel.refreshAudios(context)
                                    isProcessing = false
                                }
                                is MoveToLockerResult.RequiresUserConsent -> {
                                    folderAudiosPendingMove = audios
                                    folderOriginPendingMove = folderName
                                    deleteLauncher.launch(IntentSenderRequest.Builder(result.intentSender).build())
                                }
                                is MoveToLockerResult.Error -> {
                                    isProcessing = false
                                    Toast.makeText(context, "Failed to move folder", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }) { Text("Move") }
            },
            dismissButton = { TextButton(onClick = { folderToMoveToLocker = null }) { Text("Cancel") } }
        )
    }

    if (selectedVideoForLocker != null) {
        AlertDialog(
            onDismissRequest = { selectedVideoForLocker = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locker",
                    tint = Color(0xFF71717A)
                )
            },
            title = {
                Text(
                    text = "Move to Locker?",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Please allow MEDIA MANAGEMENT Permission in app settings for smoother locking experience.",
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val video = selectedVideoForLocker!!
                        selectedVideoForLocker = null
                        scope.launch {
                            isProcessing = true
                            val result = prepareMoveToLocker(context, video)
                            when (result) {
                                is MoveToLockerResult.Success -> {
                                    val lockerDir = File(context.filesDir, "locker")
                                    val destFile = File(lockerDir, video.name)
                                    dao.insertLockedVideo(
                                        LockedVideo(
                                            originalPath = video.path,
                                            lockerPath = destFile.absolutePath,
                                            name = video.name,
                                            duration = video.duration,
                                            originFolderName = File(video.path).parentFile?.name,
                                            isAudio = false
                                        )
                                    )
                                    Toast.makeText(context, "Video moved to locker", Toast.LENGTH_SHORT).show()
                                    viewModel.clearVideoCache(context, video.id)
                                    viewModel.refreshVideos(context)
                                    isProcessing = false
                                }
                                is MoveToLockerResult.RequiresUserConsent -> {
                                    // isProcessing will be kept true and handled by deleteLauncher
                                    videoPendingMove = video
                                    deleteLauncher.launch(
                                        IntentSenderRequest.Builder(result.intentSender).build()
                                    )
                                }
                                is MoveToLockerResult.Error -> {
                                    isProcessing = false
                                    Toast.makeText(context, "Failed to move video", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Move")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedVideoForLocker = null }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }

    if (selectedAudioForLocker != null) {
        AlertDialog(
            onDismissRequest = { selectedAudioForLocker = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locker",
                    tint = Color(0xFF71717A)
                )
            },
            title = {
                Text(
                    text = "Move Audio to Locker?",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Move '${selectedAudioForLocker?.name}' to private vault?",
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val audio = selectedAudioForLocker!!
                        selectedAudioForLocker = null
                        scope.launch {
                            isProcessing = true
                            val result = prepareAudioMoveToLocker(context, audio)
                            when (result) {
                                is MoveToLockerResult.Success -> {
                                    val lockerDir = File(context.filesDir, "locker")
                                    val destFile = File(lockerDir, audio.name)
                                    dao.insertLockedVideo(
                                        LockedVideo(
                                            originalPath = audio.path,
                                            lockerPath = destFile.absolutePath,
                                            name = audio.name,
                                            duration = audio.duration,
                                            originFolderName = File(audio.path).parentFile?.name,
                                            isAudio = true
                                        )
                                    )
                                    Toast.makeText(context, "Audio moved to locker", Toast.LENGTH_SHORT).show()
                                    audioViewModel.refreshAudios(context)
                                    isProcessing = false
                                }
                                is MoveToLockerResult.RequiresUserConsent -> {
                                    audioPendingMove = audio
                                    deleteLauncher.launch(
                                        IntentSenderRequest.Builder(result.intentSender).build()
                                    )
                                }
                                is MoveToLockerResult.Error -> {
                                    isProcessing = false
                                    Toast.makeText(context, "Failed to move audio", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Move")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedAudioForLocker = null }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }

    if (isProcessing) {
        ModernLoadingDialog()
    }

    if (showAddToPlaylistDialog && mediaPendingPlaylist != null) {
        AddToPlaylistFromHomeDialog(
            playlists = playlists,
            onDismiss = { 
                showAddToPlaylistDialog = false
                mediaPendingPlaylist = null
            },
            onPlaylistSelected = { playlistId ->
                mediaPendingPlaylist?.let { (uri, type) ->
                    audioViewModel.addToPlaylist(playlistId, uri, type)
                    Toast.makeText(context, "Added to playlist", Toast.LENGTH_SHORT).show()
                }
                showAddToPlaylistDialog = false
                mediaPendingPlaylist = null
            },
            onCreatePlaylist = { name ->
                audioViewModel.createPlaylist(name)
            }
        )
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                    .statusBarsPadding()
            ) {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            if (currentSelectedFolder != null || selectedPlaylistForDetails != null) {
                                IconButton(modifier = Modifier.size(50.dp) ,onClick = { 
                                    if (selectedTab == MediaTab.VIDEOS) viewModel.setSelectedFolder(null)
                                    else if (selectedTab == MediaTab.AUDIOS) audioViewModel.setSelectedFolder(null)
                                    else selectedPlaylistForDetails = null
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Back",tint = Color(0xFFFF6600), modifier = Modifier.size(38.dp))
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = currentSelectedFolder ?: selectedPlaylistForDetails?.name ?: "",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            } else {
                                ModernOmegaIcon()
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Omega Player",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Black,
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                Color(0xFF87CEEB), // Light Blue
                                                Color(0xFFFF6600)  // Orange
                                            )
                                        )
                                    )
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )

                if (currentSelectedFolder == null && selectedPlaylistForDetails == null) {
                    HomeDashboard(
                        selectedTab = selectedTab,
                        storageStats = storageStats,
                        onTabSelected = { tab -> selectedTab = tab }
                    )
                }
                
                // Search & Filter Bar
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { 
                            val placeholderText = if (currentSelectedFolder == null && selectedPlaylistForDetails == null) "Search folders..." 
                                               else if (selectedPlaylistForDetails != null) "Search in ${selectedPlaylistForDetails!!.name}..."
                                               else "Search in $currentSelectedFolder..."
                            Text(
                                placeholderText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            ) 
                        },
                        leadingIcon = { 
                            Icon(
                                Icons.Default.Search, 
                                contentDescription = null,
                                tint = Color(0xFFFF6600)
                            ) 
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF6600),
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        ),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge
                    )

                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { isGridView = !isGridView },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), CircleShape)
                    ) {
                        Icon(
                            if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "Toggle View",
                            tint = Color(0xFFFF6600)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (currentSelectedFolder == null && selectedPlaylistForDetails == null) "Folders" 
                               else if (selectedPlaylistForDetails != null) "Items"
                               else if (selectedTab == MediaTab.VIDEOS) "Videos" else "Audios",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    
                    if (currentSelectedFolder == null && selectedPlaylistForDetails == null) {
                        Text(
                            text = "${currentFolders.size} Folders",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (!hasPermission) {
                ExtendedFloatingActionButton(
                    modifier = Modifier.padding(bottom = 80.dp),
                    text = { Text("Grant Access") },
                    icon = { Icon(Icons.Default.AddCircle, contentDescription = null) },
                    onClick = {
                        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
                        } else {
                            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                        launcher.launch(permissions)
                    },
                    containerColor = Color(0xFFFFFFFF),
                    contentColor = Color(0xFF1A1A1A),
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(padding),
            userScrollEnabled = currentSelectedFolder == null && selectedPlaylistForDetails == null
        ) { page ->
            val pageTab = MediaTab.entries[page]
            
            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading && (if (pageTab == MediaTab.VIDEOS) videos.isEmpty() else audios.isEmpty())) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (currentSelectedFolder == null && selectedPlaylistForDetails == null && filteredFolders.isEmpty() && pageTab != MediaTab.PLAYLISTS) {
                    EmptyState(searchQuery.isNotEmpty(), true)
                } else if ((currentSelectedFolder != null || selectedPlaylistForDetails != null) && (if (pageTab == MediaTab.VIDEOS) filteredVideos.isEmpty() else if (pageTab == MediaTab.AUDIOS) filteredAudios.isEmpty() else playlistItems.isEmpty())) {
                    EmptyState(searchQuery.isNotEmpty(), false)
                } else {
                    if (isGridView) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp + bottomPadding),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (currentSelectedFolder == null && selectedPlaylistForDetails == null) {
                                if (pageTab != MediaTab.PLAYLISTS) {
                                    if (recentPlayback.isNotEmpty()) {
                                        item(span = { GridItemSpan(2) }) {
                                            RecentPlaybackSection(recentPlayback, onVideoClick, onAudioClick)
                                        }
                                    }
                                }

                                if (pageTab == MediaTab.PLAYLISTS) {
                                    if (playlists.isNotEmpty()) {
                                        items(playlists, key = { it.id }, span = { GridItemSpan(2) }) { playlist ->
                                            PlaylistListItem(playlist, { selectedPlaylistForDetails = playlist }, { audioViewModel.deletePlaylist(playlist) })
                                        }
                                    }
                                } else {
                                    items(filteredFolders.keys.toList(), key = { it }) { folderName ->
                                        FolderGridItem(
                                            name = folderName,
                                            count = filteredFolders[folderName] ?: 0,
                                            onClick = { 
                                                if (pageTab == MediaTab.VIDEOS) viewModel.setSelectedFolder(folderName)
                                                else audioViewModel.setSelectedFolder(folderName)
                                            }
                                        )
                                    }
                                }
                            } else if (selectedPlaylistForDetails != null) {
                                items(playlistItems, key = { it.id }) { item ->
                                    PlaylistGridItem(item, videos, audios, viewModel, audioViewModel, sharedTransitionScope, animatedVisibilityScope, onVideoClick, onAudioClick, selectedPlaylistForDetails!!)
                                }
                            } else if (pageTab == MediaTab.VIDEOS) {
                                items(filteredVideos, key = { it.id }) { video ->
                                    VideoGridItem(video, viewModel, sharedTransitionScope, animatedVisibilityScope, onVideoClick, { checkPinAndProceed { selectedVideoForLocker = video } }, { selectedVideoForDelete = video }, { mediaPendingPlaylist = video.uri.toString() to "video"; showAddToPlaylistDialog = true })
                                }
                            } else {
                                items(filteredAudios, key = { it.id }) { audio ->
                                    AudioGridItem(audio, audioViewModel, onAudioClick, { checkPinAndProceed { selectedAudioForLocker = audio } }, { selectedAudioForDelete = audio }, { mediaPendingPlaylist = audio.uri.toString() to "audio"; showAddToPlaylistDialog = true })
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp + bottomPadding),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Dashboard items only on first load of the list
                            if (currentSelectedFolder == null && selectedPlaylistForDetails == null && pageTab != MediaTab.PLAYLISTS) {
                                if (recentPlayback.isNotEmpty()) {
                                    item {
                                        RecentPlaybackSection(recentPlayback, onVideoClick, onAudioClick)
                                    }
                                }
                            }

                            if (currentSelectedFolder == null && selectedPlaylistForDetails == null) {
                                if (pageTab == MediaTab.PLAYLISTS) {
                                    if (playlists.isEmpty()) {
                                        item {
                                            Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                                Text("No playlists yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    } else {
                                        items(playlists, key = { it.id }) { playlist ->
                                        PlaylistListItem(
                                            playlist = playlist,
                                            onClick = { selectedPlaylistForDetails = playlist },
                                            onDelete = { audioViewModel.deletePlaylist(playlist) }
                                        )
                                    }
                                    }
                                } else {
                                    items(filteredFolders.keys.toList(), key = { it }) { folderName ->
                                        FolderListItem(
                                            name = folderName,
                                            count = filteredFolders[folderName] ?: 0,
                                            onClick = { 
                                                if (pageTab == MediaTab.VIDEOS) viewModel.setSelectedFolder(folderName)
                                                else audioViewModel.setSelectedFolder(folderName)
                                            },
                                            onDelete = { folderToDelete = folderName },
                                            onMoveToLocker = { checkPinAndProceed { folderToMoveToLocker = folderName } }
                                        )
                                    }
                                }
                            } else if (selectedPlaylistForDetails != null) {
                                items(playlistItems, key = { it.id }) { item ->
                                    MediaListItemInPlaylist(
                                        item = item,
                                        videos = videos,
                                        audios = audios,
                                        videoViewModel = viewModel,
                                        audioViewModel = audioViewModel,
                                        sharedTransitionScope = sharedTransitionScope,
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        onVideoClick = onVideoClick,
                                        onAudioClick = onAudioClick,
                                        playlist = selectedPlaylistForDetails!!,
                                        onVideoLock = { checkPinAndProceed { selectedVideoForLocker = it } },
                                        onAudioLock = { checkPinAndProceed { selectedAudioForLocker = it } },
                                        onVideoDelete = { selectedVideoForDelete = it },
                                        onAudioDelete = { selectedAudioForDelete = it }
                                    )
                                }
                            } else if (pageTab == MediaTab.VIDEOS) {
                                items(filteredVideos, key = { it.id }) { video ->
                                    VideoListItem(
                                        video = video,
                                        isPlaying = viewModel.activeVideoUri.collectAsState().value == video.uri.toString() && viewModel.isPlaying.collectAsState().value,
                                        sharedTransitionScope = sharedTransitionScope,
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        onClick = {
                                            val encodedUri = URLEncoder.encode(video.uri.toString(), StandardCharsets.UTF_8.toString())
                                            onVideoClick(encodedUri)
                                        },
                                        onLockClick = { checkPinAndProceed { selectedVideoForLocker = video } },
                                        onDeleteClick = { selectedVideoForDelete = video },
                                        onPlaylistClick = {
                                            mediaPendingPlaylist = video.uri.toString() to "video"
                                            showAddToPlaylistDialog = true
                                        }
                                    )
                                }
                            } else {
                                items(filteredAudios, key = { it.id }) { audio ->
                                    AudioListItem(
                                        audio = audio,
                                        isPlaying = audioViewModel.activeAudioUri.collectAsState().value == audio.uri.toString() && audioViewModel.isPlaying.collectAsStateWithLifecycle().value,
                                        onClick = {
                                            val encodedUri = URLEncoder.encode(audio.uri.toString(), StandardCharsets.UTF_8.toString())
                                            onAudioClick(encodedUri)
                                        },
                                        onPlayPauseClick = { audioViewModel.togglePlayPause(audio) },
                                        onLockClick = { checkPinAndProceed { selectedAudioForLocker = audio } },
                                        onDeleteClick = { selectedAudioForDelete = audio },
                                        onPlaylistClick = {
                                            mediaPendingPlaylist = audio.uri.toString() to "audio"
                                            showAddToPlaylistDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FolderListItem(
    name: String,
    count: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onMoveToLocker: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFFFF9800).copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$count items",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Folder options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Move to Locker") },
                        onClick = {
                            showMenu = false
                            onMoveToLocker()
                        },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Folder", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }
        }
    }
}

@Composable
fun FolderGridItem(
    name: String,
    count: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFFF9800).copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = "$count items",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun HomeDashboard(
    selectedTab: MediaTab,
    storageStats: StorageStats,
    onTabSelected: (MediaTab) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Modern Pill Tab Row
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MediaTab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab
                    val background by animateColorAsState(
                        if (isSelected) Color(0xFFFF6600) else Color.Transparent,
                        label = "tabBg"
                    )
                    val contentColor by animateColorAsState(
                        if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "tabContent"
                    )

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onTabSelected(tab) },
                        shape = RoundedCornerShape(24.dp),
                        color = background
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = tab.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = contentColor
                            )
                        }
                    }
                }
            }
        }

        if (selectedTab == MediaTab.VIDEOS) {
            Spacer(modifier = Modifier.height(12.dp))
            StorageVisualization(stats = storageStats)
        }
    }
}

@Composable
fun RecentPlaybackSection(
    recentPlayback: List<RecentPlayback>,
    onVideoClick: (String) -> Unit,
    onAudioClick: (String) -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(
            text = "Continue Watching",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(recentPlayback, key = { it.uri }) { item ->
                RecentPlaybackItem(
                    item = item,
                    onClick = {
                        val encodedUri = URLEncoder.encode(item.uri, StandardCharsets.UTF_8.toString())
                        if (item.mediaType == "video") onVideoClick(encodedUri)
                        else onAudioClick(encodedUri)
                    }
                )
            }
        }
    }
}

@Composable
fun PlaylistListItem(
    playlist: Playlist,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFF6600).copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, tint = Color(0xFFFF6600))
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(playlist.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MediaListItemInPlaylist(
    item: PlaylistItem,
    videos: List<VideoModel>,
    audios: List<AudioModel>,
    videoViewModel: VideoViewModel,
    audioViewModel: AudioViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onVideoClick: (String) -> Unit,
    onAudioClick: (String) -> Unit,
    playlist: Playlist,
    onVideoLock: (VideoModel) -> Unit,
    onAudioLock: (AudioModel) -> Unit,
    onVideoDelete: (VideoModel) -> Unit,
    onAudioDelete: (AudioModel) -> Unit
) {
    if (item.mediaType == "video") {
        val video = videos.find { it.uri.toString() == item.mediaUri }
        if (video != null) {
            VideoListItem(
                video = video,
                isPlaying = videoViewModel.activeVideoUri.collectAsState().value == video.uri.toString() && videoViewModel.isPlaying.collectAsState().value,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                onClick = {
                    val encodedUri = URLEncoder.encode(video.uri.toString(), StandardCharsets.UTF_8.toString())
                    onVideoClick(encodedUri)
                },
                onLockClick = { onVideoLock(video) },
                onDeleteClick = { onVideoDelete(video) },
                onPlaylistClick = { audioViewModel.removeFromPlaylist(playlist.id, video.uri.toString()) },
                isInPlaylistView = true
            )
        }
    } else {
        val audio = audios.find { it.uri.toString() == item.mediaUri }
        if (audio != null) {
            AudioListItem(
                audio = audio,
                isPlaying = audioViewModel.activeAudioUri.collectAsState().value == audio.uri.toString() && audioViewModel.isPlaying.collectAsState().value,
                onClick = {
                    val encodedUri = URLEncoder.encode(audio.uri.toString(), StandardCharsets.UTF_8.toString())
                    onAudioClick(encodedUri)
                },
                onPlayPauseClick = { audioViewModel.togglePlayPause(audio) },
                onLockClick = { onAudioLock(audio) },
                onDeleteClick = { onAudioDelete(audio) },
                onPlaylistClick = { audioViewModel.removeFromPlaylist(playlist.id, audio.uri.toString()) },
                isInPlaylistView = true
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun VideoGridItem(
    video: VideoModel,
    viewModel: VideoViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClick: (String) -> Unit,
    onLock: () -> Unit,
    onDelete: () -> Unit,
    onPlaylist: () -> Unit
) {
    val isPlaying = viewModel.activeVideoUri.collectAsState().value == video.uri.toString() && viewModel.isPlaying.collectAsState().value
    
    with(sharedTransitionScope) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clickable { 
                    val encodedUri = URLEncoder.encode(video.uri.toString(), StandardCharsets.UTF_8.toString())
                    onClick(encodedUri) 
                },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(video.uri)
                        .videoFrameMillis(1000)
                        .size(400)
                        .precision(Precision.INEXACT)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                if (isPlaying) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                        PlayingVisualizer()
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                        .padding(8.dp)
                ) {
                    Text(
                        text = video.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun AudioGridItem(
    audio: AudioModel,
    viewModel: AudioViewModel,
    onClick: (String) -> Unit,
    onLock: () -> Unit,
    onDelete: () -> Unit,
    onPlaylist: () -> Unit
) {
    val isPlaying = viewModel.activeAudioUri.collectAsState().value == audio.uri.toString() && viewModel.isPlaying.collectAsState().value
    
    val albumArtUri = remember(audio) {
        ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), audio.albumId)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable { 
                val encodedUri = URLEncoder.encode(audio.uri.toString(), StandardCharsets.UTF_8.toString())
                onClick(encodedUri) 
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = albumArtUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                error = rememberVectorPainter(Icons.Default.MusicNote),
                fallback = rememberVectorPainter(Icons.Default.MusicNote)
            )
            
            if (isPlaying) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                    PlayingVisualizer()
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                    .padding(8.dp)
            ) {
                Text(
                    text = audio.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PlaylistGridItem(
    item: PlaylistItem,
    videos: List<VideoModel>,
    audios: List<AudioModel>,
    videoViewModel: VideoViewModel,
    audioViewModel: AudioViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onVideoClick: (String) -> Unit,
    onAudioClick: (String) -> Unit,
    playlist: Playlist
) {
    if (item.mediaType == "video") {
        val video = videos.find { it.uri.toString() == item.mediaUri }
        if (video != null) {
            VideoGridItem(video, videoViewModel, sharedTransitionScope, animatedVisibilityScope, onVideoClick, {}, {}, {})
        }
    } else {
        val audio = audios.find { it.uri.toString() == item.mediaUri }
        if (audio != null) {
            AudioGridItem(audio, audioViewModel, onAudioClick, {}, {}, {})
        }
    }
}

@Composable
fun AddToPlaylistFromHomeDialog(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPlaylistSelected: (Int) -> Unit,
    onCreatePlaylist: (String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newPlaylistName.isNotBlank()) {
                        onCreatePlaylist(newPlaylistName)
                        showCreateDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Playlist") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                Button(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create New Playlist")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (playlists.isEmpty()) {
                    Text("No playlists yet", modifier = Modifier.align(Alignment.CenterHorizontally))
                } else {
                    LazyColumn {
                        items(playlists) { playlist ->
                            ListItem(
                                headlineContent = { Text(playlist.name) },
                                leadingContent = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null) },
                                modifier = Modifier.clickable { onPlaylistSelected(playlist.id) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun EmptyState(isSearching: Boolean, isFolderView: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier.size(100.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isSearching) Icons.Default.SearchOff else if (isFolderView) Icons.Default.FolderOff else Icons.Default.PlayCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = if (isSearching) "No results found" else if (isFolderView) "No folders found" else "No media items",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (isSearching) "Try a different search term" else "Your media library is empty",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun VideoListItem(
    video: VideoModel,
    isPlaying: Boolean = false,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClick: () -> Unit,
    onLockClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onPlaylistClick: () -> Unit,
    isInPlaylistView: Boolean = false
) {
    with(sharedTransitionScope) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLockClick
                )
                .sharedBounds(
                    rememberSharedContentState(key = "video_bounds_${video.uri}"),
                    animatedVisibilityScope = animatedVisibilityScope,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds()
                ),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
                                else MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isPlaying) 6.dp else 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(10.dp)
                    .fillMaxWidth()
                    .height(90.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(130.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(18.dp))
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(video.uri)
                            .videoFrameMillis(1000)
                            .size(400)
                            .precision(Precision.INEXACT)
                            .diskCacheKey("thumb_${video.id}")
                            .memoryCacheKey("thumb_${video.id}")
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .sharedElement(
                                rememberSharedContentState(key = "video_thumb_${video.uri}"),
                                animatedVisibilityScope = animatedVisibilityScope
                            ),
                        contentScale = ContentScale.Crop
                    )

                    if (isPlaying) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            PlayingVisualizer()
                        }
                    }
                    
                    Surface(
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = formatDuration(video.duration),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Column(modifier = Modifier.padding(horizontal = 16.dp).weight(1f)) {
                    Text(
                        text = video.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isPlaying) Color(0xFFFF6600) else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${video.size / (1024 * 1024)} MB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onLockClick) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Move to Locker",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (isInPlaylistView) "Remove from Playlist" else "Add to Playlist") },
                                onClick = { 
                                    showMenu = false
                                    onPlaylistClick() 
                                },
                                leadingIcon = { 
                                    Icon(
                                        if (isInPlaylistView) Icons.Default.PlaylistRemove else Icons.AutoMirrored.Filled.PlaylistAdd, 
                                        contentDescription = null
                                    ) 
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                onClick = { 
                                    showMenu = false
                                    onDeleteClick() 
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                            )
                        }
                    }
                }
            }
        }
    }
}

fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / 1000) % 60
    val minutes = (durationMs / (1000 * 60)) % 60
    val hours = durationMs / (1000 * 60 * 60)
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
    else String.format("%d:%02d", minutes, seconds)
}

@Composable
fun AudioListItem(
    audio: AudioModel,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onLockClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onPlaylistClick: () -> Unit,
    isInPlaylistView: Boolean = false
) {
    val albumArtUri = remember(audio) {
        ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), audio.albumId)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
                            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPlaying) 6.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(albumArtUri)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = rememberVectorPainter(Icons.Default.MusicNote),
                    fallback = rememberVectorPainter(Icons.Default.MusicNote)
                )
                
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        PlayingVisualizer()
                    }
                }
            }
            
            Column(modifier = Modifier.padding(horizontal = 16.dp).weight(1f)) {
                Text(
                    text = audio.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isPlaying) Color(0xFFFF6600) else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = audio.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            IconButton(onClick = onPlayPauseClick) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                    contentDescription = "Play/Pause",
                    tint = Color(0xFFFF6600),
                    modifier = Modifier.size(32.dp)
                )
            }
            
            var showMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (isInPlaylistView) "Remove from Playlist" else "Add to Playlist") },
                        onClick = {
                            showMenu = false
                            onPlaylistClick()
                        },
                        leadingIcon = { Icon(if (isInPlaylistView) Icons.Default.PlaylistRemove else Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Move to Locker") },
                        onClick = {
                            showMenu = false
                            onLockClick()
                        },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDeleteClick()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ModernOmegaIconPreview() {
    MaterialTheme { Box(modifier = Modifier.padding(16.dp)) { ModernOmegaIcon() } }
}

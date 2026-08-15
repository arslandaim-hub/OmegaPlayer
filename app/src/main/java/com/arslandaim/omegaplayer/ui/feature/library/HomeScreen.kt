/*
 * OmegaPlayer Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/

package com.arslandaim.omegaplayer.ui.feature.library

import android.Manifest
import android.app.Activity
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arslandaim.omegaplayer.data.LockedVideo
import com.arslandaim.omegaplayer.data.LockerDatabase
import com.arslandaim.omegaplayer.data.VideoModel
import com.arslandaim.omegaplayer.data.AudioModel
import com.arslandaim.omegaplayer.data.Playlist
import com.arslandaim.omegaplayer.viewmodel.AudioViewModel
import com.arslandaim.omegaplayer.viewmodel.VideoViewModel
import com.arslandaim.omegaplayer.viewmodel.LockerViewModel
import com.arslandaim.omegaplayer.viewmodel.StorageViewModel
import com.arslandaim.omegaplayer.ui.feature.locker.MoveToLockerResult
import com.arslandaim.omegaplayer.ui.feature.locker.bulkPrepareMoveToLocker
import com.arslandaim.omegaplayer.ui.feature.locker.bulkPrepareAudioMoveToLocker
import com.arslandaim.omegaplayer.ui.feature.locker.prepareMoveToLocker
import com.arslandaim.omegaplayer.ui.feature.locker.prepareAudioMoveToLocker
import com.arslandaim.omegaplayer.ui.common.ModernLoadingDialog
import com.arslandaim.omegaplayer.ui.feature.library.components.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

enum class MediaTab { VIDEOS, AUDIOS, PLAYLISTS }

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
    onViewAllHistoryClick: () -> Unit,
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

    LaunchedEffect(initialTab) {
        if (initialTab != null && initialTab != selectedTab) {
            selectedTab = initialTab
        }
    }

    // Data collection
    val videos by viewModel.videos.collectAsState()
    val isLoadingVideos by viewModel.isLoading.collectAsState()
    val videoFolders by viewModel.folders.collectAsStateWithLifecycle()
    val selectedVideoFolder by viewModel.selectedFolder.collectAsStateWithLifecycle()
    val videosInFolder by viewModel.videosInSelectedFolder.collectAsStateWithLifecycle()
    val recentPlayback by viewModel.recentPlayback.collectAsStateWithLifecycle()

    val audios by audioViewModel.audios.collectAsStateWithLifecycle()
    val isLoadingAudios by audioViewModel.isLoading.collectAsStateWithLifecycle()
    val audioFolders by audioViewModel.folders.collectAsStateWithLifecycle()
    val selectedAudioFolder by audioViewModel.selectedFolder.collectAsStateWithLifecycle()
    val audiosInFolder by audioViewModel.audiosInSelectedFolder.collectAsStateWithLifecycle()
    val playlists by audioViewModel.playlists.collectAsStateWithLifecycle()

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
    var mediaPendingPlaylist by remember { mutableStateOf<Pair<String, String>?>(null) }
    var selectedPlaylistForDetails by remember { mutableStateOf<Playlist?>(null) }
    val playlistItems by (if (selectedPlaylistForDetails != null) audioViewModel.getPlaylistItems(selectedPlaylistForDetails!!.id) else flowOf(emptyList())).collectAsStateWithLifecycle(initialValue = emptyList())
    
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
        if (lockerSettings == null) showSetPinDialog = true else action()
    }

    val isCurrentFolderOpen = remember(selectedTab, selectedVideoFolder, selectedAudioFolder, selectedPlaylistForDetails) {
        (selectedTab == MediaTab.VIDEOS && selectedVideoFolder != null) || 
        (selectedTab == MediaTab.AUDIOS && selectedAudioFolder != null) ||
        (selectedTab == MediaTab.PLAYLISTS && selectedPlaylistForDetails != null)
    }

    BackHandler(enabled = isFocused && isCurrentFolderOpen) {
        if (selectedTab == MediaTab.VIDEOS) viewModel.setSelectedFolder(null)
        else if (selectedTab == MediaTab.AUDIOS) audioViewModel.setSelectedFolder(null)
        else selectedPlaylistForDetails = null
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
                videoPendingMove?.let { video ->
                    val lockerDir = File(context.filesDir, "locker")
                    val destFile = File(lockerDir, video.name)
                    dao.insertLockedVideo(LockedVideo(originalPath = video.path, lockerPath = destFile.absolutePath, name = video.name, duration = video.duration, originFolderName = File(video.path).parentFile?.name, isAudio = false))
                    viewModel.clearVideoCache(context, video.id)
                    viewModel.refreshVideos(context)
                }
                audioPendingMove?.let { audio ->
                    val lockerDir = File(context.filesDir, "locker")
                    val destFile = File(lockerDir, audio.name)
                    dao.insertLockedVideo(LockedVideo(originalPath = audio.path, lockerPath = destFile.absolutePath, name = audio.name, duration = audio.duration, originFolderName = File(audio.path).parentFile?.name, isAudio = true))
                    audioViewModel.refreshAudios(context)
                }
                if (folderVideosPendingMove.isNotEmpty()) {
                    folderVideosPendingMove.forEach { video ->
                        val destFile = File(File(context.filesDir, "locker"), video.name)
                        dao.insertLockedVideo(LockedVideo(originalPath = video.path, lockerPath = destFile.absolutePath, name = video.name, duration = video.duration, originFolderName = folderOriginPendingMove, isAudio = false))
                        viewModel.clearVideoCache(context, video.id)
                    }
                    viewModel.refreshVideos(context)
                }
                if (folderAudiosPendingMove.isNotEmpty()) {
                    folderAudiosPendingMove.forEach { audio ->
                        val destFile = File(File(context.filesDir, "locker"), audio.name)
                        dao.insertLockedVideo(LockedVideo(originalPath = audio.path, lockerPath = destFile.absolutePath, name = audio.name, duration = audio.duration, originFolderName = folderOriginPendingMove, isAudio = true))
                    }
                    audioViewModel.refreshAudios(context)
                }
                if (videoPendingMove == null && audioPendingMove == null && folderVideosPendingMove.isEmpty() && folderAudiosPendingMove.isEmpty()) {
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
            isProcessing = false
            Toast.makeText(context, "Move cancelled", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun checkPermission(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    var hasPermission by remember { mutableStateOf(checkPermission()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        hasPermission = permissions.values.all { it }
        if (hasPermission) { viewModel.fetchVideos(context); audioViewModel.fetchAudios(context) }
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
            icon = { Icon(Icons.Default.Lock, null, tint = Color(0xFFFF6600)) },
            title = { Text("PIN Required") },
            text = { Text("Please set a security PIN in the Locker tab before moving items to the private vault.") },
            confirmButton = { Button(onClick = { showSetPinDialog = false; onLockerClick() }) { Text("Set PIN Now") } },
            dismissButton = { TextButton(onClick = { showSetPinDialog = false }) { Text("Cancel") } },
            shape = RoundedCornerShape(28.dp)
        )
    }

    if (folderToDelete != null) {
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(text = if (selectedTab == MediaTab.VIDEOS) "Delete Video Folder" else "Delete Audio Folder", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold) },
            text = { Text(text = "Are you sure you want to delete folder '${folderToDelete}' and all its items?", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
            confirmButton = {
                Button(onClick = {
                    val folderName = folderToDelete!!; folderToDelete = null
                    scope.launch {
                        isProcessing = true
                        if (selectedTab == MediaTab.VIDEOS) {
                            val videos = viewModel.getVideosInFolder(folderName)
                            if (videos.isNotEmpty()) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, videos.map { it.uri })
                                    deleteLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                                } else {
                                    viewModel.stopIfPlaying(videos.map { it.uri })
                                    videos.forEach { context.contentResolver.delete(it.uri, null, null) }
                                    viewModel.refreshVideos(context); isProcessing = false
                                }
                            } else isProcessing = false
                        } else {
                            val audios = audioViewModel.getAudiosInFolder(folderName)
                            if (audios.isNotEmpty()) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, audios.map { it.uri })
                                    deleteLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                                } else {
                                    audioViewModel.stopIfPlaying(audios.map { it.uri })
                                    audios.forEach { context.contentResolver.delete(it.uri, null, null) }
                                    audioViewModel.refreshAudios(context); isProcessing = false
                                }
                            } else isProcessing = false
                        }
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { folderToDelete = null }) { Text("Cancel") } }
        )
    }

    if (selectedVideoForDelete != null) {
        AlertDialog(
            onDismissRequest = { selectedVideoForDelete = null },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(text = "Delete Video", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold) },
            text = { Text(text = "Are you sure you want to delete '${selectedVideoForDelete?.name}'?", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
            confirmButton = {
                Button(onClick = {
                    val video = selectedVideoForDelete!!; selectedVideoForDelete = null
                    scope.launch {
                        isProcessing = true
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, listOf(video.uri))
                            deleteLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                        } else {
                            viewModel.stopIfPlaying(video.uri)
                            context.contentResolver.delete(video.uri, null, null)
                            viewModel.refreshVideos(context); isProcessing = false
                        }
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { selectedVideoForDelete = null }) { Text("Cancel") } }
        )
    }

    if (selectedAudioForDelete != null) {
        AlertDialog(
            onDismissRequest = { selectedAudioForDelete = null },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(text = "Delete Audio", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold) },
            text = { Text(text = "Are you sure you want to delete '${selectedAudioForDelete?.name}'?", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
            confirmButton = {
                Button(onClick = {
                    val audio = selectedAudioForDelete!!; selectedAudioForDelete = null
                    scope.launch {
                        isProcessing = true
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, listOf(audio.uri))
                            deleteLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                        } else {
                            audioViewModel.stopIfPlaying(audio.uri)
                            context.contentResolver.delete(audio.uri, null, null)
                            audioViewModel.refreshAudios(context); isProcessing = false
                        }
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
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
                    val folderName = folderToMoveToLocker!!; folderToMoveToLocker = null
                    scope.launch {
                        isProcessing = true
                        if (selectedTab == MediaTab.VIDEOS) {
                            val videos = viewModel.getVideosInFolder(folderName)
                            val result = bulkPrepareMoveToLocker(context, videos)
                            if (result is MoveToLockerResult.Success) {
                                videos.forEach { video -> dao.insertLockedVideo(LockedVideo(originalPath = video.path, lockerPath = File(File(context.filesDir, "locker"), video.name).absolutePath, name = video.name, duration = video.duration, originFolderName = folderName, isAudio = false)); viewModel.clearVideoCache(context, video.id) }
                                viewModel.refreshVideos(context); isProcessing = false
                            } else if (result is MoveToLockerResult.RequiresUserConsent) { folderVideosPendingMove = videos; folderOriginPendingMove = folderName; deleteLauncher.launch(IntentSenderRequest.Builder(result.intentSender).build()) }
                            else { isProcessing = false; Toast.makeText(context, "Failed to move folder", Toast.LENGTH_SHORT).show() }
                        } else {
                            val audios = audioViewModel.getAudiosInFolder(folderName)
                            val result = bulkPrepareAudioMoveToLocker(context, audios)
                            if (result is MoveToLockerResult.Success) {
                                audios.forEach { audio -> dao.insertLockedVideo(LockedVideo(originalPath = audio.path, lockerPath = File(File(context.filesDir, "locker"), audio.name).absolutePath, name = audio.name, duration = audio.duration, originFolderName = folderName, isAudio = true)) }
                                audioViewModel.refreshAudios(context); isProcessing = false
                            } else if (result is MoveToLockerResult.RequiresUserConsent) { folderAudiosPendingMove = audios; folderOriginPendingMove = folderName; deleteLauncher.launch(IntentSenderRequest.Builder(result.intentSender).build()) }
                            else { isProcessing = false; Toast.makeText(context, "Failed to move folder", Toast.LENGTH_SHORT).show() }
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
            icon = { Icon(Icons.Default.Lock, null, tint = Color(0xFF71717A)) },
            title = { Text(text = "Move to Locker?", fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
            text = { Text(text = "Please allow MEDIA MANAGEMENT Permission in app settings for smoother locking experience.", textAlign = TextAlign.Center) },
            confirmButton = {
                Button(onClick = {
                    val video = selectedVideoForLocker!!; selectedVideoForLocker = null
                    scope.launch {
                        isProcessing = true
                        val result = prepareMoveToLocker(context, video)
                        if (result is MoveToLockerResult.Success) {
                            dao.insertLockedVideo(LockedVideo(originalPath = video.path, lockerPath = File(File(context.filesDir, "locker"), video.name).absolutePath, name = video.name, duration = video.duration, originFolderName = File(video.path).parentFile?.name, isAudio = false))
                            viewModel.clearVideoCache(context, video.id); viewModel.refreshVideos(context); isProcessing = false
                        } else if (result is MoveToLockerResult.RequiresUserConsent) { videoPendingMove = video; deleteLauncher.launch(IntentSenderRequest.Builder(result.intentSender).build()) }
                        else { isProcessing = false; Toast.makeText(context, "Failed to move video", Toast.LENGTH_SHORT).show() }
                    }
                }, shape = RoundedCornerShape(12.dp)) { Text("Move") }
            },
            dismissButton = { TextButton(onClick = { selectedVideoForLocker = null }) { Text("Cancel") } },
            shape = RoundedCornerShape(28.dp)
        )
    }

    if (selectedAudioForLocker != null) {
        AlertDialog(
            onDismissRequest = { selectedAudioForLocker = null },
            icon = { Icon(Icons.Default.Lock, null, tint = Color(0xFF71717A)) },
            title = { Text(text = "Move Audio to Locker?", fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
            text = { Text(text = "Move '${selectedAudioForLocker?.name}' to private vault?", textAlign = TextAlign.Center) },
            confirmButton = {
                Button(onClick = {
                    val audio = selectedAudioForLocker!!; selectedAudioForLocker = null
                    scope.launch {
                        isProcessing = true
                        val result = prepareAudioMoveToLocker(context, audio)
                        if (result is MoveToLockerResult.Success) {
                            dao.insertLockedVideo(LockedVideo(originalPath = audio.path, lockerPath = File(File(context.filesDir, "locker"), audio.name).absolutePath, name = audio.name, duration = audio.duration, originFolderName = File(audio.path).parentFile?.name, isAudio = true))
                            audioViewModel.refreshAudios(context); isProcessing = false
                        } else if (result is MoveToLockerResult.RequiresUserConsent) { audioPendingMove = audio; deleteLauncher.launch(IntentSenderRequest.Builder(result.intentSender).build()) }
                        else { isProcessing = false; Toast.makeText(context, "Failed to move audio", Toast.LENGTH_SHORT).show() }
                    }
                }, shape = RoundedCornerShape(12.dp)) { Text("Move") }
            },
            dismissButton = { TextButton(onClick = { selectedAudioForLocker = null }) { Text("Cancel") } },
            shape = RoundedCornerShape(28.dp)
        )
    }

    if (isProcessing) ModernLoadingDialog()

    if (showAddToPlaylistDialog && mediaPendingPlaylist != null) {
        AddToPlaylistFromHomeDialog(playlists = playlists, onDismiss = { showAddToPlaylistDialog = false; mediaPendingPlaylist = null }, onPlaylistSelected = { playlistId ->
            mediaPendingPlaylist?.let { (uri, type) -> audioViewModel.addToPlaylist(playlistId, uri, type); Toast.makeText(context, "Added to playlist", Toast.LENGTH_SHORT).show() }
            showAddToPlaylistDialog = false; mediaPendingPlaylist = null
        }, onCreatePlaylist = { name -> audioViewModel.createPlaylist(name) })
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)).statusBarsPadding()) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            if (currentSelectedFolder != null || selectedPlaylistForDetails != null) {
                                IconButton(modifier = Modifier.size(50.dp) ,onClick = { 
                                    if (selectedTab == MediaTab.VIDEOS) viewModel.setSelectedFolder(null)
                                    else if (selectedTab == MediaTab.AUDIOS) audioViewModel.setSelectedFolder(null)
                                    else selectedPlaylistForDetails = null
                                }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(38.dp)) }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = currentSelectedFolder ?: selectedPlaylistForDetails?.name ?: "", style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold))
                            } else {
                                ModernOmegaIcon()
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(text = "Omega Player", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black, brush = Brush.linearGradient(colors = listOf(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.primary))))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
                if (currentSelectedFolder == null && selectedPlaylistForDetails == null) {
                    HomeDashboard(selectedTab = selectedTab, storageStats = storageStats, onTabSelected = { tab -> selectedTab = tab })
                }
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, modifier = Modifier.weight(1f), placeholder = { Text(if (currentSelectedFolder == null && selectedPlaylistForDetails == null) "Search folders..." else if (selectedPlaylistForDetails != null) "Search in ${selectedPlaylistForDetails!!.name}..." else "Search in $currentSelectedFolder...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) }, leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary) }, trailingIcon = { if (searchQuery.isNotEmpty()) { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, null) } } }, shape = RoundedCornerShape(20.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color.Transparent, focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)), singleLine = true, textStyle = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { isGridView = !isGridView }, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), CircleShape)) { Icon(if (isGridView) Icons.Default.ViewList else Icons.Default.GridView, null, tint = MaterialTheme.colorScheme.primary) }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = if (currentSelectedFolder == null && selectedPlaylistForDetails == null) "Folders" else if (selectedPlaylistForDetails != null) "Items" else if (selectedTab == MediaTab.VIDEOS) "Videos" else "Audios", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface))
                    if (currentSelectedFolder == null && selectedPlaylistForDetails == null) { Text(text = "${currentFolders.size} Folders", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        },
        floatingActionButton = {
            if (!hasPermission) {
                ExtendedFloatingActionButton(modifier = Modifier.padding(bottom = 80.dp), text = { Text("Grant Access") }, icon = { Icon(Icons.Default.AddCircle, null) }, onClick = { launcher.launch(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)) }, containerColor = Color.White, contentColor = Color(0xFF1A1A1A), shape = RoundedCornerShape(16.dp))
            }
        }
    ) { padding ->
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize().padding(padding), userScrollEnabled = currentSelectedFolder == null && selectedPlaylistForDetails == null) { page ->
            val pageTab = MediaTab.entries[page]
            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading && (if (pageTab == MediaTab.VIDEOS) videos.isEmpty() else audios.isEmpty())) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else if (currentSelectedFolder == null && selectedPlaylistForDetails == null && filteredFolders.isEmpty() && pageTab != MediaTab.PLAYLISTS) {
                    EmptyState(searchQuery.isNotEmpty(), true)
                } else if ((currentSelectedFolder != null || selectedPlaylistForDetails != null) && (if (pageTab == MediaTab.VIDEOS) filteredVideos.isEmpty() else if (pageTab == MediaTab.AUDIOS) filteredAudios.isEmpty() else playlistItems.isEmpty())) {
                    EmptyState(searchQuery.isNotEmpty(), false)
                } else {
                    if (isGridView) {
                        LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp + bottomPadding), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (currentSelectedFolder == null && selectedPlaylistForDetails == null) {
                                if (pageTab != MediaTab.PLAYLISTS && recentPlayback.isNotEmpty()) { item(span = { GridItemSpan(2) }) { RecentPlaybackSection(recentPlayback, onVideoClick, onAudioClick, onViewAllHistoryClick) } }
                                if (pageTab == MediaTab.PLAYLISTS) {
                                    if (playlists.isNotEmpty()) { items(playlists, key = { it.id }, span = { GridItemSpan(2) }) { playlist -> PlaylistListItem(playlist, { selectedPlaylistForDetails = playlist }, { audioViewModel.deletePlaylist(playlist) }) } }
                                } else {
                                    items(filteredFolders.keys.toList(), key = { it }) { folderName -> FolderGridItem(name = folderName, count = filteredFolders[folderName] ?: 0, onClick = { if (pageTab == MediaTab.VIDEOS) viewModel.setSelectedFolder(folderName) else audioViewModel.setSelectedFolder(folderName) }) }
                                }
                            } else if (selectedPlaylistForDetails != null) {
                                items(playlistItems, key = { it.id }) { item -> PlaylistGridItem(item, videos, audios, viewModel, audioViewModel, sharedTransitionScope, animatedVisibilityScope, onVideoClick, onAudioClick, selectedPlaylistForDetails!!) }
                            } else if (pageTab == MediaTab.VIDEOS) {
                                items(filteredVideos, key = { it.id }) { video -> VideoGridItem(video, viewModel, sharedTransitionScope, animatedVisibilityScope, onVideoClick, { checkPinAndProceed { selectedVideoForLocker = video } }, { selectedVideoForDelete = video }, { mediaPendingPlaylist = video.uri.toString() to "video"; showAddToPlaylistDialog = true }) }
                            } else {
                                items(filteredAudios, key = { it.id }) { audio -> AudioGridItem(audio, audioViewModel, onAudioClick, { checkPinAndProceed { selectedAudioForLocker = audio } }, { selectedAudioForDelete = audio }, { mediaPendingPlaylist = audio.uri.toString() to "audio"; showAddToPlaylistDialog = true }) }
                            }
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp + bottomPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (currentSelectedFolder == null && selectedPlaylistForDetails == null && pageTab != MediaTab.PLAYLISTS && recentPlayback.isNotEmpty()) { item { RecentPlaybackSection(recentPlayback, onVideoClick, onAudioClick, onViewAllHistoryClick) } }
                            if (currentSelectedFolder == null && selectedPlaylistForDetails == null) {
                                if (pageTab == MediaTab.PLAYLISTS) {
                                    if (playlists.isEmpty()) { item { Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { Text("No playlists yet", color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
                                    else { items(playlists, key = { it.id }) { playlist -> PlaylistListItem(playlist = playlist, onClick = { selectedPlaylistForDetails = playlist }, onDelete = { audioViewModel.deletePlaylist(playlist) }) } }
                                } else {
                                    items(filteredFolders.keys.toList(), key = { it }) { folderName -> FolderListItem(name = folderName, count = filteredFolders[folderName] ?: 0, onClick = { if (pageTab == MediaTab.VIDEOS) viewModel.setSelectedFolder(folderName) else audioViewModel.setSelectedFolder(folderName) }, onDelete = { folderToDelete = folderName }, onMoveToLocker = { checkPinAndProceed { folderToMoveToLocker = folderName } }) }
                                }
                            } else if (selectedPlaylistForDetails != null) {
                                items(playlistItems, key = { it.id }) { item -> MediaListItemInPlaylist(item = item, videos = videos, audios = audios, videoViewModel = viewModel, audioViewModel = audioViewModel, sharedTransitionScope = sharedTransitionScope, animatedVisibilityScope = animatedVisibilityScope, onVideoClick = onVideoClick, onAudioClick = onAudioClick, playlist = selectedPlaylistForDetails!!, onVideoLock = { checkPinAndProceed { selectedVideoForLocker = it } }, onAudioLock = { checkPinAndProceed { selectedAudioForLocker = it } }, onVideoDelete = { selectedVideoForDelete = it }, onAudioDelete = { selectedAudioForDelete = it }) }
                            } else if (pageTab == MediaTab.VIDEOS) {
                                items(filteredVideos, key = { it.id }) { video -> VideoListItem(video = video, isPlaying = viewModel.activeVideoUri.collectAsState().value == video.uri.toString() && viewModel.isPlaying.collectAsState().value, sharedTransitionScope = sharedTransitionScope, animatedVisibilityScope = animatedVisibilityScope, onClick = { val encodedUri = URLEncoder.encode(video.uri.toString(), StandardCharsets.UTF_8.toString()); onVideoClick(encodedUri) }, onLockClick = { checkPinAndProceed { selectedVideoForLocker = video } }, onDeleteClick = { selectedVideoForDelete = video }, onPlaylistClick = { mediaPendingPlaylist = video.uri.toString() to "video"; showAddToPlaylistDialog = true }) }
                            } else {
                                items(filteredAudios, key = { it.id }) { audio -> AudioListItem(audio = audio, isPlaying = audioViewModel.activeAudioUri.collectAsState().value == audio.uri.toString() && audioViewModel.isPlaying.collectAsStateWithLifecycle().value, onClick = { val encodedUri = URLEncoder.encode(audio.uri.toString(), StandardCharsets.UTF_8.toString()); onAudioClick(encodedUri) }, onPlayPauseClick = { audioViewModel.togglePlayPause(audio) }, onLockClick = { checkPinAndProceed { selectedAudioForLocker = audio } }, onDeleteClick = { selectedAudioForDelete = audio }, onPlaylistClick = { mediaPendingPlaylist = audio.uri.toString() to "audio"; showAddToPlaylistDialog = true }) }
                            }
                        }
                    }
                }
            }
        }
    }
}

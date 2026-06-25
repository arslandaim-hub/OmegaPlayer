package com.arslandaim.omegaplayer.ui.home

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.arslandaim.omegaplayer.viewmodel.AudioViewModel
import com.arslandaim.omegaplayer.viewmodel.VideoViewModel
import com.arslandaim.omegaplayer.viewmodel.LockerViewModel
import com.arslandaim.omegaplayer.ui.locker.MoveToLockerResult
import com.arslandaim.omegaplayer.ui.locker.bulkPrepareMoveToLocker
import com.arslandaim.omegaplayer.ui.locker.bulkPrepareAudioMoveToLocker
import com.arslandaim.omegaplayer.ui.locker.prepareMoveToLocker
import com.arslandaim.omegaplayer.ui.locker.prepareAudioMoveToLocker
import com.arslandaim.omegaplayer.ui.common.ModernLoadingDialog
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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

enum class MediaTab { VIDEOS, AUDIOS }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreen(
    viewModel: VideoViewModel,
    audioViewModel: AudioViewModel,
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

    // Data loading logic
    LaunchedEffect(Unit) {
        viewModel.fetchVideos(context)
        audioViewModel.fetchAudios(context)
    }

    LaunchedEffect(initialTab) {
        if (initialTab != null) {
            selectedTab = initialTab
        }
    }

    // Video Data
    val videos by viewModel.videos.collectAsState()
    val isLoadingVideos by viewModel.isLoading.collectAsState()
    val videoFolders by viewModel.folders.collectAsStateWithLifecycle()
    val selectedVideoFolder by viewModel.selectedFolder.collectAsStateWithLifecycle()
    val videosInFolder by viewModel.videosInSelectedFolder.collectAsStateWithLifecycle()

    // Audio Data
    val audios by audioViewModel.audios.collectAsStateWithLifecycle()
    val isLoadingAudios by audioViewModel.isLoading.collectAsStateWithLifecycle()
    val audioFolders by audioViewModel.folders.collectAsStateWithLifecycle()
    val selectedAudioFolder by audioViewModel.selectedFolder.collectAsStateWithLifecycle()
    val audiosInFolder by audioViewModel.audiosInSelectedFolder.collectAsStateWithLifecycle()
    
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
    
    val currentSelectedFolder = if (selectedTab == MediaTab.VIDEOS) selectedVideoFolder else selectedAudioFolder
    val currentFolders = if (selectedTab == MediaTab.VIDEOS) videoFolders else audioFolders
    val currentMediaInFolder = if (selectedTab == MediaTab.VIDEOS) videosInFolder else audiosInFolder

    fun checkPinAndProceed(action: () -> Unit) {
        if (lockerSettings == null) {
            showSetPinDialog = true
        } else {
            action()
        }
    }

    // Robust Back Handling: 
    // We only enable the BackHandler if there is a folder to close.
    // If no folder is open, we leave 'enabled = false' so the system (or NavHost) 
    // can handle the back press (e.g., exiting the app).
    val isCurrentFolderOpen = remember(selectedTab, selectedVideoFolder, selectedAudioFolder) {
        (selectedTab == MediaTab.VIDEOS && selectedVideoFolder != null) || 
        (selectedTab == MediaTab.AUDIOS && selectedAudioFolder != null)
    }

    BackHandler(enabled = isFocused && isCurrentFolderOpen) {
        if (selectedTab == MediaTab.VIDEOS) {
            viewModel.setSelectedFolder(null)
        } else {
            audioViewModel.setSelectedFolder(null)
        }
    }

    val filteredFolders = remember(currentFolders, searchQuery, currentSelectedFolder) {
        if (currentSelectedFolder != null) emptyMap()
        else if (searchQuery.isEmpty()) currentFolders
        else currentFolders.filterKeys { it.contains(searchQuery, ignoreCase = true) }
    }

    val filteredVideos = remember(currentMediaInFolder, searchQuery, currentSelectedFolder) {
        if (currentSelectedFolder == null || selectedTab != MediaTab.VIDEOS) emptyList()
        else if (searchQuery.isEmpty()) currentMediaInFolder as List<VideoModel>
        else (currentMediaInFolder as List<VideoModel>).filter { it.name.contains(searchQuery, ignoreCase = true) }
    }
    
    val filteredAudios = remember(currentMediaInFolder, searchQuery, currentSelectedFolder) {
        if (currentSelectedFolder == null || selectedTab != MediaTab.AUDIOS) emptyList()
        else if (searchQuery.isEmpty()) currentMediaInFolder as List<AudioModel>
        else (currentMediaInFolder as List<AudioModel>).filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            scope.launch {
                isProcessing = true
                // This is to handle single video move completion
                videoPendingMove?.let { video ->
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
                audioPendingMove?.let { audio ->
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
                            if (currentSelectedFolder != null) {
                                IconButton(modifier = Modifier.size(50.dp) ,onClick = { 
                                    if (selectedTab == MediaTab.VIDEOS) viewModel.setSelectedFolder(null)
                                    else audioViewModel.setSelectedFolder(null)
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Back",tint = Color(0xFFFF6600), modifier = Modifier.size(38.dp))
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = currentSelectedFolder,
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

                if (currentSelectedFolder == null) {
                    TabRow(
                        selectedTabIndex = selectedTab.ordinal,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                        divider = {},
                        indicator = { tabPositions ->
                            if (selectedTab.ordinal < tabPositions.size) {
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                                    color = Color(0xFFFF6600)
                                )
                            }
                        }
                    ) {
                        Tab(
                            selected = selectedTab == MediaTab.VIDEOS,
                            onClick = { selectedTab = MediaTab.VIDEOS },
                            text = { Text("Videos") }
                        )
                        Tab(
                            selected = selectedTab == MediaTab.AUDIOS,
                            onClick = { selectedTab = MediaTab.AUDIOS },
                            text = { Text("Audios") }
                        )
                    }
                }
                
                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { 
                        Text(if (currentSelectedFolder == null) "Search folders..." else "Search in $currentSelectedFolder...") 
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                    ),
                    singleLine = true
                )

                Text(
                    text = if (currentSelectedFolder == null) "Folders" else if (selectedTab == MediaTab.VIDEOS) "Videos" else "Audios",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp)
                )
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading && (if (selectedTab == MediaTab.VIDEOS) videos.isEmpty() else audios.isEmpty())) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (currentSelectedFolder == null && filteredFolders.isEmpty()) {
                EmptyState(searchQuery.isNotEmpty(), true)
            } else if (currentSelectedFolder != null && (if (selectedTab == MediaTab.VIDEOS) filteredVideos.isEmpty() else filteredAudios.isEmpty())) {
                EmptyState(searchQuery.isNotEmpty(), false)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + bottomPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (currentSelectedFolder == null) {
                        items(filteredFolders.keys.toList()) { folderName ->
                            FolderListItem(
                                name = folderName,
                                count = filteredFolders[folderName] ?: 0,
                                onClick = { 
                                    if (selectedTab == MediaTab.VIDEOS) viewModel.setSelectedFolder(folderName)
                                    else audioViewModel.setSelectedFolder(folderName)
                                },
                                onDelete = { folderToDelete = folderName },
                                onMoveToLocker = { checkPinAndProceed { folderToMoveToLocker = folderName } }
                            )
                        }
                    } else if (selectedTab == MediaTab.VIDEOS) {
                        items(filteredVideos, key = { it.id }) { video ->
                            val isActive = viewModel.activeVideoUri.collectAsState().value == video.uri.toString()
                            VideoListItem(
                                video = video,
                                isPlaying = isActive && viewModel.isPlaying.collectAsState().value,
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                onClick = {
                                    val encodedUri = URLEncoder.encode(video.uri.toString(), StandardCharsets.UTF_8.toString())
                                    onVideoClick(encodedUri)
                                },
                                onLockClick = { checkPinAndProceed { selectedVideoForLocker = video } },
                                onDeleteClick = { selectedVideoForDelete = video }
                            )
                        }
                    } else {
                        items(filteredAudios, key = { it.id }) { audio ->
                            val isActive = audioViewModel.activeAudioUri.collectAsState().value == audio.uri.toString()
                            AudioListItem(
                                audio = audio,
                                isPlaying = isActive && audioViewModel.isPlaying.collectAsStateWithLifecycle().value,
                                onClick = {
                                    val encodedUri = URLEncoder.encode(audio.uri.toString(), StandardCharsets.UTF_8.toString())
                                    onAudioClick(encodedUri)
                                },
                                onPlayPauseClick = { audioViewModel.togglePlayPause(audio) },
                                onLockClick = { checkPinAndProceed { selectedAudioForLocker = audio } },
                                onDeleteClick = { selectedAudioForDelete = audio }
                            )
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
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            
            Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
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
fun EmptyState(isSearching: Boolean, isFolderView: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (isSearching) Icons.Default.Search else if (isFolderView) Icons.Default.FolderOpen else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                if (isSearching) "No matches found" else if (isFolderView) "No folders found" else "No videos in this folder",
                style = MaterialTheme.typography.titleMedium,
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
    onDeleteClick: () -> Unit
) {
    with(sharedTransitionScope) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
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
                containerColor = if (isPlaying) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
                    .height(80.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp))
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
                        contentScale = ContentScale.Crop,
                        alpha = if (isPlaying) 0.6f else 1f
                    )

                    if (isPlaying) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            PlayingVisualizer()
                        }
                    }
                    
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = formatDuration(video.duration),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                
                Column(modifier = Modifier.padding(12.dp).weight(1f)) {
                    Text(
                        text = video.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isPlaying) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${video.size / (1024 * 1024)} MB",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isPlaying) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDeleteClick) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFF71717A)
                    )
                }

                IconButton(onClick = onLockClick) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "Move to Locker",
                        tint = Color(0xFF71717A)
                    )
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
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onLockClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isPlaying) {
                        PlayingVisualizer(modifier = Modifier.size(24.dp))
                    } else {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color(0xFFF97316),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = audio.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${audio.artist} • ${formatDuration(audio.duration)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            IconButton(onClick = onPlayPauseClick) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color(0xFFFF6600)
                )
            }

            IconButton(onClick = onLockClick) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "Lock",
                    tint = Color(0xFF71717A),
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = onDeleteClick) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color(0xFF71717A),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ModernOmegaIconPreview() {
    MaterialTheme { Box(modifier = Modifier.padding(16.dp)) { ModernOmegaIcon() } }
}

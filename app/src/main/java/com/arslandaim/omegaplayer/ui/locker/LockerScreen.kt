package com.arslandaim.omegaplayer.ui.locker

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.arslandaim.omegaplayer.data.LockerDatabase
import com.arslandaim.omegaplayer.data.LockerSettings
import com.arslandaim.omegaplayer.ui.home.formatDuration
import com.arslandaim.omegaplayer.ui.settings.authenticateBiometric
import com.arslandaim.omegaplayer.ui.common.ModernLoadingDialog
import com.arslandaim.omegaplayer.viewmodel.LockerViewModel
import com.arslandaim.omegaplayer.viewmodel.PinState
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun LockerScreen(
    viewModel: LockerViewModel,
    onBack: () -> Unit, 
    onVideoClick: (String) -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    isFocused: Boolean = true
) {
    val expandedFolder by viewModel.expandedFolder.collectAsStateWithLifecycle()
    
    // Unified robust back handling for Locker
    BackHandler(enabled = isFocused) {
        if (expandedFolder != null) {
            viewModel.setExpandedFolder(null)
        } else {
            onBack()
        }
    }
    
    val context = LocalContext.current
    val pinState by viewModel.pinState.collectAsStateWithLifecycle()
    val lockedVideos by viewModel.lockedVideos.collectAsStateWithLifecycle()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Crossfade(targetState = pinState, label = "locker_fade") { state ->
            when (state) {
                PinState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                PinState.NotSet -> {
                    LockerSetupScreen(viewModel, bottomPadding)
                }
                PinState.Required -> {
                    LockerAuthScreen(viewModel, onBack, bottomPadding)
                }
                PinState.Unlocked -> {
                    LockedVideosList(
                        viewModel = viewModel,
                        videos = lockedVideos,
                        onBack = onBack,
                        onVideoClick = onVideoClick,
                        onUnlock = { video ->
                            // Video unlocking logic
                        },
                        onRefresh = { viewModel.fetchLockedVideos() },
                        bottomPadding = bottomPadding
                    )
                }
            }
        }
    }
}

@Composable
fun LockerAuthScreen(
    viewModel: LockerViewModel, 
    onBack: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    BackHandler(onBack = onBack)
    
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()
    var pinInput by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 24.dp + bottomPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
//        Icon(
//            Icons.Default.Lock,
//            contentDescription = null,
//            tint = Color(0xFFFF6600),
//            modifier = Modifier.size(64.dp)
//        )
//        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Private Vault",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            "Enter PIN to access your hidden content",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(15.dp))
        
        // PIN Dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            repeat(4) { index ->
                val filled = index < pinInput.length
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            if (filled) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Number Pad
        Column(
            modifier = Modifier.fillMaxWidth(0.8f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val buttons = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("Back", "0", "OK")
            )
            
            buttons.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { label ->
                        NumButton(
                            label = label,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                when (label) {
                                    "Back" -> if (pinInput.isNotEmpty()) pinInput = pinInput.dropLast(1)
                                    "OK" -> {
                                        scope.launch {
                                            val settings = viewModel.getSettings()
                                            if (settings?.pin == pinInput) {
                                                viewModel.unlock(true)
                                            } else {
                                                Toast.makeText(context, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                                                pinInput = ""
                                            }
                                        }
                                    }
                                    else -> if (pinInput.length < 4) {
                                        pinInput += label
                                        if (pinInput.length == 4) {
                                            // Auto verify
                                            scope.launch {
                                                val settings = viewModel.getSettings()
                                                if (settings?.pin == pinInput) {
                                                    viewModel.unlock(true)
                                                } else {
                                                    Toast.makeText(context, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                                                    pinInput = ""
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Fingerprint Button instead of Cancel
        FilledTonalIconButton(
            onClick = {
                scope.launch {
                    val settings = viewModel.getSettings()
                    if (settings?.isBiometricEnabled == true && activity != null) {
                        authenticateBiometric(activity, {
                            viewModel.unlock(true)
                        }, { errorCode, error ->
                            if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && 
                                errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                                Toast.makeText(context, "Authentication failed: $error", Toast.LENGTH_SHORT).show()
                            }
                        })
                    } else {
                        Toast.makeText(context, "Biometric unlock not enabled in Settings", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier.size(64.dp),
            shape = CircleShape
        ) {
            Icon(
                Icons.Default.Fingerprint,
                contentDescription = "Fingerprint Unlock",
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun NumButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val isAction = label == "Back" || label == "OK"
    Surface(
        onClick = onClick,
        modifier = modifier.aspectRatio(1f),
        shape = CircleShape,
        color = if (isAction) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) 
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            when (label) {
                "Back" -> {
                    Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = null)
                }
                "OK" -> {
                    Icon(Icons.Default.Check, contentDescription = null)
                }
                else -> {
                    Text(
                        label, 
                        fontSize = 24.sp, 
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockerSetupScreen(
    viewModel: LockerViewModel,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    var pin by remember { mutableStateOf("") }
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 24.dp + bottomPadding)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Setup Vault",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                // sequential blue, yellow, and red color spectrum
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFF44336), // Red
                        Color(0xFF2196F3), // Blue
                        Color(0xFFFFEB3B)  // Yellow
                    )
                )
            )
        )
        Text(
            "Secure your private media with a PIN",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4) pin = it },
                    label = { Text("4-digit PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Security Question") },
                    placeholder = { Text("e.g. First pet's name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    label = { Text("Answer") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                if (pin.length == 4 && question.isNotBlank() && answer.isNotBlank()) {
                    viewModel.saveSettings(LockerSettings(pin = pin, securityQuestion = question, securityAnswer = answer))
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFFFFFF), // Vibrant Material Yellow
                contentColor = Color(0xFF1A1A1A)    // Dark text color
            )
        ) {
            Text("Initialize Vault", fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockedVideosList(
    viewModel: LockerViewModel,
    videos: List<com.arslandaim.omegaplayer.data.LockedVideo>,
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit,
    onUnlock: (com.arslandaim.omegaplayer.data.LockedVideo) -> Unit,
    onRefresh: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val expandedFolder by viewModel.expandedFolder.collectAsStateWithLifecycle()

    // Robust Back Handling: Prioritize closing expanded folder over exiting locker
    // MOVED TO LockerScreen

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { LockerDatabase.getDatabase(context).lockerDao() }
    var isProcessing by remember { mutableStateOf(false) }
    
    // Group videos by folder origin
    val groupedVideos = remember(videos) {
        videos.groupBy { it.originFolderName }
    }
    
    if (isProcessing) {
        ModernLoadingDialog()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (expandedFolder != null) {
                            IconButton(onClick = { viewModel.setExpandedFolder(null) }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                        Text(
                            if (expandedFolder != null) expandedFolder!! else "Private Vault", 
                            fontSize = 24.sp, 
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.ReplayCircleFilled, contentDescription = "Refresh", tint = Color(0xFF87CEEB))
                    }
                }
            )
        }
    ) { padding ->
        if (videos.isEmpty()) {
            EmptyVaultState()
        } else {
            val contentModifier = Modifier
                .fillMaxSize()
                .padding(padding)
            
            if (expandedFolder != null) {
                // Show videos in the selected folder
                val folderVideos = groupedVideos[expandedFolder] ?: emptyList()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = contentModifier,
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + bottomPadding),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(folderVideos) { video ->
                        VaultItem(
                            video = video,
                            onClick = { onVideoClick(video.lockerPath) },
                            onUnlock = {
                                scope.launch {
                                    isProcessing = true
                                    val success = unlockVideo(context, video, dao)
                                    isProcessing = false
                                    if (success) {
                                        Toast.makeText(context, "Restored to Gallery", Toast.LENGTH_SHORT).show()
                                        onRefresh()
                                    }
                                }
                            }
                        )
                    }
                }
            } else {
                // Show folders and individual videos
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = contentModifier,
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + bottomPadding),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Folders Section
                    val folderNames = groupedVideos.keys.filterNotNull()
                    if (folderNames.isNotEmpty()) {
                        items(folderNames) { folderName ->
                            VaultFolderItem(
                                name = folderName,
                                count = groupedVideos[folderName]?.size ?: 0,
                                onClick = { viewModel.setExpandedFolder(folderName) },
                                onUnlock = {
                                    scope.launch {
                                        isProcessing = true
                                        val folderVideos = groupedVideos[folderName] ?: emptyList()
                                        var successCount = 0
                                        folderVideos.forEach { video ->
                                            if (unlockVideo(context, video, dao)) successCount++
                                        }
                                        isProcessing = false
                                        if (successCount > 0) {
                                            Toast.makeText(context, "Restored $successCount videos to Gallery", Toast.LENGTH_SHORT).show()
                                            onRefresh()
                                        }
                                    }
                                }
                            )
                        }
                    }

                    // 2. Individual Videos Section
                    val individualVideos = groupedVideos[null] ?: emptyList()
                    items(individualVideos) { video ->
                        VaultItem(
                            video = video,
                            onClick = { onVideoClick(video.lockerPath) },
                            onUnlock = {
                                scope.launch {
                                    isProcessing = true
                                    val success = unlockVideo(context, video, dao)
                                    isProcessing = false
                                    if (success) {
                                        Toast.makeText(context, "Restored to Gallery", Toast.LENGTH_SHORT).show()
                                        onRefresh()
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

@Composable
fun VaultFolderItem(
    name: String,
    count: Int,
    onClick: () -> Unit,
    onUnlock: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color(0xFFE53935)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$count videos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = { onUnlock() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = "Unlock Folder",
                    tint = Color(0xFFE53935)
                )
            }
        }
    }
}

@Composable
fun VaultItem(
    video: com.arslandaim.omegaplayer.data.LockedVideo,
    onClick: () -> Unit,
    onUnlock: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.8f)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(File(video.lockerPath))
                        .decoderFactory { result, options, _ -> VideoFrameDecoder(result.source, options) }
                        .videoFrameMillis(1000)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                ) {
                    Text(
                        text = formatDuration(video.duration),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                
                IconButton(
                    onClick = onUnlock,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                ) {
                    Icon(Icons.Default.LockOpen, "Unlock", tint = Color(0xFFE53935))
                }
            }
            
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = video.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun EmptyVaultState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.VpnKey,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Vault is empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

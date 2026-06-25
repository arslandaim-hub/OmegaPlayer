package com.arslandaim.omegaplayer.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arslandaim.omegaplayer.data.AppTheme
import com.arslandaim.omegaplayer.data.LockerDatabase
import com.arslandaim.omegaplayer.viewmodel.LockerViewModel
import com.arslandaim.omegaplayer.viewmodel.ThemeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: LockerViewModel,
    onLockerClick: () -> Unit,
    onBack: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    isFocused: Boolean = true
) {
    BackHandler(enabled = isFocused, onBack = onBack)

    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()
    val dao = remember { LockerDatabase.getDatabase(context).lockerDao() }
    val themeViewModel: ThemeViewModel = viewModel()
    val currentTheme by themeViewModel.theme.collectAsState()

    var showChangePinDialog by remember { mutableStateOf(false) }
    var showSecurityVerification by remember { mutableStateOf(false) }
    var showAboutDeveloperDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    
    // Local state for snappy switch animation
    var biometricEnabledLocal by remember(settings?.isBiometricEnabled) { 
        mutableStateOf(settings?.isBiometricEnabled ?: false) 
    }

    val biometricManager = remember { BiometricManager.from(context) }
    val isBiometricSupported = remember {
        biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    var canManageMedia by remember { 
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaStore.canManageMedia(context)
            } else {
                true
            }
        )
    }

    val manageMediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            canManageMedia = MediaStore.canManageMedia(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Settings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(scrollState)
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + bottomPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("App Theme", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(12.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        AppTheme.entries.forEachIndexed { index, theme ->
                            SegmentedButton(
                                selected = currentTheme == theme,
                                onClick = { themeViewModel.setTheme(theme) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = AppTheme.entries.size),
                                label = { Text(theme.name.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                }
            }

            Text("Security", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            
            // PIN Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                ListItem(
                    headlineContent = { Text("Change Locker PIN", fontWeight = FontWeight.Medium) },
                    supportingContent = { Text("Update your 4-digit security code") },
                    leadingContent = { 
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFFF6600))
                            }
                        }
                    },
                    trailingContent = {
                        Button(
                            onClick = {
                                if (settings == null) {
                                    Toast.makeText(context, "Setup PIN first", Toast.LENGTH_SHORT).show()
                                } else {
                                    showSecurityVerification = true
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Change",
                                color = Color(0xFFFF6600),)
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            // Biometric Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                ListItem(
                    headlineContent = { Text("Fingerprint Unlock", fontWeight = FontWeight.Medium) },
                    supportingContent = { 
                        Text(if (isBiometricSupported) "Enable biometric access for locker" else "Device doesn't support biometric") 
                    },
                    leadingContent = { 
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = Color(0xFFFF6600))
                            }
                        }
                    },
                    trailingContent = {
                        Switch(
                            checked = biometricEnabledLocal,
                            enabled = isBiometricSupported && settings != null,
                            onCheckedChange = { isEnabled ->
                                biometricEnabledLocal = isEnabled
                                
                                if (activity != null) {
                                    authenticateBiometric(activity, {
                                        scope.launch {
                                            settings?.let {
                                                dao.saveSettings(it.copy(isBiometricEnabled = isEnabled))
                                                Toast.makeText(context, "Biometric updated", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }, { errorCode, error ->
                                        biometricEnabledLocal = !isEnabled // Revert on failure
                                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && 
                                            errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                                            Toast.makeText(context, "Authentication failed: $error", Toast.LENGTH_SHORT).show()
                                        }
                                    })
                                }
                            }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Text("Storage Management", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    ListItem(
                        headlineContent = { Text("Automatic Locking", fontWeight = FontWeight.Medium) },
                        supportingContent = { 
                            Text(if (canManageMedia) "Requires Media Management permission" else "Requires 'Media Management' permission")
                        },
                        leadingContent = { 
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.AutoMode, contentDescription = null, tint =Color(0xFFFF6600))
                                }
                            }
                        },
                        trailingContent = {
                            Switch(
                                checked = canManageMedia,
                                onCheckedChange = { isEnabled ->
                                    if (isEnabled) {
                                        val intent = Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA)
                                        manageMediaLauncher.launch(intent)
                                    } else {
                                        Toast.makeText(context, "Disable this in system settings", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            Text("About", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

            // About Developer Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                ListItem(
                    headlineContent = { Text("About Developer", fontWeight = FontWeight.Medium) },
                    supportingContent = { Text("Information about the app developer") },
                    leadingContent = { 
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFFFF6600))
                            }
                        }
                    },
                    modifier = Modifier.clickable { showAboutDeveloperDialog = true },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            // View Source Code Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                ListItem(
                    headlineContent = { Text("View Source Code", fontWeight = FontWeight.Medium) },
                    supportingContent = { Text("Check the app source-code on GitHub") },
                    leadingContent = { 
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Code, contentDescription = null, tint = Color(0xFFFF6600))
                            }
                        }
                    },
                    modifier = Modifier.clickable { 
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/arslandaim-hub/OmegaPlayer"))
                        context.startActivity(intent)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Version Info
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Omega Player v:1.4.2", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        val currentSettings = settings
        if (showSecurityVerification && currentSettings != null) {
            SecurityVerificationDialog(
                question = currentSettings.securityQuestion,
                answer = currentSettings.securityAnswer,
                onDismiss = { showSecurityVerification = false },
                onSuccess = {
                    showSecurityVerification = false
                    showChangePinDialog = true
                }
            )
        }

        if (showChangePinDialog) {
            ChangePinModernDialog(
                onDismiss = { showChangePinDialog = false },
                onConfirm = { newPin ->
                    scope.launch {
                        settings?.let {
                            dao.saveSettings(it.copy(pin = newPin))
                            Toast.makeText(context, "PIN updated successfully", Toast.LENGTH_SHORT).show()
                            showChangePinDialog = false
                        }
                    }
                }
            )
        }

        if (showAboutDeveloperDialog) {
            AlertDialog(
                onDismissRequest = { showAboutDeveloperDialog = false },
                title = { Text("About Developer", fontWeight = FontWeight.Bold) },
                text = {
                    Text("Developer: Arsalan Daim Shar.\n"+"Student of BS Artificial Intelligence.")
                },
                confirmButton = {
                    TextButton(onClick = { showAboutDeveloperDialog = false }) {
                        Text("Close")
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }
    }
}

fun authenticateBiometric(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onError: (Int, String) -> Unit
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            onSuccess()
        }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            onError(errorCode, errString.toString())
        }
    })

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Biometric Authentication")
        .setSubtitle("Confirm your identity")
        .setNegativeButtonText("Cancel")
        .build()

    biometricPrompt.authenticate(promptInfo)
}

@Composable
fun SecurityVerificationDialog(
    question: String,
    answer: String,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var answerInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Verify Identity", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Please answer your security question to continue.", style = MaterialTheme.typography.bodyMedium)
                Text(question, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(
                    value = answerInput,
                    onValueChange = { answerInput = it },
                    label = { Text("Security Answer") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (answerInput.trim().equals(answer.trim(), ignoreCase = true)) {
                        onSuccess()
                    } else {
                        error = "Incorrect answer"
                    }
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Verify")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
fun ChangePinModernDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var newPin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Security PIN", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Enter a new 4-digit code to protect your videos.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 4) newPin = it },
                    label = { Text("4-digit PIN") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (newPin.length == 4) onConfirm(newPin) },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

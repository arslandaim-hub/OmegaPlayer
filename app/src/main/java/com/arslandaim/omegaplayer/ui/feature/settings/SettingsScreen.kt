/*
 * OmegaPlayer Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/

package com.arslandaim.omegaplayer.ui.feature.settings

import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arslandaim.omegaplayer.R
import com.arslandaim.omegaplayer.data.AppTheme
import com.arslandaim.omegaplayer.data.LockerDatabase
import com.arslandaim.omegaplayer.viewmodel.LockerViewModel
import com.arslandaim.omegaplayer.viewmodel.ThemeViewModel
import com.arslandaim.omegaplayer.viewmodel.VideoViewModel
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

    val versionName = remember {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName
        } catch (_: Exception) {
            "1.4.2"
        }
    }

    val dao = remember { LockerDatabase.getDatabase(context).lockerDao() }
    val themeViewModel: ThemeViewModel = hiltViewModel()
    val videoViewModel: VideoViewModel = hiltViewModel()
    val currentTheme by themeViewModel.theme.collectAsState()
    val dynamicColorEnabled by themeViewModel.dynamicColor.collectAsState()
    val isHistoryPaused by videoViewModel.isHistoryPaused.collectAsStateWithLifecycle()

    val setupPinFirstToast = stringResource(R.string.toast_setup_pin_first)
    val pinUpdatedToast = stringResource(R.string.toast_pin_updated)
    val biometricUpdatedToast = stringResource(R.string.toast_biometric_updated)

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
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
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
            Text(stringResource(R.string.section_appearance), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.app_theme), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(12.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        AppTheme.entries.forEachIndexed { index, theme ->
                            val themeLabel = when (theme) {
                                AppTheme.SYSTEM -> stringResource(R.string.theme_system)
                                AppTheme.LIGHT -> stringResource(R.string.theme_light)
                                AppTheme.DARK -> stringResource(R.string.theme_dark)
                            }
                            SegmentedButton(
                                selected = currentTheme == theme,
                                onClick = { themeViewModel.setTheme(theme) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = AppTheme.entries.size),
                                label = { Text(themeLabel) }
                            )
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Spacer(modifier = Modifier.height(16.dp))
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.dynamic_color), fontWeight = FontWeight.Medium) },
                            supportingContent = { Text(stringResource(R.string.dynamic_color_sub)) },
                            trailingContent = {
                                Switch(
                                    checked = dynamicColorEnabled,
                                    onCheckedChange = { themeViewModel.setDynamicColor(it) }
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }

            Text(stringResource(R.string.section_privacy), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.watch_history_setting), fontWeight = FontWeight.Medium) },
                    supportingContent = { Text(if (isHistoryPaused) stringResource(R.string.history_paused_sub) else stringResource(R.string.history_active_sub)) },
                    trailingContent = {
                        Switch(
                            checked = !isHistoryPaused,
                            onCheckedChange = { videoViewModel.toggleHistoryPause(!it) }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            Text(stringResource(R.string.section_security), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            
            // PIN Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.change_locker_pin), fontWeight = FontWeight.Medium) },
                    supportingContent = { Text(stringResource(R.string.change_pin_sub)) },
                    leadingContent = { 
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    trailingContent = {
                        Button(
                            onClick = {
                                if (settings == null) {
                                    Toast.makeText(context, setupPinFirstToast, Toast.LENGTH_SHORT).show()
                                } else {
                                    showSecurityVerification = true
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.action_change))
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
                    headlineContent = { Text(stringResource(R.string.fingerprint_unlock), fontWeight = FontWeight.Medium) },
                    supportingContent = { 
                        Text(if (isBiometricSupported) stringResource(R.string.biometric_supported_sub) else stringResource(R.string.biometric_unsupported_sub)) 
                    },
                    leadingContent = { 
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                                                Toast.makeText(context, biometricUpdatedToast, Toast.LENGTH_SHORT).show()
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
                Text(stringResource(R.string.section_storage), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.auto_locking), fontWeight = FontWeight.Medium) },
                        supportingContent = { 
                            Text(stringResource(R.string.media_mgmt_permission))
                        },
                        leadingContent = { 
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.AutoMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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

            Text(stringResource(R.string.section_about), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

            // About Developer Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_developer), fontWeight = FontWeight.Medium) },
                    supportingContent = { Text(stringResource(R.string.about_dev_sub)) },
                    leadingContent = { 
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                    headlineContent = { Text(stringResource(R.string.view_source_code), fontWeight = FontWeight.Medium) },
                    supportingContent = { Text(stringResource(R.string.view_source_sub)) },
                    leadingContent = { 
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                Text(stringResource(R.string.version_format, versionName ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                onConfirm = { newPin: String ->
                    scope.launch {
                        settings?.let {
                            dao.saveSettings(it.copy(pin = newPin))
                            Toast.makeText(context, pinUpdatedToast, Toast.LENGTH_SHORT).show()
                            showChangePinDialog = false
                        }
                    }
                }
            )
        }

        if (showAboutDeveloperDialog) {
            AlertDialog(
                onDismissRequest = { showAboutDeveloperDialog = false },
                title = { Text(stringResource(R.string.about_developer), fontWeight = FontWeight.Bold) },
                text = {
                    Text(stringResource(R.string.developer_info))
                },
                confirmButton = {
                    TextButton(onClick = { showAboutDeveloperDialog = false }) {
                        Text(stringResource(R.string.action_close))
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
        .setTitle(activity.getString(R.string.fingerprint_unlock))
        .setSubtitle(activity.getString(R.string.verify_identity))
        .setNegativeButtonText(activity.getString(R.string.action_cancel))
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
    val incorrectAnswerMessage = stringResource(R.string.incorrect_answer)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.verify_identity), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.security_question_prompt), style = MaterialTheme.typography.bodyMedium)
                Text(question, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(
                    value = answerInput,
                    onValueChange = { answerInput = it },
                    label = { Text(stringResource(R.string.security_answer_label)) },
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
                        error = incorrectAnswerMessage
                    }
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.action_verify))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
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
        title = { Text(stringResource(R.string.new_security_pin), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.enter_new_pin_prompt), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 4) newPin = it },
                    label = { Text(stringResource(R.string.pin_label)) },
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
                Text(stringResource(R.string.action_update))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

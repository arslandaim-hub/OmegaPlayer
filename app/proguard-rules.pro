# ====================================================================
# OmegaPlayer R8 / ProGuard Keep Rules
# ====================================================================

# --- Room Database Keep Rules ---
-keep class * extends androidx.room.RoomDatabase
-keep class com.arslandaim.omegaplayer.data.** { *; }
-keep interface com.arslandaim.omegaplayer.data.LockerDao { *; }

# --- Media3 & ExoPlayer Keep Rules ---
-keep class com.arslandaim.omegaplayer.service.PlaybackService { *; }
-keep class androidx.media3.session.MediaSessionService { *; }
-keep class androidx.media3.session.MediaSession { *; }
-keep class androidx.media3.exoplayer.ExoPlayer { *; }
-keep class androidx.media3.ui.PlayerView { *; }
-keepclassmembers class * extends androidx.media3.common.Player$Listener { *; }

# --- Hilt & Dagger Dependency Injection ---
-keep class * extends dagger.hilt.internal.UnsafeCasts
-keep class **_HiltModules* { *; }
-keep class com.arslandaim.omegaplayer.di.** { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
    @dagger.Provides *;
}

# --- Coil Image Loading & Video Decoder ---
-keep class coil.decode.VideoFrameDecoder { *; }
-keep class coil.decode.VideoFrameDecoder$Factory { *; }

# --- Biometric Prompt & Security ---
-keep class androidx.biometric.** { *; }
-keepclassmembers class * extends androidx.biometric.BiometricPrompt$AuthenticationCallback { *; }

# --- DataStore & Preferences ---
-keep class androidx.datastore.preferences.** { *; }

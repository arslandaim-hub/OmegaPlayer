package com.arslandaim.omegaplayer.ui.locker

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.arslandaim.omegaplayer.data.AudioModel
import com.arslandaim.omegaplayer.data.LockedVideo
import com.arslandaim.omegaplayer.data.LockerDao
import com.arslandaim.omegaplayer.data.VideoModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

import android.media.MediaScannerConnection
import android.net.Uri

sealed class MoveToLockerResult {
    object Success : MoveToLockerResult()
    data class RequiresUserConsent(val intentSender: IntentSender) : MoveToLockerResult()
    object Error : MoveToLockerResult()
}

suspend fun prepareMoveToLocker(
    context: Context,
    video: VideoModel
): MoveToLockerResult = prepareMoveToLockerInternal(context, video.uri, video.name)

suspend fun prepareAudioMoveToLocker(
    context: Context,
    audio: AudioModel
): MoveToLockerResult = prepareMoveToLockerInternal(context, audio.uri, audio.name)

private suspend fun prepareMoveToLockerInternal(
    context: Context,
    uri: Uri,
    name: String
): MoveToLockerResult = withContext(Dispatchers.IO) {
    val lockerDir = File(context.filesDir, "locker")
    if (!lockerDir.exists() && !lockerDir.mkdirs()) {
        Log.e("OmegaPlayer", "Failed to create locker directory")
        return@withContext MoveToLockerResult.Error
    }
    
    val destFile = File(lockerDir, name)

    try {
        // 1. Copy to locker with verification
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        } ?: return@withContext MoveToLockerResult.Error

        // Verify copy exists and has size
        if (!destFile.exists() || destFile.length() == 0L) {
            Log.e("OmegaPlayer", "Copy failed or file is empty")
            return@withContext MoveToLockerResult.Error
        }

        // 2. Try delete original
        try {
            val deleted = context.contentResolver.delete(uri, null, null)
            if (deleted > 0) {
                return@withContext MoveToLockerResult.Success
            } else {
                Log.w("OmegaPlayer", "Delete returned 0, might need consent or file already gone")
            }
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intentSender = MediaStore.createDeleteRequest(context.contentResolver, listOf(uri)).intentSender
                return@withContext MoveToLockerResult.RequiresUserConsent(intentSender)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                return@withContext MoveToLockerResult.RequiresUserConsent(e.userAction.actionIntent.intentSender)
            }
            Log.e("OmegaPlayer", "Failed to delete source", e)
        }
    } catch (e: Exception) {
        Log.e("OmegaPlayer", "Error preparing move to locker", e)
        if (destFile.exists()) destFile.delete()
    }
    return@withContext MoveToLockerResult.Error
}

suspend fun unlockVideo(
    context: Context,
    video: LockedVideo,
    dao: LockerDao
): Boolean = withContext(Dispatchers.IO) {
    val lockerFile = File(video.lockerPath)
    if (!lockerFile.exists()) {
        Log.e("OmegaPlayer", "Locker file not found: ${video.lockerPath}")
        return@withContext false
    }

    try {
        val resolver = context.contentResolver
        
        val mediaCollection = if (video.isAudio) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
        }

        // Use origin folder name if available, otherwise default to OmegaPlayer
        val folderName = video.originFolderName ?: "OmegaPlayer"
        val baseDir = if (video.isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
        val targetDir = File(Environment.getExternalStoragePublicDirectory(baseDir), folderName)
        if (!targetDir.exists()) targetDir.mkdirs()
        
        var targetFile = File(targetDir, video.name)
        
        // If file exists, we need to handle it (e.g., rename or delete)
        if (targetFile.exists()) {
            val nameWithoutExt = video.name.substringBeforeLast(".")
            val ext = video.name.substringAfterLast(".", "")
            val suffix = if (ext.isNotEmpty()) ".$ext" else ""
            targetFile = File(targetDir, "${nameWithoutExt}_${System.currentTimeMillis()}$suffix")
        }

        val mediaDetails = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, targetFile.name)
            put(MediaStore.MediaColumns.MIME_TYPE, if (video.isAudio) "audio/mpeg" else "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, baseDir + "/" + folderName)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val contentUri = resolver.insert(mediaCollection, mediaDetails) ?: return@withContext false

        resolver.openOutputStream(contentUri)?.use { outputStream ->
            FileInputStream(lockerFile).use { inputStream ->
                inputStream.copyTo(outputStream)
            }
        } ?: return@withContext false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mediaDetails.clear()
            mediaDetails.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(contentUri, mediaDetails, null, null)
        }

        // 3. Scan the new file so it shows up in Home/Gallery
        MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), null) { path, uri ->
            Log.d("OmegaPlayer", "Scanned unlocked media: $uri at $path")
        }

        // 4. Final Cleanup
        if (lockerFile.delete()) {
            dao.deleteLockedVideo(video)
            return@withContext true
        } else {
            Log.e("OmegaPlayer", "Failed to delete locker file after unlock")
        }
    } catch (e: Exception) {
        Log.e("OmegaPlayer", "Error unlocking media", e)
    }
    return@withContext false
}

/**
 * Rename a folder by updating RELATIVE_PATH for each video file within it (Android 10+)
 * or renaming the physical directory (Legacy).
 */
/**
 * Prepare to move multiple videos to locker.
 * Returns Success if all moved, RequiresUserConsent if some need bulk delete permission, or Error.
 */
suspend fun bulkPrepareMoveToLocker(
    context: Context,
    videos: List<VideoModel>
): MoveToLockerResult = bulkPrepareMoveToLockerInternal(context, videos.map { it.uri to it.name })

suspend fun bulkPrepareAudioMoveToLocker(
    context: Context,
    audios: List<AudioModel>
): MoveToLockerResult = bulkPrepareMoveToLockerInternal(context, audios.map { it.uri to it.name })

private suspend fun bulkPrepareMoveToLockerInternal(
    context: Context,
    mediaItems: List<Pair<Uri, String>>
): MoveToLockerResult = withContext(Dispatchers.IO) {
    val lockerDir = File(context.filesDir, "locker")
    if (!lockerDir.exists() && !lockerDir.mkdirs()) return@withContext MoveToLockerResult.Error

    val needsConsent = mutableListOf<Uri>()
    var successCount = 0

    mediaItems.forEach { (uri, name) ->
        val destFile = File(lockerDir, name)
        try {
            // Copy
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            }
            
            // Try Delete
            try {
                val deleted = context.contentResolver.delete(uri, null, null)
                if (deleted > 0) {
                    successCount++
                } else {
                    needsConsent.add(uri)
                }
            } catch (e: Exception) {
                needsConsent.add(uri)
            }
        } catch (e: Exception) {
            Log.e("OmegaPlayer", "Failed to copy $name", e)
        }
    }

    if (needsConsent.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val intentSender = MediaStore.createDeleteRequest(context.contentResolver, needsConsent).intentSender
        return@withContext MoveToLockerResult.RequiresUserConsent(intentSender)
    }

    if (successCount == mediaItems.size) return@withContext MoveToLockerResult.Success
    if (successCount > 0 || needsConsent.isNotEmpty()) return@withContext MoveToLockerResult.Success // Partial success treated as success for now
    
    return@withContext MoveToLockerResult.Error
}

suspend fun renameFolder(
    context: Context,
    oldName: String,
    newName: String,
    videos: List<VideoModel>
): Boolean = withContext(Dispatchers.IO) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            var successCount = 0
            videos.forEach { video ->
                val values = ContentValues().apply {
                    val relativePath = Environment.DIRECTORY_MOVIES + "/" + newName
                    put(MediaStore.Video.Media.RELATIVE_PATH, relativePath)
                }
                val updated = context.contentResolver.update(video.uri, values, null, null)
                if (updated > 0) successCount++
            }
            return@withContext successCount > 0
        } catch (e: Exception) {
            Log.e("OmegaPlayer", "Failed to rename folder via MediaStore", e)
            return@withContext false
        }
    } else {
        try {
            val firstVideo = videos.firstOrNull() ?: return@withContext false
            val folderFile = File(firstVideo.path).parentFile ?: return@withContext false
            val newFolderFile = File(folderFile.parentFile, newName)
            if (folderFile.renameTo(newFolderFile)) {
                MediaScannerConnection.scanFile(
                    context, 
                    newFolderFile.listFiles()?.map { it.absolutePath }?.toTypedArray(), 
                    null, null
                )
                return@withContext true
            }
        } catch (e: Exception) {
            Log.e("OmegaPlayer", "Legacy rename failed", e)
        }
    }
    return@withContext false
}

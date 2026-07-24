/*
 * OmegaPlayer Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/

package com.arslandaim.omegaplayer.viewmodel

import android.app.Application
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arslandaim.omegaplayer.data.AudioModel
import com.arslandaim.omegaplayer.data.VideoModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

data class StorageStats(
    val totalBytes: Long = 0L,
    val freeBytes: Long = 0L,
    val videoBytes: Long = 0L,
    val audioBytes: Long = 0L,
    val otherBytes: Long = 0L,
) {
    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
        return String.format(Locale.getDefault(), "%.1f %s", bytes / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
    }
}

class StorageViewModel(application: Application) : AndroidViewModel(application) {

    private val _storageStats = MutableStateFlow(StorageStats())
    val storageStats: StateFlow<StorageStats> = _storageStats.asStateFlow()

    fun updateStorageStats(videos: List<VideoModel>, audios: List<AudioModel>) {
        viewModelScope.launch {
            val stats = withContext(Dispatchers.IO) {
                val path = Environment.getDataDirectory()
                val stat = StatFs(path.path)
                val blockSize = stat.blockSizeLong
                val totalBlocks = stat.blockCountLong
                val availableBlocks = stat.availableBlocksLong

                val total = totalBlocks * blockSize
                val free = availableBlocks * blockSize
                
                val videoSize = videos.sumOf { it.size }
                val audioSize = audios.sumOf { it.size }
                
                val used = total - free
                val other = (used - videoSize - audioSize).coerceAtLeast(0L)

                StorageStats(
                    totalBytes = total,
                    freeBytes = free,
                    videoBytes = videoSize,
                    audioBytes = audioSize,
                    otherBytes = other
                )
            }
            _storageStats.value = stats
        }
    }
}

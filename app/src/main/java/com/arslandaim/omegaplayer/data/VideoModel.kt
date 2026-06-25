package com.arslandaim.omegaplayer.data

import android.net.Uri

data class VideoModel(
    val id: Long,
    val uri: Uri,
    val name: String,
    val duration: Long,
    val size: Long,
    val path: String
)

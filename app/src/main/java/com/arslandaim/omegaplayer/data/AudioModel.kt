/*
 * OmegaPlayer Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/

package com.arslandaim.omegaplayer.data

import android.net.Uri

data class AudioModel(
    val id: Long,
    val albumId: Long,
    val uri: Uri,
    val name: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val size: Long,
    val path: String
)

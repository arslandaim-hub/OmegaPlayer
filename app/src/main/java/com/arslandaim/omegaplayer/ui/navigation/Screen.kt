/*
 * OmegaPlayer Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/

package com.arslandaim.omegaplayer.ui.navigation

sealed class Screen(val route: String) {
    object Main : Screen("main?tab={tab}") {
        fun createRoute(tab: String?) = if (tab != null) "main?tab=$tab" else "main"
    }
    object Home : Screen("home")
    object Player : Screen("player/{videoUri}?from={from}") {
        fun createRoute(videoUri: String, from: String? = null) = 
            "player/$videoUri" + (if (from != null) "?from=$from" else "")
    }
    object AudioPlayer : Screen("audio_player/{audioUri}") {
        fun createRoute(audioUri: String) = "audio_player/$audioUri"
    }
    object Locker : Screen("locker")
    object Settings : Screen("settings")
}

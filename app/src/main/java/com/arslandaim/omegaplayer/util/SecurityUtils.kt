package com.arslandaim.omegaplayer.util

import java.security.MessageDigest

object SecurityUtils {
    fun hashString(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

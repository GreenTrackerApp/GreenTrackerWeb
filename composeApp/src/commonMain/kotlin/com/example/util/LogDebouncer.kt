package com.example.util

import kotlinx.datetime.Clock

object LogDebouncer {
    private var lastLogTime = 0L
    private const val COOLDOWN_MS = 2000L

    /**
     * Checks if a new log is allowed (2-second cooldown) and updates the timestamp if allowed.
     */
    fun canLog(): Boolean {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        return if (currentTime - lastLogTime >= COOLDOWN_MS) {
            lastLogTime = currentTime
            true
        } else {
            false
        }
    }
}

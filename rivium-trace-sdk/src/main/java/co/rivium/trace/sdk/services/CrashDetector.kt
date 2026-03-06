package co.rivium.trace.sdk.services

import android.content.Context
import co.rivium.trace.sdk.utils.RiviumTraceLogger
import java.io.File

/**
 * Crash detection using two-file marker approach
 *
 * How it works:
 * 1. On app start: Check if "clean exit" marker exists from last session
 * 2. If session file exists but NO clean exit marker → Previous session crashed
 * 3. When app goes to background → Create clean exit marker
 * 4. When app returns to foreground → Remove clean exit marker
 * 5. If app crashes while in foreground, no clean exit marker exists → crash detected on next launch
 *
 * This prevents false crash detection when user swipes the app away normally.
 */
class CrashDetector(private val context: Context) {

    companion object {
        private const val SESSION_FILENAME = "rivium_trace_session.txt"
        private const val CLEAN_EXIT_FILENAME = "rivium_trace_clean_exit.txt"
        private const val MAX_CRASH_AGE_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    private val sessionFile: File
        get() = File(context.filesDir, SESSION_FILENAME)

    private val cleanExitFile: File
        get() = File(context.filesDir, CLEAN_EXIT_FILENAME)

    /**
     * Data class for crash info
     */
    data class CrashInfo(
        val timestamp: Long,
        val sessionId: String?,
        val lastActivity: String?,
        val timeSinceCrashSeconds: Long
    )

    /**
     * Check if a crash occurred in a previous session
     * Crash = session file exists but clean exit file does NOT exist
     * @return CrashInfo if crash detected, null otherwise
     */
    fun checkForCrash(): CrashInfo? {
        val session = sessionFile
        val cleanExit = cleanExitFile

        val hadSession = session.exists()
        val hadCleanExit = cleanExit.exists()

        RiviumTraceLogger.debug("Crash check - hadSession: $hadSession, hadCleanExit: $hadCleanExit")

        // Clean up the clean exit marker first
        if (cleanExit.exists()) {
            try {
                cleanExit.delete()
            } catch (e: Exception) {
                RiviumTraceLogger.error("Failed to delete clean exit marker: ${e.message}")
            }
        }

        // No crash if: no session, or had clean exit
        if (!hadSession || hadCleanExit) {
            RiviumTraceLogger.debug("No crash detected")
            // Clean up session file if it exists
            if (session.exists()) {
                try {
                    session.delete()
                } catch (e: Exception) {
                    RiviumTraceLogger.error("Failed to delete session file: ${e.message}")
                }
            }
            return null
        }

        // Had session but no clean exit = crash
        try {
            val content = session.readText()
            val lines = content.split("\n")
            val timestamp = lines.getOrNull(0)?.toLongOrNull() ?: return null
            val sessionId = lines.getOrNull(1)?.takeIf { it.isNotBlank() }
            val lastActivity = lines.getOrNull(2)?.takeIf { it.isNotBlank() }

            // Check if crash marker is too old
            val currentTime = System.currentTimeMillis()
            if (currentTime - timestamp > MAX_CRASH_AGE_MS) {
                RiviumTraceLogger.debug("Session too old, ignoring")
                session.delete()
                return null
            }

            val timeSinceSeconds = (currentTime - timestamp) / 1000

            RiviumTraceLogger.info("Crash detected from previous session (${timeSinceSeconds}s ago)")

            // Delete the session file after reading
            session.delete()

            return CrashInfo(
                timestamp = timestamp,
                sessionId = sessionId,
                lastActivity = lastActivity,
                timeSinceCrashSeconds = timeSinceSeconds
            )
        } catch (e: Exception) {
            RiviumTraceLogger.error("Failed to read session file: ${e.message}")
            try { session.delete() } catch (e2: Exception) {}
            return null
        }
    }

    /**
     * Create a session marker file
     * Call this at app start to indicate a new session has begun
     */
    fun createMarker(sessionId: String? = null) {
        try {
            val content = buildString {
                append(System.currentTimeMillis())
                append("\n")
                append(sessionId ?: "")
                append("\n")
                append("") // Placeholder for last activity
            }
            sessionFile.writeText(content)

            // Remove any existing clean exit marker (we're now running)
            if (cleanExitFile.exists()) {
                cleanExitFile.delete()
            }

            RiviumTraceLogger.debug("Session marker created")
        } catch (e: Exception) {
            RiviumTraceLogger.error("Failed to create session marker: ${e.message}")
        }
    }

    /**
     * Update the session marker with latest activity info
     */
    fun updateLastActivity(activityName: String) {
        try {
            val file = sessionFile
            if (!file.exists()) return

            val content = file.readText()
            val lines = content.split("\n").toMutableList()
            while (lines.size < 3) lines.add("")
            lines[2] = activityName

            file.writeText(lines.joinToString("\n"))
        } catch (e: Exception) {
            RiviumTraceLogger.error("Failed to update session marker: ${e.message}")
        }
    }

    /**
     * Mark that the app is going to background (clean exit)
     * Call this when app enters background/paused state
     */
    fun markCleanExit() {
        try {
            cleanExitFile.writeText(System.currentTimeMillis().toString())
            RiviumTraceLogger.debug("Clean exit marker created (app going to background)")
        } catch (e: Exception) {
            RiviumTraceLogger.error("Failed to create clean exit marker: ${e.message}")
        }
    }

    /**
     * Remove clean exit marker when app comes back to foreground
     * Call this when app enters foreground/resumed state
     */
    fun clearCleanExit() {
        try {
            if (cleanExitFile.exists()) {
                cleanExitFile.delete()
                RiviumTraceLogger.debug("Clean exit marker removed (app in foreground)")
            }
        } catch (e: Exception) {
            RiviumTraceLogger.error("Failed to remove clean exit marker: ${e.message}")
        }
    }

    /**
     * Delete all markers (call on explicit close() for complete cleanup)
     */
    fun deleteMarker() {
        try {
            if (sessionFile.exists()) {
                sessionFile.delete()
            }
            if (cleanExitFile.exists()) {
                cleanExitFile.delete()
            }
            RiviumTraceLogger.debug("All markers deleted (graceful shutdown)")
        } catch (e: Exception) {
            RiviumTraceLogger.error("Failed to delete markers: ${e.message}")
        }
    }

    /**
     * Check if session marker exists
     */
    fun hasMarker(): Boolean = sessionFile.exists()
}

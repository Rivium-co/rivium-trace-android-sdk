package co.rivium.trace.sdk.services

import android.content.Context
import co.rivium.trace.sdk.utils.RiviumTraceLogger
import java.io.File

/**
 * Signal crash handler for native crashes (JNI/NDK)
 *
 * This class provides native crash detection via file marker approach.
 * For true signal handling (SIGSEGV, SIGABRT, etc.), you would need
 * a native (C/C++) component. This implementation provides detection
 * of crashes that occurred in the previous session.
 *
 * Works in conjunction with CrashDetector to detect all crash types:
 * - Java/Kotlin exceptions (handled by UncaughtExceptionHandler)
 * - Native crashes (detected via file marker)
 * - ANRs (handled by ANRWatchdogService)
 */
class SignalCrashHandler(private val context: Context) {

    companion object {
        private const val SIGNAL_MARKER_FILENAME = "rivium_trace_signal_marker.txt"
        private val SIGNAL_NAMES = mapOf(
            1 to "SIGHUP",
            2 to "SIGINT",
            3 to "SIGQUIT",
            4 to "SIGILL",
            5 to "SIGTRAP",
            6 to "SIGABRT",
            7 to "SIGBUS",
            8 to "SIGFPE",
            9 to "SIGKILL",
            10 to "SIGUSR1",
            11 to "SIGSEGV",
            12 to "SIGUSR2",
            13 to "SIGPIPE",
            14 to "SIGALRM",
            15 to "SIGTERM"
        )

        @Volatile
        private var instance: SignalCrashHandler? = null

        fun getInstance(context: Context): SignalCrashHandler {
            return instance ?: synchronized(this) {
                instance ?: SignalCrashHandler(context.applicationContext).also { instance = it }
            }
        }

        /**
         * Get signal name from signal number
         */
        fun getSignalName(signal: Int): String {
            return SIGNAL_NAMES[signal] ?: "SIGNAL($signal)"
        }
    }

    private val signalMarkerFile: File
        get() = File(context.filesDir, SIGNAL_MARKER_FILENAME)

    private var isInstalled = false

    /**
     * Data class for signal crash info
     */
    data class SignalCrashInfo(
        val signal: Int,
        val signalName: String,
        val timestamp: Long,
        val nativeStackTrace: String?
    )

    /**
     * Install signal crash detection
     * Note: Full signal handling requires native (JNI) code.
     * This implementation uses the crash marker approach.
     */
    fun install() {
        if (isInstalled) {
            RiviumTraceLogger.debug("Signal crash handler already installed")
            return
        }

        // Mark as installed
        isInstalled = true
        RiviumTraceLogger.debug("Signal crash handler installed (marker-based detection)")

        // Note: For true signal handling, you would need to:
        // 1. Include a native library (e.g., Breakpad, Crashpad)
        // 2. Register signal handlers in native code
        // 3. Capture signal info and stack traces
        //
        // This is beyond the scope of a pure Kotlin implementation
        // but the infrastructure is here for when native support is added.
    }

    /**
     * Uninstall signal crash detection
     */
    fun uninstall() {
        if (!isInstalled) return

        isInstalled = false
        RiviumTraceLogger.debug("Signal crash handler uninstalled")
    }

    /**
     * Create a signal marker (called when signal is caught)
     * This would be called from native code when a signal is caught
     */
    fun createSignalMarker(signal: Int, stackTrace: String? = null) {
        try {
            val content = buildString {
                append(signal)
                append("\n")
                append(System.currentTimeMillis())
                append("\n")
                append(stackTrace ?: "")
            }
            signalMarkerFile.writeText(content)
            RiviumTraceLogger.debug("Signal marker created for ${getSignalName(signal)}")
        } catch (e: Exception) {
            RiviumTraceLogger.error("Failed to create signal marker: ${e.message}")
        }
    }

    /**
     * Check for signal crash from previous session
     */
    fun checkForSignalCrash(): SignalCrashInfo? {
        val file = signalMarkerFile

        if (!file.exists()) {
            return null
        }

        try {
            val content = file.readText()
            val lines = content.split("\n")

            val signal = lines.getOrNull(0)?.toIntOrNull() ?: return null
            val timestamp = lines.getOrNull(1)?.toLongOrNull() ?: System.currentTimeMillis()
            val stackTrace = lines.drop(2).joinToString("\n").takeIf { it.isNotBlank() }

            // Delete marker after reading
            file.delete()

            RiviumTraceLogger.info("Signal crash detected: ${getSignalName(signal)}")

            return SignalCrashInfo(
                signal = signal,
                signalName = getSignalName(signal),
                timestamp = timestamp,
                nativeStackTrace = stackTrace
            )
        } catch (e: Exception) {
            RiviumTraceLogger.error("Failed to read signal marker: ${e.message}")
            file.delete()
            return null
        }
    }

    /**
     * Delete signal marker (graceful shutdown)
     */
    fun deleteMarker() {
        try {
            if (signalMarkerFile.exists()) {
                signalMarkerFile.delete()
                RiviumTraceLogger.debug("Signal marker deleted")
            }
        } catch (e: Exception) {
            RiviumTraceLogger.error("Failed to delete signal marker: ${e.message}")
        }
    }

    /**
     * Check if signal crash handler is installed
     */
    fun isInstalled(): Boolean = isInstalled
}

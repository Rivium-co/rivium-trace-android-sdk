package co.rivium.trace.sdk.services

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.core.content.edit
import co.rivium.trace.sdk.models.MessageLevel
import co.rivium.trace.sdk.models.RiviumTraceError
import co.rivium.trace.sdk.utils.DeviceInfo
import co.rivium.trace.sdk.utils.RiviumTraceLogger
import java.io.ByteArrayOutputStream

/**
 * Native crash reporting backed by Android's [ApplicationExitInfo] (API 30+).
 *
 * On each launch this class queries [ActivityManager.getHistoricalProcessExitReasons]
 * and converts any crash/ANR/native-crash exit records since the previous
 * launch into [RiviumTraceError] payloads. The timestamp of the most recent
 * processed record is persisted in [SharedPreferences] so the same crash is
 * not reported twice.
 *
 * Why this approach:
 * - On API 30+ the OS records exit reasons (REASON_CRASH, REASON_CRASH_NATIVE,
 *   REASON_ANR) with stack traces collected by the platform itself. This is
 *   strictly more reliable than any in-process signal handler an app could
 *   install, because it survives the very signals that terminate the process.
 * - JVM/Kotlin crashes are also captured by [RiviumTrace]'s in-process
 *   `Thread.setDefaultUncaughtExceptionHandler` for immediate (same-session)
 *   delivery. This class is the cold-start safety net.
 *
 * On API 29 and below, the historical API is unavailable and this class
 * silently does nothing. JVM crashes are still captured by the in-process
 * uncaught handler.
 */
internal class NativeCrashReporter(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun drainPendingCrashReports(
        environment: String,
        releaseVersion: String?,
        userAgent: String?
    ): List<RiviumTraceError> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            RiviumTraceLogger.debug("ApplicationExitInfo unavailable on API ${Build.VERSION.SDK_INT}")
            return emptyList()
        }

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return emptyList()

        val sinceTimestamp = prefs.getLong(KEY_LAST_PROCESSED_TIMESTAMP, 0L)
        val infos: List<ApplicationExitInfo> = try {
            am.getHistoricalProcessExitReasons(context.packageName, 0, MAX_RECORDS_TO_FETCH)
        } catch (e: Exception) {
            RiviumTraceLogger.error("getHistoricalProcessExitReasons failed: ${e.message}")
            return emptyList()
        }

        val errors = mutableListOf<RiviumTraceError>()
        var newestSeenTimestamp = sinceTimestamp

        for (info in infos) {
            if (info.timestamp <= sinceTimestamp) continue
            if (info.timestamp > newestSeenTimestamp) newestSeenTimestamp = info.timestamp

            val reasonKey = exitReasonKey(info.reason) ?: continue
            errors += buildError(info, reasonKey, environment, releaseVersion, userAgent)
        }

        if (newestSeenTimestamp > sinceTimestamp) {
            prefs.edit { putLong(KEY_LAST_PROCESSED_TIMESTAMP, newestSeenTimestamp) }
        }

        return errors
    }

    private fun exitReasonKey(reason: Int): String? = when (reason) {
        ApplicationExitInfo.REASON_CRASH -> "crash_jvm"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "crash_native"
        ApplicationExitInfo.REASON_ANR -> "anr"
        else -> null
    }

    private fun buildError(
        info: ApplicationExitInfo,
        reasonKey: String,
        environment: String,
        releaseVersion: String?,
        userAgent: String?
    ): RiviumTraceError {
        // ApplicationExitInfo#getTraceInputStream returns the tombstone or
        // ANR trace the platform captured at exit time. It is the closest
        // thing to a real stack trace available without an in-process
        // crash handler that survived the signal.
        val traceText = readTraceText(info)

        val description = info.description ?: ""
        val processName = info.processName
        val reasonName = reasonHumanName(info.reason)

        val extra = mutableMapOf<String, Any?>(
            "error_type" to "native_crash",
            "exit_reason" to reasonKey,
            "exit_reason_code" to info.reason,
            "exit_reason_name" to reasonName,
            "exit_description" to description,
            "process_name" to processName,
            "pid" to info.pid,
            "importance" to info.importance,
            "rss_kb" to info.rss,
            "pss_kb" to info.pss,
            "crash_reporter" to "application_exit_info",
            "device_info" to DeviceInfo.getDeviceInfo()
        )

        val message = "Native crash: $reasonName${if (description.isNotBlank()) " ($description)" else ""}"

        return RiviumTraceError(
            message = message,
            stackTrace = traceText.ifBlank { "No tombstone trace available from ApplicationExitInfo." },
            environment = environment,
            releaseVersion = releaseVersion,
            timestamp = info.timestamp,
            userAgent = userAgent,
            extra = extra,
            level = MessageLevel.FATAL.value
        )
    }

    private fun readTraceText(info: ApplicationExitInfo): String {
        val stream = try {
            info.traceInputStream
        } catch (e: Exception) {
            RiviumTraceLogger.debug("traceInputStream threw: ${e.message}")
            null
        } ?: return ""

        return try {
            stream.use {
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8 * 1024)
                while (true) {
                    val n = it.read(buf)
                    if (n <= 0) break
                    if (out.size() + n > MAX_TRACE_BYTES) {
                        out.write(buf, 0, MAX_TRACE_BYTES - out.size())
                        out.write("\n... (truncated)".toByteArray())
                        break
                    }
                    out.write(buf, 0, n)
                }
                out.toString(Charsets.UTF_8.name())
            }
        } catch (e: Exception) {
            RiviumTraceLogger.debug("Reading trace stream failed: ${e.message}")
            ""
        }
    }

    private fun reasonHumanName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_CRASH -> "JVM crash"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "Native crash"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "Low memory"
        ApplicationExitInfo.REASON_SIGNALED -> "Signaled"
        ApplicationExitInfo.REASON_UNKNOWN -> "Unknown"
        else -> "Reason($reason)"
    }

    companion object {
        private const val PREFS_NAME = "rivium_trace_native_crash"
        private const val KEY_LAST_PROCESSED_TIMESTAMP = "last_processed_timestamp"
        private const val MAX_RECORDS_TO_FETCH = 20
        private const val MAX_TRACE_BYTES = 64 * 1024
    }
}

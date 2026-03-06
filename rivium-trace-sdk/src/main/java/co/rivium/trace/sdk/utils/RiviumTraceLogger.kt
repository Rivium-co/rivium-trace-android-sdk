package co.rivium.trace.sdk.utils

import android.util.Log

/**
 * Internal logger for RiviumTrace SDK
 */
object RiviumTraceLogger {
    private const val TAG = "RiviumTrace"

    @Volatile
    var isDebugEnabled: Boolean = false

    fun debug(message: String) {
        if (isDebugEnabled) {
            Log.d(TAG, message)
        }
    }

    fun info(message: String) {
        if (isDebugEnabled) {
            Log.i(TAG, message)
        }
    }

    fun warn(message: String) {
        Log.w(TAG, message)
    }

    fun error(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, message, throwable)
        } else {
            Log.e(TAG, message)
        }
    }
}

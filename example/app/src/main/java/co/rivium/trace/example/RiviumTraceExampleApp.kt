package co.rivium.trace.example

import android.app.Application
import co.rivium.trace.sdk.RiviumTrace
import co.rivium.trace.sdk.RiviumTraceConfig

class RiviumTraceExampleApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize RiviumTrace SDK
        val config = RiviumTraceConfig.Builder("rv_live_df66936060af29df2bf5212e7c7ab38d62289ac1cf1e6f79")
            .environment(if (BuildConfig.DEBUG) "development" else "production")
            .release(BuildConfig.VERSION_NAME)
            .debug(BuildConfig.DEBUG)
            .captureUncaughtExceptions(true)
            .captureAnr(true)
            .anrTimeoutMs(5000)
            .maxBreadcrumbs(30)
            // Sample rate: 1.0 = capture 100% of errors
            // Set to 0.5 for 50%, 0.25 for 25%, etc.
            .sampleRate(1.0f)
            // Offline storage: store errors when offline, retry on reconnect
            .enableOfflineStorage(true)
            .build()

        RiviumTrace.init(this, config)

        // Optionally set user ID if known at startup
        // RiviumTrace.setUserId("user-123")

        // Enable logging
        RiviumTrace.enableLogging(
            sourceId = "android-demo-app",
            sourceName = "Android Demo App"
        )
    }

    override fun onTerminate() {
        // Close SDK gracefully
        RiviumTrace.close()
        super.onTerminate()
    }
}

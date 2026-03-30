package co.rivium.trace.example

import android.app.Application
import co.rivium.trace.sdk.RiviumTrace
import co.rivium.trace.sdk.RiviumTraceConfig

class RiviumTraceExampleApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize RiviumTrace SDK
        val config = RiviumTraceConfig.Builder("rv_live_d15ea4bd5e2c7a4c9e55576433c0e78aaed005a0036d89ac")
            .environment(if (BuildConfig.DEBUG) "development" else "production")
            .apiUrl("http://192.168.224.147:3001")
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

package co.rivium.trace.sdk

/**
 * Configuration for RiviumTrace SDK
 *
 * @property apiKey The API Key from Rivium Console (rv_live_xxx or rv_test_xxx)
 * @property environment Environment name (e.g., "production", "staging", "development")
 * @property release Release/version string of your application
 * @property debug Enable debug logging
 * @property enabled Enable/disable the SDK entirely
 * @property captureUncaughtExceptions Automatically capture uncaught exceptions
 * @property captureSignalCrashes Capture signal crashes (native crashes via marker detection)
 * @property captureAnr Capture Application Not Responding (ANR) events
 * @property anrTimeoutMs ANR detection timeout in milliseconds (default: 5000ms)
 * @property maxBreadcrumbs Maximum number of breadcrumbs to store (default: 20)
 * @property httpTimeout HTTP request timeout in seconds
 * @property enableOfflineStorage Cache errors when offline for later sending
 * @property sampleRate Sample rate for error capture (0.0 to 1.0)
 */
data class RiviumTraceConfig(
    val apiKey: String,
    val environment: String = "production",
    val release: String? = null,
    val debug: Boolean = false,
    val enabled: Boolean = true,
    val captureUncaughtExceptions: Boolean = true,
    val captureSignalCrashes: Boolean = true,
    val captureAnr: Boolean = true,
    val anrTimeoutMs: Long = 5000L,
    val maxBreadcrumbs: Int = 20,
    val httpTimeout: Int = 30,
    val enableOfflineStorage: Boolean = true,
    val sampleRate: Float = 1.0f
) {
    init {
        require(apiKey.isNotBlank()) { "API key cannot be empty" }
        require(apiKey.startsWith("rv_live_") || apiKey.startsWith("rv_test_") || apiKey.startsWith("nl_live_") || apiKey.startsWith("nl_test_")) { "API key must start with rv_live_ or rv_test_" }
        require(maxBreadcrumbs > 0) { "maxBreadcrumbs must be positive" }
        require(httpTimeout > 0) { "httpTimeout must be positive" }
        require(sampleRate in 0.0f..1.0f) { "sampleRate must be between 0.0 and 1.0" }
    }

    /**
     * Builder pattern for creating RiviumTraceConfig
     */
    class Builder(private val apiKey: String) {
        private var environment: String = "production"
        private var release: String? = null
        private var debug: Boolean = false
        private var enabled: Boolean = true
        private var captureUncaughtExceptions: Boolean = true
        private var captureSignalCrashes: Boolean = true
        private var captureAnr: Boolean = true
        private var anrTimeoutMs: Long = 5000L
        private var maxBreadcrumbs: Int = 20
        private var httpTimeout: Int = 30
        private var enableOfflineStorage: Boolean = true
        private var sampleRate: Float = 1.0f

        fun environment(environment: String) = apply { this.environment = environment }
        fun release(release: String?) = apply { this.release = release }
        fun debug(debug: Boolean) = apply { this.debug = debug }
        fun enabled(enabled: Boolean) = apply { this.enabled = enabled }
        fun captureUncaughtExceptions(capture: Boolean) = apply { this.captureUncaughtExceptions = capture }
        fun captureSignalCrashes(capture: Boolean) = apply { this.captureSignalCrashes = capture }
        fun captureAnr(capture: Boolean) = apply { this.captureAnr = capture }
        fun anrTimeoutMs(timeout: Long) = apply { this.anrTimeoutMs = timeout }
        fun maxBreadcrumbs(max: Int) = apply { this.maxBreadcrumbs = max }
        fun httpTimeout(timeout: Int) = apply { this.httpTimeout = timeout }
        fun enableOfflineStorage(enable: Boolean) = apply { this.enableOfflineStorage = enable }
        fun sampleRate(rate: Float) = apply { this.sampleRate = rate }

        fun build(): RiviumTraceConfig = RiviumTraceConfig(
            apiKey = apiKey,
            environment = environment,
            release = release,
            debug = debug,
            enabled = enabled,
            captureUncaughtExceptions = captureUncaughtExceptions,
            captureSignalCrashes = captureSignalCrashes,
            captureAnr = captureAnr,
            anrTimeoutMs = anrTimeoutMs,
            maxBreadcrumbs = maxBreadcrumbs,
            httpTimeout = httpTimeout,
            enableOfflineStorage = enableOfflineStorage,
            sampleRate = sampleRate
        )
    }

    companion object {
        /**
         * Create a simple config with just API key
         */
        fun simple(apiKey: String): RiviumTraceConfig = RiviumTraceConfig(apiKey = apiKey)
    }
}

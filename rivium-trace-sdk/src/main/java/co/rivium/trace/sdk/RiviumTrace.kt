package co.rivium.trace.sdk

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import co.rivium.trace.sdk.models.*
import co.rivium.trace.sdk.network.RiviumTraceClient
import co.rivium.trace.sdk.services.*
import co.rivium.trace.sdk.utils.DeviceInfo
import co.rivium.trace.sdk.utils.RiviumTraceLogger
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * RiviumTrace Android SDK
 *
 * Error tracking SDK for Android applications.
 * Supports API 16+ (Android 4.1 Jelly Bean and above).
 *
 * Usage:
 * ```kotlin
 * // In Application.onCreate()
 * RiviumTrace.init(this, RiviumTraceConfig.Builder("your-api-key")
 *     .environment("production")
 *     .debug(BuildConfig.DEBUG)
 *     .build())
 *
 * // Capture exceptions
 * RiviumTrace.captureException(exception)
 *
 * // Capture messages
 * RiviumTrace.captureMessage("User completed onboarding", MessageLevel.INFO)
 *
 * // Add breadcrumbs
 * RiviumTrace.addBreadcrumb("Button clicked", BreadcrumbType.USER)
 * ```
 */
object RiviumTrace {

    private var config: RiviumTraceConfig? = null
    private var client: RiviumTraceClient? = null
    private var context: Context? = null
    private var crashDetector: CrashDetector? = null
    private var anrWatchdog: ANRWatchdogService? = null

    private val isInitialized = AtomicBoolean(false)
    private var originalExceptionHandler: Thread.UncaughtExceptionHandler? = null
    private var sessionId: String = UUID.randomUUID().toString()

    @Volatile
    private var userId: String? = null

    @Volatile
    private var userAgent: String? = null

    @Volatile
    private var currentActivityName: String? = null

    private val extraContext = mutableMapOf<String, Any?>()
    private val tags = mutableMapOf<String, String>()

    private var logService: LogService? = null

    // ==================== INITIALIZATION ====================

    /**
     * Initialize RiviumTrace SDK
     *
     * @param context Application context
     * @param config SDK configuration
     */
    @JvmStatic
    fun init(context: Context, config: RiviumTraceConfig) {
        if (isInitialized.getAndSet(true)) {
            RiviumTraceLogger.warn("RiviumTrace already initialized")
            return
        }

        this.context = context.applicationContext
        this.config = config
        this.client = RiviumTraceClient(config)
        this.userAgent = DeviceInfo.getUserAgent(context)

        // Set debug mode
        RiviumTraceLogger.isDebugEnabled = config.debug

        RiviumTraceLogger.info("Initializing RiviumTrace SDK v${BuildConfig.SDK_VERSION}")
        RiviumTraceLogger.debug("Config: API Key=${config.apiKey.take(10)}..., env=${config.environment}")

        if (!config.enabled) {
            RiviumTraceLogger.info("RiviumTrace SDK is disabled")
            return
        }

        // Configure breadcrumbs
        BreadcrumbService.setMaxBreadcrumbs(config.maxBreadcrumbs)

        // Setup crash detection
        setupCrashDetection()

        // Setup uncaught exception handler
        if (config.captureUncaughtExceptions) {
            setupUncaughtExceptionHandler()
        }

        // Setup ANR detection
        if (config.captureAnr) {
            setupAnrDetection()
        }

        // Setup lifecycle callbacks
        setupLifecycleCallbacks()

        // Add system breadcrumb
        BreadcrumbService.addSystem("RiviumTrace SDK initialized", mapOf(
            "sdk_version" to BuildConfig.SDK_VERSION,
            "environment" to config.environment
        ))

        RiviumTraceLogger.info("RiviumTrace SDK initialized successfully")
    }

    /**
     * Initialize with simple API key string
     */
    @JvmStatic
    fun init(context: Context, apiKey: String) {
        init(context, RiviumTraceConfig.simple(apiKey))
    }

    /**
     * Check if SDK is initialized
     */
    @JvmStatic
    fun isInitialized(): Boolean = isInitialized.get()

    // ==================== ERROR CAPTURE ====================

    /**
     * Capture an exception
     *
     * @param throwable The exception to capture
     * @param message Optional custom message
     * @param extra Additional context data
     * @param tags Tags for categorization
     * @param callback Callback with success status
     */
    @JvmStatic
    @JvmOverloads
    fun captureException(
        throwable: Throwable,
        message: String? = null,
        extra: Map<String, Any?> = emptyMap(),
        tags: Map<String, String> = emptyMap(),
        callback: ((Boolean, String?) -> Unit)? = null
    ) {
        if (!ensureInitialized()) {
            callback?.invoke(false, "SDK not initialized")
            return
        }

        val cfg = config ?: return

        // Apply sample rate
        if (cfg.sampleRate < 1.0f && Math.random() > cfg.sampleRate) {
            RiviumTraceLogger.debug("Error dropped due to sample rate")
            callback?.invoke(false, "Dropped due to sample rate")
            return
        }

        // Add error breadcrumb
        BreadcrumbService.addError("Exception: ${throwable.javaClass.simpleName}", mapOf(
            "message" to throwable.message
        ))

        val error = RiviumTraceError.fromThrowable(
            throwable = throwable,
            message = message,
            environment = cfg.environment,
            releaseVersion = cfg.release ?: DeviceInfo.getAppVersion(context!!),
            userAgent = userAgent,
            breadcrumbs = BreadcrumbService.getBreadcrumbs(),
            extra = extraContext + extra + mapOf(
                "user_id" to userId,
                "session_id" to sessionId,
                "device_info" to DeviceInfo.getDeviceInfo()
            ),
            tags = this.tags + tags,
            url = currentActivityName?.let { "android://$it" }
        )

        client?.sendError(error, callback)
    }

    /**
     * Capture a message
     *
     * @param message The message to capture
     * @param level Message severity level
     * @param extra Additional context data
     * @param tags Tags for categorization
     * @param callback Callback with success status
     */
    @JvmStatic
    @JvmOverloads
    fun captureMessage(
        message: String,
        level: MessageLevel = MessageLevel.INFO,
        extra: Map<String, Any?> = emptyMap(),
        tags: Map<String, String> = emptyMap(),
        callback: ((Boolean, String?) -> Unit)? = null
    ) {
        if (!ensureInitialized()) {
            callback?.invoke(false, "SDK not initialized")
            return
        }

        val cfg = config ?: return

        val msg = RiviumTraceError.message(
            message = message,
            level = level,
            environment = cfg.environment,
            releaseVersion = cfg.release ?: DeviceInfo.getAppVersion(context!!),
            userAgent = userAgent,
            breadcrumbs = BreadcrumbService.getBreadcrumbs(),
            extra = extraContext + extra + mapOf(
                "user_id" to userId,
                "session_id" to sessionId,
                "device_info" to DeviceInfo.getDeviceInfo()
            ),
            tags = this.tags + tags,
            url = currentActivityName?.let { "android://$it" }
        )

        client?.sendMessage(msg, callback)
    }

    // ==================== BREADCRUMBS ====================

    /**
     * Add a breadcrumb
     */
    @JvmStatic
    @JvmOverloads
    fun addBreadcrumb(message: String, type: BreadcrumbType = BreadcrumbType.INFO, data: Map<String, Any?> = emptyMap()) {
        BreadcrumbService.add(message, type, data)
    }

    /**
     * Add a navigation breadcrumb
     */
    @JvmStatic
    fun addNavigationBreadcrumb(from: String?, to: String) {
        BreadcrumbService.addNavigation(from, to)
    }

    /**
     * Add a user action breadcrumb
     */
    @JvmStatic
    @JvmOverloads
    fun addUserBreadcrumb(action: String, data: Map<String, Any?> = emptyMap()) {
        BreadcrumbService.addUser(action, data)
    }

    /**
     * Add an HTTP request breadcrumb
     */
    @JvmStatic
    @JvmOverloads
    fun addHttpBreadcrumb(method: String, url: String, statusCode: Int? = null, duration: Long? = null) {
        BreadcrumbService.addHttp(method, url, statusCode, duration)
    }

    /**
     * Clear all breadcrumbs
     */
    @JvmStatic
    fun clearBreadcrumbs() {
        BreadcrumbService.clear()
    }

    // ==================== CONTEXT & TAGS ====================

    /**
     * Set the user ID
     */
    @JvmStatic
    fun setUserId(id: String?) {
        userId = id
        if (id != null) {
            BreadcrumbService.addSystem("User ID set", mapOf("user_id" to id))
        }
    }

    /**
     * Get current user ID
     */
    @JvmStatic
    fun getUserId(): String? = userId

    /**
     * Set extra context data
     */
    @JvmStatic
    fun setExtra(key: String, value: Any?) {
        extraContext[key] = value
    }

    /**
     * Set multiple extra context values
     */
    @JvmStatic
    fun setExtras(extras: Map<String, Any?>) {
        extraContext.putAll(extras)
    }

    /**
     * Clear extra context
     */
    @JvmStatic
    fun clearExtras() {
        extraContext.clear()
    }

    /**
     * Set a tag
     */
    @JvmStatic
    fun setTag(key: String, value: String) {
        tags[key] = value
    }

    /**
     * Set multiple tags
     */
    @JvmStatic
    fun setTags(newTags: Map<String, String>) {
        tags.putAll(newTags)
    }

    /**
     * Clear all tags
     */
    @JvmStatic
    fun clearTags() {
        tags.clear()
    }

    // ==================== PERFORMANCE / APM ====================

    /**
     * Report a performance span (for APM tracking)
     *
     * This method sends HTTP request timing data to RiviumTrace APM.
     * Requires a paid plan (Starter or higher).
     *
     * @param method HTTP method (GET, POST, etc.)
     * @param url Request URL
     * @param statusCode HTTP status code (null if request failed)
     * @param durationMs Duration in milliseconds
     * @param startTime Start time in milliseconds since epoch
     * @param errorMessage Error message if request failed
     * @param tags Additional tags for filtering
     */
    @JvmStatic
    @JvmOverloads
    fun reportPerformanceSpan(
        method: String,
        url: String,
        statusCode: Int?,
        durationMs: Long,
        startTime: Long,
        errorMessage: String? = null,
        tags: Map<String, String> = emptyMap()
    ) {
        if (!ensureInitialized()) return

        val cfg = config ?: return

        val span = PerformanceSpan.fromHttpRequest(
            method = method,
            url = url,
            statusCode = statusCode,
            durationMs = durationMs,
            startTime = startTime,
            environment = cfg.environment,
            releaseVersion = cfg.release ?: DeviceInfo.getAppVersion(context!!),
            tags = this.tags + tags
        ).let {
            if (errorMessage != null) {
                it.copy(status = "error", errorMessage = errorMessage)
            } else {
                it
            }
        }

        client?.sendPerformanceSpan(span)
    }

    /**
     * Report a PerformanceSpan object directly (for DB queries, custom operations, etc.)
     *
     * @param span The PerformanceSpan to report
     * @param callback Optional callback with success status
     */
    @JvmStatic
    fun reportPerformanceSpan(
        span: PerformanceSpan,
        callback: ((Boolean, String?) -> Unit)? = null
    ) {
        if (!ensureInitialized()) return

        val cfg = config ?: return

        val enrichedSpan = span.copy(
            environment = span.environment ?: cfg.environment,
            releaseVersion = span.releaseVersion ?: cfg.release ?: DeviceInfo.getAppVersion(context!!),
            tags = this.tags + span.tags
        )

        client?.sendPerformanceSpan(enrichedSpan, callback)
    }

    /**
     * Report multiple performance spans in a batch
     *
     * @param spans List of PerformanceSpan objects
     * @param callback Optional callback with success status
     */
    @JvmStatic
    fun reportPerformanceSpanBatch(
        spans: List<PerformanceSpan>,
        callback: ((Boolean, String?) -> Unit)? = null
    ) {
        if (!ensureInitialized()) return

        val cfg = config ?: return

        val enrichedSpans = spans.map { span ->
            span.copy(
                environment = span.environment ?: cfg.environment,
                releaseVersion = span.releaseVersion ?: cfg.release ?: DeviceInfo.getAppVersion(context!!),
                tags = this.tags + span.tags
            )
        }

        client?.sendPerformanceSpanBatch(enrichedSpans, callback)
    }

    /**
     * Track a synchronous operation and automatically report it as a performance span
     *
     * @param operation Name of the operation
     * @param operationType Type of operation (default: "custom")
     * @param tags Additional tags
     * @param block The operation to execute and time
     * @return The result of the operation
     */
    @JvmStatic
    @JvmOverloads
    fun <T> trackOperation(
        operation: String,
        operationType: String = "custom",
        tags: Map<String, String> = emptyMap(),
        block: () -> T
    ): T {
        val startTime = System.currentTimeMillis()
        var status = "ok"
        var errorMessage: String? = null

        return try {
            val result = block()
            result
        } catch (e: Exception) {
            status = "error"
            errorMessage = e.message
            throw e
        } finally {
            val durationMs = System.currentTimeMillis() - startTime
            val span = PerformanceSpan.custom(
                operation = operation,
                durationMs = durationMs,
                startTime = startTime,
                operationType = operationType,
                status = status,
                errorMessage = errorMessage,
                tags = tags
            )
            reportPerformanceSpan(span)
        }
    }

    /**
     * Create a performance interceptor for OkHttp
     *
     * Usage:
     * ```kotlin
     * val client = OkHttpClient.Builder()
     *     .addInterceptor(RiviumTrace.performanceInterceptor())
     *     .build()
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun performanceInterceptor(minDurationMs: Long = 0): okhttp3.Interceptor {
        return co.rivium.trace.sdk.network.RiviumTracePerformanceInterceptor(minDurationMs)
    }

    // ==================== LOGGING ====================

    /**
     * Enable logging with optional configuration
     *
     * @param sourceId Identifier for this log source (e.g., "my-android-app")
     * @param sourceName Human-readable name for this source
     * @param batchSize Number of logs to batch before sending (default: 50)
     * @param flushIntervalMs How often to flush logs in milliseconds (default: 5000)
     */
    @JvmStatic
    @JvmOverloads
    fun enableLogging(
        sourceId: String? = null,
        sourceName: String? = null,
        batchSize: Int = 50,
        flushIntervalMs: Long = 5000
    ) {
        if (!ensureInitialized()) return

        val cfg = config ?: return

        logService = LogService(
            apiKey = cfg.apiKey,
            sourceId = sourceId,
            sourceName = sourceName,
            platform = "android",
            environment = cfg.environment,
            release = cfg.release ?: DeviceInfo.getAppVersion(context!!),
            batchSize = batchSize,
            flushIntervalMs = flushIntervalMs
        )

        RiviumTraceLogger.debug("Logging enabled with sourceId: $sourceId")
    }

    /**
     * Log a message with the specified level
     *
     * @param message The log message
     * @param level Log level (TRACE, DEBUG, INFO, WARN, ERROR, FATAL)
     * @param metadata Additional metadata to attach to the log
     */
    @JvmStatic
    @JvmOverloads
    fun log(
        message: String,
        level: LogLevel = LogLevel.INFO,
        metadata: Map<String, Any?>? = null
    ) {
        if (!ensureInitialized()) return

        // Auto-enable logging if not already enabled
        if (logService == null) {
            enableLogging()
        }

        logService?.log(message, level, metadata, userId)
    }

    /**
     * Log a trace-level message
     */
    @JvmStatic
    @JvmOverloads
    fun trace(message: String, metadata: Map<String, Any?>? = null) {
        log(message, LogLevel.TRACE, metadata)
    }

    /**
     * Log a debug-level message
     */
    @JvmStatic
    @JvmOverloads
    fun logDebug(message: String, metadata: Map<String, Any?>? = null) {
        log(message, LogLevel.DEBUG, metadata)
    }

    /**
     * Log an info-level message
     */
    @JvmStatic
    @JvmOverloads
    fun info(message: String, metadata: Map<String, Any?>? = null) {
        log(message, LogLevel.INFO, metadata)
    }

    /**
     * Log a warning-level message
     */
    @JvmStatic
    @JvmOverloads
    fun warn(message: String, metadata: Map<String, Any?>? = null) {
        log(message, LogLevel.WARN, metadata)
    }

    /**
     * Log an error-level message (for non-exception errors)
     */
    @JvmStatic
    @JvmOverloads
    fun logError(message: String, metadata: Map<String, Any?>? = null) {
        log(message, LogLevel.ERROR, metadata)
    }

    /**
     * Log a fatal-level message
     */
    @JvmStatic
    @JvmOverloads
    fun fatal(message: String, metadata: Map<String, Any?>? = null) {
        log(message, LogLevel.FATAL, metadata)
    }

    /**
     * Flush all pending logs immediately
     */
    @JvmStatic
    fun flushLogs(callback: ((Boolean) -> Unit)? = null) {
        logService?.flush(callback)
    }

    /**
     * Get the number of logs currently buffered
     */
    @JvmStatic
    fun getPendingLogCount(): Int = logService?.bufferSize ?: 0

    // ==================== LIFECYCLE ====================

    /**
     * Close the SDK (call on app termination for graceful shutdown)
     */
    @JvmStatic
    fun close() {
        RiviumTraceLogger.debug("RiviumTrace SDK closing...")

        // Flush and shutdown log service
        logService?.shutdown()

        // Delete crash marker (graceful shutdown)
        crashDetector?.deleteMarker()

        // Stop ANR watchdog
        anrWatchdog?.stop()

        // Shutdown network client
        client?.shutdown()

        BreadcrumbService.addSystem("RiviumTrace SDK closed")

        RiviumTraceLogger.info("RiviumTrace SDK closed")
    }

    // ==================== PRIVATE METHODS ====================

    private fun ensureInitialized(): Boolean {
        if (!isInitialized.get()) {
            RiviumTraceLogger.error("RiviumTrace SDK not initialized. Call RiviumTrace.init() first.")
            return false
        }
        if (config?.enabled == false) {
            RiviumTraceLogger.debug("RiviumTrace SDK is disabled")
            return false
        }
        return true
    }

    private fun setupCrashDetection() {
        val ctx = context ?: return
        crashDetector = CrashDetector(ctx)

        // Check for crash from previous session
        crashDetector?.checkForCrash()?.let { crashInfo ->
            RiviumTraceLogger.info("Previous session crash detected, sending report...")

            val cfg = config ?: return@let
            val error = RiviumTraceError.nativeCrash(
                crashInfo = "Session ID: ${crashInfo.sessionId ?: "unknown"}\n" +
                        "Last Activity: ${crashInfo.lastActivity ?: "unknown"}\n" +
                        "Crash Time: ${java.util.Date(crashInfo.timestamp)}",
                environment = cfg.environment,
                releaseVersion = cfg.release ?: DeviceInfo.getAppVersion(ctx),
                userAgent = userAgent,
                timeSinceCrashSeconds = crashInfo.timeSinceCrashSeconds
            )

            // Send synchronously to ensure it's sent before app continues
            client?.sendErrorSync(error)
        }

        // Create new crash marker for this session
        crashDetector?.createMarker(sessionId)
    }

    private fun setupUncaughtExceptionHandler() {
        originalExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            RiviumTraceLogger.error("Uncaught exception in thread ${thread.name}", throwable)

            try {
                val cfg = config ?: return@setDefaultUncaughtExceptionHandler
                val error = RiviumTraceError.fromThrowable(
                    throwable = throwable,
                    message = "Uncaught exception in thread: ${thread.name}",
                    environment = cfg.environment,
                    releaseVersion = cfg.release ?: DeviceInfo.getAppVersion(context!!),
                    userAgent = userAgent,
                    breadcrumbs = BreadcrumbService.getBreadcrumbs(),
                    extra = extraContext + mapOf(
                        "user_id" to userId,
                        "session_id" to sessionId,
                        "thread_name" to thread.name,
                        "thread_id" to thread.id,
                        "error_type" to "uncaught_exception",
                        "device_info" to DeviceInfo.getDeviceInfo()
                    ),
                    tags = tags,
                    url = currentActivityName?.let { "android://$it" }
                )

                // Send synchronously to ensure delivery before crash
                client?.sendErrorSync(error)
            } catch (e: Exception) {
                RiviumTraceLogger.error("Failed to send crash report: ${e.message}")
            }

            // Call original handler
            originalExceptionHandler?.uncaughtException(thread, throwable)
        }

        RiviumTraceLogger.debug("Uncaught exception handler installed")
    }

    private fun setupAnrDetection() {
        val cfg = config ?: return
        anrWatchdog = ANRWatchdogService()

        anrWatchdog?.start(cfg.anrTimeoutMs) { stackTrace ->
            RiviumTraceLogger.warn("ANR detected, sending report...")

            val error = RiviumTraceError.anr(
                stackTrace = stackTrace,
                environment = cfg.environment,
                releaseVersion = cfg.release ?: DeviceInfo.getAppVersion(context!!),
                userAgent = userAgent,
                anrDurationMs = cfg.anrTimeoutMs
            )

            client?.sendError(error)
        }

        RiviumTraceLogger.debug("ANR watchdog started")
    }

    private fun setupLifecycleCallbacks() {
        val ctx = context ?: return

        // Track app foreground/background and handle crash detection markers
        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : LifecycleEventObserver {
                override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                    when (event) {
                        Lifecycle.Event.ON_START -> {
                            BreadcrumbService.addSystem("App entered foreground")
                            // Clear clean exit marker - app is now in foreground
                            crashDetector?.clearCleanExit()
                        }
                        Lifecycle.Event.ON_STOP -> {
                            BreadcrumbService.addSystem("App entered background")
                            // Mark clean exit - app is going to background
                            crashDetector?.markCleanExit()
                        }
                        else -> {}
                    }
                }
            })
        } catch (e: Exception) {
            RiviumTraceLogger.error("Failed to setup lifecycle observer: ${e.message}")
        }

        // Track activity lifecycle
        if (ctx is Application) {
            ctx.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                private var currentActivity: String? = null

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    val activityName = activity.javaClass.simpleName
                    BreadcrumbService.addNavigation(currentActivity, activityName)
                    currentActivity = activityName
                    currentActivityName = activityName
                    crashDetector?.updateLastActivity(activityName)
                }

                override fun onActivityStarted(activity: Activity) {}

                override fun onActivityResumed(activity: Activity) {
                    val activityName = activity.javaClass.simpleName
                    if (currentActivity != activityName) {
                        BreadcrumbService.addNavigation(currentActivity, activityName)
                        currentActivity = activityName
                        currentActivityName = activityName
                        crashDetector?.updateLastActivity(activityName)
                    }
                }

                override fun onActivityPaused(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {}
            })
        }

        RiviumTraceLogger.debug("Lifecycle callbacks registered")
    }
}

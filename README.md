# RiviumTrace Android SDK

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Official Android SDK for [RiviumTrace](https://rivium.co/cloud/rivium-trace) - Error tracking, crash detection, and performance monitoring for Android apps.

**[RiviumTrace Landing Page](https://rivium.co/cloud/rivium-trace)** | **[Documentation](https://rivium.co/cloud/rivium-trace/docs/sdks-android)** | **[Issues](https://github.com/Rivium-co/rivium-trace-android-sdk/issues)**

## Features

- **Error Tracking** - Automatically capture uncaught exceptions and crashes
- **ANR Detection** - Detect Application Not Responding events
- **Crash Detection** - Report native crashes from previous sessions on the next launch (Android 11+)
- **Breadcrumbs** - Track user actions leading up to errors
- **Performance Monitoring** - HTTP request timing, custom operation tracking, and batched span reporting
- **Logging** - Structured logging with batching, exponential backoff retries, and level-based filtering
- **OkHttp Integration** - Automatic HTTP breadcrumbs, error capture, and APM tracking
- **Offline Support** - Errors that cannot be sent while the device is offline are kept on the device and sent later
- **Rich Context** - User sessions, global extras, tags, and custom metadata
- **Minimum API 19** - Supports Android 4.4+

## Installation

### Gradle (Maven Central)

Add the dependency to your app's `build.gradle`:

```gradle
dependencies {
    implementation 'co.rivium.trace:rivium-trace-android-sdk:0.2.3'
}
```

### Gradle (JitPack)

Alternatively, you can use JitPack. Add the repository to your project's `build.gradle`:

```gradle
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```

Then add the dependency:

```gradle
dependencies {
    implementation 'com.github.Rivium-co:rivium-trace-android-sdk:0.2.3'
}
```

### Maven

```xml
<dependency>
    <groupId>co.rivium.trace</groupId>
    <artifactId>rivium-trace-android-sdk</artifactId>
    <version>0.2.3</version>
</dependency>
```

## Quick Start

### Initialize the SDK (Default: Rivium Cloud)

In your `Application` class:

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val config = RiviumTraceConfig.Builder("rv_live_your_api_key")
            .environment(if (BuildConfig.DEBUG) "development" else "production")
            .release(BuildConfig.VERSION_NAME)
            .debug(BuildConfig.DEBUG)
            .captureUncaughtExceptions(true)
            .captureAnr(true)
            .build()

        RiviumTrace.init(this, config)
    }

    override fun onTerminate() {
        RiviumTrace.close()
        super.onTerminate()
    }
}
```

### Initialize the SDK (Advanced: Self-Hosted)

If you're running [RiviumTrace Self-Hosted](https://github.com/Rivium-co/rivium-selfhosted), just add `.apiUrl()` pointing to your server:

```kotlin
val config = RiviumTraceConfig.Builder("rv_live_your_api_key")
    .apiUrl("http://your-server:3001")  // Your self-hosted Trace API
    .environment(if (BuildConfig.DEBUG) "development" else "production")
    .release(BuildConfig.VERSION_NAME)
    .build()

RiviumTrace.init(this, config)
```

### 2. Capture Errors

```kotlin
// Capture exception
try {
    riskyOperation()
} catch (e: Exception) {
    RiviumTrace.captureException(e)
}

// Capture with extra context
RiviumTrace.captureException(
    throwable = e,
    message = "Failed to process payment",
    extra = mapOf("order_id" to "123", "amount" to 99.99)
)

// Capture message
RiviumTrace.captureMessage(
    message = "User completed checkout",
    level = MessageLevel.INFO,
    extra = mapOf("items" to 3)
)
```

### 3. Add Breadcrumbs

Breadcrumbs are automatically added for:
- Activity navigation
- App foreground/background
- System events

Add custom breadcrumbs:

```kotlin
// User action
RiviumTrace.addUserBreadcrumb("Added item to cart", mapOf("product_id" to "abc"))

// Navigation
RiviumTrace.addNavigationBreadcrumb("HomeScreen", "ProductScreen")

// HTTP request
RiviumTrace.addHttpBreadcrumb("GET", "https://api.example.com/users", 200, 150)

// Custom
RiviumTrace.addBreadcrumb("Custom event", BreadcrumbType.INFO, mapOf("key" to "value"))
```

### 4. User Context

```kotlin
// Set user ID
RiviumTrace.setUserId("user-123")

// Get user ID
val userId = RiviumTrace.getUserId()

// Add custom context
RiviumTrace.setExtra("subscription", "premium")
RiviumTrace.setTag("build_type", "release")
```

## Context & Tags

### Global Extra Context

Set persistent context that is automatically included with all errors and messages:

```kotlin
// Set individual extra
RiviumTrace.setExtra("organizationId", "org-123")
RiviumTrace.setExtra("feature", "checkout-v2")

// Set multiple extras at once
RiviumTrace.setExtras(mapOf(
    "organizationId" to "org-123",
    "feature" to "checkout-v2",
    "experiment" to "new-flow"
))

// Clear all extras
RiviumTrace.clearExtras()
```

### Tags

Tags are key-value string pairs attached to all events:

```kotlin
// Set individual tag
RiviumTrace.setTag("team", "payments")
RiviumTrace.setTag("region", "us-east")

// Set multiple tags at once
RiviumTrace.setTags(mapOf("team" to "payments", "version" to "2.1"))

// Clear all tags
RiviumTrace.clearTags()
```

## Performance Monitoring

### OkHttp Performance Interceptor

Automatically track HTTP request timing for APM:

```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor(RiviumTracePerformanceInterceptor())  // APM spans
    .build()

// With minimum duration filter (only report requests > 100ms)
val client = OkHttpClient.Builder()
    .addInterceptor(RiviumTracePerformanceInterceptor(minDurationMs = 100))
    .build()
```

Or use the convenience method:

```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor(RiviumTrace.performanceInterceptor())
    .build()
```

### Track Custom Operations

```kotlin
// Track a synchronous operation
val result = RiviumTrace.trackOperation("fetchUserProfile", "http") {
    api.getUserProfile(userId)
}

// Track with tags
val data = RiviumTrace.trackOperation(
    operation = "processPayment",
    operationType = "custom",
    tags = mapOf("payment_method" to "credit_card")
) {
    paymentService.process(order)
}
```

### Manual Span Reporting

```kotlin
// Report an HTTP span directly
RiviumTrace.reportPerformanceSpan(
    method = "POST",
    url = "https://api.example.com/orders",
    statusCode = 201,
    durationMs = 245,
    startTime = System.currentTimeMillis() - 245
)

// Report a PerformanceSpan object
val span = PerformanceSpan.custom(
    operation = "image_processing",
    durationMs = 1500,
    startTime = System.currentTimeMillis() - 1500,
    operationType = "custom",
    tags = mapOf("format" to "webp")
)
RiviumTrace.reportPerformanceSpan(span)

// Report a database query span
val dbSpan = PerformanceSpan.forDbQuery(
    queryType = "SELECT",
    tableName = "users",
    durationMs = 45,
    startTime = System.currentTimeMillis() - 45,
    rowsAffected = 10
)
RiviumTrace.reportPerformanceSpan(dbSpan)

// Report multiple spans in a batch
RiviumTrace.reportPerformanceSpanBatch(listOf(span, dbSpan))
```

## Logging

### Enable Logging

```kotlin
RiviumTrace.enableLogging(
    sourceId = "my-android-app",       // Optional: group logs by source
    sourceName = "My Android App",     // Optional: human-readable name
    batchSize = 50,                    // Logs per batch (default: 50)
    flushIntervalMs = 5000             // Auto-flush interval in ms (default: 5000)
)
```

### Log Messages

```kotlin
// Convenience methods for each level
RiviumTrace.trace("Entering function X")
RiviumTrace.logDebug("Cache miss for key: user-123")
RiviumTrace.info("User logged in", mapOf("userId" to "user-123"))
RiviumTrace.warn("Rate limit approaching", mapOf("current" to 950, "limit" to 1000))
RiviumTrace.logError("Failed to process payment", mapOf("orderId" to "ord-456"))
RiviumTrace.fatal("Database connection lost")

// Generic log with explicit level
RiviumTrace.log("Custom message", LogLevel.INFO, mapOf("key" to "value"))
```

### Flush & Buffer Management

```kotlin
// Check pending log count
val pending = RiviumTrace.getPendingLogCount()

// Force flush all buffered logs immediately
RiviumTrace.flushLogs { success ->
    Log.d("RiviumTrace", "Flush result: $success")
}
```

### Logging Features

- **Batching** - Logs are buffered and sent in configurable batches (default: 50). They go out as one batch request when `sourceId` is set; without it each buffered log is sent in its own request
- **Auto-flush** - Timer flushes logs at a configurable interval (default: 5s)
- **Exponential backoff** - A failed batch is retried with delays: 2s, 4s, 8s... up to 60s (max 10 attempts). Logs sent without a `sourceId` are not retried
- **Buffer limit** - Max 1000 logs in buffer; oldest logs dropped when exceeded
- **Lazy timer** - Flush timer only runs when the buffer has logs
- **Lifecycle-aware** - Automatically flushes when app goes to background

## OkHttp Integration

The SDK provides three OkHttp interceptors for different use cases:

```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor(RiviumTraceInterceptor())              // Breadcrumbs
    .addInterceptor(RiviumTraceErrorInterceptor())         // Error capture (5xx)
    .addInterceptor(RiviumTracePerformanceInterceptor())   // APM spans
    .build()
```

| Interceptor | Purpose |
|-------------|---------|
| `RiviumTraceInterceptor` | Adds HTTP breadcrumbs for all requests |
| `RiviumTraceErrorInterceptor` | Captures server errors (5xx) as messages, optionally client errors (4xx) |
| `RiviumTracePerformanceInterceptor` | Reports HTTP timing as performance spans for APM |

### Error Interceptor Options

```kotlin
// Default: only capture 5xx server errors
RiviumTraceErrorInterceptor()

// Also capture 4xx client errors
RiviumTraceErrorInterceptor(captureClientErrors = true, captureServerErrors = true)
```

## Crash Detection

### How It Works

RiviumTrace reports crashes through three mechanisms:

1. **Uncaught exception handler** (all Android versions): When a Java/Kotlin exception is not caught, on the main thread or any other thread, the SDK sends the report — with breadcrumbs, user ID, extras and tags — before the process exits. It waits at most 2 seconds for the server. If the report was not accepted in that time (no network, a slow connection, a server error), it is kept on the device (see `enableOfflineStorage`) and sent on the next launch. The handler that was installed before `RiviumTrace.init()` is always called afterwards.
2. **Exit records from Android** (Android 11 / API 30 and newer): On each launch, `RiviumTrace.init()` reads the exit reasons Android recorded for your app (`ApplicationExitInfo`, up to the 20 most recent) and reports every native crash and ANR record it has not reported yet. A Java/Kotlin crash record is reported from here only when the uncaught exception handler could not report that crash itself. Native crash reports include the tombstone captured by the OS (signal, threads, stack frames). A report that cannot be sent is retried on the next launch.
3. **ANR watchdog** (all Android versions): A background thread checks that the main thread responds within `anrTimeoutMs`. When it does not, the SDK sends an ANR report with the main thread's stack trace while the app is still running.

### Types of Crashes Detected

| Crash Type | Detection | Notes |
|------------|-----------|-------|
| Java/Kotlin Exceptions | Real-time | Sent by the uncaught exception handler before the process exits, or on the next launch when it could not be sent then |
| ANR Events | Real-time | Watchdog: main thread blocked for `anrTimeoutMs` (default 5 seconds). On Android 11+, ANRs recorded by the system are also reported on the next launch |
| Native Crashes (SIGSEGV, etc.) | Next Launch | Android 11+ (API 30) only, from the exit record and OS tombstone |
| OOM Crashes | Real-time | A Java `OutOfMemoryError` is reported like any other uncaught exception. A process killed by the system for low memory is not reported |

### What Is Not Captured

- **Native crashes on Android 10 (API 29) and older** - There the SDK reports Java/Kotlin exceptions and watchdog ANRs only.
- **Process kills that are not crashes** - Low-memory kills by the system, force stops and apps swiped away by the user are not reported.
- **Context on exit-record and ANR reports** - Reports built from exit records and ANR watchdog reports do not include breadcrumbs, user ID, extras or tags.

### You Do Not Need to Call close()

Crash detection does not depend on `RiviumTrace.close()`. `close()` flushes buffered logs, stops the ANR watchdog and shuts down the SDK's HTTP client, and the SDK cannot be initialized again in the same process. Do not call it from an Activity's `onDestroy()`: the process usually outlives the Activity.

## Configuration

| Option | Default | Description |
|--------|---------|-------------|
| `apiKey` | Required | Your API key from Rivium Console. Must start with `rv_live_`; any other value throws `IllegalArgumentException` when the config is built |
| `apiUrl` | `https://trace.rivium.co` | API URL — set for self-hosted only |
| `environment` | `"production"` | Environment name (production, staging, etc.) |
| `release` | null | App version string (the app's `versionName` is used if null) |
| `debug` | false | Enable debug logging |
| `enabled` | true | Enable/disable SDK |
| `captureUncaughtExceptions` | true | Capture uncaught exceptions |
| `captureSignalCrashes` | true | Report native crashes and ANRs that Android recorded for earlier sessions, and Java/Kotlin crashes the uncaught exception handler could not report (Android 11+ / API 30+) |
| `captureAnr` | true | Detect ANR events |
| `anrTimeoutMs` | 5000 | ANR detection timeout |
| `maxBreadcrumbs` | 20 | Maximum breadcrumbs to store |
| `httpTimeout` | 30 | HTTP request timeout (seconds) |
| `enableOfflineStorage` | true | Keep errors that could not be sent because of a network failure, and crash reports the server did not accept in time (up to 100, oldest dropped first), and send them on the next launch or once sending works again |
| `sampleRate` | 1.0 | Error capture sample rate (0.0 - 1.0) |

## API Reference

### RiviumTrace

**Initialization & Lifecycle:**
- `init(context, config)` - Initialize SDK with configuration
- `init(context, apiKey)` - Initialize SDK with just an API key
- `isInitialized()` - Check if SDK is initialized
- `close()` - Flush buffered logs and shut the SDK down (it cannot be initialized again in the same process)

**Error Capture:**
- `captureException(throwable, message?, extra?, tags?, callback?)` - Capture exception
- `captureMessage(message, level?, extra?, tags?, callback?)` - Capture log message

**User & Session:**
- `setUserId(id)` - Set user ID
- `getUserId()` - Get current user ID

**Global Context (Extras):**
- `setExtra(key, value)` - Set a single extra context value
- `setExtras(extras)` - Set multiple extra context values
- `clearExtras()` - Clear all extras

**Tags:**
- `setTag(key, value)` - Set a single tag
- `setTags(tags)` - Set multiple tags
- `clearTags()` - Clear all tags

**Breadcrumbs:**
- `addBreadcrumb(message, type?, data?)` - Add generic breadcrumb
- `addNavigationBreadcrumb(from, to)` - Add navigation breadcrumb
- `addUserBreadcrumb(action, data?)` - Add user action breadcrumb
- `addHttpBreadcrumb(method, url, statusCode?, duration?)` - Add HTTP breadcrumb
- `clearBreadcrumbs()` - Clear all breadcrumbs

**Performance Monitoring:**
- `reportPerformanceSpan(method, url, statusCode, durationMs, startTime, errorMessage?, tags?)` - Report HTTP span
- `reportPerformanceSpan(span, callback?)` - Report a PerformanceSpan object
- `reportPerformanceSpanBatch(spans, callback?)` - Report multiple spans
- `trackOperation(operation, operationType?, tags?, block)` - Track and time an operation
- `performanceInterceptor(minDurationMs?)` - Create OkHttp performance interceptor

**Logging:**
- `enableLogging(sourceId?, sourceName?, batchSize?, flushIntervalMs?)` - Enable logging
- `log(message, level?, metadata?)` - Log a message
- `trace(message, metadata?)` - Log at trace level
- `logDebug(message, metadata?)` - Log at debug level
- `info(message, metadata?)` - Log at info level
- `warn(message, metadata?)` - Log at warn level
- `logError(message, metadata?)` - Log at error level
- `fatal(message, metadata?)` - Log at fatal level
- `flushLogs(callback?)` - Flush all pending logs
- `getPendingLogCount()` - Get buffered log count

### PerformanceSpan

- `PerformanceSpan.fromHttpRequest(...)` - Create span from HTTP request
- `PerformanceSpan.forDbQuery(...)` - Create span for database query
- `PerformanceSpan.custom(...)` - Create custom span
- `PerformanceSpan.generateTraceId()` - Generate random trace ID
- `PerformanceSpan.generateSpanId()` - Generate random span ID

## ProGuard / R8

The SDK includes ProGuard rules automatically. No additional configuration needed.

## Minimum Requirements

- **Android API 19+** (Android 4.4 KitKat)
- **Java 8+** or **Kotlin 1.5+**

## Device Compatibility

| Android Version | API Level | Support |
|----------------|-----------|---------|
| Android 4.4 KitKat | 19 | Supported |
| Android 5.0 Lollipop | 21 | Supported |
| Android 6.0 Marshmallow | 23 | Supported |
| Android 7.0 Nougat | 24 | Supported |
| Android 8.0 Oreo | 26 | Supported |
| Android 9.0 Pie | 28 | Supported |
| Android 10 | 29 | Supported |
| Android 11 | 30 | Supported |
| Android 12 | 31 | Supported |
| Android 13 | 33 | Supported |
| Android 14 | 34 | Supported |

## Examples

See the [example app](./example) for a complete working implementation.

## License

MIT - see [LICENSE](LICENSE) for details.

## Support

- Landing Page: https://rivium.co/cloud/rivium-trace
- Documentation: https://rivium.co/cloud/rivium-trace/docs/sdks-android
- Issues: https://github.com/Rivium-co/rivium-trace-android-sdk/issues
- Email: support@rivium.co

# RiviumTrace Android SDK

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Official Android SDK for [RiviumTrace](https://rivium.co/cloud/rivium-trace) - Error tracking, crash detection, and performance monitoring for Android apps.

**[RiviumTrace Landing Page](https://rivium.co/cloud/rivium-trace)** | **[Documentation](https://rivium.co/cloud/rivium-trace/docs/sdks-android)** | **[Issues](https://github.com/Rivium-co/rivium-trace-android-sdk/issues)**

## Features

- **Error Tracking** - Automatically capture uncaught exceptions and crashes
- **ANR Detection** - Detect Application Not Responding events
- **Crash Detection** - Detect native crashes from previous sessions
- **Breadcrumbs** - Track user actions leading up to errors
- **Performance Monitoring** - HTTP request timing, custom operation tracking, and batched span reporting
- **Logging** - Structured logging with batching, exponential backoff retries, and level-based filtering
- **OkHttp Integration** - Automatic HTTP breadcrumbs, error capture, and APM tracking
- **Offline Support** - Cache errors when offline
- **Rich Context** - User sessions, global extras, tags, and custom metadata
- **Minimum API 16** - Supports Android 4.1+ (99.9% of devices)

## Installation

### Gradle (Maven Central)

Add the dependency to your app's `build.gradle`:

```gradle
dependencies {
    implementation 'co.rivium.trace:rivium-trace-android-sdk:0.1.0'
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
    implementation 'com.github.Rivium-co:rivium-trace-android-sdk:0.1.0'
}
```

### Maven

```xml
<dependency>
    <groupId>co.rivium.trace</groupId>
    <artifactId>rivium-trace-android-sdk</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Quick Start

### 1. Initialize the SDK

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
        RiviumTrace.close()  // Graceful shutdown
        super.onTerminate()
    }
}
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

- **Batching** - Logs are buffered and sent in configurable batches (default: 50)
- **Auto-flush** - Timer flushes logs at a configurable interval (default: 5s)
- **Exponential backoff** - Failed sends retry with delays: 1s, 2s, 4s, 8s... up to 60s (max 10 attempts)
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

RiviumTrace uses a marker-based crash detection system that works for all crash types:

1. **On SDK Init**: Creates a crash marker file
2. **On Graceful Shutdown**: Deletes the marker via `RiviumTrace.close()`
3. **On Next Launch**: If marker exists, a crash occurred - sends report

### Types of Crashes Detected

| Crash Type | Detection | Notes |
|------------|-----------|-------|
| Java/Kotlin Exceptions | Real-time | Captured immediately |
| ANR Events | Real-time | Main thread blocked detection |
| Native Crashes (SIGSEGV, etc.) | Next Launch | Via crash marker |
| OOM Crashes | Next Launch | Via crash marker |

### Important: Call close() on Exit

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onDestroy() {
        if (isFinishing) {
            RiviumTrace.close()  // Mark graceful shutdown
        }
        super.onDestroy()
    }
}
```

## Configuration

| Option | Default | Description |
|--------|---------|-------------|
| `apiKey` | Required | Your API key from Rivium Console (`rv_live_xxx` or `rv_test_xxx`) |
| `environment` | `"production"` | Environment name (production, staging, etc.) |
| `release` | null | App version string |
| `debug` | false | Enable debug logging |
| `enabled` | true | Enable/disable SDK |
| `captureUncaughtExceptions` | true | Capture uncaught exceptions |
| `captureSignalCrashes` | true | Detect native signal crashes |
| `captureAnr` | true | Detect ANR events |
| `anrTimeoutMs` | 5000 | ANR detection timeout |
| `maxBreadcrumbs` | 20 | Maximum breadcrumbs to store |
| `httpTimeout` | 30 | HTTP request timeout (seconds) |
| `enableOfflineStorage` | true | Cache errors when offline |
| `sampleRate` | 1.0 | Error capture sample rate (0.0 - 1.0) |

## API Reference

### RiviumTrace

**Initialization & Lifecycle:**
- `init(context, config)` - Initialize SDK with configuration
- `init(context, apiKey)` - Initialize SDK with just an API key
- `isInitialized()` - Check if SDK is initialized
- `close()` - Cleanup SDK, flush pending data

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

- **Android API 16+** (Android 4.1 Jelly Bean)
- **Java 8+** or **Kotlin 1.5+**

## Device Compatibility

| Android Version | API Level | Support |
|----------------|-----------|---------|
| Android 4.1 Jelly Bean | 16 | Supported |
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

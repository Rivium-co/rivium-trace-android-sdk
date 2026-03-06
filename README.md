# RiviumTrace Android SDK

Official Android SDK for [RiviumTrace](https://rivium.co) - Error Tracking and A/B Testing platform.

## Features

- **Error Tracking**: Automatically capture uncaught exceptions and crashes
- **ANR Detection**: Detect Application Not Responding events
- **Crash Detection**: Detect native crashes from previous sessions
- **Breadcrumbs**: Track user actions leading up to errors
- **A/B Testing**: Run experiments and track conversions
- **Offline Support**: Cache errors when offline
- **Minimum API 16**: Supports Android 4.1+ (99.9% of devices)

## Installation

### Gradle

Add JitPack repository to your project's `build.gradle`:

```gradle
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```

Add the dependency to your app's `build.gradle`:

```gradle
dependencies {
    implementation 'com.github.rivium-trace:rivium-trace-android-sdk:1.0.0'
}
```

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.rivium-trace</groupId>
    <artifactId>rivium-trace-android-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Quick Start

### 1. Initialize the SDK

In your `Application` class:

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val config = RiviumTraceConfig.Builder("nl_live_your_api_key")
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
// Set user ID (required for A/B testing)
RiviumTrace.setUserId("user-123")

// Add custom context
RiviumTrace.setExtra("subscription", "premium")
RiviumTrace.setTag("build_type", "release")
```

## A/B Testing

### Initialize A/B Testing

```kotlin
// Initialize (fetches experiments from server)
RiviumTrace.initializeABTesting { success ->
    if (success) {
        Log.d("RiviumTrace", "A/B testing ready")
    }
}

// Set user ID (required)
RiviumTrace.setUserId("user-123")
```

### Get Variant Assignment

```kotlin
// Async
RiviumTrace.getAssignment("checkout-flow-experiment") { assignment ->
    when (assignment?.variantName) {
        "control" -> showOldCheckout()
        "variant_a" -> showNewCheckout()
        else -> showOldCheckout()  // Default
    }
}

// Check specific variant
RiviumTrace.isInVariant("checkout-flow", "variant_a") { isInVariant ->
    if (isInVariant) {
        showNewFeature()
    }
}

// Get variant config value
RiviumTrace.getVariantConfig("button-color", "color", "#0000FF") { color ->
    button.setBackgroundColor(Color.parseColor(color))
}
```

### Track Events

```kotlin
// Track conversion
RiviumTrace.trackConversion(
    experimentId = "checkout-flow",
    eventName = "purchase",
    eventValue = 99.99,
    metadata = mapOf("currency" to "USD")
)

// Track click
RiviumTrace.trackABEvent(
    experimentId = "checkout-flow",
    eventType = ABEventType.CLICK,
    eventName = "buy_button"
)

// Track view
RiviumTrace.trackABEvent(
    experimentId = "checkout-flow",
    eventType = ABEventType.VIEW,
    eventName = "product_page"
)

// Convenience methods
RiviumTrace.trackClick(experimentId = "checkout-flow", elementName = "buy_button")
RiviumTrace.trackView(experimentId = "checkout-flow", viewName = "product_page")
```

## OkHttp Integration

Automatically track HTTP requests as breadcrumbs:

```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor(RiviumTraceInterceptor())  // Adds breadcrumbs
    .addInterceptor(RiviumTraceErrorInterceptor())  // Captures HTTP errors
    .build()
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

**Example crash report:**
```json
{
  "message": "Native crash detected from previous session",
  "platform": "android",
  "crash_time": "2025-11-12T03:24:20.000Z",
  "extra": {
    "error_type": "native_crash",
    "time_since_crash_seconds": 293,
    "last_activity": "CheckoutActivity"
  }
}
```

## Configuration Options

| Option | Default | Description |
|--------|---------|-------------|
| `apiKey` | Required | Your API key from Rivium Console (nl_live_xxx or nl_test_xxx) |
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

## ProGuard / R8

The SDK includes ProGuard rules automatically. No additional configuration needed.

## Minimum Requirements

- **Android API 16+** (Android 4.1 Jelly Bean)
- **Java 8+** or **Kotlin 1.5+**

## Device Compatibility

| Android Version | API Level | Support |
|----------------|-----------|---------|
| Android 4.1 Jelly Bean | 16 | ✅ |
| Android 4.4 KitKat | 19 | ✅ |
| Android 5.0 Lollipop | 21 | ✅ |
| Android 6.0 Marshmallow | 23 | ✅ |
| Android 7.0 Nougat | 24 | ✅ |
| Android 8.0 Oreo | 26 | ✅ |
| Android 9.0 Pie | 28 | ✅ |
| Android 10 | 29 | ✅ |
| Android 11 | 30 | ✅ |
| Android 12 | 31 | ✅ |
| Android 13 | 33 | ✅ |
| Android 14 | 34 | ✅ |

## License

MIT License - see [LICENSE](LICENSE) for details.

## Support

- Documentation: https://docs.rivium.co
- Issues: https://github.com/rivium-trace/rivium-trace-android-sdk/issues
- Email: support@rivium.co

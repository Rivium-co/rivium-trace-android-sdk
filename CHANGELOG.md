# Changelog

All notable changes to the RiviumTrace Android SDK will be documented in this file.

## [0.2.3] - 2026-10-05

### Fixed
- One hang is one ANR report. Before, a report was sent every `anrTimeoutMs`
  for as long as the main thread stayed blocked. The report now carries how
  long the thread had been blocked when it was sent.
- No false ANR reports after a crash while the system's "keeps stopping"
  dialog is open.
- Every error carries an `event_id`, so a report that is sent again (for
  example a stored crash report on the next launch) is counted once by the
  server.

## [0.2.2] - 2026-10-05

### Fixed
- Crashes on the main thread are now reported. Before, their report was
  neither sent nor stored.
- A crash report the server does not accept within 2 seconds is kept on the
  device and sent on the next launch.
- Android 11+: a Java/Kotlin crash that was already reported is no longer
  reported again as "Native crash" on the next launch.

## [0.2.1] - 2026-10-05

### Fixed
- `enableOfflineStorage` now works: errors that cannot be sent because the
  device is offline are kept on the device (up to 100) and sent later.
- The minimum supported Android version is API 19 (Android 4.4). Earlier
  releases declared API 16, but apps below API 19 could not build with the SDK.

## [0.2.0] - 2026-07-24

### Breaking changes
- **Native crash detection is now backed by `ApplicationExitInfo` (API 30+).**
  The previous lifecycle-marker `CrashDetector` reported any non-graceful app
  close (swipe-to-quit, OS memory kill, force-quit) as a "native crash" with
  no real stack trace. It has been deleted. Real crashes (JVM, native, ANR)
  are now drained on the next launch from the platform's authoritative exit
  reason history, including the tombstone trace the OS captured at exit time.
- **Minimum supported API for native crash reporting is API 30 (Android 11).**
  The SDK itself still runs on lower APIs; JVM/Kotlin crashes are captured
  by the in-process uncaught exception handler everywhere. Below API 30,
  native crash and ANR records are unavailable from the platform.
- `CrashDetector` and `SignalCrashHandler` classes are removed. They had no
  real signal handling — the previous "SignalCrashHandler" was a stub whose
  own header acknowledged real handling would require Breakpad/Crashpad.

### Added
- `NativeCrashReporter` service. On each launch it queries
  `ActivityManager.getHistoricalProcessExitReasons` for `REASON_CRASH`,
  `REASON_CRASH_NATIVE`, and `REASON_ANR` records newer than the last
  poll timestamp, reads the tombstone trace from `getTraceInputStream()`,
  and posts each as a `RiviumTraceError`.
- Last-processed timestamp persisted in `SharedPreferences` so the same
  exit record is not reported twice.

### Configuration
- `captureSignalCrashes` (default `true`) now controls `NativeCrashReporter`
  installation. The flag is preserved for source compatibility.

## [0.1.0] - 2026-03-07

### Added
- Initial release of RiviumTrace Android SDK
- Error tracking with automatic uncaught exception capture
- ANR (Application Not Responding) detection
- Native crash detection via file marker system
- Breadcrumb system for tracking user journey
  - Navigation breadcrumbs (automatic)
  - User action breadcrumbs
  - HTTP request breadcrumbs
  - System event breadcrumbs
- A/B Testing support
  - Experiment fetching
  - Variant assignment
  - Conversion tracking
  - Custom event tracking
- OkHttp interceptors for automatic HTTP tracking
- User context and tagging
- Offline error caching
- Sample rate configuration
- Debug mode for development
- ProGuard/R8 support
- Minimum API 16 (Android 4.1+) support

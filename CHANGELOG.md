# Changelog

All notable changes to the RiviumTrace Android SDK will be documented in this file.

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

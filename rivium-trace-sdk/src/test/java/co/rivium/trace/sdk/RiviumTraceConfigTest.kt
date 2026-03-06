package co.rivium.trace.sdk

import org.junit.Assert.*
import org.junit.Test

class RiviumTraceConfigTest {

    // --- Valid API Key Prefixes ---

    @Test
    fun `config accepts rv_live_ api key`() {
        val config = RiviumTraceConfig(apiKey = "rv_live_abc123")
        assertEquals("rv_live_abc123", config.apiKey)
    }

    @Test
    fun `config accepts rv_test_ api key`() {
        val config = RiviumTraceConfig(apiKey = "rv_test_abc123")
        assertEquals("rv_test_abc123", config.apiKey)
    }

    @Test
    fun `config accepts nl_live_ api key`() {
        val config = RiviumTraceConfig(apiKey = "nl_live_abc123")
        assertEquals("nl_live_abc123", config.apiKey)
    }

    @Test
    fun `config accepts nl_test_ api key`() {
        val config = RiviumTraceConfig(apiKey = "nl_test_abc123")
        assertEquals("nl_test_abc123", config.apiKey)
    }

    // --- Invalid API Keys ---

    @Test(expected = IllegalArgumentException::class)
    fun `config rejects blank api key`() {
        RiviumTraceConfig(apiKey = "")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `config rejects whitespace-only api key`() {
        RiviumTraceConfig(apiKey = "   ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `config rejects api key with invalid prefix`() {
        RiviumTraceConfig(apiKey = "invalid_key_123")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `config rejects api key with sk_ prefix`() {
        RiviumTraceConfig(apiKey = "sk_live_abc123")
    }

    // --- Default Values ---

    @Test
    fun `config has correct default values`() {
        val config = RiviumTraceConfig(apiKey = "rv_live_test")
        assertEquals("production", config.environment)
        assertNull(config.release)
        assertFalse(config.debug)
        assertTrue(config.enabled)
        assertTrue(config.captureUncaughtExceptions)
        assertTrue(config.captureSignalCrashes)
        assertTrue(config.captureAnr)
        assertEquals(5000L, config.anrTimeoutMs)
        assertEquals(20, config.maxBreadcrumbs)
        assertEquals(30, config.httpTimeout)
        assertTrue(config.enableOfflineStorage)
        assertEquals(1.0f, config.sampleRate, 0.001f)
    }

    // --- Parameter Validation ---

    @Test(expected = IllegalArgumentException::class)
    fun `config rejects zero maxBreadcrumbs`() {
        RiviumTraceConfig(apiKey = "rv_live_test", maxBreadcrumbs = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `config rejects negative maxBreadcrumbs`() {
        RiviumTraceConfig(apiKey = "rv_live_test", maxBreadcrumbs = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `config rejects zero httpTimeout`() {
        RiviumTraceConfig(apiKey = "rv_live_test", httpTimeout = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `config rejects negative httpTimeout`() {
        RiviumTraceConfig(apiKey = "rv_live_test", httpTimeout = -5)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `config rejects sampleRate below 0`() {
        RiviumTraceConfig(apiKey = "rv_live_test", sampleRate = -0.1f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `config rejects sampleRate above 1`() {
        RiviumTraceConfig(apiKey = "rv_live_test", sampleRate = 1.1f)
    }

    @Test
    fun `config accepts sampleRate at boundaries`() {
        val configZero = RiviumTraceConfig(apiKey = "rv_live_test", sampleRate = 0.0f)
        assertEquals(0.0f, configZero.sampleRate, 0.001f)

        val configOne = RiviumTraceConfig(apiKey = "rv_live_test", sampleRate = 1.0f)
        assertEquals(1.0f, configOne.sampleRate, 0.001f)
    }

    @Test
    fun `config accepts valid custom values`() {
        val config = RiviumTraceConfig(
            apiKey = "rv_test_custom",
            environment = "staging",
            release = "1.2.3",
            debug = true,
            enabled = false,
            captureUncaughtExceptions = false,
            captureSignalCrashes = false,
            captureAnr = false,
            anrTimeoutMs = 10000L,
            maxBreadcrumbs = 50,
            httpTimeout = 60,
            enableOfflineStorage = false,
            sampleRate = 0.5f
        )

        assertEquals("rv_test_custom", config.apiKey)
        assertEquals("staging", config.environment)
        assertEquals("1.2.3", config.release)
        assertTrue(config.debug)
        assertFalse(config.enabled)
        assertFalse(config.captureUncaughtExceptions)
        assertFalse(config.captureSignalCrashes)
        assertFalse(config.captureAnr)
        assertEquals(10000L, config.anrTimeoutMs)
        assertEquals(50, config.maxBreadcrumbs)
        assertEquals(60, config.httpTimeout)
        assertFalse(config.enableOfflineStorage)
        assertEquals(0.5f, config.sampleRate, 0.001f)
    }

    // --- Builder ---

    @Test
    fun `builder creates config with defaults`() {
        val config = RiviumTraceConfig.Builder("rv_live_builder").build()
        assertEquals("rv_live_builder", config.apiKey)
        assertEquals("production", config.environment)
        assertNull(config.release)
        assertFalse(config.debug)
        assertTrue(config.enabled)
    }

    @Test
    fun `builder sets all properties`() {
        val config = RiviumTraceConfig.Builder("rv_test_builder")
            .environment("development")
            .release("2.0.0")
            .debug(true)
            .enabled(false)
            .captureUncaughtExceptions(false)
            .captureSignalCrashes(false)
            .captureAnr(false)
            .anrTimeoutMs(8000L)
            .maxBreadcrumbs(100)
            .httpTimeout(45)
            .enableOfflineStorage(false)
            .sampleRate(0.75f)
            .build()

        assertEquals("rv_test_builder", config.apiKey)
        assertEquals("development", config.environment)
        assertEquals("2.0.0", config.release)
        assertTrue(config.debug)
        assertFalse(config.enabled)
        assertFalse(config.captureUncaughtExceptions)
        assertFalse(config.captureSignalCrashes)
        assertFalse(config.captureAnr)
        assertEquals(8000L, config.anrTimeoutMs)
        assertEquals(100, config.maxBreadcrumbs)
        assertEquals(45, config.httpTimeout)
        assertFalse(config.enableOfflineStorage)
        assertEquals(0.75f, config.sampleRate, 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `builder validates api key on build`() {
        RiviumTraceConfig.Builder("bad_key").build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `builder validates sampleRate on build`() {
        RiviumTraceConfig.Builder("rv_live_test")
            .sampleRate(2.0f)
            .build()
    }

    // --- Companion ---

    @Test
    fun `simple factory creates config with defaults`() {
        val config = RiviumTraceConfig.simple("rv_live_simple")
        assertEquals("rv_live_simple", config.apiKey)
        assertEquals("production", config.environment)
        assertTrue(config.enabled)
    }

    // --- Data Class Equality ---

    @Test
    fun `configs with same values are equal`() {
        val config1 = RiviumTraceConfig(apiKey = "rv_live_test", environment = "staging")
        val config2 = RiviumTraceConfig(apiKey = "rv_live_test", environment = "staging")
        assertEquals(config1, config2)
    }

    @Test
    fun `configs with different values are not equal`() {
        val config1 = RiviumTraceConfig(apiKey = "rv_live_test", environment = "staging")
        val config2 = RiviumTraceConfig(apiKey = "rv_live_test", environment = "production")
        assertNotEquals(config1, config2)
    }

    @Test
    fun `config copy works correctly`() {
        val original = RiviumTraceConfig(apiKey = "rv_live_test", debug = false)
        val copy = original.copy(debug = true)
        assertFalse(original.debug)
        assertTrue(copy.debug)
        assertEquals(original.apiKey, copy.apiKey)
    }
}

package co.rivium.trace.sdk.services

import android.content.Context
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.File

class CrashDetectorTest {

    private lateinit var mockContext: Context
    private lateinit var crashDetector: CrashDetector
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = createTempDir("rivium_trace_test")
        mockContext = mock(Context::class.java)
        `when`(mockContext.filesDir).thenReturn(tempDir)
        crashDetector = CrashDetector(mockContext)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun getSessionFile(): File = File(tempDir, "rivium_trace_session.txt")
    private fun getCleanExitFile(): File = File(tempDir, "rivium_trace_clean_exit.txt")

    // --- createMarker ---

    @Test
    fun `createMarker creates session file`() {
        crashDetector.createMarker("session_123")
        assertTrue(getSessionFile().exists())
    }

    @Test
    fun `createMarker writes timestamp and session id`() {
        crashDetector.createMarker("session_abc")
        val content = getSessionFile().readText()
        val lines = content.split("\n")

        assertTrue(lines[0].toLongOrNull() != null) // timestamp
        assertEquals("session_abc", lines[1]) // session id
    }

    @Test
    fun `createMarker with null session id`() {
        crashDetector.createMarker(null)
        val content = getSessionFile().readText()
        val lines = content.split("\n")

        assertTrue(lines[0].toLongOrNull() != null)
        assertEquals("", lines[1]) // empty session id
    }

    @Test
    fun `createMarker removes existing clean exit file`() {
        getCleanExitFile().writeText("123")
        assertTrue(getCleanExitFile().exists())

        crashDetector.createMarker("session")
        assertFalse(getCleanExitFile().exists())
    }

    // --- hasMarker ---

    @Test
    fun `hasMarker returns false when no marker`() {
        assertFalse(crashDetector.hasMarker())
    }

    @Test
    fun `hasMarker returns true when marker exists`() {
        crashDetector.createMarker("test")
        assertTrue(crashDetector.hasMarker())
    }

    // --- markCleanExit ---

    @Test
    fun `markCleanExit creates clean exit file`() {
        crashDetector.markCleanExit()
        assertTrue(getCleanExitFile().exists())
    }

    @Test
    fun `markCleanExit writes timestamp`() {
        crashDetector.markCleanExit()
        val content = getCleanExitFile().readText()
        assertNotNull(content.toLongOrNull())
    }

    // --- clearCleanExit ---

    @Test
    fun `clearCleanExit removes clean exit file`() {
        crashDetector.markCleanExit()
        assertTrue(getCleanExitFile().exists())

        crashDetector.clearCleanExit()
        assertFalse(getCleanExitFile().exists())
    }

    @Test
    fun `clearCleanExit does nothing if no file`() {
        assertFalse(getCleanExitFile().exists())
        crashDetector.clearCleanExit() // should not throw
        assertFalse(getCleanExitFile().exists())
    }

    // --- deleteMarker ---

    @Test
    fun `deleteMarker removes both files`() {
        crashDetector.createMarker("test")
        crashDetector.markCleanExit()
        assertTrue(getSessionFile().exists())
        assertTrue(getCleanExitFile().exists())

        crashDetector.deleteMarker()
        assertFalse(getSessionFile().exists())
        assertFalse(getCleanExitFile().exists())
    }

    @Test
    fun `deleteMarker does nothing if no files`() {
        crashDetector.deleteMarker() // should not throw
    }

    // --- checkForCrash ---

    @Test
    fun `checkForCrash returns null when no session file`() {
        assertNull(crashDetector.checkForCrash())
    }

    @Test
    fun `checkForCrash returns null when clean exit exists`() {
        crashDetector.createMarker("session")
        crashDetector.markCleanExit()

        val result = crashDetector.checkForCrash()
        assertNull(result)
    }

    @Test
    fun `checkForCrash detects crash when session exists without clean exit`() {
        // Simulate: session was started but app crashed (no clean exit)
        val timestamp = System.currentTimeMillis()
        getSessionFile().writeText("$timestamp\nsession_crash\nMainActivity")

        val result = crashDetector.checkForCrash()
        assertNotNull(result)
        assertEquals(timestamp, result!!.timestamp)
        assertEquals("session_crash", result.sessionId)
        assertEquals("MainActivity", result.lastActivity)
    }

    @Test
    fun `checkForCrash cleans up session file after detection`() {
        val timestamp = System.currentTimeMillis()
        getSessionFile().writeText("$timestamp\nsession1\n")

        crashDetector.checkForCrash()
        assertFalse(getSessionFile().exists())
    }

    @Test
    fun `checkForCrash ignores sessions older than 24 hours`() {
        val oldTimestamp = System.currentTimeMillis() - (25 * 60 * 60 * 1000L) // 25 hours ago
        getSessionFile().writeText("$oldTimestamp\nsession_old\n")

        val result = crashDetector.checkForCrash()
        assertNull(result)
    }

    @Test
    fun `checkForCrash accepts sessions within 24 hours`() {
        val recentTimestamp = System.currentTimeMillis() - (23 * 60 * 60 * 1000L) // 23 hours ago
        getSessionFile().writeText("$recentTimestamp\nsession_recent\n")

        val result = crashDetector.checkForCrash()
        assertNotNull(result)
    }

    @Test
    fun `checkForCrash handles missing session id`() {
        val timestamp = System.currentTimeMillis()
        getSessionFile().writeText("$timestamp\n\n")

        val result = crashDetector.checkForCrash()
        assertNotNull(result)
        assertNull(result!!.sessionId) // blank => null
    }

    @Test
    fun `checkForCrash handles missing last activity`() {
        val timestamp = System.currentTimeMillis()
        getSessionFile().writeText("$timestamp\nsession1\n")

        val result = crashDetector.checkForCrash()
        assertNotNull(result)
        assertNull(result!!.lastActivity) // blank => null
    }

    @Test
    fun `checkForCrash calculates time since crash`() {
        val timestamp = System.currentTimeMillis() - 5000 // 5 seconds ago
        getSessionFile().writeText("$timestamp\nsession1\n")

        val result = crashDetector.checkForCrash()
        assertNotNull(result)
        assertTrue(result!!.timeSinceCrashSeconds >= 4) // at least 4 seconds
        assertTrue(result.timeSinceCrashSeconds <= 10)   // no more than 10 seconds
    }

    @Test
    fun `checkForCrash cleans up clean exit file`() {
        crashDetector.createMarker("test")
        crashDetector.markCleanExit()
        assertTrue(getCleanExitFile().exists())

        crashDetector.checkForCrash()
        assertFalse(getCleanExitFile().exists())
    }

    @Test
    fun `checkForCrash returns null for corrupt session file`() {
        getSessionFile().writeText("not_a_timestamp\nsession\n")

        val result = crashDetector.checkForCrash()
        assertNull(result)
    }

    // --- updateLastActivity ---

    @Test
    fun `updateLastActivity updates session file`() {
        crashDetector.createMarker("session1")
        crashDetector.updateLastActivity("SettingsActivity")

        val content = getSessionFile().readText()
        val lines = content.split("\n")
        assertEquals("SettingsActivity", lines[2])
    }

    @Test
    fun `updateLastActivity does nothing if no session file`() {
        crashDetector.updateLastActivity("SomeActivity") // should not throw
        assertFalse(getSessionFile().exists())
    }

    @Test
    fun `updateLastActivity overwrites previous activity`() {
        crashDetector.createMarker("session1")
        crashDetector.updateLastActivity("FirstActivity")
        crashDetector.updateLastActivity("SecondActivity")

        val content = getSessionFile().readText()
        val lines = content.split("\n")
        assertEquals("SecondActivity", lines[2])
    }

    // --- CrashInfo data class ---

    @Test
    fun `CrashInfo holds correct values`() {
        val info = CrashDetector.CrashInfo(
            timestamp = 1000L,
            sessionId = "sess1",
            lastActivity = "Main",
            timeSinceCrashSeconds = 60
        )
        assertEquals(1000L, info.timestamp)
        assertEquals("sess1", info.sessionId)
        assertEquals("Main", info.lastActivity)
        assertEquals(60L, info.timeSinceCrashSeconds)
    }

    @Test
    fun `CrashInfo equality`() {
        val info1 = CrashDetector.CrashInfo(1000, "s1", "A", 10)
        val info2 = CrashDetector.CrashInfo(1000, "s1", "A", 10)
        assertEquals(info1, info2)
    }
}

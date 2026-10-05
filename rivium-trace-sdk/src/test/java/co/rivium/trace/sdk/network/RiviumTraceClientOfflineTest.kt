package co.rivium.trace.sdk.network

import co.rivium.trace.sdk.RiviumTraceConfig
import co.rivium.trace.sdk.models.RiviumTraceError
import co.rivium.trace.sdk.services.OfflineErrorStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * End-to-end tests of the error send path against a local HTTP server
 * (reachable = HTTP answer) and a closed local port (network failure).
 */
class RiviumTraceClientOfflineTest {

    private data class Received(val path: String, val apiKey: String?, val userAgent: String?, val body: String)

    @get:Rule
    val temp = TemporaryFolder()

    private var server: ServerSocket? = null
    private val received = Collections.synchronizedList(mutableListOf<Received>())
    @Volatile private var status = 200

    private val apiKey = "rv_live_secret_key_123"

    @After
    fun tearDown() {
        try { server?.close() } catch (_: Exception) {}
    }

    /** Minimal HTTP/1.1 server: records each request and answers with [status]. */
    private fun startServer(): String {
        try { server?.close() } catch (_: Exception) {}
        val s = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        server = s
        Thread {
            while (!s.isClosed) {
                val socket = try { s.accept() } catch (e: Exception) { break }
                Thread { socket.use { handle(it) } }.apply { isDaemon = true }.start()
            }
        }.apply { isDaemon = true }.start()
        return "http://127.0.0.1:${s.localPort}"
    }

    private fun handle(socket: Socket) {
        try {
            val input = socket.getInputStream()
            val head = ByteArrayOutputStream()
            while (!head.toString("ISO-8859-1").endsWith("\r\n\r\n")) {
                val b = input.read()
                if (b < 0) return
                head.write(b)
            }
            val lines = head.toString("ISO-8859-1").trim().split("\r\n")
            val path = lines.first().split(" ")[1]
            val headers = lines.drop(1).associate {
                it.substringBefore(":").trim().lowercase() to it.substringAfter(":").trim()
            }
            val length = headers["content-length"]?.toInt() ?: 0
            val body = ByteArray(length)
            var read = 0
            while (read < length) {
                val n = input.read(body, read, length - read)
                if (n < 0) break
                read += n
            }
            received += Received(path, headers["x-api-key"], headers["user-agent"], String(body, Charsets.UTF_8))

            val answer = "HTTP/1.1 $status Status\r\nContent-Type: application/json\r\n" +
                "Content-Length: 2\r\nConnection: close\r\n\r\n{}"
            socket.getOutputStream().apply {
                write(answer.toByteArray(Charsets.ISO_8859_1))
                flush()
            }
        } catch (_: Exception) {
        }
    }

    /** A URL nothing listens on: every request fails with an IOException. */
    private fun deadUrl(): String {
        val port = ServerSocket(0).use { it.localPort }
        return "http://127.0.0.1:$port"
    }

    private fun client(url: String, offline: Boolean = true): RiviumTraceClient {
        val config = RiviumTraceConfig(
            apiKey = apiKey,
            apiUrl = url,
            httpTimeout = 5,
            enableOfflineStorage = offline
        )
        return RiviumTraceClient(config).apply {
            offlineStore = OfflineErrorStore.createIfEnabled(config) { temp.root }
        }
    }

    private fun store() = OfflineErrorStore({ temp.root })
    private fun dataFile() = File(temp.root, OfflineErrorStore.FILE_NAME)

    private fun error(message: String) = RiviumTraceError(
        message = message,
        stackTrace = "java.lang.IllegalStateException: $message\n\tat a.B.c(B.kt:1)",
        timestamp = 1700000000000L,
        extra = mapOf("user_id" to "u1", "count" to 3),
        tags = mapOf("screen" to "home")
    )

    private fun sendAndWait(client: RiviumTraceClient, error: RiviumTraceError): Pair<Boolean, String?> {
        val latch = CountDownLatch(1)
        var result: Pair<Boolean, String?> = false to "no callback"
        client.sendError(error) { ok, detail ->
            result = ok to detail
            latch.countDown()
        }
        assertTrue("callback not invoked", latch.await(20, TimeUnit.SECONDS))
        return result
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        fail("timed out waiting for: $what")
    }

    // --- Storing ---

    @Test
    fun `network failure stores the exact body that would have been sent`() {
        // What the server gets when the send works
        val online = client(startServer(), offline = false)
        assertTrue(sendAndWait(online, error("boom")).first)
        val sentBody = received.single().body

        // Same error, unreachable server
        val offline = client(deadUrl())
        val (ok, _) = sendAndWait(offline, error("boom"))

        assertFalse(ok)
        assertEquals(listOf(sentBody), store().readAll().map { it.body })
    }

    @Test
    fun `stored file never contains the api key`() {
        val offline = client(deadUrl())
        sendAndWait(offline, error("boom"))

        assertTrue(dataFile().exists())
        assertFalse(dataFile().readText().contains(apiKey))
        assertFalse(dataFile().readText().contains("rv_live_"))
    }

    @Test
    fun `http error response is not stored`() {
        val client = client(startServer())
        for (code in listOf(400, 401, 429, 500, 503)) {
            status = code
            assertFalse("code=$code", sendAndWait(client, error("boom $code")).first)
        }

        assertEquals(5, received.size)
        assertTrue(store().readAll().isEmpty())
        assertFalse(dataFile().exists())
    }

    @Test
    fun `disabled option stores nothing and does not touch the disk`() {
        val client = client(deadUrl(), offline = false)
        assertNull(client.offlineStore)

        assertFalse(sendAndWait(client, error("boom")).first)
        assertFalse(client.sendErrorSyncOrStore(error("crash")))
        client.flushStoredErrors()

        assertTrue(temp.root.listFiles()!!.isEmpty())
    }

    @Test
    fun `messages are not stored`() {
        val client = client(deadUrl())
        val latch = CountDownLatch(1)
        client.sendMessage(error("just a message")) { _, _ -> latch.countDown() }
        assertTrue(latch.await(20, TimeUnit.SECONDS))

        assertFalse(dataFile().exists())
    }

    // --- Crash paths ---

    @Test
    fun `sendErrorSync never stores so previous-session crash reports are not queued twice`() {
        val client = client(deadUrl())

        assertFalse(client.sendErrorSync(error("native crash")))

        assertFalse(dataFile().exists())
    }

    @Test
    fun `uncaught exception path stores on network failure`() {
        val client = client(deadUrl())

        assertFalse(client.sendErrorSyncOrStore(error("crash")))

        assertEquals(1, store().readAll().size)
    }

    @Test
    fun `uncaught exception path does not store on http error`() {
        status = 500
        val client = client(startServer())

        assertFalse(client.sendErrorSyncOrStore(error("crash")))

        assertFalse(dataFile().exists())
    }

    // --- Resending ---

    @Test
    fun `stored errors are resent with the current key and removed on 2xx`() {
        sendAndWait(client(deadUrl()), error("stored while offline"))
        val storedBody = store().readAll().single().body

        // Network is back (next launch)
        val client = client(startServer())
        client.flushStoredErrors()
        waitUntil("stored error resent") { received.size == 1 && !dataFile().exists() }

        val request = received.single()
        assertEquals("/api/errors", request.path)
        assertEquals(apiKey, request.apiKey)
        assertTrue(request.userAgent!!.startsWith("RiviumTrace-SDK/"))
        assertEquals(storedBody, request.body)

        // A second pass has nothing left to send
        client.flushStoredErrors()
        Thread.sleep(200)
        assertEquals(1, received.size)
    }

    @Test
    fun `a successful send triggers resending of stored errors exactly once each`() {
        val offline = client(deadUrl())
        sendAndWait(offline, error("offline 1"))
        sendAndWait(offline, error("offline 2"))
        assertEquals(2, store().readAll().size)

        val client = client(startServer())
        assertTrue(sendAndWait(client, error("live")).first)
        waitUntil("stored errors resent") { received.size == 3 && !dataFile().exists() }

        // Later sends do not resend anything again
        assertTrue(sendAndWait(client, error("live 2")).first)
        Thread.sleep(300)

        val messages = received.map { it.body }
        assertEquals(4, messages.size)
        assertEquals(1, messages.count { it.contains("offline 1") })
        assertEquals(1, messages.count { it.contains("offline 2") })
    }

    @Test
    fun `stored errors are dropped on 4xx and kept on 5xx`() {
        sendAndWait(client(deadUrl()), error("stored"))

        status = 503
        val client = client(startServer())
        client.flushStoredErrors()
        waitUntil("first attempt") { received.size == 1 }
        Thread.sleep(200)
        assertEquals(1, store().readAll().size)

        status = 422
        val next = client(startServer().also { received.clear() })
        next.flushStoredErrors()
        waitUntil("rejected entry dropped") { received.size == 1 && !dataFile().exists() }
    }

    @Test
    fun `stored errors are kept while the network is still down`() {
        val offline = client(deadUrl())
        sendAndWait(offline, error("stored 1"))
        sendAndWait(offline, error("stored 2"))

        offline.flushStoredErrors()
        Thread.sleep(500)

        assertEquals(2, store().readAll().size)
    }
}

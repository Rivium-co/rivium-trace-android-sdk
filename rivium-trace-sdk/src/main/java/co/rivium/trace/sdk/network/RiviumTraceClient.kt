package co.rivium.trace.sdk.network

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import co.rivium.trace.sdk.RiviumTraceConfig
import co.rivium.trace.sdk.models.*
import co.rivium.trace.sdk.services.OfflineErrorStore
import co.rivium.trace.sdk.services.SendOutcome
import co.rivium.trace.sdk.utils.RiviumTraceLogger
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * HTTP client for RiviumTrace API
 */
class RiviumTraceClient(
    private val config: RiviumTraceConfig
) {
    companion object {
        private val JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8")
        private const val API_KEY_HEADER = "X-API-Key"

        // Longest the crash handler waits for a failed report to reach disk.
        private const val CRASH_STORE_TIMEOUT_MS = 2000L
    }

    /**
     * Disk queue for errors that could not be sent because the network was
     * unavailable. Null when offline storage is disabled.
     */
    @Volatile
    internal var offlineStore: OfflineErrorStore? = null

    private val flushScheduled = AtomicBoolean(false)

    private val baseUrl: String = config.apiUrl.trimEnd('/')

    private val gson: Gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .create()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(config.httpTimeout.toLong(), TimeUnit.SECONDS)
        .readTimeout(config.httpTimeout.toLong(), TimeUnit.SECONDS)
        .writeTimeout(config.httpTimeout.toLong(), TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Send an error to RiviumTrace
     */
    fun sendError(error: RiviumTraceError, callback: ((Boolean, String?) -> Unit)? = null) {
        val json = gson.toJson(error.toMap())
        val request = buildErrorRequest(json)

        RiviumTraceLogger.debug("Sending error to RiviumTrace: ${error.message}")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                RiviumTraceLogger.error("Failed to send error: ${e.message}")
                // No answer from the server: keep the report for later.
                // Runs on OkHttp's dispatcher thread, never the main thread.
                offlineStore?.store(json)
                callback?.invoke(false, e.message)
            }

            override fun onResponse(call: Call, response: Response) {
                val success = response.isSuccessful
                val responseBody = response.body()?.string()
                response.close()

                if (success) {
                    RiviumTraceLogger.debug("Error sent successfully")
                } else {
                    RiviumTraceLogger.error("Error response: $responseBody")
                }

                try {
                    callback?.invoke(success, responseBody)
                } finally {
                    // The network is back: try the stored errors too.
                    if (success) flushStoredErrors()
                }
            }
        })
    }

    /**
     * Send an error synchronously (for use in crash handlers)
     */
    fun sendErrorSync(error: RiviumTraceError): Boolean {
        // Never stored offline: callers of this method (the previous-session
        // crash drain) keep their own record of what still has to be sent.
        return sendErrorSync(error, storeOnNetworkFailure = false)
    }

    /**
     * Send an error synchronously and, if the network is unavailable, keep
     * it on disk for a later launch. Used by the uncaught exception handler,
     * where the process is about to die and nothing else would retry.
     */
    internal fun sendErrorSyncOrStore(error: RiviumTraceError): Boolean {
        return sendErrorSync(error, storeOnNetworkFailure = true)
    }

    private fun sendErrorSync(error: RiviumTraceError, storeOnNetworkFailure: Boolean): Boolean {
        val url = "$baseUrl/api/errors"
        val json = gson.toJson(error.toMap())

        return try {
            val response = client.newCall(buildErrorRequest(json)).execute()
            val success = response.isSuccessful
            if (!success) {
                RiviumTraceLogger.error("sendErrorSync HTTP ${response.code()} url=$url")
            }
            response.close()
            success
        } catch (e: Exception) {
            RiviumTraceLogger.error(
                "sendErrorSync ${e.javaClass.simpleName}: ${e.message ?: "no message"} url=$url"
            )
            if (storeOnNetworkFailure && e is IOException) {
                storeOfflineBounded(json)
            }
            false
        }
    }

    /**
     * Resend errors that were stored while the network was unavailable.
     * Returns immediately; the work happens on a background thread, and only
     * one pass runs at a time.
     */
    internal fun flushStoredErrors() {
        val store = offlineStore ?: return
        if (!store.mayHavePending) return
        if (!flushScheduled.compareAndSet(false, true)) return

        try {
            Thread({
                try {
                    store.flush { body -> postStoredError(body) }
                } catch (t: Throwable) {
                    RiviumTraceLogger.error("Sending stored errors failed: ${t.message}")
                } finally {
                    flushScheduled.set(false)
                }
            }, "RiviumTrace-OfflineFlush").apply {
                isDaemon = true
                start()
            }
        } catch (t: Throwable) {
            flushScheduled.set(false)
            RiviumTraceLogger.error("Could not start sending stored errors: ${t.message}")
        }
    }

    /**
     * Post a stored error body with the current API key and headers.
     * Blocking; called from the offline flush thread only.
     */
    private fun postStoredError(json: String): SendOutcome {
        return try {
            val response = client.newCall(buildErrorRequest(json)).execute()
            val code = response.code()
            response.close()
            SendOutcome.Http(code)
        } catch (e: IOException) {
            SendOutcome.NetworkFailure
        } catch (e: Exception) {
            RiviumTraceLogger.error("Stored error could not be sent: ${e.message}")
            SendOutcome.Failed
        }
    }

    /**
     * Store from the crash handler. The crashing thread may be the main
     * thread, so the write happens on a helper thread and the caller waits
     * a bounded time for it.
     */
    private fun storeOfflineBounded(json: String) {
        val store = offlineStore ?: return
        try {
            val writer = Thread({ store.store(json) }, "RiviumTrace-OfflineStore")
            writer.isDaemon = true
            writer.start()
            writer.join(CRASH_STORE_TIMEOUT_MS)
        } catch (t: Throwable) {
            RiviumTraceLogger.error("Failed to store crash report offline: ${t.message}")
        }
    }

    private fun buildErrorRequest(json: String): Request {
        return Request.Builder()
            .url("$baseUrl/api/errors")
            .post(RequestBody.create(JSON_MEDIA_TYPE, json))
            .addHeader("Content-Type", "application/json")
            .addHeader(API_KEY_HEADER, config.apiKey)
            .addHeader("User-Agent", "RiviumTrace-SDK/${co.rivium.trace.sdk.BuildConfig.SDK_VERSION} (android)")
            .build()
    }

    /**
     * Send a message to RiviumTrace
     */
    fun sendMessage(message: RiviumTraceError, callback: ((Boolean, String?) -> Unit)? = null) {
        val url = "$baseUrl/api/messages"
        val json = gson.toJson(message.toMap())
        val body = RequestBody.create(JSON_MEDIA_TYPE, json)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .addHeader(API_KEY_HEADER, config.apiKey)
            .addHeader("User-Agent", "RiviumTrace-SDK/${co.rivium.trace.sdk.BuildConfig.SDK_VERSION} (android)")
            .build()

        RiviumTraceLogger.debug("Sending message to RiviumTrace: ${message.message}")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                RiviumTraceLogger.error("Failed to send message: ${e.message}")
                callback?.invoke(false, e.message)
            }

            override fun onResponse(call: Call, response: Response) {
                val success = response.isSuccessful
                val responseBody = response.body()?.string()
                response.close()

                if (success) {
                    RiviumTraceLogger.debug("Message sent successfully")
                } else {
                    RiviumTraceLogger.error("Message response: $responseBody")
                }

                callback?.invoke(success, responseBody)
            }
        })
    }

    /**
     * Send a performance span to RiviumTrace APM
     */
    fun sendPerformanceSpan(span: PerformanceSpan, callback: ((Boolean, String?) -> Unit)? = null) {
        val url = "$baseUrl/api/performance/spans"
        val json = gson.toJson(span.toMap())
        val body = RequestBody.create(JSON_MEDIA_TYPE, json)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .addHeader(API_KEY_HEADER, config.apiKey)
            .addHeader("User-Agent", "RiviumTrace-SDK/${co.rivium.trace.sdk.BuildConfig.SDK_VERSION} (android)")
            .build()

        RiviumTraceLogger.debug("Sending performance span: ${span.operation}")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                RiviumTraceLogger.error("Failed to send span: ${e.message}")
                callback?.invoke(false, e.message)
            }

            override fun onResponse(call: Call, response: Response) {
                val success = response.isSuccessful
                val responseBody = response.body()?.string()
                response.close()

                if (success) {
                    RiviumTraceLogger.debug("Span sent successfully")
                } else {
                    RiviumTraceLogger.error("Span response: $responseBody")
                }

                callback?.invoke(success, responseBody)
            }
        })
    }

    /**
     * Send multiple performance spans in a batch
     */
    fun sendPerformanceSpanBatch(spans: List<PerformanceSpan>, callback: ((Boolean, String?) -> Unit)? = null) {
        if (spans.isEmpty()) {
            callback?.invoke(true, null)
            return
        }

        val url = "$baseUrl/api/performance/spans/batch"
        val json = gson.toJson(mapOf("spans" to spans.map { it.toMap() }))
        val body = RequestBody.create(JSON_MEDIA_TYPE, json)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .addHeader(API_KEY_HEADER, config.apiKey)
            .addHeader("User-Agent", "RiviumTrace-SDK/${co.rivium.trace.sdk.BuildConfig.SDK_VERSION} (android)")
            .build()

        RiviumTraceLogger.debug("Sending ${spans.size} performance spans")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                RiviumTraceLogger.error("Failed to send span batch: ${e.message}")
                callback?.invoke(false, e.message)
            }

            override fun onResponse(call: Call, response: Response) {
                val success = response.isSuccessful
                val responseBody = response.body()?.string()
                response.close()

                if (success) {
                    RiviumTraceLogger.debug("Span batch sent successfully")
                } else {
                    RiviumTraceLogger.error("Span batch response: $responseBody")
                }

                callback?.invoke(success, responseBody)
            }
        })
    }

    /**
     * Shutdown the client
     */
    fun shutdown() {
        client.dispatcher().executorService().shutdown()
        client.connectionPool().evictAll()
    }
}

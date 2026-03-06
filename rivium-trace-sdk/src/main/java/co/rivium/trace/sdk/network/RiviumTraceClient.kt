package co.rivium.trace.sdk.network

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import co.rivium.trace.sdk.RiviumTraceConfig
import co.rivium.trace.sdk.models.*
import co.rivium.trace.sdk.utils.RiviumTraceLogger
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * HTTP client for RiviumTrace API
 */
class RiviumTraceClient(
    private val config: RiviumTraceConfig
) {
    companion object {
        private const val BASE_URL = "https://trace.rivium.co"
        private val JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8")
        private const val API_KEY_HEADER = "X-API-Key"
    }

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
        val url = "$BASE_URL/api/errors"
        val json = gson.toJson(error.toMap())
        val body = RequestBody.create(JSON_MEDIA_TYPE, json)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .addHeader(API_KEY_HEADER, config.apiKey)
            .addHeader("User-Agent", "RiviumTrace-SDK/${co.rivium.trace.sdk.BuildConfig.SDK_VERSION} (android)")
            .build()

        RiviumTraceLogger.debug("Sending error to RiviumTrace: ${error.message}")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                RiviumTraceLogger.error("Failed to send error: ${e.message}")
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

                callback?.invoke(success, responseBody)
            }
        })
    }

    /**
     * Send an error synchronously (for use in crash handlers)
     */
    fun sendErrorSync(error: RiviumTraceError): Boolean {
        val url = "$BASE_URL/api/errors"
        val json = gson.toJson(error.toMap())
        val body = RequestBody.create(JSON_MEDIA_TYPE, json)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .addHeader(API_KEY_HEADER, config.apiKey)
            .addHeader("User-Agent", "RiviumTrace-SDK/${co.rivium.trace.sdk.BuildConfig.SDK_VERSION} (android)")
            .build()

        return try {
            val response = client.newCall(request).execute()
            val success = response.isSuccessful
            response.close()
            success
        } catch (e: Exception) {
            RiviumTraceLogger.error("Failed to send error sync: ${e.message}")
            false
        }
    }

    /**
     * Send a message to RiviumTrace
     */
    fun sendMessage(message: RiviumTraceError, callback: ((Boolean, String?) -> Unit)? = null) {
        val url = "$BASE_URL/api/messages"
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
        val url = "$BASE_URL/api/performance/spans"
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

        val url = "$BASE_URL/api/performance/spans/batch"
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

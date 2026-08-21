package com.amaanb.androidbuzzer.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class HttpBuzzerApi(
    baseUrl: String = DEFAULT_BASE_URL,
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    },
) : BuzzerApi {
    private val baseUrl = baseUrl.trimEnd('/')

    override suspend fun getStatus(): BuzzerStatus = execute("api/status")

    override suspend fun ring(): BuzzerStatus = execute("api/ring", post = true)

    override suspend fun stop(): BuzzerStatus = execute("api/stop", post = true)

    private suspend fun execute(path: String, post: Boolean = false): BuzzerStatus {
        val requestBuilder = Request.Builder()
            .url("$baseUrl/$path")
            .header("Accept", JSON_MEDIA_TYPE.toString())

        if (post) {
            requestBuilder.post(ByteArray(0).toRequestBody(null))
        }

        val call = client.newCall(requestBuilder.build()).apply {
            timeout().timeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
        val responseBody = call.await().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Buzzer returned HTTP ${response.code}")
            }
            response.body.string()
        }

        return try {
            json.decodeFromString<BuzzerStatus>(responseBody)
        } catch (error: SerializationException) {
            throw IOException("Buzzer returned an invalid status", error)
        }
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (continuation.isActive) {
                        continuation.resume(response)
                    } else {
                        response.close()
                    }
                }
            },
        )
    }

    companion object {
        const val DEFAULT_BASE_URL = "http://192.168.50.50"
        private const val CALL_TIMEOUT_SECONDS = 2L
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .writeTimeout(3, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
}

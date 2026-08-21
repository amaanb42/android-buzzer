package com.amaanb.androidbuzzer.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BuzzerStatus(
    val ringing: Boolean,
    val source: BuzzerChangeSource? = null,
)

@Serializable
enum class BuzzerChangeSource {
    @SerialName("startup")
    Startup,

    @SerialName("api")
    Api,

    @SerialName("bedroom_button")
    BedroomButton,

    @SerialName("hc12")
    Hc12,

    @SerialName("timeout")
    Timeout,
}

enum class BuzzerCommand {
    Ring,
    Stop,
}

interface BuzzerApi {
    suspend fun getStatus(): BuzzerStatus

    suspend fun ring(): BuzzerStatus

    suspend fun stop(): BuzzerStatus
}

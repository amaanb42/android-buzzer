package com.amaanb.androidbuzzer.data

import kotlinx.serialization.Serializable

@Serializable
data class BuzzerStatus(val ringing: Boolean)

enum class BuzzerCommand {
    Ring,
    Stop,
}

interface BuzzerApi {
    suspend fun getStatus(): BuzzerStatus

    suspend fun ring(): BuzzerStatus

    suspend fun stop(): BuzzerStatus
}

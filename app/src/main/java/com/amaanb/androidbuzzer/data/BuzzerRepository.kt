package com.amaanb.androidbuzzer.data

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface BuzzerRepository {
    fun observeStatus(): Flow<Result<BuzzerStatus>>

    suspend fun send(command: BuzzerCommand): BuzzerStatus
}

class DefaultBuzzerRepository(
    private val api: BuzzerApi,
    private val pollIntervalMillis: Long = 500,
) : BuzzerRepository {
    private val requestMutex = Mutex()

    override fun observeStatus(): Flow<Result<BuzzerStatus>> = flow {
        while (true) {
            val result = try {
                Result.success(requestMutex.withLock { api.getStatus() })
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }
            emit(result)
            delay(pollIntervalMillis)
        }
    }

    override suspend fun send(command: BuzzerCommand): BuzzerStatus = requestMutex.withLock {
        when (command) {
            BuzzerCommand.Ring -> api.ring()
            BuzzerCommand.Stop -> api.stop()
        }
    }
}

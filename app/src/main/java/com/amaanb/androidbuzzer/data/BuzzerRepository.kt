package com.amaanb.androidbuzzer.data

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface BuzzerRepository {
    fun observeStatus(): Flow<Result<BuzzerStatus>>

    suspend fun send(command: BuzzerCommand): CommandOutcome
}

sealed interface CommandOutcome {
    data class Confirmed(val status: BuzzerStatus) : CommandOutcome

    data class NotApplied(val status: BuzzerStatus) : CommandOutcome

    data class Unreachable(val cause: Exception) : CommandOutcome
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

    override suspend fun send(command: BuzzerCommand): CommandOutcome {
        val expectedRinging = command == BuzzerCommand.Ring

        repeat(COMMAND_ATTEMPTS) { attempt ->
            try {
                val status = requestMutex.withLock { sendOnce(command) }
                return if (status.ringing == expectedRinging) {
                    CommandOutcome.Confirmed(status)
                } else {
                    CommandOutcome.NotApplied(status)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (attempt < COMMAND_ATTEMPTS - 1) delay(COMMAND_RETRY_DELAY_MILLIS)
            }
        }

        return try {
            val status = requestMutex.withLock { api.getStatus() }
            if (status.ringing == expectedRinging) {
                CommandOutcome.Confirmed(status)
            } else {
                CommandOutcome.NotApplied(status)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            CommandOutcome.Unreachable(error)
        }
    }

    private suspend fun sendOnce(command: BuzzerCommand): BuzzerStatus = when (command) {
        BuzzerCommand.Ring -> api.ring()
        BuzzerCommand.Stop -> api.stop()
    }

    private companion object {
        const val COMMAND_ATTEMPTS = 2
        const val COMMAND_RETRY_DELAY_MILLIS = 150L
    }
}

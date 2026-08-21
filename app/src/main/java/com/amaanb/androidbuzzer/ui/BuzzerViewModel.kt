package com.amaanb.androidbuzzer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.amaanb.androidbuzzer.data.BuzzerCommand
import com.amaanb.androidbuzzer.data.BuzzerRepository
import com.amaanb.androidbuzzer.data.CommandOutcome
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ConnectionState {
    Checking,
    Connected,
    Offline,
}

data class BuzzerUiState(
    val ringing: Boolean = false,
    val connection: ConnectionState = ConnectionState.Checking,
    val commandInFlight: Boolean = false,
)

sealed interface BuzzerUiEffect {
    data class CommandSucceeded(val command: BuzzerCommand) : BuzzerUiEffect

    data class CommandFailed(val message: String) : BuzzerUiEffect

    data object ExternalAcknowledgement : BuzzerUiEffect
}

class BuzzerViewModel(
    private val repository: BuzzerRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BuzzerUiState())
    val uiState: StateFlow<BuzzerUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<BuzzerUiEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<BuzzerUiEffect> = _effects.asSharedFlow()

    private var pollingJob: Job? = null
    private var commandJob: Job? = null
    private var pollingRequested = false
    private var pollingGeneration = 0L
    private var commandGeneration = 0L
    private var consecutivePollFailures = 0
    private var lastConfirmedRinging: Boolean? = null

    fun startPolling() {
        pollingRequested = true
        startPollingIfNeeded()
    }

    private fun startPollingIfNeeded() {
        if (!pollingRequested || commandJob?.isActive == true || pollingJob?.isActive == true) {
            return
        }

        val generation = ++pollingGeneration
        pollingJob = viewModelScope.launch {
            repository.observeStatus().collectLatest { result ->
                if (generation != pollingGeneration) return@collectLatest
                result.fold(
                    onSuccess = { status ->
                        val externallyStopped = lastConfirmedRinging == true && !status.ringing
                        consecutivePollFailures = 0
                        lastConfirmedRinging = status.ringing
                        _uiState.update {
                            it.copy(ringing = status.ringing, connection = ConnectionState.Connected)
                        }
                        if (externallyStopped) {
                            _effects.tryEmit(BuzzerUiEffect.ExternalAcknowledgement)
                        }
                    },
                    onFailure = {
                        consecutivePollFailures++
                        if (consecutivePollFailures >= OFFLINE_FAILURE_THRESHOLD) {
                            _uiState.update { it.copy(connection = ConnectionState.Offline) }
                        }
                    },
                )
            }
        }
    }

    fun stopPolling() {
        pollingRequested = false
        cancelPolling()
    }

    fun toggleRinging() {
        val targetRinging = !_uiState.value.ringing
        val command = if (targetRinging) BuzzerCommand.Ring else BuzzerCommand.Stop
        val generation = ++commandGeneration

        _uiState.update { it.copy(ringing = targetRinging, commandInFlight = true) }
        commandJob?.cancel()
        cancelPolling()

        commandJob = viewModelScope.launch {
            try {
                val outcome = repository.send(command)
                if (generation != commandGeneration) return@launch

                when (outcome) {
                    is CommandOutcome.Confirmed -> {
                        consecutivePollFailures = 0
                        lastConfirmedRinging = outcome.status.ringing
                        _uiState.update {
                            it.copy(
                                ringing = outcome.status.ringing,
                                connection = ConnectionState.Connected,
                                commandInFlight = false,
                            )
                        }
                        _effects.emit(BuzzerUiEffect.CommandSucceeded(command))
                    }

                    is CommandOutcome.NotApplied -> {
                        consecutivePollFailures = 0
                        lastConfirmedRinging = outcome.status.ringing
                        _uiState.update {
                            it.copy(
                                ringing = outcome.status.ringing,
                                connection = ConnectionState.Connected,
                                commandInFlight = false,
                            )
                        }
                        _effects.emit(BuzzerUiEffect.CommandFailed(notAppliedMessage(command)))
                    }

                    is CommandOutcome.Unreachable -> {
                        consecutivePollFailures = OFFLINE_FAILURE_THRESHOLD
                        _uiState.update {
                            it.copy(
                                ringing = lastConfirmedRinging ?: false,
                                connection = ConnectionState.Offline,
                                commandInFlight = false,
                            )
                        }
                        _effects.emit(
                            BuzzerUiEffect.CommandFailed("Could not reach the bedroom buzzer"),
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (generation == commandGeneration) {
                    consecutivePollFailures = OFFLINE_FAILURE_THRESHOLD
                    _uiState.update {
                        it.copy(
                            ringing = lastConfirmedRinging ?: false,
                            connection = ConnectionState.Offline,
                            commandInFlight = false,
                        )
                    }
                    _effects.emit(
                        BuzzerUiEffect.CommandFailed("Could not reach the bedroom buzzer"),
                    )
                }
            } finally {
                if (generation == commandGeneration) {
                    commandJob = null
                    startPollingIfNeeded()
                }
            }
        }
    }

    private fun cancelPolling() {
        pollingGeneration++
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun notAppliedMessage(command: BuzzerCommand): String = when (command) {
        BuzzerCommand.Ring -> "The bedroom buzzer did not start ringing"
        BuzzerCommand.Stop -> "The bedroom buzzer did not stop"
    }

    class Factory(
        private val repository: BuzzerRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(BuzzerViewModel::class.java))
            return BuzzerViewModel(repository) as T
        }
    }

    private companion object {
        const val OFFLINE_FAILURE_THRESHOLD = 3
    }
}

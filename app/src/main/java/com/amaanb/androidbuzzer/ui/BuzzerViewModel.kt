package com.amaanb.androidbuzzer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.amaanb.androidbuzzer.data.BuzzerCommand
import com.amaanb.androidbuzzer.data.BuzzerChangeSource
import com.amaanb.androidbuzzer.data.BuzzerRepository
import com.amaanb.androidbuzzer.data.BuzzerStatus
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
    val pendingCommand: BuzzerCommand? = null,
    val acknowledgementVisible: Boolean = false,
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
    private var pendingCommandWasAttempted = false

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
                        val pendingCommand = _uiState.value.pendingCommand
                        if (pendingCommand == null) {
                            handlePolledStatus(status)
                        } else {
                            handleReconnectStatus(pendingCommand, status)
                        }
                    },
                    onFailure = {
                        consecutivePollFailures++
                        if (consecutivePollFailures >= OFFLINE_FAILURE_THRESHOLD) {
                            _uiState.update {
                                it.copy(
                                    ringing = if (it.pendingCommand == null) {
                                        lastConfirmedRinging ?: false
                                    } else {
                                        it.ringing
                                    },
                                    connection = ConnectionState.Offline,
                                    commandInFlight = false,
                                )
                            }
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
        pendingCommandWasAttempted = false

        _uiState.update {
            it.copy(
                ringing = targetRinging,
                pendingCommand = command,
                commandInFlight = false,
                acknowledgementVisible = false,
            )
        }

        if (_uiState.value.connection != ConnectionState.Connected) return

        sendCommand(command)
    }

    private fun sendCommand(command: BuzzerCommand) {
        val generation = ++commandGeneration
        pendingCommandWasAttempted = false

        _uiState.update {
            it.copy(
                ringing = command == BuzzerCommand.Ring,
                pendingCommand = null,
                commandInFlight = true,
                acknowledgementVisible = false,
            )
        }
        commandJob?.cancel()
        cancelPolling()

        commandJob = viewModelScope.launch {
            try {
                val outcome = repository.send(command)
                if (generation != commandGeneration) return@launch

                when (outcome) {
                    is CommandOutcome.Confirmed -> {
                        resolveCommandWithStatus(command, outcome.status)
                    }

                    is CommandOutcome.NotApplied -> {
                        resolveCommandWithStatus(command, outcome.status)
                    }

                    is CommandOutcome.Unreachable -> {
                        queueUnreachableCommand(command)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (generation == commandGeneration) {
                    queueUnreachableCommand(command)
                }
            } finally {
                if (generation == commandGeneration) {
                    commandJob = null
                    startPollingIfNeeded()
                }
            }
        }
    }

    private fun queueUnreachableCommand(command: BuzzerCommand) {
        consecutivePollFailures = 0
        pendingCommandWasAttempted = true
        _uiState.update {
            it.copy(
                ringing = command == BuzzerCommand.Ring,
                connection = ConnectionState.Checking,
                commandInFlight = false,
                pendingCommand = command,
            )
        }
    }

    private fun handleReconnectStatus(command: BuzzerCommand, status: BuzzerStatus) {
        consecutivePollFailures = 0
        lastConfirmedRinging = status.ringing
        val quicklyAcknowledged = pendingCommandWasAttempted &&
            command == BuzzerCommand.Ring &&
            !status.ringing &&
            status.source == BuzzerChangeSource.BedroomButton

        if (quicklyAcknowledged) {
            pendingCommandWasAttempted = false
            _uiState.update {
                it.copy(
                    ringing = false,
                    connection = ConnectionState.Connected,
                    commandInFlight = false,
                    pendingCommand = null,
                    acknowledgementVisible = true,
                )
            }
            _effects.tryEmit(BuzzerUiEffect.ExternalAcknowledgement)
            return
        }

        if (status.ringing == (command == BuzzerCommand.Ring)) {
            pendingCommandWasAttempted = false
            _uiState.update {
                it.copy(
                    ringing = status.ringing,
                    connection = ConnectionState.Connected,
                    commandInFlight = false,
                    pendingCommand = null,
                )
            }
            _effects.tryEmit(BuzzerUiEffect.CommandSucceeded(command))
            return
        }

        _uiState.update { it.copy(connection = ConnectionState.Connected) }
        sendCommand(command)
    }

    private fun handlePolledStatus(status: BuzzerStatus) {
        val externallyStopped = lastConfirmedRinging == true &&
            !status.ringing &&
            (status.source == BuzzerChangeSource.BedroomButton || status.source == null)
        consecutivePollFailures = 0
        lastConfirmedRinging = status.ringing
        _uiState.update {
            it.copy(
                ringing = status.ringing,
                connection = ConnectionState.Connected,
                pendingCommand = null,
                acknowledgementVisible = when {
                    externallyStopped -> true
                    status.ringing -> false
                    else -> it.acknowledgementVisible
                },
            )
        }
        if (externallyStopped) {
            _effects.tryEmit(BuzzerUiEffect.ExternalAcknowledgement)
        }
    }

    private fun resolveCommandWithStatus(command: BuzzerCommand, status: BuzzerStatus) {
        val expectedRinging = command == BuzzerCommand.Ring
        val quicklyAcknowledged = command == BuzzerCommand.Ring &&
            !status.ringing &&
            status.source == BuzzerChangeSource.BedroomButton

        consecutivePollFailures = 0
        pendingCommandWasAttempted = false
        lastConfirmedRinging = status.ringing
        _uiState.update {
            it.copy(
                ringing = status.ringing,
                connection = ConnectionState.Connected,
                commandInFlight = false,
                pendingCommand = null,
                acknowledgementVisible = quicklyAcknowledged,
            )
        }

        when {
            quicklyAcknowledged -> {
                _effects.tryEmit(BuzzerUiEffect.ExternalAcknowledgement)
            }

            status.ringing == expectedRinging -> {
                _effects.tryEmit(BuzzerUiEffect.CommandSucceeded(command))
            }

            else -> {
                _effects.tryEmit(BuzzerUiEffect.CommandFailed(notAppliedMessage(command)))
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

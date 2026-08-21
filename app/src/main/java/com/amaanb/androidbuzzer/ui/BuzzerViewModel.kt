package com.amaanb.androidbuzzer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.amaanb.androidbuzzer.data.BuzzerCommand
import com.amaanb.androidbuzzer.data.BuzzerRepository
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
}

class BuzzerViewModel(
    private val repository: BuzzerRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BuzzerUiState())
    val uiState: StateFlow<BuzzerUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<BuzzerUiEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<BuzzerUiEffect> = _effects.asSharedFlow()

    private var pollingJob: Job? = null

    fun startPolling() {
        if (pollingJob?.isActive == true) return

        pollingJob = viewModelScope.launch {
            repository.observeStatus().collectLatest { result ->
                result.fold(
                    onSuccess = { status ->
                        _uiState.update {
                            it.copy(ringing = status.ringing, connection = ConnectionState.Connected)
                        }
                    },
                    onFailure = {
                        _uiState.update { it.copy(connection = ConnectionState.Offline) }
                    },
                )
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun toggleRinging() {
        val current = _uiState.value
        if (current.commandInFlight) return

        val command = if (current.ringing) BuzzerCommand.Stop else BuzzerCommand.Ring
        _uiState.update { it.copy(commandInFlight = true) }

        viewModelScope.launch {
            try {
                val status = repository.send(command)
                _uiState.update {
                    it.copy(
                        ringing = status.ringing,
                        connection = ConnectionState.Connected,
                        commandInFlight = false,
                    )
                }
                _effects.emit(BuzzerUiEffect.CommandSucceeded(command))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(connection = ConnectionState.Offline, commandInFlight = false)
                }
                _effects.emit(
                    BuzzerUiEffect.CommandFailed(
                        error.message ?: "Could not reach the bedroom buzzer",
                    ),
                )
            }
        }
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
}

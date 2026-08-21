package com.amaanb.androidbuzzer.ui

import com.amaanb.androidbuzzer.data.BuzzerCommand
import com.amaanb.androidbuzzer.data.BuzzerChangeSource
import com.amaanb.androidbuzzer.data.BuzzerRepository
import com.amaanb.androidbuzzer.data.BuzzerStatus
import com.amaanb.androidbuzzer.data.CommandOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BuzzerViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `polling reflects external ring and stop changes`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = BuzzerViewModel(repository)
        viewModel.startPolling()
        runCurrent()

        repository.statuses.emit(Result.success(BuzzerStatus(ringing = true)))
        runCurrent()
        assertTrue(viewModel.uiState.value.ringing)
        assertEquals(ConnectionState.Connected, viewModel.uiState.value.connection)

        repository.statuses.emit(
            Result.success(
                BuzzerStatus(
                    ringing = false,
                    source = BuzzerChangeSource.BedroomButton,
                ),
            ),
        )
        runCurrent()
        assertFalse(viewModel.uiState.value.ringing)
    }

    @Test
    fun `api and HC12 stops do not emit acknowledgements`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = BuzzerViewModel(repository)
        val effects = mutableListOf<BuzzerUiEffect>()
        backgroundScope.launch(dispatcher) { viewModel.effects.collect { effects += it } }
        viewModel.startPolling()
        runCurrent()

        repository.statuses.emit(Result.success(BuzzerStatus(ringing = true)))
        runCurrent()
        repository.statuses.emit(
            Result.success(BuzzerStatus(ringing = false, source = BuzzerChangeSource.Api)),
        )
        runCurrent()
        repository.statuses.emit(Result.success(BuzzerStatus(ringing = true)))
        runCurrent()
        repository.statuses.emit(
            Result.success(BuzzerStatus(ringing = false, source = BuzzerChangeSource.Hc12)),
        )
        runCurrent()

        assertFalse(viewModel.uiState.value.acknowledgementVisible)
        assertFalse(effects.contains(BuzzerUiEffect.ExternalAcknowledgement))
    }

    @Test
    fun `external stop emits one acknowledgement`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = BuzzerViewModel(repository)
        viewModel.startPolling()
        runCurrent()

        repository.statuses.emit(Result.success(BuzzerStatus(ringing = true)))
        runCurrent()
        val effect = async { viewModel.effects.first() }
        runCurrent()

        repository.statuses.emit(Result.success(BuzzerStatus(ringing = false)))
        runCurrent()

        assertEquals(BuzzerUiEffect.ExternalAcknowledgement, effect.await())
        assertTrue(viewModel.uiState.value.acknowledgementVisible)

        viewModel.toggleRinging()
        assertFalse(viewModel.uiState.value.acknowledgementVisible)
    }

    @Test
    fun `three poll failures are required before connection is offline`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = BuzzerViewModel(repository)
        viewModel.startPolling()
        runCurrent()

        repository.statuses.emit(Result.success(BuzzerStatus(ringing = true)))
        runCurrent()
        repository.statuses.emit(Result.failure(IllegalStateException("offline")))
        runCurrent()
        repository.statuses.emit(Result.failure(IllegalStateException("offline")))
        runCurrent()

        assertTrue(viewModel.uiState.value.ringing)
        assertEquals(ConnectionState.Connected, viewModel.uiState.value.connection)

        repository.statuses.emit(Result.failure(IllegalStateException("offline")))
        runCurrent()

        assertEquals(ConnectionState.Offline, viewModel.uiState.value.connection)
    }

    @Test
    fun `successful poll resets the offline failure count`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = BuzzerViewModel(repository)
        viewModel.startPolling()
        runCurrent()

        repository.statuses.emit(Result.success(BuzzerStatus(ringing = false)))
        runCurrent()
        repository.statuses.emit(Result.failure(IllegalStateException("offline")))
        runCurrent()
        repository.statuses.emit(Result.failure(IllegalStateException("offline")))
        runCurrent()
        repository.statuses.emit(Result.success(BuzzerStatus(ringing = true)))
        runCurrent()
        repository.statuses.emit(Result.failure(IllegalStateException("offline")))
        runCurrent()
        repository.statuses.emit(Result.failure(IllegalStateException("offline")))
        runCurrent()

        assertTrue(viewModel.uiState.value.ringing)
        assertEquals(ConnectionState.Connected, viewModel.uiState.value.connection)
    }

    @Test
    fun `button sends command for current state and applies confirmed response`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = BuzzerViewModel(repository)

        viewModel.toggleRinging()
        runCurrent()

        assertEquals(BuzzerCommand.Ring, repository.lastCommand)
        assertTrue(viewModel.uiState.value.ringing)
        assertFalse(viewModel.uiState.value.commandInFlight)

        viewModel.toggleRinging()
        runCurrent()

        assertEquals(BuzzerCommand.Stop, repository.lastCommand)
        assertFalse(viewModel.uiState.value.ringing)
    }

    @Test
    fun `unreachable command reconciles before error`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = BuzzerViewModel(repository)
        viewModel.startPolling()
        runCurrent()
        repository.statuses.emit(Result.success(BuzzerStatus(ringing = false)))
        runCurrent()
        repository.sendBlock = {
            CommandOutcome.Unreachable(IllegalStateException("low-level network details"))
        }
        val effect = async { viewModel.effects.first() }
        runCurrent()

        viewModel.toggleRinging()
        runCurrent()

        assertTrue(viewModel.uiState.value.ringing)
        assertTrue(viewModel.uiState.value.commandInFlight)
        assertEquals(ConnectionState.Connected, viewModel.uiState.value.connection)

        repeat(2) {
            repository.statuses.emit(Result.failure(IllegalStateException("offline")))
            runCurrent()
        }
        assertEquals(ConnectionState.Connected, viewModel.uiState.value.connection)

        repository.statuses.emit(Result.failure(IllegalStateException("offline")))
        runCurrent()

        assertFalse(viewModel.uiState.value.ringing)
        assertFalse(viewModel.uiState.value.commandInFlight)
        assertEquals(ConnectionState.Offline, viewModel.uiState.value.connection)
        assertEquals(
            BuzzerUiEffect.CommandFailed("Could not reach the bedroom buzzer"),
            effect.await(),
        )
    }

    @Test
    fun `lost app stop response is reconciled silently`() = runTest(dispatcher) {
        val repository = FakeRepository().apply {
            sendBlock = {
                CommandOutcome.Unreachable(IllegalStateException("response lost"))
            }
        }
        val viewModel = BuzzerViewModel(repository)
        viewModel.startPolling()
        runCurrent()
        repository.statuses.emit(Result.success(BuzzerStatus(ringing = true)))
        runCurrent()
        val effect = async { viewModel.effects.first() }
        runCurrent()

        viewModel.toggleRinging()
        runCurrent()
        repository.statuses.emit(
            Result.success(BuzzerStatus(ringing = false, source = BuzzerChangeSource.Api)),
        )
        runCurrent()

        assertEquals(BuzzerUiEffect.CommandSucceeded(BuzzerCommand.Stop), effect.await())
        assertFalse(viewModel.uiState.value.acknowledgementVisible)
        assertFalse(viewModel.uiState.value.commandInFlight)
        assertEquals(ConnectionState.Connected, viewModel.uiState.value.connection)
    }

    @Test
    fun `quick bedroom acknowledgement resolves an unconfirmed ring`() = runTest(dispatcher) {
        val repository = FakeRepository().apply {
            sendBlock = {
                CommandOutcome.Unreachable(IllegalStateException("response lost"))
            }
        }
        val viewModel = BuzzerViewModel(repository)
        viewModel.startPolling()
        runCurrent()
        repository.statuses.emit(Result.success(BuzzerStatus(ringing = false)))
        runCurrent()
        val effect = async { viewModel.effects.first() }
        runCurrent()

        viewModel.toggleRinging()
        runCurrent()
        repository.statuses.emit(
            Result.success(
                BuzzerStatus(
                    ringing = false,
                    source = BuzzerChangeSource.BedroomButton,
                ),
            ),
        )
        runCurrent()

        assertEquals(BuzzerUiEffect.ExternalAcknowledgement, effect.await())
        assertTrue(viewModel.uiState.value.acknowledgementVisible)
        assertFalse(viewModel.uiState.value.commandInFlight)
    }

    @Test
    fun `reachable command rejection rolls back without marking offline`() = runTest(dispatcher) {
        val repository = FakeRepository().apply {
            sendBlock = { CommandOutcome.NotApplied(BuzzerStatus(ringing = false)) }
        }
        val viewModel = BuzzerViewModel(repository)
        val effect = async { viewModel.effects.first() }
        runCurrent()

        viewModel.toggleRinging()
        assertTrue(viewModel.uiState.value.ringing)
        assertTrue(viewModel.uiState.value.commandInFlight)
        runCurrent()

        assertFalse(viewModel.uiState.value.ringing)
        assertEquals(ConnectionState.Connected, viewModel.uiState.value.connection)
        assertEquals(
            BuzzerUiEffect.CommandFailed("The bedroom buzzer did not start ringing"),
            effect.await(),
        )
    }

    @Test
    fun `rapid taps cancel obsolete work and latest tap wins`() = runTest(dispatcher) {
        val ringStarted = CompletableDeferred<Unit>()
        val repository = FakeRepository().apply {
            sendBlock = { command ->
                if (command == BuzzerCommand.Ring) {
                    ringStarted.complete(Unit)
                    awaitCancellation()
                }
                CommandOutcome.Confirmed(BuzzerStatus(ringing = false))
            }
        }
        val viewModel = BuzzerViewModel(repository)

        viewModel.toggleRinging()
        runCurrent()
        ringStarted.await()
        assertTrue(viewModel.uiState.value.ringing)

        viewModel.toggleRinging()
        runCurrent()

        assertEquals(listOf(BuzzerCommand.Ring, BuzzerCommand.Stop), repository.commands)
        assertFalse(viewModel.uiState.value.ringing)
        assertFalse(viewModel.uiState.value.commandInFlight)
        assertEquals(ConnectionState.Connected, viewModel.uiState.value.connection)
    }

    @Test
    fun `command cancels polling before it is sent`() = runTest(dispatcher) {
        val pollingCancelled = CompletableDeferred<Unit>()
        val repository = object : BuzzerRepository {
            override fun observeStatus(): Flow<Result<BuzzerStatus>> = flow {
                try {
                    awaitCancellation()
                } finally {
                    pollingCancelled.complete(Unit)
                }
            }

            override suspend fun send(command: BuzzerCommand): CommandOutcome =
                CommandOutcome.Confirmed(BuzzerStatus(ringing = true))
        }
        val viewModel = BuzzerViewModel(repository)
        viewModel.startPolling()
        runCurrent()

        viewModel.toggleRinging()
        runCurrent()

        assertTrue(pollingCancelled.isCompleted)
        assertTrue(viewModel.uiState.value.ringing)
    }

    private class FakeRepository : BuzzerRepository {
        val statuses = MutableSharedFlow<Result<BuzzerStatus>>(extraBufferCapacity = 1)
        val commands = mutableListOf<BuzzerCommand>()
        var lastCommand: BuzzerCommand? = null
        var sendBlock: suspend (BuzzerCommand) -> CommandOutcome = { command ->
            CommandOutcome.Confirmed(BuzzerStatus(ringing = command == BuzzerCommand.Ring))
        }

        override fun observeStatus(): Flow<Result<BuzzerStatus>> = statuses

        override suspend fun send(command: BuzzerCommand): CommandOutcome {
            lastCommand = command
            commands += command
            return sendBlock(command)
        }
    }
}

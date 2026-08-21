package com.amaanb.androidbuzzer.ui

import com.amaanb.androidbuzzer.data.BuzzerCommand
import com.amaanb.androidbuzzer.data.BuzzerRepository
import com.amaanb.androidbuzzer.data.BuzzerStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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

        repository.statuses.emit(Result.success(BuzzerStatus(ringing = false)))
        runCurrent()
        assertFalse(viewModel.uiState.value.ringing)
    }

    @Test
    fun `poll failure keeps last state and marks connection offline`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = BuzzerViewModel(repository)
        viewModel.startPolling()
        runCurrent()

        repository.statuses.emit(Result.success(BuzzerStatus(ringing = true)))
        runCurrent()
        repository.statuses.emit(Result.failure(IllegalStateException("offline")))
        runCurrent()

        assertTrue(viewModel.uiState.value.ringing)
        assertEquals(ConnectionState.Offline, viewModel.uiState.value.connection)
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

    private class FakeRepository : BuzzerRepository {
        val statuses = MutableSharedFlow<Result<BuzzerStatus>>(extraBufferCapacity = 1)
        var lastCommand: BuzzerCommand? = null

        override fun observeStatus(): Flow<Result<BuzzerStatus>> = statuses

        override suspend fun send(command: BuzzerCommand): BuzzerStatus {
            lastCommand = command
            return BuzzerStatus(ringing = command == BuzzerCommand.Ring)
        }
    }
}

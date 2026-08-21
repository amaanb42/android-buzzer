package com.amaanb.androidbuzzer.data

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultBuzzerRepositoryTest {
    @Test
    fun `command retries once after a transport failure`() = runTest {
        val api = FakeBuzzerApi().apply {
            ringBlock = {
                if (++ringCalls == 1) throw IOException("response lost")
                BuzzerStatus(ringing = true)
            }
        }

        val outcome = DefaultBuzzerRepository(api).send(BuzzerCommand.Ring)

        assertEquals(CommandOutcome.Confirmed(BuzzerStatus(ringing = true)), outcome)
        assertEquals(2, api.ringCalls)
        assertEquals(0, api.statusCalls)
    }

    @Test
    fun `status confirmation suppresses a false command error`() = runTest {
        val api = FakeBuzzerApi().apply {
            ringBlock = {
                ringCalls++
                throw IOException("response lost")
            }
            statusBlock = {
                statusCalls++
                BuzzerStatus(ringing = true)
            }
        }

        val outcome = DefaultBuzzerRepository(api).send(BuzzerCommand.Ring)

        assertEquals(CommandOutcome.Confirmed(BuzzerStatus(ringing = true)), outcome)
        assertEquals(2, api.ringCalls)
        assertEquals(1, api.statusCalls)
    }

    @Test
    fun `reachable mismatched status reports command not applied`() = runTest {
        val api = FakeBuzzerApi().apply {
            ringBlock = {
                ringCalls++
                throw IOException("response lost")
            }
            statusBlock = {
                statusCalls++
                BuzzerStatus(ringing = false)
            }
        }

        val outcome = DefaultBuzzerRepository(api).send(BuzzerCommand.Ring)

        assertEquals(CommandOutcome.NotApplied(BuzzerStatus(ringing = false)), outcome)
    }

    @Test
    fun `three failed requests report unreachable`() = runTest {
        val api = FakeBuzzerApi().apply {
            ringBlock = {
                ringCalls++
                throw IOException("post failed")
            }
            statusBlock = {
                statusCalls++
                throw IOException("status failed")
            }
        }

        val outcome = DefaultBuzzerRepository(api).send(BuzzerCommand.Ring)

        assertTrue(outcome is CommandOutcome.Unreachable)
        assertEquals(2, api.ringCalls)
        assertEquals(1, api.statusCalls)
    }

    private class FakeBuzzerApi : BuzzerApi {
        var ringCalls = 0
        var statusCalls = 0
        var ringBlock: suspend () -> BuzzerStatus = { BuzzerStatus(ringing = true) }
        var statusBlock: suspend () -> BuzzerStatus = { BuzzerStatus(ringing = false) }

        override suspend fun getStatus(): BuzzerStatus = statusBlock()

        override suspend fun ring(): BuzzerStatus = ringBlock()

        override suspend fun stop(): BuzzerStatus = BuzzerStatus(ringing = false)
    }
}

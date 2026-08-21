package com.amaanb.androidbuzzer.data

import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HttpBuzzerApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: HttpBuzzerApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = HttpBuzzerApi(baseUrl = server.url("/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `status uses GET and parses ringing state`() = runTest {
        server.enqueue(MockResponse().setBody("{\"ringing\":true}"))

        assertTrue(api.getStatus().ringing)
        server.takeRequest().apply {
            assertEquals("GET", method)
            assertEquals("/api/status", path)
        }
    }

    @Test
    fun `ring and stop use POST and return confirmed state`() = runTest {
        server.enqueue(MockResponse().setBody("{\"ringing\":true}"))
        server.enqueue(MockResponse().setBody("{\"ringing\":false}"))

        assertTrue(api.ring().ringing)
        assertFalse(api.stop().ringing)
        assertEquals("POST", server.takeRequest().method)
        assertEquals("POST", server.takeRequest().method)
    }

    @Test
    fun `non-success response is reported as an IO failure`() {
        server.enqueue(MockResponse().setResponseCode(503))

        assertThrows(IOException::class.java) {
            runTest { api.getStatus() }
        }
    }

    @Test
    fun `malformed JSON is reported as an IO failure`() {
        server.enqueue(MockResponse().setBody("not-json"))

        assertThrows(IOException::class.java) {
            runTest { api.getStatus() }
        }
    }
}

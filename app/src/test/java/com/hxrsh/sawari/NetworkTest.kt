package com.hxrsh.sawari

import com.hxrsh.sawari.data.remote.AuthInterceptor
import com.hxrsh.sawari.data.remote.RetryInterceptor
import com.hxrsh.sawari.data.remote.apiCall
import com.hxrsh.sawari.domain.AppError
import com.hxrsh.sawari.domain.AppResult
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NetworkTest {
    private lateinit var server: MockWebServer
    private val sleeps = mutableListOf<Long>()

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() = server.shutdown()

    private fun client(token: String? = null) = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor { token })
        .addInterceptor(RetryInterceptor(maxRetries = 2, backoffMillis = 10) { sleeps += it })
        .build()

    @Test fun `GET is retried on 503 and then succeeds`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(502))
        server.enqueue(MockResponse().setBody("[${Fixtures.carJson(Fixtures.swift)}]"))
        val result = apiCall { testApi(server, client()).cars() }
        assertTrue(result is AppResult.Success)
        assertEquals(3, server.requestCount)
        assertEquals(listOf(10L, 20L), sleeps) // exponential backoff
    }

    @Test fun `GET gives up after the retry budget and reports a server error`() = runTest {
        repeat(5) { server.enqueue(MockResponse().setResponseCode(503).setBody("""{"detail":"down"}""")) }
        val result = apiCall { testApi(server, client()).cars() } as AppResult.Failure
        assertEquals(AppError.Server(503, "down"), result.error)
        assertEquals(3, server.requestCount) // 1 try + 2 retries
    }

    @Test fun `GET is retried after a dropped connection`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(MockResponse().setBody("[]"))
        assertTrue(apiCall { testApi(server, client()).cars() } is AppResult.Success)
        assertEquals(2, server.requestCount)
    }

    @Test fun `POST is never retried`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setBody(Fixtures.bookingJson(1)))
        val result = apiCall { testApi(server, client()).cancelBooking(1) }
        assertTrue(result is AppResult.Failure)
        assertEquals(1, server.requestCount)
        assertTrue(sleeps.isEmpty())
    }

    @Test fun `bearer token is attached when present and omitted otherwise`() = runTest {
        server.enqueue(MockResponse().setBody("[]")); server.enqueue(MockResponse().setBody("[]"))
        apiCall { testApi(server, client("abc")).bookings() }
        apiCall { testApi(server, client(null)).bookings() }
        assertEquals("Bearer abc", server.takeRequest().getHeader("Authorization"))
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test fun `HTTP errors map to typed AppErrors with the server detail`() = runTest {
        suspend fun fail(code: Int, body: String): AppError {
            server.enqueue(MockResponse().setResponseCode(code).setBody(body))
            return (apiCall { testApi(server).bookings() } as AppResult.Failure).error
        }
        assertEquals(AppError.Unauthorized("Invalid or missing token"), fail(401, """{"detail":"Invalid or missing token"}"""))
        assertEquals(AppError.Conflict("slot taken"), fail(409, """{"detail":"slot taken"}"""))
        assertEquals(AppError.Validation("end_time must be after start_time"), fail(422, """{"detail":"end_time must be after start_time"}"""))
        assertEquals(AppError.Validation("Field required"), fail(422, """{"detail":[{"msg":"Field required","loc":["body"]}]}"""))
        assertEquals(AppError.NotFound("Not found"), fail(404, "not json"))
        assertEquals(AppError.Server(500), fail(500, ""))
    }

    @Test fun `unreachable server maps to Network`() = runTest {
        val api = testApi(server)
        server.shutdown()
        val result = apiCall { api.cars() } as AppResult.Failure
        assertTrue(result.error is AppError.Network)
    }
}

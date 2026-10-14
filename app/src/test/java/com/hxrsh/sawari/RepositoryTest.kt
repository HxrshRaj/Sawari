package com.hxrsh.sawari

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hxrsh.sawari.data.local.BookingEntity
import com.hxrsh.sawari.data.local.CarEntity
import com.hxrsh.sawari.data.local.SawariDatabase
import com.hxrsh.sawari.data.repository.AuthRepositoryImpl
import com.hxrsh.sawari.data.repository.BookingRepositoryImpl
import com.hxrsh.sawari.data.repository.CarRepositoryImpl
import com.hxrsh.sawari.domain.AppError
import com.hxrsh.sawari.domain.AppResult
import com.hxrsh.sawari.domain.model.BookingStatus
import com.hxrsh.sawari.domain.model.CarFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** Repository + real Room (in-memory, via Robolectric) + real Retrofit against MockWebServer. */
@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var db: SawariDatabase
    private lateinit var sessions: InMemorySessionStore

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), SawariDatabase::class.java)
            .allowMainThreadQueries().build()
        sessions = InMemorySessionStore(Fixtures.session)
    }

    @After fun tearDown() { db.close(); server.shutdown() }

    private fun cars() = CarRepositoryImpl(testApi(server), db.carDao())
    private fun bookings() = BookingRepositoryImpl(testApi(server), db.bookingDao(), sessions)

    private fun carsBody() = Fixtures.allCars.joinToString(",", "[", "]") { Fixtures.carJson(it) }

    @Test fun `refresh stores the catalogue and observers see it`() = runTest {
        server.enqueue(MockResponse().setBody(carsBody()))
        val repo = cars()
        assertTrue(repo.refresh() is AppResult.Success)
        assertEquals(Fixtures.allCars, repo.observeCars(CarFilter()).first())
        assertEquals(listOf("Bengaluru", "Mumbai"), repo.observeCities().first())
    }

    @Test fun `cache fallback - a failed refresh leaves the cached cars untouched`() = runTest {
        server.enqueue(MockResponse().setBody(carsBody()))
        val repo = cars()
        repo.refresh()
        server.enqueue(MockResponse().setResponseCode(500)) // GET retry: 3 attempts all fail
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(500))
        val result = repo.refresh()
        assertTrue(result is AppResult.Failure)
        assertEquals(Fixtures.allCars, repo.observeCars(CarFilter()).first())
    }

    @Test fun `refresh replaces cars removed on the server`() = runTest {
        server.enqueue(MockResponse().setBody(carsBody()))
        server.enqueue(MockResponse().setBody("[${Fixtures.carJson(Fixtures.swift)}]"))
        val repo = cars()
        repo.refresh(); repo.refresh()
        assertEquals(listOf(Fixtures.swift), repo.observeCars(CarFilter()).first())
    }

    @Test fun `filtering happens offline against Room`() = runTest {
        server.enqueue(MockResponse().setBody(carsBody()))
        val repo = cars()
        repo.refresh()
        assertEquals(listOf(Fixtures.nexon), repo.observeCars(CarFilter(city = "Bengaluru", fuel = "Electric")).first())
        assertEquals(listOf(Fixtures.creta), repo.observeCars(CarFilter(query = "creta")).first())
    }

    @Test fun `create booking stores it`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(Fixtures.bookingJson(11)))
        val repo = bookings()
        val result = repo.create(1, Instant.parse("2030-01-01T10:00:00Z"), Instant.parse("2030-01-01T13:00:00Z")) as AppResult.Success
        assertEquals(11, result.data.id)
        assertEquals(BookingStatus.CONFIRMED, repo.observeBookings().first().single().status)
        val sent = server.takeRequest()
        assertEquals("/bookings", sent.path)
        assertTrue(sent.body.readUtf8().contains(""""start_time":"2030-01-01T10:00:00Z""""))
    }

    @Test fun `409 conflict is surfaced and nothing is cached`() = runTest {
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"detail":"This car is already booked for part of that time range"}"""))
        val repo = bookings()
        val result = repo.create(1, Instant.parse("2030-01-01T10:00:00Z"), Instant.parse("2030-01-01T13:00:00Z")) as AppResult.Failure
        assertEquals(AppError.Conflict("This car is already booked for part of that time range"), result.error)
        assertTrue(repo.observeBookings().first().isEmpty())
        assertEquals(1, server.requestCount) // POST never retried
    }

    @Test fun `cancel updates the cached booking`() = runTest {
        server.enqueue(MockResponse().setBody("[${Fixtures.bookingJson(3)}]"))
        server.enqueue(MockResponse().setBody(Fixtures.bookingJson(3, status = "cancelled")))
        val repo = bookings()
        repo.refresh()
        assertEquals(BookingStatus.CONFIRMED, repo.observeBookings().first().single().status)
        repo.cancel(3)
        assertEquals(BookingStatus.CANCELLED, repo.observeBookings().first().single().status)
    }

    @Test fun `401 on an authenticated call drops the session and the cached bookings`() = runTest {
        server.enqueue(MockResponse().setBody("[${Fixtures.bookingJson(3)}]"))
        val repo = bookings()
        repo.refresh()
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":"Invalid or missing token"}"""))
        val result = repo.refresh() as AppResult.Failure
        assertTrue(result.error is AppError.Unauthorized)
        assertNull(sessions.session.first())
        assertTrue(repo.observeBookings().first().isEmpty())
    }

    @Test fun `login stores the session and logout clears bookings`() = runTest {
        server.enqueue(MockResponse().setBody("""{"access_token":"t1","token_type":"bearer","user":{"id":7,"name":"Asha","email":"asha@example.com"}}"""))
        val emptySessions = InMemorySessionStore()
        val auth = AuthRepositoryImpl(testApi(server), emptySessions, db.bookingDao())
        val result = auth.login("asha@example.com", "secret12") as AppResult.Success
        assertEquals("t1", result.data.token)
        assertEquals("t1", emptySessions.session.first()?.token)
        db.bookingDao().upsert(BookingEntity(1, 0, 1, 1, "confirmed", CarEntity(1, "a", "b", 1, "u", 1, "f")))
        auth.logout()
        assertNull(emptySessions.session.first())
        assertTrue(db.bookingDao().observeAll().first().isEmpty())
    }

    @Test fun `bad login keeps the session empty`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":"Incorrect email or password"}"""))
        val emptySessions = InMemorySessionStore()
        val auth = AuthRepositoryImpl(testApi(server), emptySessions, db.bookingDao())
        val result = auth.login("a@b.co", "wrong-pw") as AppResult.Failure
        assertEquals(AppError.Unauthorized("Incorrect email or password"), result.error)
        assertNull(emptySessions.session.first())
    }
}

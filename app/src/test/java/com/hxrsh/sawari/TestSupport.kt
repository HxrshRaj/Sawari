package com.hxrsh.sawari

import com.hxrsh.sawari.analytics.Analytics
import com.hxrsh.sawari.data.local.SessionStore
import com.hxrsh.sawari.data.remote.SawariApi
import com.hxrsh.sawari.domain.AppError
import com.hxrsh.sawari.domain.AppResult
import com.hxrsh.sawari.domain.model.Booking
import com.hxrsh.sawari.domain.model.BookingStatus
import com.hxrsh.sawari.domain.model.Car
import com.hxrsh.sawari.domain.model.CarFilter
import com.hxrsh.sawari.domain.model.Session
import com.hxrsh.sawari.domain.model.TimeSlot
import com.hxrsh.sawari.domain.model.User
import com.hxrsh.sawari.domain.repository.BookingRepository
import com.hxrsh.sawari.domain.repository.CarRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockWebServer
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    val dispatcher = UnconfinedTestDispatcher()
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

object Fixtures {
    val swift = Car(1, "Maruti Swift", "Bengaluru", 180, "https://img/1", 5, "Petrol")
    val nexon = Car(2, "Tata Nexon EV", "Bengaluru", 320, "https://img/2", 5, "Electric")
    val creta = Car(3, "Hyundai Creta", "Mumbai", 330, "https://img/3", 5, "Diesel")
    val allCars = listOf(swift, nexon, creta)

    val user = User(7, "Asha", "asha@example.com")
    val session = Session("tok", user)

    fun booking(id: Int, car: Car = swift, status: BookingStatus = BookingStatus.CONFIRMED, startHoursFromNow: Long = 2) =
        Booking(id, car, Instant.now().plusSeconds(startHoursFromNow * 3600), Instant.now().plusSeconds((startHoursFromNow + 3) * 3600), car.pricePerHour * 3, status)

    fun carJson(c: Car) =
        """{"id":${c.id},"name":"${c.name}","city":"${c.city}","price_per_hour":${c.pricePerHour},"image_url":"${c.imageUrl}","seats":${c.seats},"fuel":"${c.fuel}"}"""

    fun bookingJson(id: Int, car: Car = swift, status: String = "confirmed") =
        """{"id":$id,"car_id":${car.id},"start_time":"2030-01-01T10:00:00Z","end_time":"2030-01-01T13:00:00Z","total_price":${car.pricePerHour * 3},"status":"$status","created_at":"2030-01-01T00:00:00Z","car":${carJson(car)}}"""
}

class FakeCarRepository : CarRepository {
    val cars = MutableStateFlow(Fixtures.allCars)
    var refreshResult: AppResult<Unit> = AppResult.Success(Unit)
    var busy: AppResult<List<TimeSlot>> = AppResult.Success(emptyList())
    var refreshCalls = 0
    /** Simulates an empty cache until the first successful refresh, like a fresh install. */
    var startEmpty = false
        set(value) { field = value; if (value) cars.value = emptyList() }

    override fun observeCars(filter: CarFilter): Flow<List<Car>> = cars.map { list ->
        list.filter { c ->
            (filter.city == null || c.city == filter.city) &&
                (filter.fuel == null || c.fuel == filter.fuel) &&
                c.name.contains(filter.query.trim(), ignoreCase = true)
        }
    }

    override fun observeCities(): Flow<List<String>> = cars.map { l -> l.map { it.city }.distinct().sorted() }
    override fun observeCar(id: Int): Flow<Car?> = cars.map { l -> l.firstOrNull { it.id == id } }

    override suspend fun refresh(): AppResult<Unit> {
        refreshCalls++
        if (refreshResult is AppResult.Success && startEmpty) cars.value = Fixtures.allCars
        return refreshResult
    }

    override suspend fun busySlots(carId: Int) = busy
}

class FakeBookingRepository : BookingRepository {
    val bookings = MutableStateFlow<List<Booking>>(emptyList())
    var refreshResult: AppResult<Unit> = AppResult.Success(Unit)
    var createResult: AppResult<Booking>? = null
    var cancelResult: AppResult<Booking>? = null
    val created = mutableListOf<Triple<Int, Instant, Instant>>()

    override fun observeBookings() = bookings
    override suspend fun refresh() = refreshResult

    override suspend fun create(carId: Int, start: Instant, end: Instant): AppResult<Booking> {
        created += Triple(carId, start, end)
        return createResult ?: AppResult.Success(Fixtures.booking(99, Fixtures.allCars.first { it.id == carId }))
    }

    override suspend fun cancel(bookingId: Int): AppResult<Booking> =
        cancelResult ?: AppResult.Success(
            Fixtures.booking(bookingId, status = BookingStatus.CANCELLED).also { c -> bookings.value = bookings.value.map { if (it.id == bookingId) c else it } },
        )
}

class FakeAnalytics : Analytics {
    val events = mutableListOf<Pair<String, Map<String, String>>>()
    override fun logEvent(name: String, params: Map<String, String>) { events += name to params }
    override fun setUser(id: String?) = Unit
    override fun recordNonFatal(throwable: Throwable) = Unit
    override fun crashForTest() = Unit
}

class InMemorySessionStore(initial: Session? = null) : SessionStore {
    private val flow = MutableStateFlow(initial)
    override val session: Flow<Session?> = flow
    override suspend fun save(session: Session) { flow.value = session }
    override suspend fun clear() { flow.value = null }
}

val networkFailure = AppResult.Failure(AppError.Network())

/** Retrofit pointed at a MockWebServer, with the same JSON setup as production. */
fun testApi(server: MockWebServer, client: okhttp3.OkHttpClient = okhttp3.OkHttpClient()): SawariApi =
    Retrofit.Builder()
        .baseUrl(server.url("/"))
        .client(client)
        .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(SawariApi::class.java)

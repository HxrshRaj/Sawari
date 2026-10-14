package com.hxrsh.sawari.data.repository

import com.hxrsh.sawari.data.local.BookingDao
import com.hxrsh.sawari.data.local.CarDao
import com.hxrsh.sawari.data.local.SessionStore
import com.hxrsh.sawari.data.mapper.toDomain
import com.hxrsh.sawari.data.mapper.toEntity
import com.hxrsh.sawari.data.remote.BookingRequest
import com.hxrsh.sawari.data.remote.LoginRequest
import com.hxrsh.sawari.data.remote.RegisterRequest
import com.hxrsh.sawari.data.remote.SawariApi
import com.hxrsh.sawari.data.remote.apiCall
import com.hxrsh.sawari.domain.AppError
import com.hxrsh.sawari.domain.AppResult
import com.hxrsh.sawari.domain.map
import com.hxrsh.sawari.domain.model.Booking
import com.hxrsh.sawari.domain.model.Car
import com.hxrsh.sawari.domain.model.CarFilter
import com.hxrsh.sawari.domain.model.Session
import com.hxrsh.sawari.domain.model.TimeSlot
import com.hxrsh.sawari.domain.repository.AuthRepository
import com.hxrsh.sawari.domain.repository.BookingRepository
import com.hxrsh.sawari.domain.repository.CarRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CarRepositoryImpl @Inject constructor(
    private val api: SawariApi,
    private val dao: CarDao,
) : CarRepository {

    override fun observeCars(filter: CarFilter): Flow<List<Car>> =
        dao.observe(filter.city, filter.fuel, filter.query.trim()).map { rows -> rows.map { it.toDomain() } }

    override fun observeCities(): Flow<List<String>> = dao.observeCities()

    override fun observeCar(id: Int): Flow<Car?> = dao.observeById(id).map { it?.toDomain() }

    override suspend fun refresh(): AppResult<Unit> {
        val result = apiCall { api.cars() }
        if (result is AppResult.Success) dao.replaceAll(result.data.map { it.toEntity() })
        return result.map { }
    }

    override suspend fun busySlots(carId: Int): AppResult<List<TimeSlot>> =
        apiCall { api.busySlots(carId) }.map { list -> list.map { it.toDomain() } }
}

@Singleton
class BookingRepositoryImpl @Inject constructor(
    private val api: SawariApi,
    private val dao: BookingDao,
    private val sessions: SessionStore,
) : BookingRepository {

    override fun observeBookings(): Flow<List<Booking>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun refresh(): AppResult<Unit> {
        val result = handleAuth { apiCall { api.bookings() } }
        if (result is AppResult.Success) dao.replaceAll(result.data.map { it.toEntity() })
        return result.map { }
    }

    override suspend fun create(carId: Int, start: Instant, end: Instant): AppResult<Booking> {
        val result = handleAuth { apiCall { api.createBooking(BookingRequest(carId, start.toString(), end.toString())) } }
        if (result is AppResult.Success) dao.upsert(result.data.toEntity())
        return result.map { it.toEntity().toDomain() }
    }

    override suspend fun cancel(bookingId: Int): AppResult<Booking> {
        val result = handleAuth { apiCall { api.cancelBooking(bookingId) } }
        if (result is AppResult.Success) dao.upsert(result.data.toEntity())
        return result.map { it.toEntity().toDomain() }
    }

    /** A 401 on an authenticated call means the token is dead: drop the session so the UI returns to login. */
    private suspend fun <T> handleAuth(call: suspend () -> AppResult<T>): AppResult<T> {
        val result = call()
        if (result is AppResult.Failure && result.error is AppError.Unauthorized) {
            sessions.clear()
            dao.deleteAll()
        }
        return result
    }
}

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: SawariApi,
    private val sessions: SessionStore,
    private val bookings: BookingDao,
) : AuthRepository {

    override val session: Flow<Session?> = sessions.session

    override suspend fun login(email: String, password: String): AppResult<Session> =
        apiCall { api.login(LoginRequest(email, password)) }.map { it.toDomain() }.also { saveIfOk(it) }

    override suspend fun register(name: String, email: String, password: String): AppResult<Session> =
        apiCall { api.register(RegisterRequest(name, email, password)) }.map { it.toDomain() }.also { saveIfOk(it) }

    override suspend fun logout() {
        sessions.clear()
        bookings.deleteAll() // bookings are per-user; the car catalogue is shared and stays cached
    }

    private suspend fun saveIfOk(result: AppResult<Session>) {
        if (result is AppResult.Success) {
            bookings.deleteAll() // never show a previous user's cached bookings
            sessions.save(result.data)
        }
    }
}

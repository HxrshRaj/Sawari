package com.hxrsh.sawari.domain.repository

import com.hxrsh.sawari.domain.AppResult
import com.hxrsh.sawari.domain.model.Booking
import com.hxrsh.sawari.domain.model.Car
import com.hxrsh.sawari.domain.model.CarFilter
import com.hxrsh.sawari.domain.model.Session
import com.hxrsh.sawari.domain.model.TimeSlot
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface AuthRepository {
    val session: Flow<Session?>
    suspend fun login(email: String, password: String): AppResult<Session>
    suspend fun register(name: String, email: String, password: String): AppResult<Session>
    suspend fun logout()
}

/** Room is the single source of truth: observe* never touches the network, refresh() fills the cache. */
interface CarRepository {
    fun observeCars(filter: CarFilter): Flow<List<Car>>
    fun observeCities(): Flow<List<String>>
    fun observeCar(id: Int): Flow<Car?>
    suspend fun refresh(): AppResult<Unit>
    suspend fun busySlots(carId: Int): AppResult<List<TimeSlot>>
}

interface BookingRepository {
    fun observeBookings(): Flow<List<Booking>>
    suspend fun refresh(): AppResult<Unit>
    suspend fun create(carId: Int, start: Instant, end: Instant): AppResult<Booking>
    suspend fun cancel(bookingId: Int): AppResult<Booking>
}

package com.hxrsh.sawari.domain.usecase

import com.hxrsh.sawari.domain.AppError
import com.hxrsh.sawari.domain.AppResult
import com.hxrsh.sawari.domain.model.Booking
import com.hxrsh.sawari.domain.model.Car
import com.hxrsh.sawari.domain.model.CarFilter
import com.hxrsh.sawari.domain.model.Session
import com.hxrsh.sawari.domain.model.TimeSlot
import com.hxrsh.sawari.domain.repository.AuthRepository
import com.hxrsh.sawari.domain.repository.BookingRepository
import com.hxrsh.sawari.domain.repository.CarRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject

class ObserveCarsUseCase @Inject constructor(private val repo: CarRepository) {
    operator fun invoke(filter: CarFilter): Flow<List<Car>> = repo.observeCars(filter)
}

class ObserveCitiesUseCase @Inject constructor(private val repo: CarRepository) {
    operator fun invoke(): Flow<List<String>> = repo.observeCities()
}

class ObserveCarUseCase @Inject constructor(private val repo: CarRepository) {
    operator fun invoke(id: Int): Flow<Car?> = repo.observeCar(id)
}

class RefreshCarsUseCase @Inject constructor(private val repo: CarRepository) {
    suspend operator fun invoke(): AppResult<Unit> = repo.refresh()
}

class GetBusySlotsUseCase @Inject constructor(private val repo: CarRepository) {
    suspend operator fun invoke(carId: Int): AppResult<List<TimeSlot>> = repo.busySlots(carId)
}

class ObserveBookingsUseCase @Inject constructor(private val repo: BookingRepository) {
    operator fun invoke(): Flow<List<Booking>> = repo.observeBookings()
}

class RefreshBookingsUseCase @Inject constructor(private val repo: BookingRepository) {
    suspend operator fun invoke(): AppResult<Unit> = repo.refresh()
}

class CreateBookingUseCase @Inject constructor(private val repo: BookingRepository) {
    suspend operator fun invoke(carId: Int, start: Instant, end: Instant, now: Instant): AppResult<Booking> {
        if (!end.isAfter(start)) return AppResult.Failure(AppError.Validation("End time must be after start time"))
        if (start.isBefore(now.minusSeconds(300))) return AppResult.Failure(AppError.Validation("Start time is in the past"))
        return repo.create(carId, start, end)
    }
}

class CancelBookingUseCase @Inject constructor(private val repo: BookingRepository) {
    suspend operator fun invoke(bookingId: Int): AppResult<Booking> = repo.cancel(bookingId)
}

class ObserveSessionUseCase @Inject constructor(private val repo: AuthRepository) {
    operator fun invoke(): Flow<Session?> = repo.session
}

class LoginUseCase @Inject constructor(private val repo: AuthRepository) {
    suspend operator fun invoke(email: String, password: String): AppResult<Session> =
        repo.login(email.trim(), password)
}

class RegisterUseCase @Inject constructor(private val repo: AuthRepository) {
    suspend operator fun invoke(name: String, email: String, password: String): AppResult<Session> =
        repo.register(name.trim(), email.trim(), password)
}

class LogoutUseCase @Inject constructor(private val repo: AuthRepository) {
    suspend operator fun invoke() = repo.logout()
}

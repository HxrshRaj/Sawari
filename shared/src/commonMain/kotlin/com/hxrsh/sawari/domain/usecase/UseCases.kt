package com.hxrsh.sawari.domain.usecase

import com.hxrsh.sawari.domain.AppError
import com.hxrsh.sawari.domain.AppResult
import com.hxrsh.sawari.domain.model.Booking
import com.hxrsh.sawari.domain.model.Car
import com.hxrsh.sawari.domain.model.CarFilter
import com.hxrsh.sawari.domain.model.Session
import com.hxrsh.sawari.domain.model.TimeSlot
import com.hxrsh.sawari.domain.onSuccess
import com.hxrsh.sawari.domain.platform.AppLog
import com.hxrsh.sawari.domain.reminder.ReminderScheduler
import com.hxrsh.sawari.domain.repository.AuthRepository
import com.hxrsh.sawari.domain.repository.BookingRepository
import com.hxrsh.sawari.domain.repository.CarRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.minutes

// Plain classes, no @Inject: javax.inject is JVM-only and cannot be used from common code.
// The Android app builds them in di/UseCaseModule.kt.

class ObserveCarsUseCase(private val repo: CarRepository) {
    operator fun invoke(filter: CarFilter): Flow<List<Car>> = repo.observeCars(filter)
}

class ObserveCitiesUseCase(private val repo: CarRepository) {
    operator fun invoke(): Flow<List<String>> = repo.observeCities()
}

class ObserveCarUseCase(private val repo: CarRepository) {
    operator fun invoke(id: Int): Flow<Car?> = repo.observeCar(id)
}

class RefreshCarsUseCase(private val repo: CarRepository) {
    suspend operator fun invoke(): AppResult<Unit> = repo.refresh()
}

class GetBusySlotsUseCase(private val repo: CarRepository) {
    suspend operator fun invoke(carId: Int): AppResult<List<TimeSlot>> = repo.busySlots(carId)
}

class ObserveBookingsUseCase(private val repo: BookingRepository) {
    operator fun invoke(): Flow<List<Booking>> = repo.observeBookings()
}

class RefreshBookingsUseCase(private val repo: BookingRepository) {
    suspend operator fun invoke(): AppResult<Unit> = repo.refresh()
}

class CreateBookingUseCase(
    private val repo: BookingRepository,
    private val reminders: ReminderScheduler,
) {
    suspend operator fun invoke(carId: Int, start: Instant, end: Instant, now: Instant): AppResult<Booking> {
        if (end <= start) return reject("End time must be after start time")
        if (start < now - 5.minutes) return reject("Start time is in the past")
        return repo.create(carId, start, end).onSuccess { reminders.schedule(it) }
    }

    private fun reject(message: String): AppResult<Booking> {
        AppLog.d("CreateBooking", "rejected locally: $message") // platform-specific logging: see AppLog expect/actual
        return AppResult.Failure(AppError.Validation(message))
    }
}

class CancelBookingUseCase(
    private val repo: BookingRepository,
    private val reminders: ReminderScheduler,
) {
    suspend operator fun invoke(bookingId: Int): AppResult<Booking> = repo.cancel(bookingId).onSuccess { reminders.cancel(bookingId) }
}

class ObserveSessionUseCase(private val repo: AuthRepository) {
    operator fun invoke(): Flow<Session?> = repo.session
}

class LoginUseCase(private val repo: AuthRepository) {
    suspend operator fun invoke(email: String, password: String): AppResult<Session> =
        repo.login(email.trim(), password)
}

class RegisterUseCase(private val repo: AuthRepository) {
    suspend operator fun invoke(name: String, email: String, password: String): AppResult<Session> =
        repo.register(name.trim(), email.trim(), password)
}

class LogoutUseCase(private val repo: AuthRepository) {
    suspend operator fun invoke() = repo.logout()
}

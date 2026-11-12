package com.hxrsh.sawari.di

import com.hxrsh.sawari.domain.reminder.ReminderScheduler
import com.hxrsh.sawari.domain.repository.AuthRepository
import com.hxrsh.sawari.domain.repository.BookingRepository
import com.hxrsh.sawari.domain.repository.CarRepository
import com.hxrsh.sawari.domain.usecase.CancelBookingUseCase
import com.hxrsh.sawari.domain.usecase.CreateBookingUseCase
import com.hxrsh.sawari.domain.usecase.GetBusySlotsUseCase
import com.hxrsh.sawari.domain.usecase.LoginUseCase
import com.hxrsh.sawari.domain.usecase.LogoutUseCase
import com.hxrsh.sawari.domain.usecase.ObserveBookingsUseCase
import com.hxrsh.sawari.domain.usecase.ObserveCarUseCase
import com.hxrsh.sawari.domain.usecase.ObserveCarsUseCase
import com.hxrsh.sawari.domain.usecase.ObserveCitiesUseCase
import com.hxrsh.sawari.domain.usecase.ObserveSessionUseCase
import com.hxrsh.sawari.domain.usecase.RefreshBookingsUseCase
import com.hxrsh.sawari.domain.usecase.RefreshCarsUseCase
import com.hxrsh.sawari.domain.usecase.RegisterUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * The use cases live in the shared module, where javax.inject (JVM-only) is not available, so they carry no @Inject.
 * Hilt builds them here instead.
 */
@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
    @Provides fun observeCars(repo: CarRepository) = ObserveCarsUseCase(repo)
    @Provides fun observeCities(repo: CarRepository) = ObserveCitiesUseCase(repo)
    @Provides fun observeCar(repo: CarRepository) = ObserveCarUseCase(repo)
    @Provides fun refreshCars(repo: CarRepository) = RefreshCarsUseCase(repo)
    @Provides fun busySlots(repo: CarRepository) = GetBusySlotsUseCase(repo)
    @Provides fun observeBookings(repo: BookingRepository) = ObserveBookingsUseCase(repo)
    @Provides fun refreshBookings(repo: BookingRepository) = RefreshBookingsUseCase(repo)
    @Provides fun createBooking(repo: BookingRepository, reminders: ReminderScheduler) = CreateBookingUseCase(repo, reminders)
    @Provides fun cancelBooking(repo: BookingRepository, reminders: ReminderScheduler) = CancelBookingUseCase(repo, reminders)
    @Provides fun observeSession(repo: AuthRepository) = ObserveSessionUseCase(repo)
    @Provides fun login(repo: AuthRepository) = LoginUseCase(repo)
    @Provides fun register(repo: AuthRepository) = RegisterUseCase(repo)
    @Provides fun logout(repo: AuthRepository) = LogoutUseCase(repo)
}

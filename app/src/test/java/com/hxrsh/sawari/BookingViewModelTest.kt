package com.hxrsh.sawari

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.hxrsh.sawari.analytics.Events
import com.hxrsh.sawari.domain.AppError
import com.hxrsh.sawari.domain.AppResult
import com.hxrsh.sawari.domain.model.TimeSlot
import com.hxrsh.sawari.domain.usecase.CreateBookingUseCase
import com.hxrsh.sawari.domain.usecase.GetBusySlotsUseCase
import com.hxrsh.sawari.domain.usecase.ObserveCarUseCase
import com.hxrsh.sawari.ui.booking.BookingEffect
import com.hxrsh.sawari.ui.booking.BookingIntent
import com.hxrsh.sawari.ui.booking.BookingState
import com.hxrsh.sawari.ui.booking.BookingViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class BookingViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val carRepo = FakeCarRepository()
    private val bookingRepo = FakeBookingRepository()
    private val analytics = FakeAnalytics()
    // 2030-06-01 09:20 UTC, so the default slot is 10:00 that day (now + 1h, truncated to the hour)
    private val clock = Clock.fixed(Instant.parse("2030-06-01T09:20:00Z"), ZoneOffset.UTC)

    private fun vm() = BookingViewModel(
        SavedStateHandle(mapOf("carId" to 1)),
        ObserveCarUseCase(carRepo),
        GetBusySlotsUseCase(carRepo),
        CreateBookingUseCase(bookingRepo),
        analytics,
        clock,
    )

    @Test fun `initial slot is now plus one hour, truncated to the hour, car and price are loaded`() = runTest {
        val s = vm().state.value
        assertEquals(LocalDate.of(2030, 6, 1), s.startDate)
        assertEquals(LocalTime.of(10, 0), s.startTime)
        assertEquals(Fixtures.swift, s.car)
        assertEquals(3 * 180, s.total)
    }

    @Test fun `duration is clamped between 1 and 72 hours`() = runTest {
        val vm = vm()
        repeat(10) { vm.onIntent(BookingIntent.ChangeDuration(-1)) }
        assertEquals(BookingState.MIN_HOURS, vm.state.value.durationHours)
        vm.onIntent(BookingIntent.ChangeDuration(500))
        assertEquals(BookingState.MAX_HOURS, vm.state.value.durationHours)
    }

    @Test fun `submit success emits Booked effect and logs analytics`() = runTest {
        val vm = vm()
        vm.effects.test {
            vm.onIntent(BookingIntent.Submit)
            assertTrue(awaitItem() is BookingEffect.Booked)
        }
        val (carId, start, end) = bookingRepo.created.single()
        assertEquals(1, carId)
        assertEquals(Instant.parse("2030-06-01T10:00:00Z"), start)
        assertEquals(Instant.parse("2030-06-01T13:00:00Z"), end)
        assertEquals(Events.BOOKING_CREATED, analytics.events.single().first)
        assertFalse(vm.state.value.isSubmitting)
    }

    @Test fun `conflict from the server shows the conflict state, reloads busy slots and keeps the user on the screen`() = runTest {
        bookingRepo.createResult = AppResult.Failure(AppError.Conflict("This car is already booked for part of that time range"))
        val slot = TimeSlot(Instant.parse("2030-06-01T10:00:00Z"), Instant.parse("2030-06-01T13:00:00Z"))
        carRepo.busy = AppResult.Success(listOf(slot))
        val vm = vm()
        vm.effects.test {
            vm.onIntent(BookingIntent.Submit)
            val effect = awaitItem()
            assertTrue(effect is BookingEffect.ShowMessage)
        }
        val s = vm.state.value
        assertTrue(s.conflict)
        assertNotNull(s.error)
        assertEquals(listOf(slot), s.busy)
        assertEquals(Events.BOOKING_CONFLICT, analytics.events.single().first)
    }

    @Test fun `changing the slot after a conflict clears the error`() = runTest {
        bookingRepo.createResult = AppResult.Failure(AppError.Conflict("taken"))
        val vm = vm()
        vm.onIntent(BookingIntent.Submit)
        assertTrue(vm.state.value.conflict)
        vm.onIntent(BookingIntent.PickTime(LocalTime.of(18, 0)))
        assertFalse(vm.state.value.conflict)
        assertNull(vm.state.value.error)
    }

    @Test fun `a start time in the past is rejected before any network call`() = runTest {
        val vm = vm()
        vm.onIntent(BookingIntent.PickDate(LocalDate.of(2030, 5, 1)))
        vm.onIntent(BookingIntent.Submit)
        assertTrue(bookingRepo.created.isEmpty())
        assertEquals("Start time is in the past", vm.state.value.error)
        assertFalse(vm.state.value.conflict)
    }

    @Test fun `network failure surfaces a message and re-enables submit`() = runTest {
        bookingRepo.createResult = networkFailure
        val vm = vm()
        vm.onIntent(BookingIntent.Submit)
        assertTrue(vm.state.value.canSubmit)
        assertEquals("Can't reach Sawari. Check your connection.", vm.state.value.error)
    }
}

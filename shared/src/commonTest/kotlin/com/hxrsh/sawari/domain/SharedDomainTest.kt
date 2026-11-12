package com.hxrsh.sawari.domain

import com.hxrsh.sawari.domain.model.Booking
import com.hxrsh.sawari.domain.model.BookingStatus
import com.hxrsh.sawari.domain.model.Car
import com.hxrsh.sawari.domain.model.CarFilter
import com.hxrsh.sawari.domain.model.Session
import com.hxrsh.sawari.domain.model.TimeSlot
import com.hxrsh.sawari.domain.model.User
import com.hxrsh.sawari.domain.reminder.ReminderPlanner
import com.hxrsh.sawari.domain.reminder.ReminderScheduler
import com.hxrsh.sawari.domain.repository.AuthRepository
import com.hxrsh.sawari.domain.repository.BookingRepository
import com.hxrsh.sawari.domain.usecase.CancelBookingUseCase
import com.hxrsh.sawari.domain.usecase.CreateBookingUseCase
import com.hxrsh.sawari.domain.usecase.LoginUseCase
import com.hxrsh.sawari.domain.usecase.RegisterUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val NOW = Instant.parse("2030-01-01T10:00:00Z")
private val SWIFT = Car(1, "Maruti Swift", "Bengaluru", 180, "https://img/1", 5, "Petrol")

private fun booking(id: Int, startsIn: Duration, status: BookingStatus = BookingStatus.CONFIRMED) =
    Booking(id, SWIFT, NOW + startsIn, NOW + startsIn + 3.hours, 540, status)

private class FakeBookings : BookingRepository {
    val all = MutableStateFlow<List<Booking>>(emptyList())
    var createResult: AppResult<Booking>? = null
    var cancelResult: AppResult<Booking>? = null
    var createCalls = 0
    override fun observeBookings(): Flow<List<Booking>> = all
    override suspend fun refresh(): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun create(carId: Int, start: Instant, end: Instant): AppResult<Booking> {
        createCalls++
        return createResult ?: AppResult.Success(Booking(99, SWIFT, start, end, 540, BookingStatus.CONFIRMED))
    }
    override suspend fun cancel(bookingId: Int): AppResult<Booking> =
        cancelResult ?: AppResult.Success(booking(bookingId, 5.hours, BookingStatus.CANCELLED))
}

private class FakeReminders : ReminderScheduler {
    val scheduled = mutableListOf<Booking>()
    val cancelled = mutableListOf<Int>()
    override fun schedule(booking: Booking) { scheduled += booking }
    override fun cancel(bookingId: Int) { cancelled += bookingId }
}

class ReminderPlannerTest {
    private val planner = ReminderPlanner()

    @Test fun firesOneHourBeforeTheStart() = assertEquals(4.hours, planner.delayUntilReminder(NOW + 5.hours, NOW))
    @Test fun exactlyOneHourAwayFiresImmediately() = assertEquals(Duration.ZERO, planner.delayUntilReminder(NOW + 1.hours, NOW))
    @Test fun lessThanAnHourAwayGetsNoReminder() {
        assertNull(planner.delayUntilReminder(NOW + 59.minutes, NOW))
        assertNull(planner.delayUntilReminder(NOW + 1.seconds, NOW))
    }
    @Test fun pastBookingGetsNoReminder() = assertNull(planner.delayUntilReminder(NOW - 2.hours, NOW))
    @Test fun farFutureBookingIsScheduledForTheRightMoment() = assertEquals(30.days - 1.hours, planner.delayUntilReminder(NOW + 30.days, NOW))
    @Test fun customLeadTimeIsRespected() = assertEquals(90.minutes, ReminderPlanner(30.minutes).delayUntilReminder(NOW + 2.hours, NOW))
}

class BookingModelTest {
    @Test fun confirmedFutureBookingCanBeCancelled() = assertTrue(booking(1, 2.hours).isCancellable(NOW))
    @Test fun cancelledBookingCannot() = assertFalse(booking(1, 2.hours, BookingStatus.CANCELLED).isCancellable(NOW))
    @Test fun finishedBookingCannot() = assertFalse(booking(1, (-5).hours).isCancellable(NOW))
    @Test fun bookingThatEndsExactlyNowCannot() = assertFalse(booking(1, (-3).hours).isCancellable(NOW))
    @Test fun carFilterDefaultsAreEmpty() = assertEquals(CarFilter(null, null, ""), CarFilter())
}

class AppResultTest {
    @Test fun mapTransformsSuccessOnly() {
        assertEquals(AppResult.Success(4), AppResult.Success(2).map { it * 2 })
        val failure = AppResult.Failure(AppError.Network())
        assertEquals(failure, failure.map { 1 })
    }

    @Test fun onSuccessAndOnFailureRunTheRightBlock() {
        var ok = 0; var bad = 0
        AppResult.Success(1).onSuccess { ok++ }.onFailure { bad++ }
        AppResult.Failure(AppError.NotFound("x")).onSuccess { ok++ }.onFailure { bad++ }
        assertEquals(1 to 1, ok to bad)
    }

    @Test fun errorOrNullOnlyForFailures() {
        assertNull(AppResult.Success(1).errorOrNull())
        assertEquals(AppError.Conflict("c"), AppResult.Failure(AppError.Conflict("c")).errorOrNull())
    }

    @Test fun everyErrorHasAUserMessage() {
        val expected = listOf(
            AppError.Network() to "Can't reach Sawari. Check your connection.",
            AppError.Unauthorized() to "Your session expired. Please sign in again.",
            AppError.Unauthorized("Bad password") to "Bad password",
            AppError.Conflict("Slot taken") to "Slot taken",
            AppError.Validation("Too short") to "Too short",
            AppError.NotFound("No car") to "No car",
            AppError.Server(503) to "Server error (503). Please try again.",
            AppError.Unknown() to "Something went wrong.",
        )
        expected.forEach { (error, message) -> assertEquals(message, error.userMessage(), "for $error") }
    }
}

class CreateAndCancelUseCaseTest {
    private val repo = FakeBookings()
    private val reminders = FakeReminders()

    @Test fun successfulBookingSchedulesItsReminder() = runTest {
        val result = CreateBookingUseCase(repo, reminders)(1, NOW + 2.hours, NOW + 5.hours, NOW)
        assertIs<AppResult.Success<*>>(result)
        assertEquals(listOf(99), reminders.scheduled.map { it.id })
    }

    @Test fun endBeforeStartIsRejectedWithoutTouchingTheRepository() = runTest {
        val result = CreateBookingUseCase(repo, reminders)(1, NOW + 2.hours, NOW + 1.hours, NOW)
        assertEquals(AppResult.Failure(AppError.Validation("End time must be after start time")), result)
        assertEquals(0, repo.createCalls)
        assertTrue(reminders.scheduled.isEmpty())
    }

    @Test fun zeroLengthBookingIsRejected() = runTest {
        val result = CreateBookingUseCase(repo, reminders)(1, NOW + 2.hours, NOW + 2.hours, NOW)
        assertIs<AppResult.Failure>(result)
    }

    @Test fun startInThePastIsRejectedButFiveMinutesOfSlackIsAllowed() = runTest {
        val useCase = CreateBookingUseCase(repo, reminders)
        assertEquals(AppResult.Failure(AppError.Validation("Start time is in the past")), useCase(1, NOW - 6.minutes, NOW + 1.hours, NOW))
        assertIs<AppResult.Success<*>>(useCase(1, NOW - 4.minutes, NOW + 1.hours, NOW))
    }

    @Test fun repositoryFailureSchedulesNothing() = runTest {
        repo.createResult = AppResult.Failure(AppError.Conflict("taken"))
        val result = CreateBookingUseCase(repo, reminders)(1, NOW + 2.hours, NOW + 5.hours, NOW)
        assertEquals(AppResult.Failure(AppError.Conflict("taken")), result)
        assertTrue(reminders.scheduled.isEmpty())
    }

    @Test fun cancellingCancelsTheReminderOnlyOnSuccess() = runTest {
        CancelBookingUseCase(repo, reminders)(5)
        assertEquals(listOf(5), reminders.cancelled)
        repo.cancelResult = AppResult.Failure(AppError.Network())
        CancelBookingUseCase(repo, reminders)(6)
        assertEquals(listOf(5), reminders.cancelled)
    }
}

class AuthUseCaseTest {
    private class FakeAuth : AuthRepository {
        val calls = mutableListOf<String>()
        override val session: Flow<Session?> = MutableStateFlow(null)
        override suspend fun login(email: String, password: String): AppResult<Session> {
            calls += "login:$email"; return AppResult.Success(Session("t", User(1, "A", email)))
        }
        override suspend fun register(name: String, email: String, password: String): AppResult<Session> {
            calls += "register:$name:$email"; return AppResult.Success(Session("t", User(1, name, email)))
        }
        override suspend fun logout() = Unit
    }

    @Test fun loginAndRegisterTrimTheirInput() = runTest {
        val repo = FakeAuth()
        LoginUseCase(repo)("  a@b.co ", "pw")
        RegisterUseCase(repo)("  Asha ", " a@b.co  ", "pw")
        assertEquals(listOf("login:a@b.co", "register:Asha:a@b.co"), repo.calls)
    }
}

class TimeSlotTest {
    @Test fun slotKeepsItsBounds() {
        val slot = TimeSlot(NOW, NOW + 1.hours)
        assertEquals(1.hours, slot.end - slot.start)
    }
}

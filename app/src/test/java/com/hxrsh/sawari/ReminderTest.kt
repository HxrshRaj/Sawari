package com.hxrsh.sawari

import com.hxrsh.sawari.domain.AppError
import com.hxrsh.sawari.domain.AppResult
import com.hxrsh.sawari.domain.model.BookingStatus
import com.hxrsh.sawari.domain.reminder.ReminderPlanner
import com.hxrsh.sawari.domain.usecase.CancelBookingUseCase
import com.hxrsh.sawari.domain.usecase.CreateBookingUseCase
import com.hxrsh.sawari.ui.permissions.PermissionStatus
import com.hxrsh.sawari.ui.permissions.resolvePermissionStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class ReminderPlannerTest {
    private val planner = ReminderPlanner()
    private val now = Instant.parse("2030-01-01T10:00:00Z")
    private fun clockAt(i: Instant) = Clock.fixed(i, ZoneOffset.UTC)

    @Test fun `fires one hour before the start`() {
        assertEquals(Duration.ofHours(4), planner.delayUntilReminder(now.plus(Duration.ofHours(5)), clockAt(now).instant()))
    }

    @Test fun `booking starting in exactly one hour fires immediately`() {
        assertEquals(Duration.ZERO, planner.delayUntilReminder(now.plus(Duration.ofHours(1)), now))
    }

    @Test fun `booking starting in less than an hour gets no reminder`() {
        assertNull(planner.delayUntilReminder(now.plus(Duration.ofMinutes(59)), now))
        assertNull(planner.delayUntilReminder(now.plusSeconds(1), now))
    }

    @Test fun `booking in the past gets no reminder`() {
        assertNull(planner.delayUntilReminder(now.minus(Duration.ofHours(2)), now))
    }

    @Test fun `far future booking is scheduled for the right moment`() {
        assertEquals(Duration.ofDays(30).minusHours(1), planner.delayUntilReminder(now.plus(Duration.ofDays(30)), now))
    }

    @Test fun `a custom lead time is respected`() {
        assertEquals(Duration.ofMinutes(90), ReminderPlanner(Duration.ofMinutes(30)).delayUntilReminder(now.plus(Duration.ofHours(2)), now))
    }

    @Test fun `delay shrinks as the clock advances`() {
        val start = now.plus(Duration.ofHours(5))
        val later = now.plus(Duration.ofHours(3))
        assertEquals(Duration.ofHours(1), planner.delayUntilReminder(start, clockAt(later).instant()))
    }
}

class ReminderUseCaseTest {
    private val repo = FakeBookingRepository()
    private val reminders = FakeReminderScheduler()
    private val now = Instant.now()

    @Test fun `successful booking schedules a reminder for that booking`() = runTest {
        val result = CreateBookingUseCase(repo, reminders)(1, now.plusSeconds(7200), now.plusSeconds(10800), now)
        assertTrue(result is AppResult.Success)
        assertEquals(listOf(99), reminders.scheduled.map { it.id })
    }

    @Test fun `failed booking schedules nothing`() = runTest {
        repo.createResult = AppResult.Failure(AppError.Conflict("taken"))
        CreateBookingUseCase(repo, reminders)(1, now.plusSeconds(7200), now.plusSeconds(10800), now)
        assertTrue(reminders.scheduled.isEmpty())
    }

    @Test fun `invalid time range schedules nothing and never reaches the repository`() = runTest {
        CreateBookingUseCase(repo, reminders)(1, now.plusSeconds(7200), now.plusSeconds(3600), now)
        assertTrue(reminders.scheduled.isEmpty())
        assertTrue(repo.created.isEmpty())
    }

    @Test fun `cancelling a booking cancels its reminder`() = runTest {
        repo.bookings.value = listOf(Fixtures.booking(5))
        CancelBookingUseCase(repo, reminders)(5)
        assertEquals(listOf(5), reminders.cancelled)
    }

    @Test fun `failed cancel keeps the reminder`() = runTest {
        repo.cancelResult = AppResult.Failure(AppError.Network())
        CancelBookingUseCase(repo, reminders)(5)
        assertTrue(reminders.cancelled.isEmpty())
    }

    @Test fun `scheduled booking is the confirmed one returned by the server`() = runTest {
        CreateBookingUseCase(repo, reminders)(2, now.plusSeconds(7200), now.plusSeconds(10800), now)
        assertEquals(BookingStatus.CONFIRMED, reminders.scheduled.single().status)
        assertEquals(Fixtures.nexon, reminders.scheduled.single().car)
    }
}

class PermissionStatusTest {
    private val table = listOf(
        // granted, askedBefore, shouldShowRationale -> expected
        Triple(Triple(true, false, false), null, PermissionStatus.Granted),
        Triple(Triple(true, true, false), null, PermissionStatus.Granted),
        Triple(Triple(false, false, false), null, PermissionStatus.NotAsked),
        Triple(Triple(false, true, true), null, PermissionStatus.DeniedCanAskAgain),
        Triple(Triple(false, true, false), null, PermissionStatus.DeniedPermanently),
        // rationale flag alone (never asked by us) must not skip the explain-first step
        Triple(Triple(false, false, true), null, PermissionStatus.NotAsked),
    )

    @Test fun `every combination resolves to the right state`() {
        table.forEach { (input, _, expected) ->
            val (granted, asked, rationale) = input
            assertEquals("granted=$granted asked=$asked rationale=$rationale", expected, resolvePermissionStatus(granted, asked, rationale))
        }
    }
}

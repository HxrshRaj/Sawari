package com.hxrsh.sawari

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.hxrsh.sawari.domain.model.Booking
import com.hxrsh.sawari.domain.model.BookingStatus
import com.hxrsh.sawari.domain.model.Car
import com.hxrsh.sawari.notifications.BookingReminderWorker
import com.hxrsh.sawari.notifications.ReminderNotifications
import com.hxrsh.sawari.notifications.WorkManagerReminderScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import kotlinx.datetime.toKotlinInstant
import java.time.ZoneOffset

/**
 * Real WorkManager (test mode), real NotificationManager on the emulator. The clock is fake, so no test waits an hour.
 */
@RunWith(AndroidJUnit4::class)
class ReminderInstrumentedTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val now = Instant.parse("2030-01-01T10:00:00Z")
    private val scheduler = WorkManagerReminderScheduler(ctx, Clock.fixed(now, ZoneOffset.UTC))
    private val car = Car(1, "Maruti Swift", "Bengaluru", 180, "", 5, "Petrol")

    private fun booking(id: Int, startsInHours: Long, status: BookingStatus = BookingStatus.CONFIRMED) =
        Booking(id, car, now.plusSeconds(startsInHours * 3600).toKotlinInstant(), now.plusSeconds((startsInHours + 3) * 3600).toKotlinInstant(), 540, status)

    private fun states(id: Int): List<WorkInfo.State> =
        WorkManager.getInstance(ctx).getWorkInfosForUniqueWork(WorkManagerReminderScheduler.workName(id)).get().map { it.state }

    @Before fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(ctx)
        ReminderNotifications.ensureChannel(ctx)
    }

    @After fun tearDown() {
        ctx.getSystemService(NotificationManager::class.java).cancelAll()
    }

    @Test fun bookingFiveHoursAwayIsScheduled() {
        scheduler.schedule(booking(1, startsInHours = 5))
        assertEquals(listOf(WorkInfo.State.ENQUEUED), states(1))
    }

    @Test fun cancellingTheBookingCancelsTheReminder() {
        scheduler.schedule(booking(2, startsInHours = 5))
        scheduler.cancel(2)
        assertEquals(listOf(WorkInfo.State.CANCELLED), states(2))
    }

    @Test fun bookingStartingInThirtyMinutesGetsNoReminder() {
        scheduler.schedule(booking(3, startsInHours = 0))
        assertTrue(states(3).isEmpty())
    }

    @Test fun cancelledBookingIsNotScheduled() {
        scheduler.schedule(booking(4, startsInHours = 5, status = BookingStatus.CANCELLED))
        assertTrue(states(4).isEmpty())
    }

    @Test fun reschedulingReplacesTheEarlierReminder() {
        scheduler.schedule(booking(5, startsInHours = 5))
        scheduler.schedule(booking(5, startsInHours = 8))
        assertEquals(1, states(5).count { it == WorkInfo.State.ENQUEUED })
    }

    @Test fun reminderChannelExistsWithTheRightName() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        val ch = ctx.getSystemService(NotificationManager::class.java).getNotificationChannel(ReminderNotifications.CHANNEL_ID)
        assertNotNull(ch)
        assertEquals("Booking reminders", ch.name.toString())
    }

    @Test fun workerPostsTheNotificationWhenAllowedAndTapIntentIsTheBookingsDeepLink() {
        grantNotificationsIfNeeded()
        val worker = TestListenableWorkerBuilder<BookingReminderWorker>(ctx)
            .setInputData(androidx.work.Data.Builder().putInt(BookingReminderWorker.KEY_BOOKING_ID, 7).putString(BookingReminderWorker.KEY_CAR_NAME, "Maruti Swift").build())
            .build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())

        val posted = awaitNotification(10_007)
        assertNotNull("notification not posted", posted)
        assertEquals("Your booking starts in 1 hour", posted!!.notification.extras.getString("android.title"))
        assertEquals("Maruti Swift", posted.notification.extras.getString("android.text"))
        assertEquals(ReminderNotifications.CHANNEL_ID, posted.notification.channelId)
        assertNotNull(posted.notification.contentIntent)
    }

    @Test fun tappingTheNotificationDeliversTheBookingsDeepLinkToMainActivity() {
        grantNotificationsIfNeeded()
        ReminderNotifications.show(ctx, 9, "Maruti Swift")
        val posted = awaitNotification(10_009)!!
        // What the system does when the user taps it. From a test process Android 14 blocks background activity starts
        // unless the sender opts in, so we opt in (SystemUI is privileged and does not need to).
        val options = if (Build.VERSION.SDK_INT >= 34) {
            android.app.ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle()
        } else null
        posted.notification.contentIntent.send(ctx, 0, null, null, null, null, options)
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = System.currentTimeMillis() + 15_000
        var dump = ""
        while (System.currentTimeMillis() < deadline && !dump.contains("dat=sawari://bookings")) {
            Thread.sleep(500)
            ui.executeShellCommand("dumpsys activity activities").use { fd ->
                dump = android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().readText()
            }
        }
        assertTrue("MainActivity did not receive sawari://bookings; dump len=${dump.length}; relevant: " + dump.lines().filter { it.contains("sawari") }.take(8).joinToString(" | "), dump.contains("dat=sawari://bookings"))
        assertTrue(dump.contains("com.hxrsh.sawari/.MainActivity"))
    }

    /** NotificationManager.notify is asynchronous: wait for the notification instead of reading it immediately. */
    private fun awaitNotification(id: Int, timeoutMs: Long = 8_000) =
        run {
            val deadline = System.currentTimeMillis() + timeoutMs
            var found: android.service.notification.StatusBarNotification? = null
            while (found == null && System.currentTimeMillis() < deadline) {
                found = ctx.getSystemService(NotificationManager::class.java).activeNotifications.firstOrNull { it.id == id }
                if (found == null) Thread.sleep(200)
            }
            found
        }

    private fun grantNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(ctx.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

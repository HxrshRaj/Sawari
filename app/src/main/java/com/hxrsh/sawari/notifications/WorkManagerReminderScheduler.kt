package com.hxrsh.sawari.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.hxrsh.sawari.domain.model.Booking
import com.hxrsh.sawari.domain.model.BookingStatus
import com.hxrsh.sawari.domain.reminder.ReminderPlanner
import com.hxrsh.sawari.domain.reminder.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

class BookingReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val id = inputData.getInt(KEY_BOOKING_ID, -1)
        if (id < 0) return Result.failure()
        // If notifications are blocked this is a quiet no-op; the booking itself is unaffected.
        ReminderNotifications.show(applicationContext, id, inputData.getString(KEY_CAR_NAME).orEmpty())
        return Result.success()
    }

    companion object {
        const val KEY_BOOKING_ID = "booking_id"
        const val KEY_CAR_NAME = "car_name"
    }
}

@Singleton
class WorkManagerReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) : ReminderScheduler {
    private val planner = ReminderPlanner()

    override fun schedule(booking: Booking) {
        if (booking.status != BookingStatus.CONFIRMED) return
        val delay = planner.delayUntilReminder(booking.start, clock.instant()) ?: return
        val request = OneTimeWorkRequestBuilder<BookingReminderWorker>()
            .setInitialDelay(delay)
            .setInputData(
                Data.Builder()
                    .putInt(BookingReminderWorker.KEY_BOOKING_ID, booking.id)
                    .putString(BookingReminderWorker.KEY_CAR_NAME, booking.car.name)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(booking.id), ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel(bookingId: Int) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(bookingId))
    }

    companion object {
        fun workName(bookingId: Int) = "booking-reminder-$bookingId"
    }
}

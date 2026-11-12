package com.hxrsh.sawari.domain.reminder

import com.hxrsh.sawari.domain.model.Booking
import java.time.Duration
import java.time.Instant

/** Schedules / cancels the "your booking starts soon" reminder. The Android implementation uses WorkManager. */
interface ReminderScheduler {
    fun schedule(booking: Booking)
    fun cancel(bookingId: Int)
}

/** Pure scheduling rule, so it can be tested with a fake clock. */
class ReminderPlanner(private val lead: Duration = DEFAULT_LEAD) {
    /**
     * Time to wait from [now] until the reminder should fire ([lead] before [start]).
     * Null when that moment has already passed (the booking starts in less than [lead]): no reminder is scheduled,
     * because a "starts in 1 hour" notification would be a lie.
     */
    fun delayUntilReminder(start: Instant, now: Instant): Duration? {
        val delay = Duration.between(now, start.minus(lead))
        return if (delay.isNegative) null else delay
    }

    companion object {
        val DEFAULT_LEAD: Duration = Duration.ofHours(1)
    }
}

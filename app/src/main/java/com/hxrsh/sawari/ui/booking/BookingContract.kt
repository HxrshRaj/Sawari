package com.hxrsh.sawari.ui.booking

import androidx.compose.runtime.Immutable
import com.hxrsh.sawari.domain.model.Booking
import com.hxrsh.sawari.domain.model.Car
import com.hxrsh.sawari.domain.model.TimeSlot
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** MVI contract of the booking screen: one State, Intents in, one-shot Effects out. */
@Immutable
data class BookingState(
    val car: Car? = null,
    val zone: ZoneId = ZoneId.systemDefault(),
    val startDate: LocalDate,
    val startTime: LocalTime,
    val durationHours: Int = 3,
    val busy: List<TimeSlot> = emptyList(),
    val isSubmitting: Boolean = false,
    val error: String? = null,
    /** True when the last failure was a 409: the slot was taken, so the UI highlights the busy list. */
    val conflict: Boolean = false,
) {
    val start: LocalDateTime get() = LocalDateTime.of(startDate, startTime)
    val end: LocalDateTime get() = start.plusHours(durationHours.toLong())
    val total: Int get() = (car?.pricePerHour ?: 0) * durationHours
    val canSubmit: Boolean get() = car != null && !isSubmitting

    companion object {
        const val MIN_HOURS = 1
        const val MAX_HOURS = 72
    }
}

sealed interface BookingIntent {
    data class PickDate(val date: LocalDate) : BookingIntent
    data class PickTime(val time: LocalTime) : BookingIntent
    data class ChangeDuration(val delta: Int) : BookingIntent
    data object Submit : BookingIntent
    data object DismissError : BookingIntent
}

sealed interface BookingEffect {
    data class Booked(val booking: Booking) : BookingEffect
    data class ShowMessage(val message: String) : BookingEffect
}

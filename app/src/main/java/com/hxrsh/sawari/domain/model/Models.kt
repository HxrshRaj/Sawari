package com.hxrsh.sawari.domain.model

import androidx.compose.runtime.Immutable
import java.time.Instant

@Immutable
data class Car(
    val id: Int,
    val name: String,
    val city: String,
    val pricePerHour: Int,
    val imageUrl: String,
    val seats: Int,
    val fuel: String,
)

enum class BookingStatus { CONFIRMED, CANCELLED }

@Immutable
data class Booking(
    val id: Int,
    val car: Car,
    val start: Instant,
    val end: Instant,
    val totalPrice: Int,
    val status: BookingStatus,
) {
    fun isCancellable(now: Instant): Boolean = status == BookingStatus.CONFIRMED && end.isAfter(now)
}

@Immutable
data class TimeSlot(val start: Instant, val end: Instant)

@Immutable
data class User(val id: Int, val name: String, val email: String)

@Immutable
data class Session(val token: String, val user: User)

@Immutable
data class CarFilter(val city: String? = null, val fuel: String? = null, val query: String = "")

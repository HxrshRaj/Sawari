package com.hxrsh.sawari.domain.model

import kotlinx.datetime.Instant

// No @Immutable here: that annotation comes from Compose, which a shared module must not depend on.
// The app module marks this package as stable through the Compose compiler's stability configuration instead
// (app/compose_stability.conf), so these classes stay skippable in the UI.

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

data class Booking(
    val id: Int,
    val car: Car,
    val start: Instant,
    val end: Instant,
    val totalPrice: Int,
    val status: BookingStatus,
) {
    fun isCancellable(now: Instant): Boolean = status == BookingStatus.CONFIRMED && end > now
}

data class TimeSlot(val start: Instant, val end: Instant)

data class User(val id: Int, val name: String, val email: String)

data class Session(val token: String, val user: User)

data class CarFilter(val city: String? = null, val fuel: String? = null, val query: String = "")

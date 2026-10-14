package com.hxrsh.sawari.data.mapper

import com.hxrsh.sawari.data.local.BookingEntity
import com.hxrsh.sawari.data.local.CarEntity
import com.hxrsh.sawari.data.remote.AuthResponse
import com.hxrsh.sawari.data.remote.BookingDto
import com.hxrsh.sawari.data.remote.BusySlotDto
import com.hxrsh.sawari.data.remote.CarDto
import com.hxrsh.sawari.domain.model.Booking
import com.hxrsh.sawari.domain.model.BookingStatus
import com.hxrsh.sawari.domain.model.Car
import com.hxrsh.sawari.domain.model.Session
import com.hxrsh.sawari.domain.model.TimeSlot
import com.hxrsh.sawari.domain.model.User
import java.time.Instant
import java.time.OffsetDateTime

// The API returns ISO-8601 with an offset ("Z" or "+00:00"); OffsetDateTime accepts both.
internal fun parseInstant(iso: String): Instant = OffsetDateTime.parse(iso).toInstant()

fun CarDto.toEntity() = CarEntity(id, name, city, pricePerHour, imageUrl, seats, fuel)

fun CarEntity.toDomain() = Car(id, name, city, pricePerHour, imageUrl, seats, fuel)

fun BookingDto.toEntity() = BookingEntity(
    id = id,
    startMillis = parseInstant(startTime).toEpochMilli(),
    endMillis = parseInstant(endTime).toEpochMilli(),
    totalPrice = totalPrice,
    status = status,
    car = car.toEntity(),
)

fun BookingEntity.toDomain() = Booking(
    id = id,
    car = car.toDomain(),
    start = Instant.ofEpochMilli(startMillis),
    end = Instant.ofEpochMilli(endMillis),
    totalPrice = totalPrice,
    status = if (status.equals("cancelled", ignoreCase = true)) BookingStatus.CANCELLED else BookingStatus.CONFIRMED,
)

fun BusySlotDto.toDomain() = TimeSlot(parseInstant(startTime), parseInstant(endTime))

fun AuthResponse.toDomain() = Session(accessToken, User(user.id, user.name, user.email))

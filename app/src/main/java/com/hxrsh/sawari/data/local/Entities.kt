package com.hxrsh.sawari.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cars")
data class CarEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val city: String,
    val pricePerHour: Int,
    val imageUrl: String,
    val seats: Int,
    val fuel: String,
)

@Entity(tableName = "bookings")
data class BookingEntity(
    @PrimaryKey val id: Int,
    val startMillis: Long,
    val endMillis: Long,
    val totalPrice: Int,
    val status: String,
    @Embedded(prefix = "car_") val car: CarEntity,
)

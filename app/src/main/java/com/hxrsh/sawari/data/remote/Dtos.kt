package com.hxrsh.sawari.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(val name: String, val email: String, val password: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class UserDto(val id: Int, val name: String, val email: String)

@Serializable
data class AuthResponse(@SerialName("access_token") val accessToken: String, val user: UserDto)

@Serializable
data class CarDto(
    val id: Int,
    val name: String,
    val city: String,
    @SerialName("price_per_hour") val pricePerHour: Int,
    @SerialName("image_url") val imageUrl: String,
    val seats: Int,
    val fuel: String,
)

@Serializable
data class BookingRequest(
    @SerialName("car_id") val carId: Int,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
)

@Serializable
data class BookingDto(
    val id: Int,
    @SerialName("car_id") val carId: Int,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    @SerialName("total_price") val totalPrice: Int,
    val status: String,
    val car: CarDto,
)

@Serializable
data class BusySlotDto(
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
)

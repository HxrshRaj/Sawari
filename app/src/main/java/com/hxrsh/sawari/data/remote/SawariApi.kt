package com.hxrsh.sawari.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface SawariApi {
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @GET("cars")
    suspend fun cars(): List<CarDto>

    @GET("cars/{id}/busy")
    suspend fun busySlots(@Path("id") carId: Int): List<BusySlotDto>

    @GET("bookings")
    suspend fun bookings(): List<BookingDto>

    @POST("bookings")
    suspend fun createBooking(@Body body: BookingRequest): BookingDto

    @POST("bookings/{id}/cancel")
    suspend fun cancelBooking(@Path("id") id: Int): BookingDto
}

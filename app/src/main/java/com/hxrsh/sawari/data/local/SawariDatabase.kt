package com.hxrsh.sawari.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [CarEntity::class, BookingEntity::class], version = 1, exportSchema = true)
abstract class SawariDatabase : RoomDatabase() {
    abstract fun carDao(): CarDao
    abstract fun bookingDao(): BookingDao
}

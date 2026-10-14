package com.hxrsh.sawari.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CarDao {
    @Query(
        """
        SELECT * FROM cars
        WHERE (:city IS NULL OR city = :city)
          AND (:fuel IS NULL OR fuel = :fuel)
          AND (:query = '' OR name LIKE '%' || :query || '%')
        ORDER BY id
        """
    )
    fun observe(city: String?, fuel: String?, query: String): Flow<List<CarEntity>>

    @Query("SELECT DISTINCT city FROM cars ORDER BY city")
    fun observeCities(): Flow<List<String>>

    @Query("SELECT * FROM cars WHERE id = :id")
    fun observeById(id: Int): Flow<CarEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cars: List<CarEntity>)

    @Query("DELETE FROM cars")
    suspend fun deleteAll()

    /** Swap the whole catalogue atomically so observers never see a half-empty table. */
    @Transaction
    suspend fun replaceAll(cars: List<CarEntity>) {
        deleteAll()
        insertAll(cars)
    }
}

@Dao
interface BookingDao {
    @Query("SELECT * FROM bookings ORDER BY startMillis DESC")
    fun observeAll(): Flow<List<BookingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(booking: BookingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(bookings: List<BookingEntity>)

    @Query("DELETE FROM bookings")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(bookings: List<BookingEntity>) {
        deleteAll()
        insertAll(bookings)
    }
}

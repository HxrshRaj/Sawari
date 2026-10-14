package com.hxrsh.sawari

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hxrsh.sawari.data.local.BookingEntity
import com.hxrsh.sawari.data.local.CarEntity
import com.hxrsh.sawari.data.local.SawariDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaoTest {
    private lateinit var db: SawariDatabase

    private fun car(id: Int, name: String, city: String, fuel: String) = CarEntity(id, name, city, 100 + id, "u$id", 5, fuel)
    private val a = car(1, "Maruti Swift", "Bengaluru", "Petrol")
    private val b = car(2, "Tata Nexon EV", "Bengaluru", "Electric")
    private val c = car(3, "Hyundai Creta", "Mumbai", "Diesel")

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), SawariDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    @Test fun `null filters match everything, ordered by id`() = runTest {
        db.carDao().insertAll(listOf(c, a, b))
        assertEquals(listOf(a, b, c), db.carDao().observe(null, null, "").first())
    }

    @Test fun `city fuel and name filters combine with AND`() = runTest {
        db.carDao().insertAll(listOf(a, b, c))
        val dao = db.carDao()
        assertEquals(listOf(a, b), dao.observe("Bengaluru", null, "").first())
        assertEquals(listOf(b), dao.observe("Bengaluru", "Electric", "").first())
        assertEquals(listOf(c), dao.observe(null, null, "creta").first()) // LIKE is case-insensitive for ASCII
        assertEquals(emptyList<CarEntity>(), dao.observe("Mumbai", "Electric", "").first())
    }

    @Test fun `replaceAll swaps the table atomically`() = runTest {
        val dao = db.carDao()
        dao.insertAll(listOf(a, b))
        dao.replaceAll(listOf(c))
        assertEquals(listOf(c), dao.observe(null, null, "").first())
        assertEquals(listOf("Mumbai"), dao.observeCities().first())
    }

    @Test fun `cities are distinct and sorted, single car lookup works`() = runTest {
        db.carDao().insertAll(listOf(c, a, b))
        assertEquals(listOf("Bengaluru", "Mumbai"), db.carDao().observeCities().first())
        assertEquals(b, db.carDao().observeById(2).first())
        assertNull(db.carDao().observeById(99).first())
    }

    @Test fun `bookings are newest first, embed the car, and upsert replaces`() = runTest {
        val dao = db.bookingDao()
        val early = BookingEntity(1, 1_000, 2_000, 300, "confirmed", a)
        val late = BookingEntity(2, 5_000, 6_000, 400, "confirmed", b)
        dao.insertAll(listOf(early, late))
        assertEquals(listOf(late, early), dao.observeAll().first())
        dao.upsert(early.copy(status = "cancelled"))
        assertEquals("cancelled", dao.observeAll().first().last().status)
        dao.replaceAll(listOf(late))
        assertEquals(listOf(late), dao.observeAll().first())
        dao.deleteAll()
        assertEquals(emptyList<BookingEntity>(), dao.observeAll().first())
    }
}

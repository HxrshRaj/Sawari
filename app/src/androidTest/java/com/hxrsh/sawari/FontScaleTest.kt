package com.hxrsh.sawari

import android.graphics.Bitmap
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.graphics.asAndroidBitmap
import com.hxrsh.sawari.domain.model.Booking
import com.hxrsh.sawari.domain.model.BookingStatus
import com.hxrsh.sawari.domain.model.Car
import com.hxrsh.sawari.domain.model.TimeSlot
import com.hxrsh.sawari.ui.auth.AuthContent
import com.hxrsh.sawari.ui.auth.AuthState
import com.hxrsh.sawari.ui.booking.BookingContent
import com.hxrsh.sawari.ui.booking.BookingState
import com.hxrsh.sawari.ui.bookings.BookingsState
import com.hxrsh.sawari.ui.bookings.MyBookingsContent
import com.hxrsh.sawari.ui.cars.CarListContent
import com.hxrsh.sawari.ui.cars.CarListState
import com.hxrsh.sawari.ui.common.UiState
import com.hxrsh.sawari.ui.detail.CarDetailContent
import com.hxrsh.sawari.ui.detail.CarDetailState
import com.hxrsh.sawari.ui.theme.SawariTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Renders every screen at the system's largest font scale (2.0x) next to 1.0x, asserts the primary action
 * is still on screen and saves PNGs as additional test output (collected into docs/a11y by hand).
 * Clipping/overflow is judged by looking at those images; the test itself only guards "action still reachable".
 */
@RunWith(AndroidJUnit4::class)
class FontScaleTest {
    @get:Rule val rule = createComposeRule()

    private val long = Car(1, "Toyota Innova Crysta Hybrid", "Bengaluru", 500, "", 7, "Diesel")
    private val short = Car(2, "Tata Nexon EV", "Bengaluru", 320, "", 5, "Electric")
    private val slot = TimeSlot(Instant.now().plusSeconds(7200), Instant.now().plusSeconds(18000))
    private val booking = Booking(5, long, Instant.now().plusSeconds(3600), Instant.now().plusSeconds(10800), 1500, BookingStatus.CONFIRMED)

    private fun render(name: String, scale: Float, content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = scale)) { SawariTheme { content() } }
        }
        rule.waitForIdle()
        // AGP copies this directory back to the build output after the run (the app itself is uninstalled).
        val out = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val dir = File(out ?: InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!.path).apply { mkdirs() }
        File(dir, "${name}_x${scale}.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 90, it)
        }
    }

    private fun both(name: String, check: () -> Unit, content: @androidx.compose.runtime.Composable () -> Unit) {
        render(name, 2.0f, content); check()
    }

    @Test fun auth() = both("auth", { rule.onNodeWithTag("submit_button").assertIsDisplayed() }) {
        AuthContent(AuthState(registering = true, error = "Enter a valid email address"), {}, {}, {}, {}, {})
    }

    @Test fun carList() = both("cars", { rule.onNodeWithTag("car_card_1").assertIsDisplayed() }) {
        CarListContent(CarListState(content = UiState.Content(listOf(long, short)), cities = listOf("Bengaluru", "Mumbai", "Delhi"), staleNotice = "You're offline. Showing saved cars."), {}, {}, {}, {}, {})
    }

    @Test fun carDetail() = both("detail", { rule.onNodeWithTag("book_button").assertIsDisplayed() }) {
        CarDetailContent(CarDetailState(UiState.Content(long), listOf(slot)), {}, {})
    }

    @Test fun booking() = both("booking", { rule.onNodeWithTag("back_button").assertIsDisplayed() }) {
        BookingContent(
            BookingState(car = long, startDate = LocalDate.now().plusDays(1), startTime = LocalTime.of(10, 0), busy = listOf(slot), error = "This car is already booked for part of that time range", conflict = true),
            SnackbarHostState(), {}, {},
        )
    }

    @Test fun myBookings() = both("bookings", { rule.onNodeWithTag("booking_card_5").assertIsDisplayed() }) {
        MyBookingsContent(BookingsState(content = UiState.Content(listOf(booking)), now = Instant.now()), SnackbarHostState(), {}, {}, {}, showDebugMenu = false)
    }
}

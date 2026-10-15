package com.hxrsh.sawari

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Writes the screen-reader view of each screen: the merged semantics tree in traversal order, one line per focus stop,
 * i.e. what TalkBack would land on and read. This is a stand-in for listening to TalkBack, NOT a recording of it.
 */
@RunWith(AndroidJUnit4::class)
class ReadingOrderTest {
    @get:Rule val rule = createComposeRule()

    private val swift = Car(1, "Maruti Swift", "Bengaluru", 180, "", 5, "Petrol")
    private val nexon = Car(2, "Tata Nexon EV", "Bengaluru", 320, "", 5, "Electric")
    private val slot = TimeSlot(Instant.now().plusSeconds(7200), Instant.now().plusSeconds(18000))
    private val booking = Booking(5, swift, Instant.now().plusSeconds(3600), Instant.now().plusSeconds(10800), 540, BookingStatus.CONFIRMED)

    private class Stop(val top: Float, val left: Float, val text: String)

    private fun focusStops(node: SemanticsNode, out: MutableList<Stop>) {
        val cfg = node.config
        val desc = cfg.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
        val text = cfg.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
        val clickable = cfg.getOrNull(SemanticsActions.OnClick) != null
        val heading = cfg.contains(SemanticsProperties.Heading)
        val isField = cfg.contains(SemanticsProperties.EditableText)
        val role = cfg.getOrNull(SemanticsProperties.Role)
        val state = cfg.getOrNull(SemanticsProperties.StateDescription)
        val live = cfg.getOrNull(SemanticsProperties.LiveRegion)
        val label = desc ?: text
        // A node is a focus stop for a screen reader if it speaks (text/description) or can be activated.
        if (label != null || clickable) {
            val b = node.boundsInRoot
            out += Stop(b.top, b.left, buildString {
                append(label ?: "(no label)")
                if (heading) append("  [heading]")
                if (isField) append("  [text field]")
                else if (clickable) append("  [button${if (role != null) ", role=$role" else ""}]")
                if (state != null) append("  [state: $state]")
                if (live != null) append("  [live region]")
                if (clickable && label == null) append("  <-- UNLABELLED")
            })
            // Children merged into this stop are not separate stops, but a child that is itself actionable stays one.
            node.children.filter { it.config.getOrNull(SemanticsActions.OnClick) != null }.forEach { focusStops(it, out) }
            return
        }
        node.children.forEach { focusStops(it, out) }
    }

    private fun dump(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent { SawariTheme { content() } }
        rule.waitForIdle()
        val raw = mutableListOf<Stop>()
        focusStops(rule.onRoot().fetchSemanticsNode(), raw)
        // TalkBack walks siblings top-to-bottom, then left-to-right.
        val stops = raw.sortedWith(compareBy({ (it.top / 8).toInt() }, { it.left })).map { it.text }
        val dir = File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!.path).apply { mkdirs() }
        File(dir, "tree_$name.txt").writeText(rule.onRoot().printToString(maxDepth = 12))
        File(dir, "reading_order_$name.txt").writeText(stops.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n"))
        assertTrue("$name has unlabelled actionable elements:\n${stops.joinToString("\n")}", stops.none { "UNLABELLED" in it })
        assertTrue("$name has no focus stops", stops.isNotEmpty())
    }

    @Test fun auth() = dump("1_auth") { AuthContent(AuthState(), {}, {}, {}, {}, {}) }

    @Test fun carList() = dump("2_car_list") {
        CarListContent(CarListState(content = UiState.Content(listOf(swift, nexon)), cities = listOf("Bengaluru", "Mumbai")), {}, {}, {}, {}, {})
    }

    @Test fun carDetail() = dump("3_car_detail") { CarDetailContent(CarDetailState(UiState.Content(swift), listOf(slot)), {}, {}) }

    @Test fun booking() = dump("4_booking") {
        BookingContent(BookingState(car = swift, startDate = LocalDate.now().plusDays(1), startTime = LocalTime.of(10, 0), busy = listOf(slot)), SnackbarHostState(), {}, {})
    }

    @Test fun myBookings() = dump("5_my_bookings") {
        MyBookingsContent(BookingsState(content = UiState.Content(listOf(booking)), now = Instant.now()), SnackbarHostState(), {}, {}, {}, showDebugMenu = false)
    }
}

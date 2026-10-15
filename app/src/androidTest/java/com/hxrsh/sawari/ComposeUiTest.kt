package com.hxrsh.sawari

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.dp
import com.hxrsh.sawari.domain.model.TimeSlot
import com.hxrsh.sawari.ui.booking.BookingContent
import com.hxrsh.sawari.ui.booking.BookingState
import com.hxrsh.sawari.ui.bookings.BookingsState
import com.hxrsh.sawari.ui.bookings.MyBookingsContent
import com.hxrsh.sawari.ui.detail.CarDetailContent
import com.hxrsh.sawari.ui.detail.CarDetailState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hxrsh.sawari.domain.model.Booking
import com.hxrsh.sawari.domain.model.BookingStatus
import com.hxrsh.sawari.domain.model.Car
import com.hxrsh.sawari.ui.auth.AuthContent
import com.hxrsh.sawari.ui.auth.AuthState
import com.hxrsh.sawari.ui.bookings.BookingCard
import com.hxrsh.sawari.ui.cars.CarListContent
import com.hxrsh.sawari.ui.cars.CarListState
import com.hxrsh.sawari.ui.common.UiState
import com.hxrsh.sawari.ui.theme.SawariTheme
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class ComposeUiTest {
    @get:Rule val rule = createComposeRule()

    private val swift = Car(1, "Maruti Swift", "Bengaluru", 180, "", 5, "Petrol")
    private val nexon = Car(2, "Tata Nexon EV", "Bengaluru", 320, "", 5, "Electric")

    private fun setList(state: CarListState, onClick: (Int) -> Unit = {}, onRefresh: () -> Unit = {}, onQuery: (String) -> Unit = {}) =
        rule.setContent {
            SawariTheme {
                CarListContent(state, onQuery, {}, {}, onRefresh, onClick)
            }
        }

    @Test fun carList_showsCars_andReportsClicks() {
        var clicked = -1
        setList(CarListState(content = UiState.Content(listOf(swift, nexon)), cities = listOf("Bengaluru")), onClick = { clicked = it })
        rule.onNodeWithText("Maruti Swift").assertIsDisplayed()
        rule.onNodeWithText("₹320/hr").assertIsDisplayed()
        rule.onNodeWithTag("car_card_2").performClick()
        assertEquals(2, clicked)
    }

    @Test fun carList_emptyAndOfflineStates() {
        setList(CarListState(content = UiState.Empty, staleNotice = "You're offline. Showing saved cars."))
        rule.onNodeWithTag("empty_state").assertIsDisplayed()
        rule.onNodeWithTag("offline_banner").assertIsDisplayed()
        rule.onNodeWithText("offline", substring = true, ignoreCase = true).assertIsDisplayed()
    }

    @Test fun carList_errorStateOffersRetry() {
        var retries = 0
        setList(CarListState(content = UiState.Error("Can't reach Sawari. Check your connection.")), onRefresh = { retries++ })
        rule.onNodeWithTag("error_state").assertIsDisplayed()
        rule.onNodeWithTag("retry_button").performClick()
        assertEquals(1, retries)
    }

    @Test fun carList_searchFieldForwardsTyping() {
        var query = ""
        setList(CarListState(content = UiState.Content(listOf(swift))), onQuery = { query = it })
        rule.onNodeWithTag("search_field").performTextInput("nex")
        assertEquals("nex", query)
    }

    @Test fun authForm_showsValidationErrorAndSwitchesMode() {
        var toggled = 0
        rule.setContent {
            SawariTheme {
                AuthContent(AuthState(error = "Enter a valid email address"), {}, {}, {}, {}, onToggleMode = { toggled++ })
            }
        }
        rule.onNodeWithTag("auth_error").assertTextContains("valid email", substring = true)
        rule.onNodeWithTag("name_field").assertDoesNotExist() // login mode has no name field
        rule.onNodeWithTag("toggle_mode").performClick()
        assertEquals(1, toggled)
    }

    @Test fun bookingCard_cancelButtonOnlyWhenCancellable() {
        val booking = Booking(5, swift, Instant.now().plusSeconds(3600), Instant.now().plusSeconds(10800), 540, BookingStatus.CONFIRMED)
        var cancelRequests = 0
        rule.setContent {
            SawariTheme {
                androidx.compose.foundation.layout.Column {
                    BookingCard(booking, cancellable = true, cancelling = false, onCancel = { cancelRequests++ })
                    BookingCard(booking.copy(id = 6, status = BookingStatus.CANCELLED), cancellable = false, cancelling = false, onCancel = {})
                }
            }
        }
        rule.onNodeWithTag("cancel_5").performClick()
        assertEquals(1, cancelRequests)
        rule.onNodeWithTag("cancel_6").assertDoesNotExist()
        rule.onNodeWithTag("booking_card_6").assertContentDescriptionContains("Cancelled", substring = true)
    }

    // ---------------------------------------------------------------------------------------------
    // Accessibility: automated Compose accessibility checks (contrast, touch target size, labels, ...)
    // plus explicit assertions. Runs the same checks on every screen.
    // ---------------------------------------------------------------------------------------------

    private val busy = listOf(TimeSlot(Instant.now().plusSeconds(7200), Instant.now().plusSeconds(18000)))
    private val upcoming = Booking(5, swift, Instant.now().plusSeconds(3600), Instant.now().plusSeconds(10800), 540, BookingStatus.CONFIRMED)

    /** Real touch-target check: Material widgets enlarge their touch area beyond their layout bounds, so measure that. */
    private fun assertTargets(vararg tags: String) {
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        tags.forEach { tag ->
            val b = rule.onNodeWithTag(tag).fetchSemanticsNode().touchBoundsInRoot
            assertTrue("$tag touch target is ${b.width / density}x${b.height / density}dp, need 48x48", b.width / density >= 47.5f && b.height / density >= 47.5f)
        }
    }

    private fun isHeading() = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    @OptIn(ExperimentalTestApi::class)
    @Test fun a11y_carList() {
        rule.enableAccessibilityChecks()
        setList(CarListState(content = UiState.Content(listOf(swift, nexon)), cities = listOf("Bengaluru", "Mumbai")))
        rule.onRoot().tryPerformAccessibilityChecks()
        // A card is ONE focusable element that reads name, place, seats, fuel and price.
        rule.onNodeWithTag("car_card_1").assertContentDescriptionContains("Maruti Swift", substring = true).assertContentDescriptionContains("180", substring = true)
        rule.onNodeWithTag("car_card_1").assertHasClickAction()
        assertTargets("car_card_1", "city_chip_Bengaluru", "fuel_chip_Petrol", "search_field")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun a11y_carDetail() {
        rule.enableAccessibilityChecks()
        rule.setContent { SawariTheme { CarDetailContent(CarDetailState(UiState.Content(swift), busy), {}, {}) } }
        rule.onRoot().tryPerformAccessibilityChecks()
        rule.onNodeWithContentDescription("Back").assertIsDisplayed()
        rule.onNode(isHeading() and hasText("Maruti Swift")).assertExists()
        assertTargets("back_button", "book_button")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun a11y_booking() {
        rule.enableAccessibilityChecks()
        val start = java.time.LocalDate.now().plusDays(1)
        val state = BookingState(car = swift, startDate = start, startTime = java.time.LocalTime.of(10, 0), busy = busy, error = "Taken", conflict = true)
        rule.setContent { SawariTheme { BookingContent(state, androidx.compose.material3.SnackbarHostState(), {}, {}) } }
        rule.onRoot().tryPerformAccessibilityChecks()
        rule.onNodeWithContentDescription("Fewer hours").assertIsDisplayed()
        rule.onNodeWithContentDescription("More hours").assertIsDisplayed()
        rule.onNode(isHeading() and hasText("When do you need it?")).assertExists()
        assertTargets("back_button", "pick_date", "pick_time", "duration_minus", "duration_plus", "confirm_button")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun a11y_myBookings() {
        rule.enableAccessibilityChecks()
        val state = BookingsState(content = UiState.Content(listOf(upcoming, upcoming.copy(id = 6, status = BookingStatus.CANCELLED))), now = Instant.now())
        rule.setContent {
            SawariTheme { MyBookingsContent(state, androidx.compose.material3.SnackbarHostState(), {}, {}, {}, showDebugMenu = false) }
        }
        rule.onRoot().tryPerformAccessibilityChecks()
        rule.onNodeWithTag("booking_card_5").assertContentDescriptionContains("Confirmed", substring = true)
        rule.onNodeWithTag("booking_card_6").assertContentDescriptionContains("Cancelled", substring = true)
        rule.onNode(isHeading() and hasText("My bookings")).assertExists()
        assertTargets("account_menu", "cancel_5")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun a11y_auth() {
        rule.enableAccessibilityChecks()
        rule.setContent { SawariTheme { AuthContent(AuthState(error = "Enter a valid email address"), {}, {}, {}, {}, {}) } }
        rule.onRoot().tryPerformAccessibilityChecks()
        assertTargets("email_field", "password_field", "submit_button", "toggle_mode")
    }
}

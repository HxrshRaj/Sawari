package com.hxrsh.sawari

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
import org.junit.Assert.assertEquals
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
        rule.onNodeWithTag("status_6").assertTextContains("Cancelled")
    }
}

package com.hxrsh.sawari

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hxrsh.sawari.domain.model.Car
import com.hxrsh.sawari.ui.booking.BookingContent
import com.hxrsh.sawari.ui.booking.BookingState
import com.hxrsh.sawari.ui.cars.CarListContent
import com.hxrsh.sawari.ui.cars.CarListState
import com.hxrsh.sawari.ui.common.LocalReduceMotion
import com.hxrsh.sawari.ui.common.UiState
import com.hxrsh.sawari.ui.common.rememberSystemReduceMotion
import com.hxrsh.sawari.ui.theme.SawariTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class AnimationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val swift = Car(1, "Maruti Swift", "Bengaluru", 180, "", 5, "Petrol")
    private val nexon = Car(2, "Tata Nexon EV", "Bengaluru", 320, "", 5, "Electric")

    private fun listState(content: UiState<List<Car>>) = CarListState(content = content)

    @Test fun skeletonReplacesTheSpinnerAndIsStillAnnouncedAsLoading() {
        rule.setContent { SawariTheme { CarListContent(listState(UiState.Loading), {}, {}, {}, {}, {}) } }
        rule.onNodeWithTag("loading_state").assertIsDisplayed()
        val desc = rule.onNodeWithTag("loading_state").fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
        assertEquals(listOf("Loading"), desc)
    }

    @Test fun skeletonRendersWithReducedMotionToo() {
        rule.setContent { SawariTheme { CompositionLocalProvider(LocalReduceMotion provides true) { CarListContent(listState(UiState.Loading), {}, {}, {}, {}, {}) } } }
        rule.onNodeWithTag("loading_state").assertIsDisplayed()
    }

    @Test fun carsAppearAfterTheEnterAnimationAndStayClickable() {
        var clicked = -1
        rule.setContent { SawariTheme { CarListContent(listState(UiState.Content(listOf(swift, nexon))), {}, {}, {}, {}, { clicked = it }) } }
        rule.onNodeWithTag("car_card_1").assertIsDisplayed()
        rule.onNodeWithTag("car_card_2").assertIsDisplayed()
        rule.onNodeWithTag("car_card_2").performClick()
        assertEquals(2, clicked)
    }

    @Test fun carsAreShownImmediatelyWithReducedMotion() {
        rule.mainClock.autoAdvance = false     // no animation frames at all: with reduced motion the cards must already be there
        rule.setContent { SawariTheme { CompositionLocalProvider(LocalReduceMotion provides true) { CarListContent(listState(UiState.Content(listOf(swift))), {}, {}, {}, {}, {}) } } }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("car_card_1").assertIsDisplayed()
    }

    private fun bookingState(hours: Int) = BookingState(car = swift, startDate = LocalDate.of(2030, 1, 1), startTime = LocalTime.NOON, durationHours = hours)

    @Test fun animatedTotalEndsOnTheNewPrice() {
        var hours by mutableStateOf(3)
        rule.setContent { SawariTheme { BookingContent(bookingState(hours), SnackbarHostState(), {}, {}) } }
        rule.onNodeWithTag("total_text").assertTextContains("540", substring = true)
        hours = 5
        rule.waitForIdle()
        rule.onNodeWithTag("total_text").assertTextContains("900", substring = true)   // 5 h x 180
        hours = 1
        rule.waitForIdle()
        rule.onNodeWithTag("total_text").assertTextContains("180", substring = true)
    }

    @Test fun totalChangesWithoutAnimationWhenMotionIsReduced() {
        var hours by mutableStateOf(3)
        rule.mainClock.autoAdvance = false
        rule.setContent { SawariTheme { CompositionLocalProvider(LocalReduceMotion provides true) { BookingContent(bookingState(hours), SnackbarHostState(), {}, {}) } } }
        rule.mainClock.advanceTimeByFrame()
        hours = 4
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        // Only one total node exists: nothing is mid-transition, there is no outgoing copy of the old price.
        rule.onNodeWithTag("total_text").assertTextContains("720", substring = true)
    }

    @Test fun systemAnimationsOffIsDetectedAndFollowedLive() {
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun setScale(v: String) = ui.executeShellCommand("settings put global animator_duration_scale $v").close()
        try {
            setScale("0")
            rule.setContent { SawariTheme { Text(if (rememberSystemReduceMotion()) "reduced" else "full") } }
            rule.waitUntil(5_000) { rule.onAllNodesWithTextCount("reduced") == 1 }
            setScale("1")
            rule.waitUntil(10_000) { rule.onAllNodesWithTextCount("full") == 1 }   // follows the setting without a restart
        } finally {
            setScale("1")
        }
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTextCount(text: String) =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
}

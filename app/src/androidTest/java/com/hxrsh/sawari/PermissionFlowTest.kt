package com.hxrsh.sawari

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.hxrsh.sawari.ui.booking.BookingContent
import com.hxrsh.sawari.ui.booking.BookingIntent
import com.hxrsh.sawari.ui.booking.BookingState
import com.hxrsh.sawari.ui.permissions.PermissionStatus
import com.hxrsh.sawari.ui.permissions.ReminderPermissionCard
import com.hxrsh.sawari.ui.permissions.rememberNotificationPermissionController
import com.hxrsh.sawari.ui.permissions.resetAskedPermissions
import com.hxrsh.sawari.ui.theme.SawariTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.time.LocalDate
import java.time.LocalTime

/**
 * The reminder permission flow. Methods run in name order on purpose: the real-dialog test (a_) needs the permission
 * to start un-granted, and the granted test (b_) grants it afterwards.
 * The real-dialog test needs Android 13+; it is skipped (not passed) on older devices.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PermissionFlowTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val device: UiDevice get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val permission = Manifest.permission.POST_NOTIFICATIONS

    private fun granted() = ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED

    private fun tapSystemButton(vararg resIds: String, textRegex: String) {
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            resIds.forEach { id -> device.findObject(By.res(id))?.let { it.click(); return } }
            device.findObject(By.text(java.util.regex.Pattern.compile(textRegex, java.util.regex.Pattern.CASE_INSENSITIVE)))?.let { it.click(); return }
            Thread.sleep(300)
        }
        error("system permission dialog button not found")
    }

    private fun denyOnSystemDialog() = tapSystemButton(
        "com.android.permissioncontroller:id/permission_deny_button",
        "com.google.android.permissioncontroller:id/permission_deny_button",
        textRegex = "don.?t allow|deny",
    )

    @Test fun a_realDialogs_denyOnce_thenDenyAgain_endsAtOpenSettings() {
        assumeTrue("needs Android 13+ (POST_NOTIFICATIONS is a runtime permission)", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        assumeTrue("permission already granted on this device; run this class on a fresh install", !granted())
        resetAskedPermissions(ctx)

        rule.setContent { SawariTheme { ReminderPermissionCard(rememberNotificationPermissionController()) } }

        // 1. explain first
        rule.onNodeWithTag("reminder_enable").assertIsDisplayed().performClick()
        rule.onNodeWithTag("rationale_continue").assertIsDisplayed().performClick()
        // 2. real system dialog -> user denies
        denyOnSystemDialog()
        rule.waitUntil(10_000) { rule.onAllNodesWithTagCount("reminder_ask_again") == 1 }
        // 3. denied once: we may ask again
        rule.onNodeWithTag("reminder_ask_again").performClick()
        rule.onNodeWithTag("rationale_continue").performClick()
        denyOnSystemDialog()
        // 4. denied twice: the system will not show the dialog again, so the only way out is settings
        rule.waitUntil(10_000) { rule.onAllNodesWithTagCount("reminder_open_settings") == 1 }
        rule.onNodeWithTag("reminder_open_settings").assertIsDisplayed()
        assertTrue(!granted())
        // 5. permission really denied: showing the reminder must be a quiet no-op, not a crash
        val manager = ctx.getSystemService(android.app.NotificationManager::class.java)
        assertEquals(false, com.hxrsh.sawari.notifications.ReminderNotifications.show(ctx, 8, "Maruti Swift"))
        Thread.sleep(1_500)
        assertTrue(manager.activeNotifications.none { it.id == 10_008 })
    }

    @Test fun b_grantedState_whenPermissionIsGranted() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(ctx.packageName, permission)
        rule.setContent { SawariTheme { ReminderPermissionCard(rememberNotificationPermissionController()) } }
        rule.onNodeWithTag("reminder_granted").assertIsDisplayed()
    }

    // ---- stateless card states: no system dialog involved, run everywhere ----

    @Test fun c_everyStateRendersItsOwnControls() {
        val expected = mapOf(
            PermissionStatus.Granted to "reminder_granted",
            PermissionStatus.NotAsked to "reminder_enable",
            PermissionStatus.DeniedCanAskAgain to "reminder_ask_again",
            PermissionStatus.DeniedPermanently to "reminder_open_settings",
        )
        var current by mutableStateOf(PermissionStatus.Granted)
        rule.setContent { SawariTheme { ReminderPermissionCard(current, {}, {}) } }
        expected.forEach { (status, tag) ->
            rule.runOnIdle { current = status }
            rule.onNodeWithTag(tag).assertIsDisplayed()
        }
    }

    @Test fun d_openSettingsButtonCallsBack() {
        var opened = 0
        rule.setContent { SawariTheme { ReminderPermissionCard(PermissionStatus.DeniedPermanently, {}, { opened++ }) } }
        rule.onNodeWithTag("reminder_open_settings").performClick()
        assertEquals(1, opened)
    }

    @Test fun e_requestOnlyHappensAfterTheRationaleIsConfirmed() {
        var requested = 0
        rule.setContent { SawariTheme { ReminderPermissionCard(PermissionStatus.NotAsked, { requested++ }, {}) } }
        rule.onNodeWithTag("reminder_enable").performClick()
        assertEquals("must not ask before the user read the explanation", 0, requested)
        rule.onNodeWithTag("rationale_dismiss").performClick()
        assertEquals(0, requested)
        rule.onNodeWithTag("reminder_enable").performClick()
        rule.onNodeWithTag("rationale_continue").performClick()
        assertEquals(1, requested)
    }

    @Test fun f_bookingStillWorksWithNotificationsDenied() {
        val intents = mutableListOf<BookingIntent>()
        val state = BookingState(car = com.hxrsh.sawari.domain.model.Car(1, "Swift", "Bengaluru", 180, "", 5, "Petrol"), startDate = LocalDate.now(), startTime = LocalTime.NOON)
        rule.setContent {
            SawariTheme {
                BookingContent(state, SnackbarHostState(), {}, { intents += it }, extras = { ReminderPermissionCard(PermissionStatus.DeniedPermanently, {}, {}) })
            }
        }
        rule.onNodeWithTag("reminder_open_settings").assertIsDisplayed()
        rule.onNodeWithTag("confirm_button").assertIsEnabled().performClick()
        assertEquals(listOf<BookingIntent>(BookingIntent.Submit), intents)
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTagCount(tag: String): Int =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size
}

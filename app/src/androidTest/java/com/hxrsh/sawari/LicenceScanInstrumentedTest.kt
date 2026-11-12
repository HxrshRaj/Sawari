package com.hxrsh.sawari

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.hxrsh.sawari.ui.licence.AndroidLicencePhotoStore
import com.hxrsh.sawari.ui.licence.LicenceScanScreen
import com.hxrsh.sawari.ui.licence.LicenceScanViewModel
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
import java.io.File
import java.util.regex.Pattern

/**
 * Camera permission paths and a real capture on the emulator's virtual back camera.
 * Name-ordered: a_ needs CAMERA un-granted (fresh install), c_ grants it afterwards.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LicenceScanInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val device: UiDevice get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val camera = Manifest.permission.CAMERA
    private fun granted() = ContextCompat.checkSelfPermission(ctx, camera) == PackageManager.PERMISSION_GRANTED
    private fun photoDir() = File(ctx.filesDir, "licence")

    private var doneWith: String? = null
    private var cancelled = 0

    private fun show() {
        rule.setContent {
            SawariTheme {
                val store = AndroidLicencePhotoStore(ctx)
                val vm: LicenceScanViewModel = viewModel(factory = viewModelFactory { initializer { LicenceScanViewModel(store) } })
                LicenceScanScreen(onDone = { doneWith = it }, onCancel = { cancelled++ }, viewModel = vm)
            }
        }
    }

    private fun count(tag: String) = rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().size
    private fun awaitTag(tag: String, ms: Long = 20_000) {
        try {
            rule.waitUntil(ms) { count(tag) > 0 || count("scan_error") > 0 }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("timed out waiting for $tag; busy=${count("scan_busy")} preview=${count("camera_preview")} capture=${count("capture_button")}", e)
        }
        if (count(tag) == 0 && count("scan_error") > 0) {
            val text = rule.onNodeWithTag("scan_error").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text]
            throw AssertionError("scanner showed an error while waiting for $tag: $text")
        }
    }

    /** The shutter enables itself once the camera is bound. */
    private fun awaitShutterReady(ms: Long = 30_000) {
        try {
            rule.waitUntil(ms) { rule.onAllNodes(hasTestTag("capture_button") and androidx.compose.ui.test.isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("camera never became ready; error shown: ${if (count("scan_error") > 0) "yes" else "no"}", e)
        }
    }

    private fun denyOnSystemDialog() {
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            listOf("com.android.permissioncontroller:id/permission_deny_button", "com.google.android.permissioncontroller:id/permission_deny_button")
                .forEach { id -> device.findObject(By.res(id))?.let { it.click(); return } }
            device.findObject(By.text(Pattern.compile("don.?t allow|deny", Pattern.CASE_INSENSITIVE)))?.let { it.click(); return }
            Thread.sleep(300)
        }
        error("system permission dialog button not found")
    }

    @Test fun a_denyTwice_endsAtOpenSettings_withPhotoPickerFallback_andCancelWorks() {
        assumeTrue("CAMERA already granted on this device; run this class on a fresh install", !granted())
        resetAskedPermissions(ctx)
        show()

        rule.onNodeWithTag("camera_enable").assertIsDisplayed().performClick()      // explain first
        rule.onNodeWithTag("camera_rationale_continue").performClick()
        denyOnSystemDialog()
        awaitTag("camera_ask_again")                                                // denied once: may ask again
        rule.onNodeWithTag("pick_photo").assertIsDisplayed()                        // fallback is available already
        rule.onNodeWithTag("camera_ask_again").performClick()
        rule.onNodeWithTag("camera_rationale_continue").performClick()
        denyOnSystemDialog()
        awaitTag("camera_open_settings")                                            // denied twice: only settings can help
        rule.onNodeWithTag("camera_open_settings").assertIsDisplayed()
        rule.onNodeWithTag("pick_photo").assertIsDisplayed()                        // the flow is still usable without the camera
        assertTrue(!granted())

        rule.onNodeWithTag("scan_cancel").performClick()
        rule.waitUntil(5_000) { cancelled == 1 }
    }

    @Test fun b_cancelBeforeAnythingIsASilentNoOp() {
        show()
        rule.onNodeWithTag("scan_cancel").performClick()
        rule.waitUntil(5_000) { cancelled == 1 }
        assertEquals(null, doneWith)
    }

    @Test fun c_realCapture_retake_thenUsePhoto() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(ctx.packageName, camera)
        photoDir().deleteRecursively()
        show()

        awaitShutterReady()
        rule.onNodeWithTag("capture_button").performClick()
        awaitTag("review_image", 30_000)
        val first = photoDir().listFiles().orEmpty().filter { it.name.endsWith(".jpg") }
        assertEquals("exactly one photo on disk after the first capture", 1, first.size)
        assertTrue("photo file is not empty", first.single().length() > 0)

        // Retake: the rejected photo must be deleted and the live preview is back.
        rule.onNodeWithTag("retake_button").performClick()
        awaitShutterReady()
        rule.waitUntil(5_000) { photoDir().listFiles().orEmpty().none { it.name.endsWith(".jpg") } }

        rule.onNodeWithTag("capture_button").performClick()
        awaitTag("review_image", 30_000)
        rule.onNodeWithTag("use_photo_button").performClick()
        rule.waitUntil(5_000) { doneWith != null }
        val kept = File(doneWith!!)
        assertTrue(kept.exists() && kept.length() > 0)
        assertEquals("only the confirmed photo remains", listOf(kept.name), photoDir().listFiles().orEmpty().map { it.name })
        assertTrue("stored in app-private storage", kept.absolutePath.startsWith(ctx.filesDir.absolutePath))
        kept.delete()
    }
}

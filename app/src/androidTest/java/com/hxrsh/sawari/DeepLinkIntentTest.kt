package com.hxrsh.sawari

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Same intents as `adb shell am start -a android.intent.action.VIEW -d <uri>`, fired through the instrumentation.
 * Covers: the manifest really routes these URIs to MainActivity, and the app survives every kind of link
 * (valid, unknown car, malformed) on a cold start without crashing.
 * "App already open" (onNewIntent) is NOT covered here: ActivityScenario cannot reliably close a singleTask activity,
 * so that case is checked with real adb on the emulator instead (see README).
 * The end-to-end "which screen did it land on" checks are done with real adb on the emulator (see README).
 */
@RunWith(AndroidJUnit4::class)
class DeepLinkIntentTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    private fun viewIntent(uri: String) =
        Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(ctx.packageName).addCategory(Intent.CATEGORY_BROWSABLE)

    @Test fun manifestResolvesCustomSchemeLinksToMainActivity() {
        listOf("sawari://car/12", "sawari://bookings").forEach { uri ->
            val info = ctx.packageManager.resolveActivity(viewIntent(uri), 0)
            assertNotNull("no activity for $uri", info)
            assertEquals(MainActivity::class.java.name, info!!.activityInfo.name)
        }
    }

    @Test fun manifestResolvesHttpsLinksToMainActivity() {
        listOf(
            "https://sawari-api-1krd.onrender.com/app/car/12",
            "https://sawari-api-1krd.onrender.com/app/bookings",
        ).forEach { uri ->
            val info = ctx.packageManager.resolveActivity(viewIntent(uri), 0)
            assertNotNull("no activity for $uri", info)
            assertEquals(MainActivity::class.java.name, info!!.activityInfo.name)
        }
    }

    @Test fun manifestDoesNotClaimForeignLinks() {
        listOf("sawari://settings", "https://example.com/app/car/1", "https://sawari-api-1krd.onrender.com/docs").forEach { uri ->
            assertNull("unexpected handler for $uri", ctx.packageManager.resolveActivity(viewIntent(uri), 0))
        }
    }

    @Test fun coldStartWithEachKindOfLinkDoesNotCrash() {
        listOf("sawari://car/12", "sawari://car/999999", "sawari://car/abc", "sawari://bookings", "sawari://bookings/zzz").forEach { uri ->
            ActivityScenario.launch<MainActivity>(viewIntent(uri)).use { scenario ->
                assertEquals(Lifecycle.State.RESUMED, scenario.state)
            }
        }
    }
}

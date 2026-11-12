package com.hxrsh.sawari

import com.hxrsh.sawari.ui.permissions.PermissionStatus
import com.hxrsh.sawari.ui.permissions.resolvePermissionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionStatusTest {
    private val table = listOf(
        // granted, askedBefore, shouldShowRationale -> expected
        Triple(Triple(true, false, false), null, PermissionStatus.Granted),
        Triple(Triple(true, true, false), null, PermissionStatus.Granted),
        Triple(Triple(false, false, false), null, PermissionStatus.NotAsked),
        Triple(Triple(false, true, true), null, PermissionStatus.DeniedCanAskAgain),
        Triple(Triple(false, true, false), null, PermissionStatus.DeniedPermanently),
        // rationale flag alone (never asked by us) must not skip the explain-first step
        Triple(Triple(false, false, true), null, PermissionStatus.NotAsked),
    )

    @Test fun `every combination resolves to the right state`() {
        table.forEach { (input, _, expected) ->
            val (granted, asked, rationale) = input
            assertEquals("granted=$granted asked=$asked rationale=$rationale", expected, resolvePermissionStatus(granted, asked, rationale))
        }
    }
}

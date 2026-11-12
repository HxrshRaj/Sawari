package com.hxrsh.sawari.ui.permissions

/** Where a runtime permission stands. Drives the explain -> ask -> denied -> open-settings UI. */
enum class PermissionStatus {
    Granted,

    /** Never asked yet: explain why first, then ask. */
    NotAsked,

    /** Denied once; the system will still show the dialog if we ask again. */
    DeniedCanAskAgain,

    /** "Don't ask again" (or denied twice): the dialog will never show, only app settings can fix it. */
    DeniedPermanently,
}

/**
 * Android cannot tell "never asked" from "permanently denied" (shouldShowRequestPermissionRationale is false for both),
 * so we remember ourselves whether we have asked before.
 */
fun resolvePermissionStatus(granted: Boolean, askedBefore: Boolean, shouldShowRationale: Boolean): PermissionStatus = when {
    granted -> PermissionStatus.Granted
    !askedBefore -> PermissionStatus.NotAsked
    shouldShowRationale -> PermissionStatus.DeniedCanAskAgain
    else -> PermissionStatus.DeniedPermanently
}

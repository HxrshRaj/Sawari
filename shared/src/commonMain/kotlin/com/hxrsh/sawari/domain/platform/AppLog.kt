package com.hxrsh.sawari.domain.platform

/**
 * Debug logging: the one genuinely platform-specific piece of the shared module.
 *
 * Why expect/actual and not common code: Android logs through `android.util.Log` (visible in logcat, filtered by tag),
 * iOS through `NSLog` (visible in the Xcode / Console log). Neither API exists in the other platform, and `println`
 * reaches neither of those channels reliably. The date/time part, which also differs per platform, does NOT need
 * expect/actual because kotlinx-datetime already provides it in common code.
 */
expect object AppLog {
    fun d(tag: String, message: String)
}

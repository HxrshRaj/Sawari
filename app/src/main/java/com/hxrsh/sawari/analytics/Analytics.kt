package com.hxrsh.sawari.analytics

/** Thin seam over Firebase so the app builds and runs without google-services.json (the "lite" flavour). */
interface Analytics {
    fun logEvent(name: String, params: Map<String, String> = emptyMap())
    fun setUser(id: String?)
    fun recordNonFatal(throwable: Throwable)
    /** Debug-menu only: crashes the process so the report shows up in Crashlytics. */
    fun crashForTest()
}

object Events {
    const val SEARCH = "search"
    const val BOOKING_CREATED = "booking_created"
    const val BOOKING_CONFLICT = "booking_conflict"
    const val BOOKING_CANCELLED = "booking_cancelled"
}

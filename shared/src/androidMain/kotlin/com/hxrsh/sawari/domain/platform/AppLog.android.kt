package com.hxrsh.sawari.domain.platform

import android.util.Log

actual object AppLog {
    actual fun d(tag: String, message: String) {
        Log.d(tag, message)
    }
}

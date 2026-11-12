package com.hxrsh.sawari.domain.platform

import platform.Foundation.NSLog

actual object AppLog {
    actual fun d(tag: String, message: String) {
        // NSLog takes a printf-style format. Passing Kotlin strings as %@ varargs crashed the iOS test process, so the
        // whole line is the format string instead, with any '%' in it escaped.
        NSLog("[$tag] $message".replace("%", "%%"))
    }
}

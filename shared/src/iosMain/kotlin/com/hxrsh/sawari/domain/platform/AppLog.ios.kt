package com.hxrsh.sawari.domain.platform

import platform.Foundation.NSLog

actual object AppLog {
    actual fun d(tag: String, message: String) {
        NSLog("%@: %@", tag, message)
    }
}

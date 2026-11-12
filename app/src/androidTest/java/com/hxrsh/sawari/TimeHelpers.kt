package com.hxrsh.sawari

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.seconds

/** An instant [seconds] from now, as the shared domain models want it. */
fun soon(seconds: Long): Instant = Clock.System.now() + seconds.seconds

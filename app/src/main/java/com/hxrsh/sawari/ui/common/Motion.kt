package com.hxrsh.sawari.ui.common

import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext

/**
 * True when the user turned animations off (Developer options "Animator duration scale: off", or the
 * "Remove animations" accessibility setting, which sets the same scale to 0). Every animation in the app checks this.
 */
val LocalReduceMotion = compositionLocalOf { false }

/** Pure rule, unit-tested: a duration scale of 0 means "no animations". */
fun isReduceMotionRequested(animatorDurationScale: Float): Boolean = animatorDurationScale == 0f

/** Reads the system animator scale and follows live changes while the screen is shown. */
@Composable
fun rememberSystemReduceMotion(): Boolean {
    val context = LocalContext.current
    val resolver = context.contentResolver
    fun read() = isReduceMotionRequested(Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
    var reduce by remember { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) { reduce = read() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduce
}

/**
 * Loading placeholder: a soft highlight sweeping across the block. With reduced motion it is a plain static block.
 * The sweep position is read in the draw phase only, so it never triggers recomposition.
 */
@Composable
fun Modifier.shimmer(): Modifier {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    if (LocalReduceMotion.current) return background(base)
    val highlight = MaterialTheme.colorScheme.surfaceContainerLowest
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1_200, easing = LinearEasing)),
        label = "shimmer-progress",
    )
    return drawBehind {
        val w = size.width
        val start = w * (progress * 3f - 1f)
        drawRect(Brush.linearGradient(listOf(base, highlight, base), start = Offset(start, 0f), end = Offset(start + w, 0f)))
    }
}

package com.hxrsh.sawari.ui.cars

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hxrsh.sawari.ui.common.LocalReduceMotion
import com.hxrsh.sawari.ui.common.shimmer
import kotlinx.coroutines.delay

/**
 * Loading state for the car list: card-shaped placeholders instead of a spinner, so the layout does not jump when
 * the real cars arrive. Announced to screen readers as one "Loading" element, same as the spinner it replaces.
 */
@Composable
fun CarListSkeleton(modifier: Modifier = Modifier, count: Int = 4) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("loading_state").semantics { contentDescription = "Loading" },
        userScrollEnabled = false,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(count) { SkeletonCard() }
    }
}

@Composable
private fun SkeletonCard() {
    Card(Modifier.fillMaxWidth()) {
        Spacer(Modifier.fillMaxWidth().height(180.dp).shimmer())
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.fillMaxWidth(0.6f).height(18.dp).clip(RoundedCornerShape(4.dp)).shimmer())
            Spacer(Modifier.width(120.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).shimmer())
        }
    }
}

/**
 * Fade + slide-up when a card first appears. [play] is false for cards that were already shown (scrolling back, refresh),
 * so only genuinely new cards animate, and never with reduced motion.
 *
 * It is a wrapper composable (not a Modifier) on purpose: the animation state is read inside this scope only, and the
 * progress is applied in the graphics layer (draw phase). The card inside is passed as a stable lambda, so it is
 * skipped, not recomposed, while the animation runs: the recomposition count of the list does not change.
 */
@Composable
fun EnterOnFirstShow(play: Boolean, index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (!play || LocalReduceMotion.current) {
        Box(modifier) { content() }
        return
    }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index.coerceAtMost(6) * 40).toLong()) // a short stagger for the first screenful only
        visible = true
    }
    val progress by animateFloatAsState(if (visible) 1f else 0f, tween(durationMillis = 280), label = "card-enter")
    Box(
        modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 24.dp.toPx()
        },
    ) { content() }
}

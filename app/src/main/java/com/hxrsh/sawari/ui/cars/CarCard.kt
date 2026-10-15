package com.hxrsh.sawari.ui.cars

import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import android.os.Trace
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import com.hxrsh.sawari.domain.model.Car
import com.hxrsh.sawari.ui.common.formatPrice

/** Decode at (at most) the size it is shown, not at the photo's native resolution. */
@Composable
fun rememberCarImageRequest(url: String, widthPx: Int = 540, heightPx: Int = 340): ImageRequest {
    val context = LocalContext.current
    return remember(url, widthPx, heightPx) {
        ImageRequest.Builder(context).data(url).size(Size(widthPx, heightPx)).precision(Precision.INEXACT).build()
    }
}

@Composable
fun CarCard(car: Car, onClick: (Int) -> Unit, modifier: Modifier = Modifier) {
    // Trace section so Macrobenchmark can count how many times a card body actually runs (see README).
    Trace.beginSection("CarCard")
    // One focus stop per card; TalkBack reads everything once instead of five separate fragments.
    val summary = "${car.name}, ${car.city}, ${car.seats} seats, ${car.fuel}, ${formatPrice(car.pricePerHour)} per hour"
    Card(
        onClick = { onClick(car.id) },
        modifier = modifier.fillMaxWidth().testTag("car_card_${car.id}").semantics(mergeDescendants = true) { contentDescription = summary },
    ) {
        AsyncImage(
            model = rememberCarImageRequest(car.imageUrl),
            contentDescription = null, // decorative here: the card summary already names the car
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(180.dp), // the Card already clips its children; a second clip forces an offscreen layer per item
        )
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(car.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Fact(Icons.Default.LocationOn, car.city)
                    Fact(Icons.Default.EventSeat, "${car.seats}")
                    Fact(Icons.Default.LocalGasStation, car.fuel)
                }
            }
            Text(
                formatPrice(car.pricePerHour) + "/hr",
                style = MaterialTheme.typography.titleMedium,
                softWrap = false,
                modifier = Modifier.padding(start = 8.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
    Trace.endSection()
}

@Composable
private fun Fact(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(" $text", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

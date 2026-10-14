package com.hxrsh.sawari.ui.cars

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.hxrsh.sawari.domain.model.Car
import com.hxrsh.sawari.ui.common.formatPrice

/** Decode at (at most) the size it is shown, not at the photo's native resolution. */
@Composable
fun rememberCarImageRequest(url: String, widthPx: Int = 640, heightPx: Int = 400): ImageRequest {
    val context = LocalContext.current
    return remember(url, widthPx, heightPx) {
        ImageRequest.Builder(context).data(url).size(Size(widthPx, heightPx)).build()
    }
}

@Composable
fun CarCard(car: Car, onClick: (Int) -> Unit, modifier: Modifier = Modifier) {
    // Trace section so Macrobenchmark can count how many times a card body actually runs (see README).
    Trace.beginSection("CarCard")
    Card(onClick = { onClick(car.id) }, modifier = modifier.fillMaxWidth().testTag("car_card_${car.id}")) {
        AsyncImage(
            model = rememberCarImageRequest(car.imageUrl),
            contentDescription = car.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(180.dp).clip(MaterialTheme.shapes.medium),
        )
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(car.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Fact(Icons.Default.LocationOn, car.city)
                    Fact(Icons.Default.EventSeat, "${car.seats}")
                    Fact(Icons.Default.LocalGasStation, car.fuel)
                }
            }
            Text(
                formatPrice(car.pricePerHour) + "/hr",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
    Trace.endSection()
}

@Composable
private fun Fact(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
        Text(" $text", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

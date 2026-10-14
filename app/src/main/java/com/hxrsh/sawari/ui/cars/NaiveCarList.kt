package com.hxrsh.sawari.ui.cars

import android.os.Trace
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.hxrsh.sawari.domain.model.Car

/**
 * DELIBERATELY NAIVE. Exists only so the README can show a measured before/after against [CarList].
 * Selected with the `naive_list` intent extra; the real UI never uses it.
 *
 * What is wrong with it, on purpose:
 *  - a plain scrolling Column: every card is composed and every image requested up front (no lazy layout);
 *  - no keys, so nothing can be matched to its previous composition;
 *  - an unstable model (vars + MutableList) is rebuilt on every composition, so no card is ever skippable;
 *  - a fresh lambda and a fresh ImageRequest per composition, requesting the original bitmap size with
 *    software bitmaps and a crossfade;
 *  - sorting done in the composable body.
 */
class NaiveCar(
    var name: String,
    var city: String,
    var pricePerHour: Int,
    var imageUrl: String,
    var seats: Int,
    var fuel: String,
    val tags: MutableList<String>,
)

@Composable
fun NaiveCarList(cars: List<Car>, onCarClick: (Int) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("car_list"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        cars.sortedBy { it.name }.forEach { car ->
            val naive = NaiveCar(car.name, car.city, car.pricePerHour, car.imageUrl, car.seats, car.fuel, mutableListOf(car.fuel, car.city))
            NaiveCarCard(naive, onClick = { onCarClick(car.id) }, id = car.id)
        }
    }
}

@Composable
private fun NaiveCarCard(car: NaiveCar, onClick: () -> Unit, id: Int) {
    Trace.beginSection("CarCard")
    val context = LocalContext.current
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("car_card_$id")) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(car.imageUrl).size(Size.ORIGINAL).allowHardware(false).crossfade(true).build(),
            contentDescription = car.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(180.dp),
        )
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(car.name, style = MaterialTheme.typography.titleMedium)
                Text("${car.tags.joinToString(" · ")} · ${car.seats} seats", style = MaterialTheme.typography.bodySmall)
            }
            Text("₹${car.pricePerHour}/hr", style = MaterialTheme.typography.titleMedium)
        }
    }
    Trace.endSection()
}

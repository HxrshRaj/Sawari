package com.hxrsh.sawari.ui.detail

import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.hxrsh.sawari.domain.AppResult
import com.hxrsh.sawari.domain.model.Car
import com.hxrsh.sawari.domain.model.TimeSlot
import com.hxrsh.sawari.domain.usecase.GetBusySlotsUseCase
import com.hxrsh.sawari.domain.usecase.ObserveCarUseCase
import com.hxrsh.sawari.ui.cars.rememberCarImageRequest
import com.hxrsh.sawari.ui.common.EmptyState
import com.hxrsh.sawari.ui.common.InfoChip
import com.hxrsh.sawari.ui.common.LoadingState
import com.hxrsh.sawari.ui.common.UiState
import com.hxrsh.sawari.ui.common.formatDateTime
import com.hxrsh.sawari.ui.common.formatPrice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CarDetailState(val car: UiState<Car> = UiState.Loading, val busy: List<TimeSlot> = emptyList())

@HiltViewModel
class CarDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    observeCar: ObserveCarUseCase,
    private val getBusySlots: GetBusySlotsUseCase,
) : ViewModel() {
    private val carId: Int = checkNotNull(savedState["carId"])
    private val busy = MutableStateFlow<List<TimeSlot>>(emptyList())

    val state: StateFlow<CarDetailState> = combine(observeCar(carId), busy) { car, busy ->
        CarDetailState(if (car == null) UiState.Empty else UiState.Content(car), busy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CarDetailState())

    init {
        viewModelScope.launch {
            // Best effort: offline we simply don't show the "already booked" list.
            (getBusySlots(carId) as? AppResult.Success)?.let { busy.value = it.data }
        }
    }
}

@Composable
fun CarDetailScreen(onBack: () -> Unit, onBook: (Int) -> Unit, viewModel: CarDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CarDetailContent(state, onBack, onBook)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarDetailContent(state: CarDetailState, onBack: () -> Unit, onBook: (Int) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Car details") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (val car = state.car) {
                UiState.Loading -> LoadingState()
                UiState.Empty, is UiState.Error -> EmptyState("Car not found", "It may have been removed.")
                is UiState.Content -> DetailBody(car.data, state.busy, onBook)
            }
        }
    }
}

@Composable
private fun DetailBody(car: Car, busy: List<TimeSlot>, onBook: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AsyncImage(
            model = rememberCarImageRequest(car.imageUrl, widthPx = 1080, heightPx = 640),
            contentDescription = null, // decorative: the heading below already names the car
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(240.dp),
        )
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(car.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("detail_name").semantics { heading() })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoChip(car.city)
                InfoChip("${car.seats} seats")
                InfoChip(car.fuel)
            }
            Text(
                "${formatPrice(car.pricePerHour)} per hour",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            if (busy.isNotEmpty()) {
                Text("Already booked", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                busy.take(5).forEach {
                    Text(
                        "${it.start.formatDateTime()} → ${it.end.formatDateTime()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onBook(car.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("book_button")) {
                Text("Book this car")
            }
        }
    }
}

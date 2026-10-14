package com.hxrsh.sawari.ui.cars

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hxrsh.sawari.analytics.Analytics
import com.hxrsh.sawari.analytics.Events
import com.hxrsh.sawari.domain.AppError
import com.hxrsh.sawari.domain.errorOrNull
import com.hxrsh.sawari.domain.model.Car
import com.hxrsh.sawari.domain.model.CarFilter
import com.hxrsh.sawari.domain.userMessage
import com.hxrsh.sawari.domain.usecase.ObserveCarsUseCase
import com.hxrsh.sawari.domain.usecase.ObserveCitiesUseCase
import com.hxrsh.sawari.domain.usecase.RefreshCarsUseCase
import com.hxrsh.sawari.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class CarListState(
    val filter: CarFilter = CarFilter(),
    val cities: List<String> = emptyList(),
    val content: UiState<List<Car>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    /** Non-null while cached data is on screen but the last refresh failed. */
    val staleNotice: String? = null,
)

@Immutable
private data class RefreshState(val inFlight: Boolean = false, val completedOnce: Boolean = false, val error: AppError? = null)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class CarListViewModel @Inject constructor(
    observeCars: ObserveCarsUseCase,
    observeCities: ObserveCitiesUseCase,
    private val refreshCars: RefreshCarsUseCase,
    analytics: Analytics,
) : ViewModel() {

    private val filter = MutableStateFlow(CarFilter())
    private val refresh = MutableStateFlow(RefreshState())

    val state: StateFlow<CarListState> = combine(
        filter.flatMapLatest { observeCars(it) },
        observeCities(),
        filter,
        refresh,
    ) { cars, cities, filter, refresh ->
        val cacheEmpty = cities.isEmpty()
        val content: UiState<List<Car>> = when {
            cars.isNotEmpty() -> UiState.Content(cars)
            cacheEmpty && !refresh.completedOnce -> UiState.Loading
            cacheEmpty && refresh.error != null -> UiState.Error(refresh.error.userMessage())
            else -> UiState.Empty
        }
        CarListState(
            filter = filter,
            cities = cities,
            content = content,
            isRefreshing = refresh.inFlight,
            staleNotice = if (!cacheEmpty) refresh.error?.staleNotice() else null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CarListState())

    init {
        refresh()
        this.filter
            .map { it.query.trim() }
            .distinctUntilChanged()
            .debounce(700)
            .filter { it.length >= 2 }
            .onEach { analytics.logEvent(Events.SEARCH, mapOf("term" to it)) }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(query: String) = filter.update { it.copy(query = query) }
    fun onCityChange(city: String?) = filter.update { it.copy(city = city) }
    fun onFuelChange(fuel: String?) = filter.update { it.copy(fuel = fuel) }

    fun refresh() {
        if (refresh.value.inFlight) return
        refresh.update { it.copy(inFlight = true) }
        viewModelScope.launch {
            val result = refreshCars()
            refresh.value = RefreshState(inFlight = false, completedOnce = true, error = result.errorOrNull())
        }
    }

    private fun AppError.staleNotice(): String = when (this) {
        is AppError.Network -> "You're offline. Showing saved cars."
        else -> "Couldn't refresh. Showing saved cars."
    }
}

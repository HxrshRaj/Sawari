package com.hxrsh.sawari.ui.booking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hxrsh.sawari.analytics.Analytics
import com.hxrsh.sawari.analytics.Events
import com.hxrsh.sawari.domain.AppError
import com.hxrsh.sawari.domain.AppResult
import com.hxrsh.sawari.domain.userMessage
import com.hxrsh.sawari.domain.usecase.CreateBookingUseCase
import com.hxrsh.sawari.domain.usecase.GetBusySlotsUseCase
import com.hxrsh.sawari.domain.usecase.ObserveCarUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class BookingViewModel @Inject constructor(
    savedState: SavedStateHandle,
    observeCar: ObserveCarUseCase,
    private val getBusySlots: GetBusySlotsUseCase,
    private val createBooking: CreateBookingUseCase,
    private val analytics: Analytics,
    private val clock: Clock,
) : ViewModel() {

    private val carId: Int = checkNotNull(savedState["carId"])

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<BookingState> = _state.asStateFlow()

    private val _effects = Channel<BookingEffect>(Channel.BUFFERED)
    val effects: Flow<BookingEffect> = _effects.receiveAsFlow()

    init {
        observeCar(carId).onEach { car -> _state.update { it.copy(car = car) } }.launchIn(viewModelScope)
        loadBusySlots()
    }

    fun onIntent(intent: BookingIntent) {
        when (intent) {
            is BookingIntent.PickDate -> _state.update { it.copy(startDate = intent.date, error = null, conflict = false) }
            is BookingIntent.PickTime -> _state.update { it.copy(startTime = intent.time, error = null, conflict = false) }
            is BookingIntent.ChangeDuration -> _state.update {
                it.copy(
                    durationHours = (it.durationHours + intent.delta).coerceIn(BookingState.MIN_HOURS, BookingState.MAX_HOURS),
                    error = null,
                    conflict = false,
                )
            }
            BookingIntent.DismissError -> _state.update { it.copy(error = null, conflict = false) }
            BookingIntent.Submit -> submit()
        }
    }

    private fun submit() {
        val s = _state.value
        if (!s.canSubmit) return
        _state.update { it.copy(isSubmitting = true, error = null, conflict = false) }
        viewModelScope.launch {
            val start = s.start.atZone(s.zone).toInstant()
            val end = s.end.atZone(s.zone).toInstant()
            when (val result = createBooking(carId, start, end, clock.instant())) {
                is AppResult.Success -> {
                    analytics.logEvent(Events.BOOKING_CREATED, mapOf("car_id" to carId.toString(), "hours" to s.durationHours.toString()))
                    _state.update { it.copy(isSubmitting = false) }
                    _effects.send(BookingEffect.Booked(result.data))
                }
                is AppResult.Failure -> {
                    val isConflict = result.error is AppError.Conflict
                    if (isConflict) {
                        analytics.logEvent(Events.BOOKING_CONFLICT, mapOf("car_id" to carId.toString()))
                        loadBusySlots() // show what is taken now
                    }
                    _state.update { it.copy(isSubmitting = false, error = result.error.userMessage(), conflict = isConflict) }
                    _effects.send(BookingEffect.ShowMessage(result.error.userMessage()))
                }
            }
        }
    }

    private fun loadBusySlots() {
        viewModelScope.launch {
            (getBusySlots(carId) as? AppResult.Success)?.let { r -> _state.update { it.copy(busy = r.data) } }
        }
    }

    private fun initialState(): BookingState {
        // Next whole hour, at least an hour away.
        val first = LocalDateTime.now(clock).plusHours(1).truncatedTo(ChronoUnit.HOURS)
        return BookingState(zone = clock.zone, startDate = first.toLocalDate(), startTime = first.toLocalTime())
    }
}

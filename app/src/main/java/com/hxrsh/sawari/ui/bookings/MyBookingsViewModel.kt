package com.hxrsh.sawari.ui.bookings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hxrsh.sawari.analytics.Analytics
import com.hxrsh.sawari.analytics.Events
import com.hxrsh.sawari.domain.AppError
import com.hxrsh.sawari.domain.AppResult
import com.hxrsh.sawari.domain.errorOrNull
import com.hxrsh.sawari.domain.model.Booking
import com.hxrsh.sawari.domain.userMessage
import com.hxrsh.sawari.domain.usecase.CancelBookingUseCase
import com.hxrsh.sawari.domain.usecase.LogoutUseCase
import com.hxrsh.sawari.domain.usecase.ObserveBookingsUseCase
import com.hxrsh.sawari.domain.usecase.RefreshBookingsUseCase
import com.hxrsh.sawari.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

@Immutable
data class BookingsState(
    val content: UiState<List<Booking>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val staleNotice: String? = null,
    val cancellingId: Int? = null,
    val now: Instant = Instant.EPOCH,
)

@Immutable
private data class Refresh(val inFlight: Boolean = false, val completedOnce: Boolean = false, val error: AppError? = null)

@HiltViewModel
class MyBookingsViewModel @Inject constructor(
    observeBookings: ObserveBookingsUseCase,
    private val refreshBookings: RefreshBookingsUseCase,
    private val cancelBooking: CancelBookingUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val analytics: Analytics,
    private val clock: Clock,
) : ViewModel() {

    private val refresh = MutableStateFlow(Refresh())
    private val cancelling = MutableStateFlow<Int?>(null)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    val state: StateFlow<BookingsState> = combine(observeBookings(), refresh, cancelling) { bookings, r, cancellingId ->
        val content: UiState<List<Booking>> = when {
            bookings.isNotEmpty() -> UiState.Content(bookings)
            !r.completedOnce -> UiState.Loading
            r.error != null -> UiState.Error(r.error.userMessage())
            else -> UiState.Empty
        }
        BookingsState(
            content = content,
            isRefreshing = r.inFlight,
            staleNotice = if (bookings.isNotEmpty()) r.error?.let(::notice) else null,
            cancellingId = cancellingId,
            now = clock.instant(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookingsState())

    init {
        refresh()
    }

    fun refresh() {
        if (refresh.value.inFlight) return
        refresh.update { it.copy(inFlight = true) }
        viewModelScope.launch {
            val result = refreshBookings()
            refresh.value = Refresh(inFlight = false, completedOnce = true, error = result.errorOrNull())
        }
    }

    fun cancel(bookingId: Int) {
        if (cancelling.value != null) return
        cancelling.value = bookingId
        viewModelScope.launch {
            when (val result = cancelBooking(bookingId)) {
                is AppResult.Success -> {
                    analytics.logEvent(Events.BOOKING_CANCELLED, mapOf("booking_id" to bookingId.toString()))
                    _messages.send("Booking cancelled")
                }
                is AppResult.Failure -> _messages.send(result.error.userMessage())
            }
            cancelling.value = null
        }
    }

    fun logout() {
        viewModelScope.launch { logoutUseCase() }
    }

    /** Debug menu: proves non-fatal reporting reaches Crashlytics. */
    fun debugNonFatal() = analytics.recordNonFatal(IllegalStateException("Sawari debug non-fatal"))

    /** Debug menu: proves fatal crash reporting reaches Crashlytics. */
    fun debugCrash() = analytics.crashForTest()

    private fun notice(error: AppError) = when (error) {
        is AppError.Network -> "You're offline. Showing saved bookings."
        else -> "Couldn't refresh. Showing saved bookings."
    }
}

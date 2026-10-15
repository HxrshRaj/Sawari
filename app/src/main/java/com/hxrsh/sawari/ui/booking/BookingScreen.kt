package com.hxrsh.sawari.ui.booking

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hxrsh.sawari.ui.common.formatDate
import com.hxrsh.sawari.ui.common.formatDateTime
import com.hxrsh.sawari.ui.common.formatPrice
import com.hxrsh.sawari.ui.common.formatTime
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@Composable
fun BookingScreen(onBack: () -> Unit, onBooked: () -> Unit, viewModel: BookingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val currentOnBooked by rememberUpdatedState(onBooked)

    // Effects are one-shot: collected once, never replayed on recomposition or rotation.
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is BookingEffect.Booked -> {
                    snackbar.showSnackbar("Booking confirmed")
                    currentOnBooked()
                }
                is BookingEffect.ShowMessage -> snackbar.showSnackbar(effect.message)
            }
        }
    }
    BookingContent(state, snackbar, onBack, viewModel::onIntent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingContent(
    state: BookingState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onIntent: (BookingIntent) -> Unit,
) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Book a car") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.car != null) {
                Text(state.car.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() }.testTag("booking_car_name"))
            }
            Text("When do you need it?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { showDate = true },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("pick_date")
                        .semantics(mergeDescendants = true) { contentDescription = "Start date, ${state.startDate.formatDate()}. Double tap to change" },
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(state.startDate.formatDate())
                }
                OutlinedButton(
                    onClick = { showTime = true },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("pick_time")
                        .semantics(mergeDescendants = true) { contentDescription = "Start time, ${state.startTime.formatTime()}. Double tap to change" },
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(state.startTime.formatTime())
                }
            }

            Text("Duration", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FilledIconButton(
                    onClick = { onIntent(BookingIntent.ChangeDuration(-1)) },
                    enabled = state.durationHours > BookingState.MIN_HOURS,
                    modifier = Modifier.testTag("duration_minus"),
                ) { Icon(Icons.Default.Remove, contentDescription = "Fewer hours") }
                Text(
                    "${state.durationHours} ${if (state.durationHours == 1) "hour" else "hours"}",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.testTag("duration_text").semantics { liveRegion = LiveRegionMode.Polite },
                )
                FilledIconButton(
                    onClick = { onIntent(BookingIntent.ChangeDuration(+1)) },
                    enabled = state.durationHours < BookingState.MAX_HOURS,
                    modifier = Modifier.testTag("duration_plus"),
                ) { Icon(Icons.Default.Add, contentDescription = "More hours") }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("From  ${state.start.formatDateTime()}", style = MaterialTheme.typography.bodyMedium)
                    Text("Until  ${state.end.formatDateTime()}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Total ${formatPrice(state.total)}",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.testTag("total_text"),
                    )
                }
            }

            if (state.error != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().testTag(if (state.conflict) "conflict_banner" else "error_banner")
                        .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive },
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (state.conflict) "Someone just booked this slot" else "Couldn't book",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(state.error, color = MaterialTheme.colorScheme.onErrorContainer)
                        if (state.conflict) Text("Pick a different time below.", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            if (state.busy.isNotEmpty()) {
                Text("Already booked", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                state.busy.take(6).forEach {
                    Text(
                        "${it.start.formatDateTime(state.zone)} → ${it.end.formatDateTime(state.zone)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Button(
                onClick = { onIntent(BookingIntent.Submit) },
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("confirm_button")
                    .semantics { if (state.isSubmitting) stateDescription = "Booking in progress" },
            ) {
                if (state.isSubmitting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("Confirm booking")
            }
        }
    }

    if (showDate) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.startDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = remember { NotInThePast },
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        onIntent(BookingIntent.PickDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()))
                    }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(pickerState) }
    }

    if (showTime) {
        val pickerState = rememberTimePickerState(initialHour = state.startTime.hour, initialMinute = state.startTime.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    onIntent(BookingIntent.PickTime(LocalTime.of(pickerState.hour, pickerState.minute)))
                    showTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } },
            text = { TimePicker(pickerState) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private object NotInThePast : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() >= LocalDate.now().minusDays(0)
}

package com.hxrsh.sawari.ui.bookings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hxrsh.sawari.BuildConfig
import com.hxrsh.sawari.domain.model.Booking
import com.hxrsh.sawari.domain.model.BookingStatus
import com.hxrsh.sawari.ui.common.EmptyState
import com.hxrsh.sawari.ui.common.ErrorState
import com.hxrsh.sawari.ui.common.LoadingState
import com.hxrsh.sawari.ui.common.OfflineBanner
import com.hxrsh.sawari.ui.common.UiState
import com.hxrsh.sawari.ui.common.formatDateTime
import com.hxrsh.sawari.ui.common.formatPrice

@Composable
fun MyBookingsScreen(viewModel: MyBookingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    MyBookingsContent(
        state = state,
        snackbar = snackbar,
        onRefresh = viewModel::refresh,
        onCancel = viewModel::cancel,
        onLogout = viewModel::logout,
        onDebugNonFatal = viewModel::debugNonFatal,
        onDebugCrash = viewModel::debugCrash,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyBookingsContent(
    state: BookingsState,
    snackbar: SnackbarHostState,
    onRefresh: () -> Unit,
    onCancel: (Int) -> Unit,
    onLogout: () -> Unit,
    onDebugNonFatal: () -> Unit = {},
    onDebugCrash: () -> Unit = {},
    showDebugMenu: Boolean = BuildConfig.DEBUG,
) {
    var pendingCancel by remember { mutableStateOf<Booking?>(null) }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("My bookings", style = MaterialTheme.typography.headlineSmall)
                AccountMenu(onLogout, showDebugMenu, onDebugNonFatal, onDebugCrash)
            }
            if (state.staleNotice != null) {
                OfflineBanner(state.staleNotice, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                when (val content = state.content) {
                    UiState.Loading -> LoadingState()
                    UiState.Empty -> EmptyState("No bookings yet", "Pick a car and book your first ride.", icon = Icons.Default.Event)
                    is UiState.Error -> ErrorState(content.message, onRetry = onRefresh)
                    is UiState.Content -> LazyColumn(
                        modifier = Modifier.fillMaxSize().testTag("bookings_list"),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(content.data, key = { it.id }, contentType = { "booking" }) { booking ->
                            BookingCard(
                                booking = booking,
                                cancellable = booking.isCancellable(state.now),
                                cancelling = state.cancellingId == booking.id,
                                onCancel = { pendingCancel = booking },
                            )
                        }
                    }
                }
            }
        }
    }

    pendingCancel?.let { booking ->
        AlertDialog(
            onDismissRequest = { pendingCancel = null },
            title = { Text("Cancel this booking?") },
            text = { Text("${booking.car.name}\n${booking.start.formatDateTime()} → ${booking.end.formatDateTime()}") },
            confirmButton = {
                TextButton(
                    onClick = { onCancel(booking.id); pendingCancel = null },
                    modifier = Modifier.testTag("confirm_cancel"),
                ) { Text("Cancel booking") }
            },
            dismissButton = { TextButton(onClick = { pendingCancel = null }) { Text("Keep it") } },
        )
    }
}

@Composable
private fun AccountMenu(onLogout: () -> Unit, showDebug: Boolean, onNonFatal: () -> Unit, onCrash: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag("account_menu")) {
            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Sign out") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                onClick = { open = false; onLogout() },
                modifier = Modifier.testTag("sign_out"),
            )
            if (showDebug) {
                DropdownMenuItem(
                    text = { Text("Debug: record non-fatal") },
                    leadingIcon = { Icon(Icons.Default.BugReport, contentDescription = null) },
                    onClick = { open = false; onNonFatal() },
                    modifier = Modifier.testTag("debug_nonfatal"),
                )
                DropdownMenuItem(
                    text = { Text("Debug: crash app") },
                    leadingIcon = { Icon(Icons.Default.BugReport, contentDescription = null) },
                    onClick = { open = false; onCrash() },
                    modifier = Modifier.testTag("debug_crash"),
                )
            }
        }
    }
}

@Composable
fun BookingCard(booking: Booking, cancellable: Boolean, cancelling: Boolean, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val cancelled = booking.status == BookingStatus.CANCELLED
    Card(
        modifier = modifier.fillMaxWidth().testTag("booking_card_${booking.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (cancelled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(booking.car.name, style = MaterialTheme.typography.titleMedium)
                AssistChip(
                    onClick = {},
                    label = { Text(if (cancelled) "Cancelled" else "Confirmed") },
                    modifier = Modifier.testTag("status_${booking.id}"),
                )
            }
            Text(booking.car.city, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${booking.start.formatDateTime()} → ${booking.end.formatDateTime()}", style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(formatPrice(booking.totalPrice), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                if (cancellable) {
                    OutlinedButton(onClick = onCancel, enabled = !cancelling, modifier = Modifier.testTag("cancel_${booking.id}")) {
                        if (cancelling) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Cancel")
                    }
                } else Spacer(Modifier.size(0.dp))
            }
        }
    }
}

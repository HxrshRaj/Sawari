package com.hxrsh.sawari.ui.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun ReminderPermissionCard(controller: PermissionController, modifier: Modifier = Modifier) =
    ReminderPermissionCard(controller.status, controller.request, controller.openSettings, modifier)

/**
 * Booking-screen card for the reminder notification. Booking works in every state; only the reminder depends on it.
 * Stateless apart from the rationale dialog, so each state is testable without touching the real permission.
 */
@Composable
fun ReminderPermissionCard(
    status: PermissionStatus,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRationale by rememberSaveable { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth().testTag("reminder_card").semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (status) {
                PermissionStatus.Granted ->
                    Text("Reminder on: we will notify you 1 hour before your booking starts.", modifier = Modifier.testTag("reminder_granted"))
                PermissionStatus.NotAsked -> {
                    Text("Get a reminder 1 hour before your booking starts?", style = MaterialTheme.typography.titleSmall)
                    Text("This is optional. Booking works either way.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { showRationale = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("reminder_enable")) {
                        Text("Turn on reminders")
                    }
                }
                PermissionStatus.DeniedCanAskAgain -> {
                    Text("Reminders are off", style = MaterialTheme.typography.titleSmall)
                    Text("You can still book. Without notifications we cannot remind you.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { showRationale = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("reminder_ask_again")) {
                        Text("Ask again")
                    }
                }
                PermissionStatus.DeniedPermanently -> {
                    Text("Notifications are blocked", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "You can still book. To get reminders, allow notifications for Sawari in system settings.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = onOpenSettings, modifier = Modifier.heightIn(min = 48.dp).testTag("reminder_open_settings")) {
                        Text("Open settings")
                    }
                }
            }
        }
    }
    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text("Why we ask") },
            text = { Text("Sawari sends one notification, one hour before a booking starts, so you do not miss your car. Nothing else.") },
            confirmButton = {
                TextButton(onClick = { showRationale = false; onRequest() }, modifier = Modifier.testTag("rationale_continue")) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }, modifier = Modifier.testTag("rationale_dismiss")) { Text("Not now") }
            },
        )
    }
}

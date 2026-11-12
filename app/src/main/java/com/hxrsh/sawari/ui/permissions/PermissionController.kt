package com.hxrsh.sawari.ui.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.hxrsh.sawari.notifications.ReminderNotifications

@Stable
class PermissionController(val status: PermissionStatus, val request: () -> Unit, val openSettings: () -> Unit)

private const val PREFS = "permission_prefs"

/** Test hook: forget that we ever asked, so the flow starts at NotAsked again. */
fun resetAskedPermissions(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

/** Notifications: a runtime permission only from Android 13; before that they are on unless switched off in settings. */
@Composable
fun rememberNotificationPermissionController(): PermissionController {
    val context = LocalContext.current
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val base = rememberPermissionController(Manifest.permission.POST_NOTIFICATIONS)
        // Create the channel only now, right before our own request, so the system never prompts on its own.
        remember(base) {
            PermissionController(base.status, request = { ReminderNotifications.ensureChannel(context); base.request() }, openSettings = base.openSettings)
        }
    } else {
        val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        PermissionController(
            if (enabled) PermissionStatus.Granted else PermissionStatus.DeniedPermanently,
            request = { openAppSettings(context) },
            openSettings = { openAppSettings(context) },
        )
    }
}

/**
 * Generic runtime-permission state holder: current [PermissionStatus], a `request()` that shows the system dialog,
 * and `openSettings()`. Re-evaluates on every resume, because the user may flip the switch in system settings.
 */
@Composable
fun rememberPermissionController(permission: String): PermissionController {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    fun compute(): PermissionStatus = resolvePermissionStatus(
        granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED,
        askedBefore = prefs.getBoolean(permission, false),
        shouldShowRationale = context.findActivity()?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, permission) } ?: false,
    )

    var status by remember { mutableStateOf(compute()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        prefs.edit().putBoolean(permission, true).apply()
        status = compute()
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) status = compute() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return remember(status) {
        PermissionController(status, request = { launcher.launch(permission) }, openSettings = { openAppSettings(context) })
    }
}

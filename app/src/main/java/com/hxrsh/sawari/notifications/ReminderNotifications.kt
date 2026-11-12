package com.hxrsh.sawari.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.hxrsh.sawari.MainActivity
import com.hxrsh.sawari.R
import com.hxrsh.sawari.ui.nav.DeepLinkParser

object ReminderNotifications {
    const val CHANNEL_ID = "booking_reminders"
    private const val NOTIFICATION_ID_BASE = 10_000

    /** Safe to call repeatedly. Channels exist from API 26 (our minSdk). */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Booking reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "A reminder one hour before a booking starts"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Tapping the notification opens My bookings through the same deep link as `sawari://bookings`. */
    fun contentIntent(context: Context, bookingId: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(DeepLinkParser.bookingsUri()), context, MainActivity::class.java)
        return PendingIntent.getActivity(context, bookingId, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** Returns false when notifications are blocked (permission denied or switched off): nothing is shown, nothing crashes. */
    @SuppressLint("MissingPermission") // guarded by areNotificationsEnabled(), which reflects the POST_NOTIFICATIONS state
    fun show(context: Context, bookingId: Int, carName: String): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle("Your booking starts in 1 hour")
            .setContentText(carName)
            .setContentIntent(contentIntent(context, bookingId))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        manager.notify(NOTIFICATION_ID_BASE + bookingId, notification)
        return true
    }
}

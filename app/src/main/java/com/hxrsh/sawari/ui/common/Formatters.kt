package com.hxrsh.sawari.ui.common

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateTime = DateTimeFormatter.ofPattern("EEE, d MMM · h:mm a", Locale.getDefault())
private val dateOnly = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())
private val timeOnly = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

fun formatPrice(rupees: Int): String = "₹$rupees"

fun Instant.formatDateTime(zone: ZoneId = ZoneId.systemDefault()): String = dateTime.format(atZone(zone))

fun LocalDate.formatDate(): String = dateOnly.format(this)

fun LocalTime.formatTime(): String = timeOnly.format(this)

fun LocalDateTime.formatDateTime(): String = dateTime.format(this)

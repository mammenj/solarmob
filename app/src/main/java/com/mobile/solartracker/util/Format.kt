package com.mobile.solartracker.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DISPLAY_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

fun LocalDate.displayDate(): String = format(DISPLAY_DATE)

fun Double.fmt2(): String = String.format(Locale.US, "%.2f", this)

fun Double.fmt1(): String = String.format(Locale.US, "%.1f", this)

fun Long.humanSize(): String = when {
    this >= 1_048_576L -> String.format(Locale.US, "%.1f MB", this / 1_048_576.0)
    this >= 1024L -> String.format(Locale.US, "%.1f KB", this / 1024.0)
    else -> "$this B"
}

package com.mobile.solartracker.data

/** Metadata about a SQLite file held in the app's private database folder. */
data class DbInfo(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val readingCount: Int,
    val valid: Boolean,
)

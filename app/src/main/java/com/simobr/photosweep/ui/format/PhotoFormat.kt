package com.simobr.photosweep.ui.format

import com.simobr.photosweep.data.media.Photo
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Display formatting. Pure functions with the zone and locale passed in, so the output is
 * testable and does not depend on the machine running the test.
 */
object PhotoFormat {

    /**
     * Bytes as the user understands them.
     *
     * Decimal megabytes, not binary: the number has to agree with what Android's own storage
     * settings shows, or the "space freed" figure looks like a lie the first time someone
     * checks it.
     */
    fun bytes(byteCount: Long): String = when {
        byteCount < 1_000L -> "$byteCount B"
        byteCount < 1_000_000L -> "${byteCount / 1_000L} KB"
        byteCount < 100_000_000L ->
            String.format(Locale.US, "%.1f MB", byteCount / 1_000_000.0)
        byteCount < 1_000_000_000L -> "${byteCount / 1_000_000L} MB"
        else -> String.format(Locale.US, "%.1f GB", byteCount / 1_000_000_000.0)
    }

    /** "Fri 12 Jan 2024 · 22:41" */
    fun captureStamp(photo: Photo, zone: ZoneId = ZoneId.systemDefault()): String =
        STAMP.withZone(zone).format(Instant.ofEpochMilli(photo.capturedAtMs))

    /** "12 Jan 2024" — the short form used in the undo toast. */
    fun shortDate(photo: Photo, zone: ZoneId = ZoneId.systemDefault()): String =
        SHORT.withZone(zone).format(Instant.ofEpochMilli(photo.capturedAtMs))

    /** "4.8 MB · 4032 × 3024 · Screenshots" */
    fun detailLine(photo: Photo): String = buildString {
        append(bytes(photo.sizeBytes))
        if (photo.width > 0 && photo.height > 0) {
            append(" · ").append(photo.width).append(" × ").append(photo.height)
        }
        photo.bucketDisplayName?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
    }

    private val STAMP: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE d MMM yyyy · HH:mm", Locale.getDefault())
    private val SHORT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
}

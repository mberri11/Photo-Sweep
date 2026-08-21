package com.simobr.photosweep.data.piles

import com.simobr.photosweep.data.media.Photo
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * Turns a flat list of [Photo] metadata into the piles the home screen offers.
 *
 * Pure and synchronous: no IO, no Android types, no clock of its own. Everything it needs
 * arrives as arguments, which is why the whole of it is testable on the JVM.
 */
object PileBuilder {

    /**
     * Builds the pile list, biggest first.
     *
     * @param photos every photo from the gallery, favourites included — this function is
     *   where they are removed.
     * @param zone the time zone month boundaries are drawn in. Injectable so tests do not
     *   depend on the machine running them.
     */
    fun build(photos: List<Photo>, zone: ZoneId = ZoneId.systemDefault()): List<Pile> {
        val sweepable = photos.filterNot { it.isFavorite }

        val grouped = LinkedHashMap<PileKind, MutableList<Photo>>()
        for (photo in sweepable) {
            val kind = when (PileRules.classify(photo.bucketDisplayName, photo.relativePath)) {
                PileCategory.Screenshots -> PileKind.Screenshots
                PileCategory.WhatsApp -> PileKind.WhatsApp
                PileCategory.Downloads -> PileKind.Downloads
                PileCategory.Other -> PileKind.Month(monthOf(photo, zone))
            }
            grouped.getOrPut(kind) { mutableListOf() } += photo
        }

        return grouped.map { (kind, members) ->
            // Newest first inside a pile; _id breaks ties so the order of a burst shot in
            // the same second is stable between runs rather than dependent on cursor order.
            val ordered = members.sortedWith(
                compareByDescending<Photo> { it.capturedAtMs }.thenByDescending { it.id },
            )
            Pile(
                kind = kind,
                photos = ordered,
                totalBytes = ordered.sumOf { it.sizeBytes },
            )
        }.sortedWith(
            // "Biggest wins first": the pile that frees the most space is the one worth
            // sweeping. Ties break on recency so the list does not reshuffle between runs.
            compareByDescending<Pile> { it.totalBytes }
                .thenByDescending { it.photos.firstOrNull()?.capturedAtMs ?: 0L }
                .thenBy { it.kind.stableRank() },
        )
    }

    private fun monthOf(photo: Photo, zone: ZoneId): YearMonth =
        YearMonth.from(Instant.ofEpochMilli(photo.capturedAtMs).atZone(zone))

    /** Last-resort deterministic ordering, so two identical piles never swap places. */
    private fun PileKind.stableRank(): String = when (this) {
        PileKind.Screenshots -> "0"
        PileKind.WhatsApp -> "1"
        PileKind.Downloads -> "2"
        PileKind.Duplicates -> "3"
        is PileKind.Month -> "4$yearMonth"
    }
}

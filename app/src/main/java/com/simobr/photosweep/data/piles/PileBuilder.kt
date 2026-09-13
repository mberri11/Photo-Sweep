package com.simobr.photosweep.data.piles

import com.simobr.photosweep.data.media.Photo
import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * Turns a flat list of [Photo] metadata into the piles the home screen offers.
 *
 * Pure and synchronous: no IO, no Android types, no decoding. Everything it needs arrives as
 * arguments, which is why the whole of it is testable on the JVM. The one concession is that
 * [build] defaults `nowMs` to the wall clock; the function body reads only the parameter, and
 * every test passes its own.
 *
 * ## Two sorts of pile
 *
 * **Partitions** — Screenshots, WhatsApp, Downloads, and the month piles — divide the library.
 * Every sweepable photo is in exactly one of them.
 *
 * **Overlays** — [PileKind.BigFiles] and [PileKind.OldShots] — do not. They are *lenses*: a
 * 14MB screenshot from last year appears in BigFiles, in OldShots, **and** in the Screenshots
 * pile, and that is the point. "Show me what is eating the storage" and "show me the folder I
 * never clear out" are different questions about the same photo, and a user who wants the
 * first should not have to guess which folder to open. The consequence to keep in mind is that
 * the pile list is not a partition: summing `count` or `totalBytes` across every pile
 * double-counts. [PileKind.isOverlay] is there to exclude them when a real total is wanted.
 *
 * Overlays are ordered ahead of the byte-sorted piles, BigFiles first, and are suppressed
 * entirely below [MIN_OVERLAY_PHOTOS] members — a shortcut holding three photos is not a
 * shortcut.
 *
 * ## Two filters, in one place
 *
 * Favourites never reach a pile. Neither do photos the user has already marked for sweeping:
 * because overlays share members with partitions, a photo swept from BigFiles would otherwise
 * turn up again, unmarked, in its month pile — the same photo offered twice, the second time
 * as though the first decision had not happened. `pending_mark` is keyed on `mediaId` alone,
 * so a mark is global and one query answers it for every pile.
 */
object PileBuilder {

    /** The biggest-files lens never offers more than this many photos at once. */
    private const val BIG_FILES_LIMIT = 100

    /** A screenshot older than this is one nobody came back for. */
    private val OLD_SHOT_AGE: Duration = Duration.ofDays(180)

    /** Below this an overlay is noise rather than a shortcut, and is not offered at all. */
    private const val MIN_OVERLAY_PHOTOS = 5

    /**
     * Builds the pile list: overlays first, then the partitions biggest-first.
     *
     * @param photos every photo from the gallery, favourites included — this function is
     *   where they are removed.
     * @param zone the time zone month boundaries are drawn in. Injectable so tests do not
     *   depend on the machine running them.
     * @param markedMediaIds photos already marked for sweeping, from
     *   `PendingMarkDao.markedMediaIds()`. Dropped from every pile, overlay and partition
     *   alike.
     * @param nowMs the instant [PileKind.OldShots] measures age against.
     */
    fun build(
        photos: List<Photo>,
        zone: ZoneId = ZoneId.systemDefault(),
        markedMediaIds: Set<Long> = emptySet(),
        nowMs: Long = System.currentTimeMillis(),
    ): List<Pile> {
        val sweepable = photos.filterNot { it.isFavorite || it.id in markedMediaIds }

        return overlayPiles(sweepable, nowMs) + partitionPiles(sweepable, zone)
    }

    /** Screenshots / WhatsApp / Downloads / month. Every sweepable photo lands in one. */
    private fun partitionPiles(sweepable: List<Photo>, zone: ZoneId): List<Pile> {
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
            val ordered = members.sortedWith(NEWEST_FIRST)
            Pile(kind = kind, photos = ordered, totalBytes = ordered.sumOf { it.sizeBytes })
        }.sortedWith(
            // "Biggest wins first": the pile that frees the most space is the one worth
            // sweeping. Ties break on recency so the list does not reshuffle between runs.
            compareByDescending<Pile> { it.totalBytes }
                .thenByDescending { it.photos.firstOrNull()?.capturedAtMs ?: 0L }
                .thenBy { it.kind.stableRank() },
        )
    }

    /**
     * The lenses, pinned above the byte-sorted rows. BigFiles first: "what is using my
     * storage" is the question the app is for, and the answer should not be the third row.
     */
    private fun overlayPiles(sweepable: List<Photo>, nowMs: Long): List<Pile> {
        val biggest = sweepable.sortedWith(LARGEST_FIRST).take(BIG_FILES_LIMIT)

        val oldShots = sweepable
            .filter {
                PileRules.classify(it.bucketDisplayName, it.relativePath) ==
                    PileCategory.Screenshots
            }
            // capturedAtMs is 0 when MediaStore gave no usable date at all. An unknown date is
            // not an old one, and calling it old would put a screenshot taken this morning in
            // front of the user labelled as forgotten.
            .filter { it.capturedAtMs > 0L && nowMs - it.capturedAtMs > OLD_SHOT_AGE.toMillis() }
            .sortedWith(NEWEST_FIRST)

        return listOfNotNull(
            overlayOrNull(PileKind.BigFiles, biggest),
            overlayOrNull(PileKind.OldShots, oldShots),
        )
    }

    private fun overlayOrNull(kind: PileKind, photos: List<Photo>): Pile? =
        if (photos.size < MIN_OVERLAY_PHOTOS) {
            null
        } else {
            Pile(kind = kind, photos = photos, totalBytes = photos.sumOf { it.sizeBytes })
        }

    private fun monthOf(photo: Photo, zone: ZoneId): YearMonth =
        YearMonth.from(Instant.ofEpochMilli(photo.capturedAtMs).atZone(zone))

    /** Newest first, `_id` descending as the tie-break. */
    private val NEWEST_FIRST =
        compareByDescending<Photo> { it.capturedAtMs }.thenByDescending { it.id }

    /**
     * Largest first, `_id` descending as the tie-break. The tie-break is not cosmetic: a
     * gallery of equally sized files would otherwise hand BigFiles a different hundred photos
     * on every load.
     */
    private val LARGEST_FIRST =
        compareByDescending<Photo> { it.sizeBytes }.thenByDescending { it.id }

    /**
     * Last-resort deterministic ordering, so two identical piles never swap places.
     *
     * Overlays never reach this — they are prepended, not sorted — but the `when` is exhaustive
     * so that adding a kind is a compile error here rather than a silent arbitrary order.
     */
    private fun PileKind.stableRank(): String = when (this) {
        PileKind.Screenshots -> "0"
        PileKind.WhatsApp -> "1"
        PileKind.Downloads -> "2"
        PileKind.Duplicates -> "3"
        is PileKind.Month -> "4$yearMonth"
        PileKind.BigFiles -> "5"
        PileKind.OldShots -> "6"
    }
}

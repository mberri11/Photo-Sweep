package com.simobr.photosweep.data.piles

import com.simobr.photosweep.data.media.Photo
import java.time.YearMonth

/** Identity of a pile. The UI turns this into a title; the data layer never formats strings. */
sealed interface PileKind {

    /**
     * Stable, storable identity.
     *
     * Written into `pending_mark.pileId`, so it has to survive a process restart and an app
     * update unchanged. Never derive it from a localised title or from an enum ordinal —
     * both would silently reassign a user's saved marks to a different pile.
     */
    val id: String

    data object Screenshots : PileKind {
        override val id: String get() = "screenshots"
    }

    data object WhatsApp : PileKind {
        override val id: String get() = "whatsapp"
    }

    data object Downloads : PileKind {
        override val id: String get() = "downloads"
    }

    /** Everything the folder rules did not claim, grouped by capture month. */
    data class Month(val yearMonth: YearMonth) : PileKind {
        override val id: String get() = "month:$yearMonth"
    }

    /**
     * Near-identical frames detected by content rather than folder. Declared here so the pile
     * type is one closed set from the start; the engine is cut from 1.0.
     */
    data object Duplicates : PileKind {
        override val id: String get() = "duplicates"
    }

    /**
     * The largest photos in the library, regardless of where they live.
     *
     * An overlay, not a folder: its members are also in their own folder or month pile. See
     * `PileBuilder` for why that is deliberate.
     */
    data object BigFiles : PileKind {
        override val id: String get() = "bigfiles"
    }

    /**
     * Screenshots old enough that nobody came back for them. Also an overlay.
     */
    data object OldShots : PileKind {
        override val id: String get() = "oldshots"
    }
}

/**
 * Whether a kind is a lens over the library rather than a slice of it.
 *
 * Overlay piles share their photos with the folder and month piles. Anything that assumes the
 * pile list is a partition — summing counts or bytes across piles to get a library total, for
 * instance — has to exclude these or it double-counts.
 */
val PileKind.isOverlay: Boolean
    get() = this is PileKind.BigFiles || this is PileKind.OldShots

/**
 * A group of photos offered to the user as one sweeping session.
 *
 * Never contains a favourite, and never contains a photo the user has already marked — both
 * are filtered in exactly one place, `PileBuilder`.
 */
data class Pile(
    val kind: PileKind,
    val photos: List<Photo>,
    val totalBytes: Long,
) {
    val count: Int get() = photos.size
}

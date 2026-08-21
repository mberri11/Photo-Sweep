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
     * Near-identical frames detected by content rather than folder. Arrives in Stage 6;
     * declared here so the pile type is one closed set from the start.
     */
    data object Duplicates : PileKind {
        override val id: String get() = "duplicates"
    }
}

/**
 * A group of photos offered to the user as one sweeping session.
 *
 * Never contains a favourite — see `PileBuilder`.
 */
data class Pile(
    val kind: PileKind,
    val photos: List<Photo>,
    val totalBytes: Long,
) {
    val count: Int get() = photos.size
}

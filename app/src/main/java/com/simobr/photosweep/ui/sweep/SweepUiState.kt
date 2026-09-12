package com.simobr.photosweep.ui.sweep

import com.simobr.photosweep.data.media.Photo

/** One recorded swipe. Nothing has been destroyed; this is a note of what the user meant. */
data class SweepDecision(
    val photoId: Long,
    val direction: SweepDirection,
    val sizeBytes: Long,
    val decidedAt: Long,
)

/** The most recent swipe, for the undo toast. Null once undone or dismissed. */
data class LastAction(
    val photo: Photo,
    val direction: SweepDirection,
    /** Changes on every swipe, so the toast restarts its four seconds rather than continuing. */
    val token: Long,
)

/**
 * Everything the sweep screen draws.
 *
 * The counter invariant — `sweptCount + keptCount == currentIndex` — is not enforced here by
 * an assertion; it is true by construction, because all three are read off one ordered list
 * of decisions. There is no separate counter to drift. `SweepViewModelTest` still checks it
 * after a thousand random actions, because "true by construction" is a claim about the
 * construction, and the construction can be changed.
 */
data class SweepUiState(
    val pileId: String,
    val pileTitle: String,
    val photos: List<Photo> = emptyList(),
    val decisions: List<SweepDecision> = emptyList(),
    val lastAction: LastAction? = null,
    val isLoading: Boolean = true,
) {
    /** How many photos have been decided. Also the index of the card on top. */
    val currentIndex: Int get() = decisions.size

    val total: Int get() = photos.size

    /**
     * The counter's numerator, for `"%d / %d"` in the header.
     *
     * [currentIndex] is a cursor: it is 0 while the first card is still on screen, which is
     * correct for `photos[currentIndex]` and wrong for a human, who is looking at photo one
     * of 1,721 and not photo zero. So the display position is the cursor plus one, clamped so
     * the finished state reads `218 / 218` rather than `219 / 218`, and an empty pile reads
     * `0 / 0` rather than `1 / 0`.
     *
     * Nothing else moves. [progress] and every internal use of [currentIndex] stay on the
     * cursor — this value exists only to be printed.
     */
    val displayIndex: Int get() = minOf(currentIndex + 1, total)

    val sweptCount: Int get() = decisions.count { it.direction == SweepDirection.Sweep }

    val keptCount: Int get() = decisions.count { it.direction == SweepDirection.Keep }

    /** Bytes that *would* be freed. Nothing has been freed yet. */
    val sweptBytes: Long
        get() = decisions.sumOf { if (it.direction == SweepDirection.Sweep) it.sizeBytes else 0L }

    /** The card on top. Null when the pile is finished. */
    val current: Photo? get() = photos.getOrNull(currentIndex)

    /** The single ghost card behind. Exactly one — never a fanned deck of three. */
    val next: Photo? get() = photos.getOrNull(currentIndex + 1)

    val isFinished: Boolean get() = !isLoading && currentIndex >= photos.size

    val canUndo: Boolean get() = decisions.isNotEmpty()

    /** 0f..1f through the pile, for the progress hairline. */
    val progress: Float get() = if (total == 0) 0f else currentIndex.toFloat() / total

    /** The photo ids marked for sweeping, in decision order. */
    val sweptIds: List<Long>
        get() = decisions.filter { it.direction == SweepDirection.Sweep }.map { it.photoId }
}

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

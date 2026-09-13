package com.simobr.photosweep.ui.sweep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.simobr.photosweep.data.db.PendingMark
import com.simobr.photosweep.data.db.PendingMarkDao
import com.simobr.photosweep.data.media.Photo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the sweep session for one pile.
 *
 * Two things live here rather than in the composable, and both for the same reason — they
 * must outlive a recomposition, a rotation, and a trip through the background:
 *
 *  - **the undo stack**, which is unbounded. Nothing is deleted until the confirm screen, so
 *    undo costs a list entry and a user may walk all the way back to index 0. There is no
 *    reason to cap it and every reason not to.
 *  - **the mark set**, written to Room behind a 300ms debounce.
 */
class SweepViewModel(
    private val pileId: String,
    pileTitle: String,
    private val photos: List<Photo>,
    private val dao: PendingMarkDao,
    private val now: () -> Long = System::currentTimeMillis,
    private val persistDebounceMs: Long = PERSIST_DEBOUNCE_MS,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SweepUiState(pileId = pileId, pileTitle = pileTitle, photos = photos),
    )
    val state: StateFlow<SweepUiState> = _state.asStateFlow()

    private var persistJob: Job? = null
    private var toastToken = 0L

    /**
     * Marks that were already on disk when this pass started.
     *
     * "Review again" rewinds to index 0 without discarding anything, so a photo the user has
     * not reached yet this pass must keep the mark it already had. Without this, the first
     * debounced write of a review pass would erase every mark ahead of the cursor.
     */
    private var baseline: Map<Long, PendingMark> = emptyMap()

    init {
        viewModelScope.launch { restore() }
    }

    /**
     * Rebuilds the session from the marks stored on disk.
     *
     * Only swept photos are persisted, so the resume point is taken as one past the
     * last-marked photo: everything before it that is not marked must have been kept. That
     * reconstructs both counters exactly for the whole prefix.
     *
     * The one thing it cannot recover is a run of keeps *after* the final sweep — those
     * photos come round again. That is a bounded, harmless loss, and the alternative was
     * writing a row for every keep, which turns a quiet 300ms debounce into a write per
     * swipe for decisions that mean "do nothing".
     */
    private suspend fun restore() {
        val stored = dao.marksForPile(pileId)
        baseline = stored.associateBy { it.mediaId }
        val marked = stored.mapTo(HashSet()) { it.mediaId }
        val resumeIndex = photos.indexOfLast { it.id in marked } + 1
        val restored = photos.take(resumeIndex).map { photo ->
            SweepDecision(
                photoId = photo.id,
                direction = if (photo.id in marked) SweepDirection.Sweep else SweepDirection.Keep,
                sizeBytes = photo.sizeBytes,
                decidedAt = now(),
            )
        }
        _state.update { it.copy(decisions = restored, isLoading = false) }
    }

    /** Swipe left: mark the current photo. Destroys nothing. */
    fun sweep() = decide(SweepDirection.Sweep)

    /** Swipe right: keep the current photo. */
    fun keep() = decide(SweepDirection.Keep)

    private fun decide(direction: SweepDirection) {
        val photo = _state.value.current ?: return
        val at = now()
        _state.update { current ->
            current.copy(
                decisions = current.decisions + SweepDecision(
                    photoId = photo.id,
                    direction = direction,
                    sizeBytes = photo.sizeBytes,
                    decidedAt = at,
                ),
                lastAction = LastAction(photo, direction, ++toastToken),
            )
        }
        schedulePersist()
    }

    /**
     * Steps back one decision, from any depth.
     *
     * The toast is cleared rather than re-pointed at the previous swipe: it exists to make
     * the last action reversible, and after an undo the last action *was* the undo.
     */
    fun undo() {
        if (!_state.value.canUndo) return
        _state.update { it.copy(decisions = it.decisions.dropLast(1), lastAction = null) }
        schedulePersist()
    }

    /**
     * Rewinds to the first card with every mark left standing.
     *
     * The counters restart because they describe this pass through the pile; the marks do
     * not, because they describe the user's decisions. Re-deciding a photo overwrites its
     * mark — sweeping adds one, keeping removes one — and anything not reached again keeps
     * what it had.
     */
    fun restartReview() {
        baseline = effectiveMarks(_state.value).associateBy { it.mediaId }
        _state.update { it.copy(decisions = emptyList(), lastAction = null) }
        schedulePersist()
    }

    /**
     * Throws this pile's marks away, in memory and on disk.
     *
     * The only caller is the "Discard" button on the back-out dialog, and it exists because
     * clearing the table alone would not work: [effectiveMarks] carries [baseline] forward for
     * any photo this pass has not re-decided, so the next debounced write would put every
     * discarded mark straight back. The pending write is cancelled first, then both halves of
     * the state are emptied, and only then is the table cleared — in that order, so a write
     * that is already in flight can only ever write less than before, never more.
     */
    suspend fun discardMarks() {
        persistJob?.cancel()
        baseline = emptyMap()
        _state.update { it.copy(decisions = emptyList(), lastAction = null) }
        dao.clearPile(pileId)
    }

    /** Hides the toast without changing any decision. */
    fun dismissToast() {
        _state.update { it.copy(lastAction = null) }
    }

    private fun schedulePersist() {
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(persistDebounceMs)
            writeMarks()
        }
    }

    /**
     * Writes immediately, cancelling any pending debounce.
     *
     * Called when the screen stops. The debounce exists to avoid a database write per swipe
     * during a fast run; it must not become a 300ms window in which the process can die and
     * take the last decision with it.
     */
    suspend fun flush() {
        persistJob?.cancel()
        writeMarks()
    }

    private suspend fun writeMarks() {
        dao.replacePile(pileId = pileId, marks = effectiveMarks(_state.value))
    }

    /**
     * The mark set as it stands: this pass's sweeps, plus any earlier mark for a photo this
     * pass has not decided yet.
     */
    private fun effectiveMarks(snapshot: SweepUiState): List<PendingMark> {
        val decidedIds = snapshot.decisions.mapTo(HashSet()) { it.photoId }
        val fromThisPass = snapshot.decisions
            .filter { it.direction == SweepDirection.Sweep }
            .map { PendingMark(mediaId = it.photoId, pileId = pileId, markedAt = it.decidedAt) }
        val carriedOver = baseline.values.filterNot { it.mediaId in decidedIds }
        return fromThisPass + carriedOver
    }

    companion object {
        const val PERSIST_DEBOUNCE_MS = 300L

        fun factory(
            pileId: String,
            pileTitle: String,
            photos: List<Photo>,
            dao: PendingMarkDao,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SweepViewModel(pileId, pileTitle, photos, dao) as T
        }
    }
}

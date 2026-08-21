package com.simobr.photosweep.ui.confirm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.simobr.photosweep.data.db.PendingMarkDao
import com.simobr.photosweep.data.db.SweepStat
import com.simobr.photosweep.data.db.SweepStatDao
import com.simobr.photosweep.data.media.Photo
import com.simobr.photosweep.data.trash.TrashConfirmation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Screen 08. */
data class ConfirmUiState(
    val pileTitle: String,
    val condemned: List<Photo> = emptyList(),
    val keptCount: Int = 0,
    val isLoading: Boolean = true,
) {
    val count: Int get() = condemned.size

    /**
     * The exact sum of the marked photos' sizes, in bytes. Not rounded, not estimated, not
     * cached from somewhere else — the number the button promises is this number.
     */
    val totalBytes: Long get() = condemned.sumOf { it.sizeBytes }
}

/**
 * What MediaStore confirmed after the system dialog closed.
 *
 * Never derived from the dialog's result code.
 */
data class TrashOutcome(
    val requestedCount: Int,
    val confirmedCount: Int,
    val confirmedBytes: Long,
) {
    val nothingHappened: Boolean get() = confirmedCount == 0
    val partial: Boolean get() = confirmedCount in 1 until requestedCount
}

class ConfirmViewModel(
    private val pileId: String,
    pileTitle: String,
    private val pilePhotos: List<Photo>,
    keptCount: Int,
    private val markDao: PendingMarkDao,
    private val statDao: SweepStatDao,
    private val trash: TrashConfirmation,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _state = MutableStateFlow(ConfirmUiState(pileTitle = pileTitle, keptCount = keptCount))
    val state: StateFlow<ConfirmUiState> = _state.asStateFlow()

    /**
     * The trash as it stood before this batch was requested.
     *
     * Needed because "is it in the trash now?" is not the same question as "did we put it
     * there?". A photo the user trashed from Google Photos five minutes ago would otherwise
     * be counted as bytes this app freed.
     */
    private var trashedBeforeRequest: Set<Long> = emptySet()

    init {
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        val marked = markDao.marksForPile(pileId).mapTo(HashSet()) { it.mediaId }
        // Pile order, not mark order: the grid should read the way the pile did.
        val condemned = pilePhotos.filter { it.id in marked }
        _state.update { it.copy(condemned = condemned, isLoading = false) }
    }

    /**
     * Pulls one photo back out of the pile. Totals update from the list, so they cannot
     * disagree with the grid.
     */
    fun unmark(photoId: Long) {
        _state.update { it.copy(condemned = it.condemned.filterNot { photo -> photo.id == photoId }) }
        viewModelScope.launch { markDao.removeMarks(listOf(photoId)) }
    }

    /**
     * Snapshots the trash and returns the ids to send. Call immediately before launching the
     * system dialog.
     */
    suspend fun beginTrashRequest(): List<Long> {
        trashedBeforeRequest = trash.trashContents().mapTo(HashSet()) { it.id }
        return _state.value.condemned.map { it.id }
    }

    /**
     * Works out what actually happened, by asking MediaStore.
     *
     * Takes no result code on purpose. `RESULT_OK` means the user tapped the confirm button,
     * not that every uri moved — the system can trash a subset, and on a cancel it trashes
     * none while still, on some devices, returning a code the app would be foolish to read as
     * success. The only trustworthy answer is a re-query, so that is the only thing consulted.
     *
     * Marks are cleared only for photos confirmed trashed. Anything the system declined keeps
     * its mark and comes back on the next confirm screen.
     */
    suspend fun reconcileAfterDialog(): TrashOutcome {
        val requested = _state.value.condemned.map { it.id }
        val nowTrashed = trash.confirmTrashed(requested)
            .filterNot { it.id in trashedBeforeRequest }

        val confirmedIds = nowTrashed.map { it.id }
        val confirmedBytes = nowTrashed.sumOf { it.sizeBytes }

        if (confirmedIds.isNotEmpty()) {
            markDao.removeMarks(confirmedIds)
            statDao.insert(
                SweepStat(
                    tsMs = now(),
                    bytesSwept = confirmedBytes,
                    photoCount = confirmedIds.size,
                ),
            )
        }
        reload()

        return TrashOutcome(
            requestedCount = requested.size,
            confirmedCount = confirmedIds.size,
            confirmedBytes = confirmedBytes,
        )
    }

    companion object {
        fun factory(
            pileId: String,
            pileTitle: String,
            pilePhotos: List<Photo>,
            keptCount: Int,
            markDao: PendingMarkDao,
            statDao: SweepStatDao,
            trash: TrashConfirmation,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ConfirmViewModel(
                pileId, pileTitle, pilePhotos, keptCount, markDao, statDao, trash,
            ) as T
        }
    }
}

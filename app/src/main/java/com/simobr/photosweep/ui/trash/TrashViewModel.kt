package com.simobr.photosweep.ui.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.simobr.photosweep.data.media.MediaRepository
import com.simobr.photosweep.data.media.Photo
import com.simobr.photosweep.data.media.TrashFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the Trash screen draws. */
data class TrashUiState(
    val photos: List<Photo> = emptyList(),
    val selected: Set<Long> = emptySet(),
    val isLoading: Boolean = true,
) {
    val totalBytes: Long get() = photos.sumOf { it.sizeBytes }

    val isEmpty: Boolean get() = !isLoading && photos.isEmpty()

    val selectionCount: Int get() = selected.size

    val hasSelection: Boolean get() = selected.isNotEmpty()

    /** Ids to act on: the selection, or everything when "Restore all" is used. */
    val allIds: List<Long> get() = photos.map { it.id }
}

/**
 * The Trash screen's state.
 *
 * The one rule that matters here is the same one `ConfirmViewModel` follows, and it is followed
 * the same way rather than re-invented: after a system dialog closes, the result code is not
 * consulted at all. [reconcileAfterDialog] re-queries MediaStore with
 * `QUERY_ARG_MATCH_TRASHED = MATCH_ONLY` — via [TrashFilter.OnlyTrashed] — and the screen
 * redraws from whatever the resolver says is still trashed. A partial grant, a cancelled
 * dialog and a full grant are therefore indistinguishable in code, which is the point: the
 * app never claims something moved because it asked for it to move.
 *
 * See RELEASE.md section 6.
 */
class TrashViewModel(private val media: MediaRepository) : ViewModel() {

    private val _state = MutableStateFlow(TrashUiState())
    val state: StateFlow<TrashUiState> = _state.asStateFlow()

    init {
        reload()
    }

    /** Newest first: the query already sorts on `date_added DESC, _id DESC`. */
    private fun reload() {
        viewModelScope.launch {
            val trashed = media.queryPhotos(TrashFilter.OnlyTrashed)
            _state.update { current ->
                val stillThere = trashed.mapTo(HashSet()) { it.id }
                current.copy(
                    photos = trashed,
                    // Drop selections for photos that are no longer in the trash, so a stale
                    // id cannot end up in the next request.
                    selected = current.selected.intersect(stillThere),
                    isLoading = false,
                )
            }
        }
    }

    fun toggle(id: Long) {
        _state.update { current ->
            current.copy(
                selected = if (id in current.selected) {
                    current.selected - id
                } else {
                    current.selected + id
                },
            )
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selected = emptySet()) }
    }

    /**
     * Called when the system dialog closes, whatever it returned.
     *
     * Takes no result code by design — there is no parameter for one.
     */
    fun reconcileAfterDialog() {
        clearSelection()
        reload()
    }

    companion object {
        fun factory(media: MediaRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    TrashViewModel(media) as T
            }
    }
}

package com.simobr.photosweep.data.trash

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import com.simobr.photosweep.data.media.MediaRepository
import com.simobr.photosweep.data.media.TrashFilter

/** A photo MediaStore itself reports as trashed, with the size MediaStore itself reports. */
data class ConfirmedPhoto(val id: Long, val sizeBytes: Long)

/**
 * Reads back what actually happened.
 *
 * Separated from the request builders so the reconciliation logic can be tested without a
 * device: the interesting behaviour is what the app believes after the dialog closes, and
 * that must never be "whatever we asked for".
 */
interface TrashConfirmation {

    /**
     * The subset of [ids] that MediaStore currently reports as trashed.
     *
     * Sizes come from the content resolver, not from any cached [com.simobr.photosweep.data.media.Photo].
     */
    suspend fun confirmTrashed(ids: List<Long>): List<ConfirmedPhoto>

    /** Everything currently in the system trash, whoever put it there. */
    suspend fun trashContents(): List<ConfirmedPhoto>
}

/**
 * Android's trash, used as-is.
 *
 * We do not build our own. Copying condemned photos into app-private storage would
 * temporarily **double** the space they occupy — inside a storage-cleaner — and would pull
 * them out of Google Photos' own bin, taking away a recovery route the user already had.
 * Android's trash costs nothing, already runs 30 days, and is visible from Files and Google
 * Photos, so a mistake has three ways back. There is no version of our own that beats that.
 *
 * Every mutation here is an IntentSender the *system* executes after showing the user a
 * dialog. This class never writes through the resolver itself.
 */
class MediaStoreTrash(private val resolver: ContentResolver) : TrashConfirmation {

    /**
     * One dialog for the whole batch.
     *
     * Deliberately a single request for every uri rather than one per photo: without
     * MANAGE_MEDIA the system asks for confirmation once per request, and sixty-two dialogs
     * would be unusable. Reversible — trashed photos come back for 30 days.
     */
    fun buildTrashRequest(ids: List<Long>): PendingIntent =
        MediaStore.createTrashRequest(resolver, ids.map(::contentUri), true)

    /** The same call with `false`: pulls photos back out of the trash. */
    fun buildRestoreRequest(ids: List<Long>): PendingIntent =
        MediaStore.createTrashRequest(resolver, ids.map(::contentUri), false)

    /**
     * **DESTROYS DATA.** Builds the system request that deletes photos permanently, with no
     * trash and no undo. The files are gone the moment the user confirms the dialog.
     *
     * This backs the single "Empty Trash now" action, and exists only because a trashed photo
     * still occupies storage for 30 days — a user who wants their space today is entitled to
     * it. The system dialog lists exactly what will go and is the actual consent step; this
     * function only asks for it.
     */
    fun dangerouslyBuildDeleteRequest(ids: List<Long>): PendingIntent =
        MediaStore.createDeleteRequest(resolver, ids.map(::contentUri))

    override suspend fun confirmTrashed(ids: List<Long>): List<ConfirmedPhoto> {
        if (ids.isEmpty()) return emptyList()
        val wanted = ids.toHashSet()
        return trashContents().filter { it.id in wanted }
    }

    override suspend fun trashContents(): List<ConfirmedPhoto> =
        MediaRepository(resolver)
            .queryPhotos(TrashFilter.OnlyTrashed)
            .map { ConfirmedPhoto(id = it.id, sizeBytes = it.sizeBytes) }

    private fun contentUri(id: Long): Uri = ContentUris.withAppendedId(
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
        id,
    )
}

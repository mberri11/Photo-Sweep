package com.simobr.photosweep.data.media

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Which side of the MediaStore trash a query looks at.
 *
 * MediaStore keeps trashed rows in the same table behind a flag, so "the gallery" and "the
 * trash" are the same query with one argument flipped.
 */
enum class TrashFilter(internal val matchTrashed: Int) {
    /** Normal listing: everything the user still considers present. */
    ExcludeTrashed(MediaStore.MATCH_EXCLUDE),

    /** The Trash screen: only rows already in the system trash. */
    OnlyTrashed(MediaStore.MATCH_ONLY),
}

/**
 * Read-only access to the user's images.
 *
 * This stage writes nothing. There is no insert, no update, no delete, and no file IO of any
 * kind here — the repository turns a MediaStore cursor into a list of [Photo] metadata and
 * closes the cursor. Trashing arrives in a later stage and will go through
 * `MediaStore.createTrashRequest()`, which is a system-gated user consent dialog, not a
 * silent write.
 */
class MediaRepository(private val contentResolver: ContentResolver) {

    /**
     * Every image on the external volume, newest first.
     *
     * Favourites are returned here with [Photo.isFavorite] set — filtering them out is
     * `PileBuilder`'s job, deliberately at a single chokepoint rather than smeared across
     * the query and the builder where only one of the two would end up tested.
     */
    suspend fun queryPhotos(
        filter: TrashFilter = TrashFilter.ExcludeTrashed,
    ): List<Photo> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

        val args = Bundle().apply {
            // No selection: the app wants the whole image collection. The argument is set
            // explicitly rather than omitted so the shape of the query is obvious.
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, null)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, null)

            // Sorted by date_added rather than datetaken: datetaken is null for most rows
            // this app deals with, and SQLite sorts nulls to one end, which would scatter
            // screenshots and downloads to the far end of the cursor. Real ordering happens
            // in PileBuilder against Photo.capturedAtMs. _id breaks ties so paging is stable.
            putString(
                ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC",
            )

            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, filter.matchTrashed)
        }

        contentResolver.query(collection, PROJECTION, args, null)
            ?.use { cursor -> readPhotos(cursor) }
            .orEmpty()
    }

    companion object {

        /**
         * Drains [cursor] into [Photo] metadata.
         *
         * Streams row by row and keeps nothing but the value objects — no bitmaps are decoded
         * and no file descriptors are opened, so cursor size is bounded by metadata, not pixels.
         * Internal so the 10,000-row test can drive it without a ContentResolver.
         */
        internal fun readPhotos(cursor: Cursor): List<Photo> {
            val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val takenIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val modifiedIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val bucketIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val pathIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            val widthIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val favoriteIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.IS_FAVORITE)
            val mimeIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

            val photos = ArrayList<Photo>(cursor.count.coerceAtLeast(0))
            while (cursor.moveToNext()) {
                photos += Photo(
                    id = cursor.getLong(idIdx),
                    displayName = cursor.getStringOrNull(nameIdx).orEmpty(),
                    sizeBytes = cursor.getLongOrNull(sizeIdx) ?: 0L,
                    bucketDisplayName = cursor.getStringOrNull(bucketIdx),
                    relativePath = cursor.getStringOrNull(pathIdx),
                    width = cursor.getLongOrNull(widthIdx)?.toInt() ?: 0,
                    height = cursor.getLongOrNull(heightIdx)?.toInt() ?: 0,
                    isFavorite = (cursor.getLongOrNull(favoriteIdx) ?: 0L) != 0L,
                    mimeType = cursor.getStringOrNull(mimeIdx),
                    rawDateTakenMs = cursor.getLongOrNull(takenIdx),
                    rawDateAddedSec = cursor.getLongOrNull(addedIdx),
                    rawDateModifiedSec = cursor.getLongOrNull(modifiedIdx),
                )
            }
            return photos
        }

        internal val PROJECTION = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.IS_FAVORITE,
            MediaStore.Images.Media.MIME_TYPE,
        )
    }
}

private fun Cursor.getStringOrNull(index: Int): String? =
    if (isNull(index)) null else getString(index)

private fun Cursor.getLongOrNull(index: Int): Long? =
    if (isNull(index)) null else getLong(index)

/**
 * The content URI for a photo. Kept out of [Photo] so the model stays free of android.net
 * types and can be constructed in plain JVM tests.
 */
fun Photo.contentUri(): Uri = ContentUris.withAppendedId(
    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
    id,
)

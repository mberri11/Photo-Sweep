package com.simobr.photosweep.data.media

/**
 * One row of MediaStore image metadata. Metadata only — a [Photo] never holds a Bitmap, a
 * Drawable, or any decoded pixel data, so a 10,000-photo gallery is a list of small value
 * objects. Decoding is Coil's job, on demand, at the size the screen actually needs.
 *
 * The three `raw*` date fields are the only copies of MediaStore's three date columns in the
 * app, and [capturedAtMs] is the only thing allowed to read them. `SourceHygieneTest`
 * enforces that.
 */
data class Photo(
    val id: Long,
    val displayName: String,
    val sizeBytes: Long,
    val bucketDisplayName: String?,
    val relativePath: String?,
    val width: Int,
    val height: Int,
    val isFavorite: Boolean,
    val mimeType: String?,

    /** MediaStore `datetaken`, **milliseconds**, frequently null. Read via [capturedAtMs]. */
    val rawDateTakenMs: Long?,

    /** MediaStore `date_added`, **seconds**. Read via [capturedAtMs]. */
    val rawDateAddedSec: Long?,

    /** MediaStore `date_modified`, **seconds**. Read via [capturedAtMs]. */
    val rawDateModifiedSec: Long?,
) {

    /**
     * The canonical capture time, in milliseconds since epoch.
     *
     * This exists because MediaStore mixes units across three columns and the app would
     * silently mis-date every pile if any call site guessed wrong:
     *
     *  - `datetaken` is **milliseconds**, and is null for most of what this app cleans —
     *    screenshots, WhatsApp saves, downloads. Anything that did not come from a camera.
     *  - `date_added` is **seconds**.
     *  - `date_modified` is **seconds**.
     *
     * @see resolveCapturedAtMs
     */
    val capturedAtMs: Long
        get() = resolveCapturedAtMs(rawDateTakenMs, rawDateAddedSec, rawDateModifiedSec)

    companion object {

        /**
         * The one and only conversion from MediaStore's three date columns to a single
         * millisecond timestamp. Nothing else in the app multiplies a MediaStore date by
         * 1000, and nothing else decides which column wins.
         *
         * Order of preference: `datetaken`, then `date_added`, then `date_modified`.
         *
         * A value is only accepted if it is strictly positive. MediaStore reports "unknown"
         * as both null *and* 0 depending on which app wrote the row, and a 0 accepted here
         * would date the photo to January 1970 and conjure a phantom month pile — the exact
         * silent mis-dating this function exists to prevent.
         *
         * Returns 0 when all three columns are missing, which cannot happen for a real
         * MediaStore row (`date_added` is provider-assigned) but is not worth crashing over.
         */
        fun resolveCapturedAtMs(
            rawDateTakenMs: Long?,
            rawDateAddedSec: Long?,
            rawDateModifiedSec: Long?,
        ): Long {
            rawDateTakenMs?.let { if (it > 0L) return it }
            rawDateAddedSec?.let { if (it > 0L) return it * 1000L }
            rawDateModifiedSec?.let { if (it > 0L) return it * 1000L }
            return 0L
        }
    }
}

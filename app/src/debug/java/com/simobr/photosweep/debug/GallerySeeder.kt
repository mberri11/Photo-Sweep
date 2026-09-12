package com.simobr.photosweep.debug

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.provider.MediaStore
import com.simobr.photosweep.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Calendar
import java.util.Random
import kotlin.coroutines.coroutineContext

/** What a seeding run produced. */
data class SeedResult(
    val monthPhotos: Int,
    val screenshots: Int,
    val whatsApp: Int,
    val downloads: Int,
    val burstFrames: Int,
    val favourites: Int,
    val totalBytes: Long,
) {
    val total: Int get() = monthPhotos + screenshots + whatsApp + downloads + burstFrames
}

/**
 * Debug-only synthetic gallery.
 *
 * The destructive stages of this app need something to destroy. Testing them against a real
 * camera roll is not an option — a bug in a trash path costs somebody their photos. So this
 * builds a disposable gallery with the same *shape* as a real one: a year of camera photos,
 * a screenshots folder, a WhatsApp-shaped tree, a downloads folder, burst groups of
 * near-identical frames, and a handful of favourites to prove the safety floor holds.
 *
 * Everything it writes lives under [ROOT_RELATIVE_PATH] and nowhere else, which is what
 * makes [dangerouslyWipeTestGallery] safe to point at a whole directory.
 *
 * Guarded by [BuildConfig.DEBUG] at every entry point. In a release build these functions
 * throw rather than silently doing nothing, because a tool that quietly no-ops is a tool
 * somebody will one day believe ran.
 */
object GallerySeeder {

    /** Everything the seeder creates lives under this path. Nothing outside it is touched. */
    const val ROOT_RELATIVE_PATH = "Pictures/PhotoSweepTest/"

    private const val TOTAL_PHOTOS = 500
    private const val MONTH_PHOTOS = 291
    private const val SCREENSHOTS = 70
    private const val WHATSAPP = 60
    private const val DOWNLOADS = 40
    private const val BURST_GROUPS = 8

    /** Frames per burst group: 4–6, as real burst captures vary. Sums to 39. */
    private val BURST_SIZES = intArrayOf(4, 5, 6, 4, 5, 6, 4, 5)

    private const val WIDTH = 1200
    private const val HEIGHT = 1600
    private const val TARGET_BYTES = 200_000
    private const val SIZE_TOLERANCE = 60_000

    /** Fixed seed: two runs produce the same gallery, so a bug reproduces. */
    private const val RANDOM_SEED = 20260821L

    private val monthPath = "${ROOT_RELATIVE_PATH}Camera/"
    private val screenshotPath = "${ROOT_RELATIVE_PATH}Screenshots/"
    private val whatsAppPath = "${ROOT_RELATIVE_PATH}WhatsApp/Media/WhatsApp Images/"
    private val downloadPath = "${ROOT_RELATIVE_PATH}Download/"

    /**
     * Writes [TOTAL_PHOTOS] synthetic JPEGs into the test folder.
     *
     * Creates files; destroys nothing. Safe to run twice — it will simply produce a second
     * set of files alongside the first.
     *
     * @param onProgress called with (written, total) on the IO dispatcher.
     */
    suspend fun seed(
        context: Context,
        onProgress: (written: Int, total: Int) -> Unit = { _, _ -> },
    ): SeedResult = withContext(Dispatchers.IO) {
        requireDebugBuild()

        val random = Random(RANDOM_SEED)
        var written = 0
        var totalBytes = 0L
        var favourites = 0

        fun report() = onProgress(++written, TOTAL_PHOTOS)

        // --- Camera photos, spread evenly across the last 12 months --------------------
        // These carry DATE_TAKEN, like real camera output.
        for (index in 0 until MONTH_PHOTOS) {
            coroutineContext.ensureActive()
            val takenAt = timestampInMonthsAgo(index % 12, random)
            // Every 24th camera photo is a favourite: enough of them to land in several
            // months, so the "favourites are never offered" floor is visible on a device.
            val favourite = index % 24 == 0
            if (favourite) favourites++
            totalBytes += insertJpeg(
                context = context,
                displayName = "ps_camera_%03d.jpg".format(index),
                relativePath = monthPath,
                bytes = encodeJpeg(syntheticImage(random)),
                dateTakenMs = takenAt,
                favourite = favourite,
            )
            report()
        }

        // --- Screenshots ---------------------------------------------------------------
        // Deliberately no DATE_TAKEN. Real screenshots have none, and that is precisely the
        // case Photo.capturedAtMs exists to handle — worth exercising on a real device.
        for (index in 0 until SCREENSHOTS) {
            coroutineContext.ensureActive()
            totalBytes += insertJpeg(
                context = context,
                displayName = "ps_screenshot_%03d.jpg".format(index),
                relativePath = screenshotPath,
                bytes = encodeJpeg(syntheticImage(random)),
                dateTakenMs = null,
                favourite = false,
            )
            report()
        }

        // --- WhatsApp ------------------------------------------------------------------
        for (index in 0 until WHATSAPP) {
            coroutineContext.ensureActive()
            totalBytes += insertJpeg(
                context = context,
                displayName = "IMG-2026%02d%02d-WA%04d.jpg".format(
                    (index % 12) + 1, (index % 27) + 1, index,
                ),
                relativePath = whatsAppPath,
                bytes = encodeJpeg(syntheticImage(random)),
                dateTakenMs = null,
                favourite = false,
            )
            report()
        }

        // --- Downloads -----------------------------------------------------------------
        for (index in 0 until DOWNLOADS) {
            coroutineContext.ensureActive()
            totalBytes += insertJpeg(
                context = context,
                displayName = "ps_download_%03d.jpg".format(index),
                relativePath = downloadPath,
                bytes = encodeJpeg(syntheticImage(random)),
                dateTakenMs = null,
                favourite = false,
            )
            report()
        }

        // --- Burst groups ---------------------------------------------------------------
        // Near-identical frames: one base image per group, each frame the base plus a little
        // fresh noise, all at identical dimensions, 0.4–2s apart. One frame per group gets
        // blurred, so a "keep the sharpest" heuristic has something real to choose between.
        var burstFrames = 0
        for (group in 0 until BURST_GROUPS) {
            coroutineContext.ensureActive()
            val frames = BURST_SIZES[group]
            val blurryFrame = random.nextInt(frames)
            val base = syntheticImage(random)
            var frameTime = timestampInMonthsAgo(group % 12, random)

            for (frame in 0 until frames) {
                val bitmap = if (frame == blurryFrame) blur(base) else jitter(base, random)
                totalBytes += insertJpeg(
                    context = context,
                    displayName = "ps_burst_%02d_%02d.jpg".format(group, frame),
                    relativePath = monthPath,
                    bytes = encodeJpeg(bitmap),
                    dateTakenMs = frameTime,
                    favourite = false,
                )
                bitmap.recycle()
                // 400ms – 2000ms between frames.
                frameTime += 400L + random.nextInt(1601).toLong()
                burstFrames++
                report()
            }
            base.recycle()
        }

        SeedResult(
            monthPhotos = MONTH_PHOTOS,
            screenshots = SCREENSHOTS,
            whatsApp = WHATSAPP,
            downloads = DOWNLOADS,
            burstFrames = burstFrames,
            favourites = favourites,
            totalBytes = totalBytes,
        )
    }

    /**
     * **DESTROYS DATA.** Permanently deletes every MediaStore row and backing file under
     * `Pictures/PhotoSweepTest/`. There is no trash step and no undo — these files are gone.
     *
     * This is the one permanent delete in the app outside Trash's "Empty now", and it is
     * allowed to exist only because:
     *
     *  - it is debug-only, and throws in a release build;
     *  - it deletes strictly within [ROOT_RELATIVE_PATH], re-checked per row below rather
     *    than trusted from the query;
     *  - every file it deletes was written by [seed] minutes earlier and contains synthetic
     *    noise, never a user's photo.
     *
     * It deliberately does not use `createTrashRequest`: sending 500 throwaway test files to
     * the system trash would bury any real trashed photo the tester was inspecting.
     *
     * @return the number of rows deleted.
     */
    suspend fun dangerouslyWipeTestGallery(context: Context): Int = withContext(Dispatchers.IO) {
        requireDebugBuild()

        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.RELATIVE_PATH,
        )
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("$ROOT_RELATIVE_PATH%")

        var deleted = 0
        resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val pathIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                coroutineContext.ensureActive()
                val path = cursor.getString(pathIdx).orEmpty()
                // Belt and braces: the LIKE pattern above should make this impossible, but a
                // permanent delete does not get to rely on "should".
                check(path.startsWith(ROOT_RELATIVE_PATH)) {
                    "refusing to delete outside the test folder: $path"
                }
                val uri = android.content.ContentUris.withAppendedId(
                    collection,
                    cursor.getLong(idIdx),
                )
                deleted += resolver.delete(uri, null, null)
            }
        }
        deleted
    }

    /**
     * Writes [count] images into the test folder and returns the bytes written.
     *
     * Exists so an instrumented test can prove the MediaStore insert path works without
     * spending two minutes generating five hundred JPEGs. The full [seed] shares every line
     * of the machinery below it.
     */
    internal suspend fun seedSmokeTest(context: Context, count: Int): Long =
        withContext(Dispatchers.IO) {
            requireDebugBuild()
            val random = Random(RANDOM_SEED)
            var bytes = 0L
            repeat(count) { index ->
                bytes += insertJpeg(
                    context = context,
                    displayName = "ps_smoke_%03d.jpg".format(index),
                    relativePath = monthPath,
                    bytes = encodeJpeg(syntheticImage(random)),
                    dateTakenMs = timestampInMonthsAgo(index % 12, random),
                    favourite = index == 0,
                )
            }
            bytes
        }

    // -- internals ---------------------------------------------------------------------

    private fun requireDebugBuild() {
        check(BuildConfig.DEBUG) {
            "GallerySeeder is a debug-only tool and must never run in a release build"
        }
    }

    /**
     * Inserts one JPEG through MediaStore, using IS_PENDING so no half-written file is ever
     * visible to the gallery or to this app's own queries.
     *
     * @return the number of bytes written.
     */
    private fun insertJpeg(
        context: Context,
        displayName: String,
        relativePath: String,
        bytes: ByteArray,
        dateTakenMs: Long?,
        favourite: Boolean,
    ): Long {
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Images.Media.WIDTH, WIDTH)
            put(MediaStore.Images.Media.HEIGHT, HEIGHT)
            put(MediaStore.Images.Media.IS_PENDING, 1)
            dateTakenMs?.let { put(MediaStore.Images.Media.DATE_TAKEN, it) }
        }

        val uri: Uri = resolver.insert(collection, values)
            ?: error("MediaStore refused to create $relativePath$displayName")

        resolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: error("could not open $uri for writing")

        val publish = ContentValues().apply {
            put(MediaStore.Images.Media.IS_PENDING, 0)
            if (favourite) put(MediaStore.Images.Media.IS_FAVORITE, 1)
        }
        resolver.update(uri, publish, null, null)

        return bytes.size.toLong()
    }

    /** A solid colour with coarse block noise — cheap to generate, awkward to compress. */
    private fun syntheticImage(random: Random): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val base = Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
        canvas.drawColor(base)

        // 8px blocks rather than per-pixel noise: fast to draw, and it survives JPEG's DCT
        // well enough to land near the target file size instead of being smoothed away.
        val paint = Paint()
        val block = 8
        for (y in 0 until HEIGHT step block) {
            for (x in 0 until WIDTH step block) {
                val delta = random.nextInt(61) - 30
                paint.color = Color.rgb(
                    (Color.red(base) + delta).coerceIn(0, 255),
                    (Color.green(base) + delta).coerceIn(0, 255),
                    (Color.blue(base) + delta).coerceIn(0, 255),
                )
                canvas.drawRect(
                    x.toFloat(), y.toFloat(),
                    (x + block).toFloat(), (y + block).toFloat(),
                    paint,
                )
            }
        }
        return bitmap
    }

    /** A copy of [source] with a little extra noise: the next frame of a burst. */
    private fun jitter(source: Bitmap, random: Random): Bitmap {
        val copy = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(copy)
        val paint = Paint().apply { alpha = 18 }
        repeat(400) {
            paint.color = Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
            val x = random.nextInt(WIDTH).toFloat()
            val y = random.nextInt(HEIGHT).toFloat()
            canvas.drawRect(x, y, x + 24f, y + 24f, paint)
        }
        return copy
    }

    /**
     * Downscale-then-upscale blur. RenderScript is gone and a Gaussian kernel in Kotlin
     * would be slow over 500 images; bilinear resampling at 1/10 scale is visibly soft,
     * which is all a "which frame is sharpest" heuristic needs to have an answer.
     */
    private fun blur(source: Bitmap): Bitmap {
        val small = Bitmap.createScaledBitmap(source, WIDTH / 10, HEIGHT / 10, true)
        val blurred = Bitmap.createScaledBitmap(small, WIDTH, HEIGHT, true)
        small.recycle()
        return blurred
    }

    /**
     * Compresses to roughly [TARGET_BYTES], by bisecting JPEG quality. Real galleries are
     * full of files in the low hundreds of kilobytes, and the "space freed" figures this app
     * shows are only believable if the test gallery is in the same range.
     */
    private fun encodeJpeg(bitmap: Bitmap): ByteArray {
        var low = 30
        var high = 95
        var best: ByteArray? = null

        repeat(6) {
            val quality = (low + high) / 2
            val stream = ByteArrayOutputStream(TARGET_BYTES * 2)
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            val bytes = stream.toByteArray()
            best = bytes
            if (kotlin.math.abs(bytes.size - TARGET_BYTES) <= SIZE_TOLERANCE) return bytes
            if (bytes.size > TARGET_BYTES) high = quality - 1 else low = quality + 1
            if (low > high) return bytes
        }
        return best ?: ByteArray(0)
    }

    /** A timestamp somewhere inside the month [monthsAgo] months before now. */
    private fun timestampInMonthsAgo(monthsAgo: Int, random: Random): Long {
        val calendar = Calendar.getInstance().apply {
            add(Calendar.MONTH, -monthsAgo)
            set(Calendar.DAY_OF_MONTH, 1 + random.nextInt(27))
            set(Calendar.HOUR_OF_DAY, random.nextInt(24))
            set(Calendar.MINUTE, random.nextInt(60))
            set(Calendar.SECOND, random.nextInt(60))
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }
}

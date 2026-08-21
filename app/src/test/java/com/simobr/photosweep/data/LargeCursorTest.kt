package com.simobr.photosweep.data

import android.provider.MediaStore
import com.simobr.photosweep.data.media.MediaRepository
import com.simobr.photosweep.data.media.Photo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * A 10,000-row gallery must cost metadata, not pixels.
 *
 * The failure this guards against is the obvious one to write by accident: decoding a
 * thumbnail per row while reading the cursor, so that opening the app on a real 10,000-photo
 * phone allocates gigabytes and dies. At 1200x1600 ARGB_8888 a single decoded frame is 7.7MB;
 * ten thousand of them is 77GB. Nothing here may hold one.
 */
class LargeCursorTest {

    private companion object {
        const val ROWS = 10_000
    }

    private fun cursorOf(rows: Int) = FakeCursor(
        columns = MediaRepository.PROJECTION,
        rowCount = rows,
    ) { row, column ->
        when (column) {
            MediaStore.Images.Media._ID -> row.toLong()
            MediaStore.Images.Media.DISPLAY_NAME -> "img_$row.jpg"
            MediaStore.Images.Media.SIZE -> 2_000_000L + row
            // Two thirds of the gallery has no datetaken, like a real one.
            MediaStore.Images.Media.DATE_TAKEN -> if (row % 3 == 0) 1_756_000_000_000L + row else null
            MediaStore.Images.Media.DATE_ADDED -> 1_755_000_000L + row
            MediaStore.Images.Media.DATE_MODIFIED -> 1_754_000_000L + row
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME -> "Camera"
            MediaStore.Images.Media.RELATIVE_PATH -> "DCIM/Camera/"
            MediaStore.Images.Media.WIDTH -> 1200L
            MediaStore.Images.Media.HEIGHT -> 1600L
            MediaStore.Images.Media.IS_FAVORITE -> if (row % 500 == 0) 1L else 0L
            MediaStore.Images.Media.MIME_TYPE -> "image/jpeg"
            else -> null
        }
    }

    @Test
    fun `ten thousand rows are consumed exactly once`() {
        val cursor = cursorOf(ROWS)
        val photos = MediaRepository.readPhotos(cursor)

        assertEquals(ROWS, photos.size)
        // One move per row plus the final move that reports exhaustion.
        assertEquals(ROWS, cursor.rowsRead)
        assertEquals(0L, photos.first().id)
        assertEquals((ROWS - 1).toLong(), photos.last().id)
    }

    @Test
    fun `the null datetaken rows fall back rather than dating to 1970`() {
        val photos = MediaRepository.readPhotos(cursorOf(ROWS))
        val withoutDateTaken = photos.filter { it.rawDateTakenMs == null }

        assertEquals(ROWS - (ROWS + 2) / 3, withoutDateTaken.size)
        assertTrue(
            "a row without datetaken resolved to the epoch",
            withoutDateTaken.all { it.capturedAtMs > 1_000_000_000_000L },
        )
    }

    /**
     * Structural proof that the read path is metadata-only: every field of [Photo] is a
     * primitive, a boxed primitive, or a String. A Bitmap, a Drawable, an InputStream or a
     * ParcelFileDescriptor hiding on the model would fail here.
     */
    @Test
    fun `Photo holds only metadata fields`() {
        val allowed = setOf(
            "long", "java.lang.Long",
            "int", "java.lang.Integer",
            "boolean", "java.lang.Boolean",
            "java.lang.String",
        )
        Photo::class.java.declaredFields
            .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }
            .forEach { field ->
                assertTrue(
                    "Photo.${field.name} is a ${field.type.name}, which is not metadata",
                    field.type.name in allowed,
                )
            }
    }

    @Test
    fun `MediaRepository holds no image or stream references`() {
        val forbidden = listOf("Bitmap", "Drawable", "InputStream", "ParcelFileDescriptor")
        MediaRepository::class.java.declaredFields
            .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }
            .forEach { field ->
                forbidden.forEach { banned ->
                    assertFalse(
                        "MediaRepository.${field.name} is a ${field.type.name}",
                        field.type.name.contains(banned),
                    )
                }
            }
    }

    /**
     * The allocation check. Reading 10,000 rows should cost single-digit megabytes of small
     * value objects. The bound is deliberately loose — enough headroom that GC timing cannot
     * make it flake, but far below what even one decoded bitmap per hundred rows would cost.
     */
    @Test
    fun `reading ten thousand rows stays within a metadata-sized heap`() {
        val runtime = Runtime.getRuntime()
        repeat(3) { runtime.gc() }
        val before = runtime.totalMemory() - runtime.freeMemory()

        val photos = MediaRepository.readPhotos(cursorOf(ROWS))

        val after = runtime.totalMemory() - runtime.freeMemory()
        val grewBytes = after - before

        assertEquals(ROWS, photos.size)
        assertTrue(
            "reading $ROWS rows grew the heap by ${grewBytes / 1_048_576}MB; " +
                "that is pixel data, not metadata",
            grewBytes < 64L * 1_048_576L,
        )
    }

    @Test
    fun `a cursor opened through use is closed`() {
        val cursor = cursorOf(10)
        cursor.use { MediaRepository.readPhotos(it) }
        assertTrue(cursor.isCursorClosed)
    }
}

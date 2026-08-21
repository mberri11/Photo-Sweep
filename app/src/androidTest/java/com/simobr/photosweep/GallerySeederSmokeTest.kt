package com.simobr.photosweep

import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import com.simobr.photosweep.debug.GallerySeeder
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the seeder can actually write to MediaStore and take it all back.
 *
 * The seeder is the safety net for every destructive stage that follows, and until this ran
 * it had never created a single file — the insert path, the IS_PENDING publish, and the wipe
 * were all unexecuted code. A safety net nobody has stood on is not a safety net.
 *
 * Writes three images into `Pictures/PhotoSweepTest/` and deletes them again. Nothing outside
 * that folder is touched, and the wipe re-checks every row's path before removing it.
 */
class GallerySeederSmokeTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun cleanUp() = runTest {
        // Belt and braces: if an assertion fails mid-test, the device does not keep the files.
        GallerySeeder.dangerouslyWipeTestGallery(context)
    }

    private fun countTestGalleryRows(): Int {
        val uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        return context.contentResolver.query(
            uri,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
            arrayOf("${GallerySeeder.ROOT_RELATIVE_PATH}%"),
            null,
        )?.use { it.count } ?: 0
    }

    @Test
    fun seedsWritesRealJpegsAndTheWipeRemovesThem() = runTest {
        GallerySeeder.dangerouslyWipeTestGallery(context)
        assertEquals("test folder was not empty at the start", 0, countTestGalleryRows())

        val bytes = GallerySeeder.seedSmokeTest(context, count = 3)

        assertEquals(3, countTestGalleryRows())
        assertTrue("no bytes were written", bytes > 0)
        // Roughly 200KB each is what makes the "space freed" figures believable.
        val average = bytes / 3
        assertTrue(
            "average encoded size was $average bytes, expected to land near 200KB",
            average in 60_000..600_000,
        )

        val removed = GallerySeeder.dangerouslyWipeTestGallery(context)
        assertEquals(3, removed)
        assertEquals(0, countTestGalleryRows())
    }
}

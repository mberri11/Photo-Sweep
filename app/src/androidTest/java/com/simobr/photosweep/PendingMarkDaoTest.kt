package com.simobr.photosweep

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.simobr.photosweep.data.db.PendingMark
import com.simobr.photosweep.data.db.PhotoSweepDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The real Room round trip, on a real SQLite.
 *
 * The JVM suite drives the ViewModel against a hand-written fake because there is no SQLite
 * driver on the desktop classpath. That fake could agree perfectly with a DAO that does not
 * compile, or whose `@Query` is wrong — only this runs the generated code.
 */
class PendingMarkDaoTest {

    private lateinit var db: PhotoSweepDatabase

    @Before
    fun open() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, PhotoSweepDatabase::class.java).build()
    }

    @After
    fun close() = db.close()

    @Test
    fun marksRoundTripThroughSqlite() = runTest {
        val dao = db.pendingMarkDao()
        dao.upsertAll(
            listOf(
                PendingMark(mediaId = 10, pileId = "screenshots", markedAt = 1),
                PendingMark(mediaId = 11, pileId = "screenshots", markedAt = 2),
                PendingMark(mediaId = 12, pileId = "whatsapp", markedAt = 3),
            ),
        )

        assertEquals(listOf(10L, 11L), dao.marksForPile("screenshots").map { it.mediaId })
        assertEquals(listOf(12L), dao.marksForPile("whatsapp").map { it.mediaId })
        assertEquals(3, dao.allMarks().size)
    }

    @Test
    fun replacePileTouchesOnlyThatPile() = runTest {
        val dao = db.pendingMarkDao()
        dao.upsertAll(
            listOf(
                PendingMark(10, "screenshots", 1),
                PendingMark(11, "screenshots", 2),
                PendingMark(12, "whatsapp", 3),
            ),
        )

        dao.replacePile("screenshots", listOf(PendingMark(99, "screenshots", 9)))

        assertEquals(listOf(99L), dao.marksForPile("screenshots").map { it.mediaId })
        assertEquals("the other pile must be untouched", listOf(12L), dao.marksForPile("whatsapp").map { it.mediaId })
    }

    @Test
    fun replacingWithAnEmptySetClearsThePile() = runTest {
        val dao = db.pendingMarkDao()
        dao.upsertAll(listOf(PendingMark(10, "screenshots", 1)))

        dao.replacePile("screenshots", emptyList())

        assertEquals(0, dao.marksForPile("screenshots").size)
    }

    @Test
    fun markingTheSamePhotoTwiceIsOneRow() = runTest {
        val dao = db.pendingMarkDao()
        dao.upsertAll(listOf(PendingMark(10, "screenshots", 1)))
        dao.upsertAll(listOf(PendingMark(10, "screenshots", 5)))

        val rows = dao.marksForPile("screenshots")
        assertEquals(1, rows.size)
        assertEquals(5L, rows.single().markedAt)
    }
}

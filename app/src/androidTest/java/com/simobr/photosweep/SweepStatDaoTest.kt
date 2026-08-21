package com.simobr.photosweep

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.simobr.photosweep.data.db.PhotoSweepDatabase
import com.simobr.photosweep.data.db.SweepStat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** Lifetime aggregation, through the generated Room queries rather than the fake. */
class SweepStatDaoTest {

    private lateinit var db: PhotoSweepDatabase

    @Before
    fun open() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, PhotoSweepDatabase::class.java).build()
    }

    @After
    fun close() = db.close()

    @Test
    fun anEmptyTableReportsZeroAndNoStartDate() = runTest {
        val dao = db.sweepStatDao()
        assertEquals(0L, dao.lifetimeBytes())
        assertEquals(0, dao.lifetimePhotos())
        // Null, not a hardcoded month — "since March 2024" would be a lie on a fresh install.
        assertNull(dao.firstSweepAtMs())
    }

    @Test
    fun totalsSumAndTheStartDateIsTheEarliestRow() = runTest {
        val dao = db.sweepStatDao()
        dao.insert(SweepStat(tsMs = 1_800_000_000_000L, bytesSwept = 1_000_000L, photoCount = 3))
        dao.insert(SweepStat(tsMs = 1_700_000_000_000L, bytesSwept = 2_500_000L, photoCount = 7))
        dao.insert(SweepStat(tsMs = 1_900_000_000_000L, bytesSwept = 500_000L, photoCount = 1))

        assertEquals(4_000_000L, dao.lifetimeBytes())
        assertEquals(11, dao.lifetimePhotos())
        assertEquals(1_700_000_000_000L, dao.firstSweepAtMs())
        assertEquals(3, dao.batchCount())
    }
}

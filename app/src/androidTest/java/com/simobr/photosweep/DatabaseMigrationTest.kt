package com.simobr.photosweep

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.simobr.photosweep.data.db.PhotoSweepDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The 1 → 2 migration, run against real SQLite.
 *
 * A v1 database holds a user's pending marks. If this migration were wrong the app would
 * either crash on launch after an update or fall back to destroying the database — and the
 * user would open Photo Sweep to find a swipe session they spent twenty minutes on simply
 * gone, with no error and nothing to report.
 */
class DatabaseMigrationTest {

    private companion object {
        const val TEST_DB = "migration-test.db"
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PhotoSweepDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2KeepsPendingMarksAndAddsTheStatTable() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO pending_mark (mediaId, pileId, markedAt) VALUES (42, 'screenshots', 1700)",
            )
            db.execSQL(
                "INSERT INTO pending_mark (mediaId, pileId, markedAt) VALUES (43, 'whatsapp', 1701)",
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            PhotoSweepDatabase.MIGRATION_1_2,
        )

        migrated.query("SELECT mediaId, pileId FROM pending_mark ORDER BY mediaId").use { cursor ->
            assertEquals("the user's marks did not survive the migration", 2, cursor.count)
            cursor.moveToFirst()
            assertEquals(42L, cursor.getLong(0))
            assertEquals("screenshots", cursor.getString(1))
        }

        // The new table exists and is usable, not merely declared.
        migrated.execSQL(
            "INSERT INTO sweep_stat (tsMs, bytesSwept, photoCount) VALUES (1700, 480000000, 62)",
        )
        migrated.query("SELECT bytesSwept, photoCount FROM sweep_stat").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals(480_000_000L, cursor.getLong(0))
            assertEquals(62, cursor.getInt(1))
        }
        assertTrue(migrated.isOpen)
        migrated.close()
    }
}

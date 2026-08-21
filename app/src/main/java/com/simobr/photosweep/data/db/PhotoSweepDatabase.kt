package com.simobr.photosweep.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The app's only database. It holds decisions, never photos.
 */
@Database(entities = [PendingMark::class, SweepStat::class], version = 2, exportSchema = true)
abstract class PhotoSweepDatabase : RoomDatabase() {

    abstract fun pendingMarkDao(): PendingMarkDao

    abstract fun sweepStatDao(): SweepStatDao

    companion object {

        /**
         * Adds the lifetime stat table.
         *
         * A real migration rather than a destructive fallback: v1 databases hold pending
         * marks, and dropping them would silently throw away a user's swipe session the
         * first time they updated the app.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sweep_stat` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`tsMs` INTEGER NOT NULL, " +
                        "`bytesSwept` INTEGER NOT NULL, " +
                        "`photoCount` INTEGER NOT NULL)",
                )
            }
        }

        @Volatile
        private var instance: PhotoSweepDatabase? = null

        fun get(context: Context): PhotoSweepDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PhotoSweepDatabase::class.java,
                    "photo_sweep.db",
                ).addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}

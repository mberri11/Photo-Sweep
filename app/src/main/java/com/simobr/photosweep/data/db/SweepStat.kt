package com.simobr.photosweep.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * One confirmed batch.
 *
 * Written only after MediaStore has been re-read and has confirmed what actually moved to the
 * trash — never on the strength of a dialog returning RESULT_OK. The lifetime counter is the
 * app's one long-lived claim about itself, so every byte in it is a byte the content resolver
 * agreed to.
 */
@Entity(tableName = "sweep_stat")
data class SweepStat(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tsMs: Long,
    val bytesSwept: Long,
    val photoCount: Int,
)

/** Lifetime totals for the success screen. */
@Dao
interface SweepStatDao {

    @Insert
    suspend fun insert(stat: SweepStat)

    @Query("SELECT COALESCE(SUM(bytesSwept), 0) FROM sweep_stat")
    suspend fun lifetimeBytes(): Long

    @Query("SELECT COALESCE(SUM(photoCount), 0) FROM sweep_stat")
    suspend fun lifetimePhotos(): Int

    /**
     * Timestamp of the earliest batch, or null if the user has never confirmed one.
     *
     * "since March 2024" is read from here and never hardcoded — a hardcoded month is a lie
     * on every device except the one it was written on.
     */
    @Query("SELECT MIN(tsMs) FROM sweep_stat")
    suspend fun firstSweepAtMs(): Long?

    @Query("SELECT COUNT(*) FROM sweep_stat")
    suspend fun batchCount(): Int
}

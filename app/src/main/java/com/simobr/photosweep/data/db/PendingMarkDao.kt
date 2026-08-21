package com.simobr.photosweep.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * Reads and writes the pending mark set.
 *
 * Declared as an interface so a test can implement it directly — the JVM test suite has no
 * SQLite driver, and the alternative was adding one to a deliberately short dependency list.
 * The real Room round trip is covered by an instrumented test instead.
 */
@Dao
interface PendingMarkDao {

    @Query("SELECT * FROM pending_mark WHERE pileId = :pileId ORDER BY markedAt ASC")
    suspend fun marksForPile(pileId: String): List<PendingMark>

    @Query("SELECT * FROM pending_mark ORDER BY markedAt ASC")
    suspend fun allMarks(): List<PendingMark>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(marks: List<PendingMark>)

    @Query("DELETE FROM pending_mark WHERE pileId = :pileId")
    suspend fun clearPile(pileId: String)

    /**
     * Forgets the marks for photos that have actually reached the trash.
     *
     * Called with the ids MediaStore confirmed, not the ids that were requested. A photo the
     * system declined to trash keeps its mark, so the next confirm screen still offers it.
     */
    @Query("DELETE FROM pending_mark WHERE mediaId IN (:mediaIds)")
    suspend fun removeMarks(mediaIds: List<Long>)

    /**
     * Makes the stored mark set for one pile match [marks] exactly.
     *
     * A whole-pile replace rather than incremental edits: the writer is debounced, so what
     * arrives here is a snapshot of the user's decisions, and reconciling a snapshot against
     * per-row deltas is how undo history ends up disagreeing with the database.
     *
     * Removes only rows belonging to [pileId]. No file is affected — see [PendingMark].
     */
    @Transaction
    suspend fun replacePile(pileId: String, marks: List<PendingMark>) {
        clearPile(pileId)
        if (marks.isNotEmpty()) upsertAll(marks)
    }
}

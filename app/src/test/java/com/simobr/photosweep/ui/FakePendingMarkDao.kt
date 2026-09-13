package com.simobr.photosweep.ui

import com.simobr.photosweep.data.db.PendingMark
import com.simobr.photosweep.data.db.PendingMarkDao

/**
 * In-memory stand-in for the Room DAO.
 *
 * The JVM test suite has no SQLite driver — adding one means adding a dependency to a list
 * that is deliberately short. Implementing the DAO interface directly costs nothing, and the
 * real Room round trip is covered on a device by `PendingMarkDaoTest`.
 *
 * `replacePile` is inherited from the interface, so this fake exercises the same
 * clear-then-insert sequence the real DAO runs inside its transaction.
 */
class FakePendingMarkDao : PendingMarkDao {

    private val rows = linkedMapOf<Long, PendingMark>()

    /** Counts writes, so a test can prove the 300ms debounce actually coalesces. */
    var writeCount: Int = 0
        private set

    override suspend fun marksForPile(pileId: String): List<PendingMark> =
        rows.values.filter { it.pileId == pileId }.sortedBy { it.markedAt }

    override suspend fun allMarks(): List<PendingMark> = rows.values.sortedBy { it.markedAt }

    override suspend fun markedMediaIds(): List<Long> = rows.keys.toList()

    override suspend fun upsertAll(marks: List<PendingMark>) {
        writeCount++
        marks.forEach { rows[it.mediaId] = it }
    }

    override suspend fun clearPile(pileId: String) {
        rows.values.removeAll { it.pileId == pileId }
    }

    override suspend fun removeMarks(mediaIds: List<Long>) {
        mediaIds.forEach { rows.remove(it) }
    }
}

package com.simobr.photosweep.ui

import com.simobr.photosweep.data.db.SweepStat
import com.simobr.photosweep.data.db.SweepStatDao
import com.simobr.photosweep.data.trash.ConfirmedPhoto
import com.simobr.photosweep.data.trash.TrashConfirmation

/**
 * Stands in for MediaStore's trash.
 *
 * Sizes are held separately from any [com.simobr.photosweep.data.media.Photo] the app is
 * carrying, so a test can make the resolver disagree with the app's cache — which is the only
 * way to prove the app reports the resolver's number and not its own.
 */
class FakeTrash : TrashConfirmation {

    private val contents = linkedMapOf<Long, Long>()

    /** Simulates the system trashing these ids, reporting [sizeBytes] for each. */
    fun systemTrashes(vararg entries: Pair<Long, Long>) {
        entries.forEach { (id, size) -> contents[id] = size }
    }

    /** Simulates something else having trashed a photo before the app asked. */
    fun alreadyInTrash(id: Long, sizeBytes: Long) {
        contents[id] = sizeBytes
    }

    override suspend fun confirmTrashed(ids: List<Long>): List<ConfirmedPhoto> {
        val wanted = ids.toHashSet()
        return contents.filterKeys { it in wanted }.map { ConfirmedPhoto(it.key, it.value) }
    }

    override suspend fun trashContents(): List<ConfirmedPhoto> =
        contents.map { ConfirmedPhoto(it.key, it.value) }
}

class FakeSweepStatDao : SweepStatDao {
    val rows = mutableListOf<SweepStat>()

    override suspend fun insert(stat: SweepStat) { rows += stat }
    override suspend fun lifetimeBytes(): Long = rows.sumOf { it.bytesSwept }
    override suspend fun lifetimePhotos(): Int = rows.sumOf { it.photoCount }
    override suspend fun firstSweepAtMs(): Long? = rows.minOfOrNull { it.tsMs }
    override suspend fun batchCount(): Int = rows.size
}

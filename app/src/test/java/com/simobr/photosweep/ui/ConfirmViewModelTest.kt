package com.simobr.photosweep.ui

import com.simobr.photosweep.data.db.PendingMark
import com.simobr.photosweep.data.media.Photo
import com.simobr.photosweep.ui.confirm.ConfirmViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConfirmViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val pileId = "screenshots"

    /** Deliberately awkward sizes: a rounded total would hide an off-by-one in the sum. */
    private val sizes = listOf(4_812_345L, 1_004_999L, 12_003_777L, 907_001L, 66_666_666L)

    private val pilePhotos = sizes.mapIndexed { index, size ->
        Photo(
            id = index.toLong() + 1,
            displayName = "img_$index.jpg",
            sizeBytes = size,
            bucketDisplayName = "Screenshots",
            relativePath = "DCIM/Screenshots/",
            width = 1080,
            height = 2400,
            isFavorite = false,
            mimeType = "image/jpeg",
            rawDateTakenMs = 1_756_000_000_000L + index,
            rawDateAddedSec = null,
            rawDateModifiedSec = null,
        )
    }

    private suspend fun markAll(dao: FakePendingMarkDao, ids: List<Long>) {
        dao.upsertAll(ids.map { PendingMark(mediaId = it, pileId = pileId, markedAt = it) })
    }

    private fun viewModel(
        markDao: FakePendingMarkDao,
        statDao: FakeSweepStatDao,
        trash: FakeTrash,
    ) = ConfirmViewModel(
        pileId = pileId,
        pileTitle = "Screenshots",
        pilePhotos = pilePhotos,
        keptCount = 156,
        markDao = markDao,
        statDao = statDao,
        trash = trash,
        now = { 1_756_000_000_000L },
    )

    // ---- the total ---------------------------------------------------------------------

    @Test
    fun `the confirm total is the exact sum of the marked photo sizes in bytes`() = runTest {
        val markDao = FakePendingMarkDao()
        markAll(markDao, listOf(1L, 2L, 3L, 4L, 5L))
        val vm = viewModel(markDao, FakeSweepStatDao(), FakeTrash())
        advanceUntilIdle()

        assertEquals(5, vm.state.value.count)
        assertEquals(sizes.sum(), vm.state.value.totalBytes)
        // Spelled out, so a change to the fixture cannot quietly change the expectation.
        assertEquals(85_394_788L, vm.state.value.totalBytes)
    }

    @Test
    fun `pulling a thumbnail back out updates the total exactly`() = runTest {
        val markDao = FakePendingMarkDao()
        markAll(markDao, listOf(1L, 2L, 3L, 4L, 5L))
        val vm = viewModel(markDao, FakeSweepStatDao(), FakeTrash())
        advanceUntilIdle()

        vm.unmark(3L)
        advanceUntilIdle()

        assertEquals(4, vm.state.value.count)
        assertEquals(sizes.sum() - 12_003_777L, vm.state.value.totalBytes)
        assertTrue(vm.state.value.condemned.none { it.id == 3L })
        // And it is gone from disk, not just from the screen.
        assertTrue(markDao.marksForPile(pileId).none { it.mediaId == 3L })
    }

    @Test
    fun `only marked photos are condemned`() = runTest {
        val markDao = FakePendingMarkDao()
        markAll(markDao, listOf(2L, 4L))
        val vm = viewModel(markDao, FakeSweepStatDao(), FakeTrash())
        advanceUntilIdle()

        assertEquals(listOf(2L, 4L), vm.state.value.condemned.map { it.id })
        assertEquals(1_004_999L + 907_001L, vm.state.value.totalBytes)
    }

    // ---- cancelling ---------------------------------------------------------------------

    @Test
    fun `cancelling the system dialog leaves the marks intact and nothing trashed`() = runTest {
        val markDao = FakePendingMarkDao()
        val statDao = FakeSweepStatDao()
        val trash = FakeTrash()
        markAll(markDao, listOf(1L, 2L, 3L, 4L, 5L))
        val vm = viewModel(markDao, statDao, trash)
        advanceUntilIdle()

        vm.beginTrashRequest()
        // The user cancelled: the system trashed nothing at all.
        val outcome = vm.reconcileAfterDialog()

        assertEquals(0, outcome.confirmedCount)
        assertEquals(0L, outcome.confirmedBytes)
        assertTrue(outcome.nothingHappened)
        assertEquals(5, markDao.marksForPile(pileId).size)
        assertEquals("no batch may be recorded for a cancelled dialog", 0, statDao.rows.size)
        assertEquals(5, vm.state.value.count)
        assertEquals(sizes.sum(), vm.state.value.totalBytes)
    }

    // ---- partial grants -------------------------------------------------------------------

    /**
     * The system can trash some uris and not others. Assuming success would report bytes that
     * were never freed and, worse, drop the marks for photos still sitting in the gallery —
     * the user would never be offered them again.
     */
    @Test
    fun `a partial grant is reconciled from the resolver, not assumed`() = runTest {
        val markDao = FakePendingMarkDao()
        val statDao = FakeSweepStatDao()
        val trash = FakeTrash()
        markAll(markDao, listOf(1L, 2L, 3L, 4L, 5L))
        val vm = viewModel(markDao, statDao, trash)
        advanceUntilIdle()

        vm.beginTrashRequest()
        // Only two of the five actually moved.
        trash.systemTrashes(1L to 4_812_345L, 4L to 907_001L)
        val outcome = vm.reconcileAfterDialog()

        assertEquals(5, outcome.requestedCount)
        assertEquals(2, outcome.confirmedCount)
        assertEquals(4_812_345L + 907_001L, outcome.confirmedBytes)
        assertTrue(outcome.partial)

        // The three that did not move keep their marks and are offered again.
        assertEquals(listOf(2L, 3L, 5L), markDao.marksForPile(pileId).map { it.mediaId }.sorted())
        assertEquals(listOf(2L, 3L, 5L), vm.state.value.condemned.map { it.id })

        // The recorded batch matches what was confirmed, not what was asked for.
        assertEquals(1, statDao.rows.size)
        assertEquals(2, statDao.rows.single().photoCount)
        assertEquals(4_812_345L + 907_001L, statDao.rows.single().bytesSwept)
    }

    /**
     * The load-bearing assertion: the byte total is whatever the content resolver says, even
     * when that disagrees with the size the app had cached from its own earlier query.
     */
    @Test
    fun `the reported byte total comes from the resolver and never from the cached photo`() = runTest {
        val markDao = FakePendingMarkDao()
        val statDao = FakeSweepStatDao()
        val trash = FakeTrash()
        markAll(markDao, listOf(1L, 2L))
        val vm = viewModel(markDao, statDao, trash)
        advanceUntilIdle()

        val cachedTotal = vm.state.value.totalBytes
        assertEquals(4_812_345L + 1_004_999L, cachedTotal)

        vm.beginTrashRequest()
        // MediaStore reports different sizes than the app cached — the file changed, or the
        // earlier query was stale.
        trash.systemTrashes(1L to 3_000_000L, 2L to 500_000L)
        val outcome = vm.reconcileAfterDialog()

        assertEquals(3_500_000L, outcome.confirmedBytes)
        assertTrue("the app reported its own cached total", outcome.confirmedBytes != cachedTotal)
        assertEquals(3_500_000L, statDao.rows.single().bytesSwept)
    }

    @Test
    fun `a photo already in the trash before the request is not counted as ours`() = runTest {
        val markDao = FakePendingMarkDao()
        val statDao = FakeSweepStatDao()
        val trash = FakeTrash()
        markAll(markDao, listOf(1L, 2L))
        // Trashed from Google Photos five minutes ago, before the app asked for anything.
        trash.alreadyInTrash(1L, 4_812_345L)

        val vm = viewModel(markDao, statDao, trash)
        advanceUntilIdle()

        vm.beginTrashRequest()
        trash.systemTrashes(2L to 1_004_999L)
        val outcome = vm.reconcileAfterDialog()

        assertEquals("bytes somebody else freed were claimed", 1_004_999L, outcome.confirmedBytes)
        assertEquals(1, outcome.confirmedCount)
    }

    @Test
    fun `a full success clears every mark and records one batch`() = runTest {
        val markDao = FakePendingMarkDao()
        val statDao = FakeSweepStatDao()
        val trash = FakeTrash()
        markAll(markDao, listOf(1L, 2L, 3L, 4L, 5L))
        val vm = viewModel(markDao, statDao, trash)
        advanceUntilIdle()

        vm.beginTrashRequest()
        trash.systemTrashes(*pilePhotos.map { it.id to it.sizeBytes }.toTypedArray())
        val outcome = vm.reconcileAfterDialog()

        assertEquals(5, outcome.confirmedCount)
        assertEquals(sizes.sum(), outcome.confirmedBytes)
        assertEquals(0, markDao.marksForPile(pileId).size)
        assertEquals(0, vm.state.value.count)
        assertEquals(1, statDao.rows.size)
        assertEquals(sizes.sum(), statDao.lifetimeBytes())
        assertEquals(5, statDao.lifetimePhotos())
    }

    @Test
    fun `lifetime totals accumulate across batches and keep the earliest timestamp`() = runTest {
        val statDao = FakeSweepStatDao()
        val trash = FakeTrash()

        val markDao = FakePendingMarkDao()
        markAll(markDao, listOf(1L))
        val first = ConfirmViewModel(
            pileId, "Screenshots", pilePhotos, 0, markDao, statDao, trash, now = { 1_700_000_000_000L },
        )
        advanceUntilIdle()
        first.beginTrashRequest()
        trash.systemTrashes(1L to 4_812_345L)
        first.reconcileAfterDialog()

        markAll(markDao, listOf(2L))
        val second = ConfirmViewModel(
            pileId, "Screenshots", pilePhotos, 0, markDao, statDao, trash, now = { 1_800_000_000_000L },
        )
        advanceUntilIdle()
        second.beginTrashRequest()
        trash.systemTrashes(2L to 1_004_999L)
        second.reconcileAfterDialog()

        assertEquals(2, statDao.batchCount())
        assertEquals(4_812_345L + 1_004_999L, statDao.lifetimeBytes())
        assertEquals(2, statDao.lifetimePhotos())
        // "since <month>" must read from the earliest row, never the latest and never a constant.
        assertEquals(1_700_000_000_000L, statDao.firstSweepAtMs())
    }
}

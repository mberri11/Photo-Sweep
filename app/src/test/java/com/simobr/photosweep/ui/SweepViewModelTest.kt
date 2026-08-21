package com.simobr.photosweep.ui

import com.simobr.photosweep.data.media.Photo
import com.simobr.photosweep.ui.sweep.SweepDirection
import com.simobr.photosweep.ui.sweep.SweepViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class SweepViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val pileId = "screenshots"

    private fun photos(count: Int) = List(count) { index ->
        Photo(
            id = index.toLong() + 1,
            displayName = "img_$index.jpg",
            sizeBytes = 1_000_000L + index,
            bucketDisplayName = "Screenshots",
            relativePath = "DCIM/Screenshots/",
            width = 1080,
            height = 2400,
            isFavorite = false,
            mimeType = "image/jpeg",
            rawDateTakenMs = 1_756_000_000_000L + index * 1_000L,
            rawDateAddedSec = null,
            rawDateModifiedSec = null,
        )
    }

    private fun viewModel(
        photoList: List<Photo>,
        dao: FakePendingMarkDao = FakePendingMarkDao(),
        clock: () -> Long = { 1_756_000_000_000L },
    ) = SweepViewModel(
        pileId = pileId,
        pileTitle = "Screenshots",
        photos = photoList,
        dao = dao,
        now = clock,
    )

    // ---- the counter invariant -----------------------------------------------------------

    /**
     * `sweptCount + keptCount == currentIndex`, after every one of a thousand random actions.
     *
     * The mockup shows 34/218 with 62 swept and 157 kept, which cannot be true of anything.
     * Illustrative numbers are fine in a mockup and fatal in a counter: a user who sees the
     * tallies disagree with the index has no way to know which of the three to believe, and
     * the app is asking them to trust it with their photos.
     */
    @Test
    fun `the counter invariant holds after a thousand random actions`() = runTest {
        val all = photos(200)
        val vm = viewModel(all)
        advanceUntilIdle()

        val random = Random(seed = 20260821)
        var sweeps = 0
        var keeps = 0
        var undos = 0

        repeat(1_000) { step ->
            when (random.nextInt(3)) {
                0 -> { vm.sweep(); sweeps++ }
                1 -> { vm.keep(); keeps++ }
                else -> { vm.undo(); undos++ }
            }

            val state = vm.state.value
            assertEquals(
                "invariant broken at step $step",
                state.currentIndex,
                state.sweptCount + state.keptCount,
            )
            assertTrue("index went negative at step $step", state.currentIndex >= 0)
            assertTrue(
                "index ran past the pile at step $step",
                state.currentIndex <= state.total,
            )
        }

        // Prove the run actually exercised all three actions rather than no-opping.
        assertTrue(sweeps > 250 && keeps > 250 && undos > 250)
    }

    @Test
    fun `the invariant holds when the pile is swept to exhaustion`() = runTest {
        val vm = viewModel(photos(5))
        advanceUntilIdle()

        repeat(20) { vm.sweep() }

        val state = vm.state.value
        assertEquals(5, state.currentIndex)
        assertEquals(5, state.sweptCount)
        assertEquals(0, state.keptCount)
        assertEquals(state.currentIndex, state.sweptCount + state.keptCount)
        assertTrue(state.isFinished)
        assertNull(state.current)
    }

    @Test
    fun `swept bytes only count swept photos`() = runTest {
        val all = photos(4)
        val vm = viewModel(all)
        advanceUntilIdle()

        vm.sweep(); vm.keep(); vm.sweep()

        assertEquals(all[0].sizeBytes + all[2].sizeBytes, vm.state.value.sweptBytes)
    }

    // ---- undo -------------------------------------------------------------------------------

    @Test
    fun `undo pops from an arbitrary depth all the way to zero`() = runTest {
        val vm = viewModel(photos(120))
        advanceUntilIdle()

        repeat(97) { if (it % 3 == 0) vm.sweep() else vm.keep() }
        assertEquals(97, vm.state.value.currentIndex)

        repeat(97) { vm.undo() }

        val state = vm.state.value
        assertEquals(0, state.currentIndex)
        assertEquals(0, state.sweptCount)
        assertEquals(0, state.keptCount)
        assertFalse(state.canUndo)
        assertEquals(photos(120).first().id, state.current?.id)
    }

    @Test
    fun `the undo stack is unbounded`() = runTest {
        // Nothing is deleted until the confirm screen, so a step back costs a list entry.
        // A capped stack would be a limit invented for no reason.
        val vm = viewModel(photos(1_000))
        advanceUntilIdle()

        repeat(1_000) { vm.sweep() }
        assertEquals(1_000, vm.state.value.currentIndex)

        repeat(1_000) { vm.undo() }
        assertEquals(0, vm.state.value.currentIndex)
    }

    @Test
    fun `undo past the beginning does nothing`() = runTest {
        val vm = viewModel(photos(3))
        advanceUntilIdle()

        repeat(10) { vm.undo() }

        assertEquals(0, vm.state.value.currentIndex)
        assertEquals(0, vm.state.value.sweptCount + vm.state.value.keptCount)
    }

    @Test
    fun `undo restores the exact photo that was decided`() = runTest {
        val all = photos(6)
        val vm = viewModel(all)
        advanceUntilIdle()

        vm.sweep(); vm.sweep(); vm.keep()
        assertEquals(all[3].id, vm.state.value.current?.id)

        vm.undo()
        assertEquals(all[2].id, vm.state.value.current?.id)
        assertEquals(2, vm.state.value.sweptCount)
        assertEquals(0, vm.state.value.keptCount)
    }

    // ---- the toast ---------------------------------------------------------------------------

    @Test
    fun `a second swipe replaces the toast and issues a new token`() = runTest {
        val all = photos(4)
        val vm = viewModel(all)
        advanceUntilIdle()

        vm.sweep()
        val first = vm.state.value.lastAction
        assertNotNull(first)
        assertEquals(all[0].id, first?.photo?.id)

        vm.sweep()
        val second = vm.state.value.lastAction
        assertEquals(all[1].id, second?.photo?.id)
        // A changed token is what restarts the four-second countdown rather than letting the
        // second sweep inherit the remains of the first one's timer.
        assertTrue(second!!.token > first!!.token)
    }

    @Test
    fun `undo clears the toast`() = runTest {
        val vm = viewModel(photos(4))
        advanceUntilIdle()

        vm.sweep()
        assertNotNull(vm.state.value.lastAction)

        vm.undo()
        assertNull(vm.state.value.lastAction)
    }

    // ---- persistence -------------------------------------------------------------------------

    @Test
    fun `marks survive a simulated ViewModel recreation`() = runTest {
        val all = photos(40)
        val dao = FakePendingMarkDao()

        val first = viewModel(all, dao)
        advanceUntilIdle()
        // sweep, keep, sweep, keep … ending on a sweep so the resume point is exact.
        repeat(11) { if (it % 2 == 0) first.sweep() else first.keep() }
        first.flush()

        val expectedSwept = all.filterIndexed { index, _ -> index % 2 == 0 && index < 11 }.map { it.id }
        assertEquals(expectedSwept, dao.marksForPile("screenshots").map { it.mediaId })

        // Process death, then a fresh ViewModel over the same database.
        val second = viewModel(all, dao)
        advanceUntilIdle()

        val restored = second.state.value
        assertEquals(11, restored.currentIndex)
        assertEquals(6, restored.sweptCount)
        assertEquals(5, restored.keptCount)
        assertEquals(expectedSwept, restored.sweptIds)
        assertEquals(restored.currentIndex, restored.sweptCount + restored.keptCount)
        assertEquals(all[11].id, restored.current?.id)
    }

    @Test
    fun `an undo is persisted, not just hidden`() = runTest {
        val all = photos(10)
        val dao = FakePendingMarkDao()

        val first = viewModel(all, dao)
        advanceUntilIdle()
        first.sweep(); first.sweep(); first.sweep()
        first.undo()
        first.flush()

        assertEquals(2, dao.marksForPile("screenshots").size)

        val second = viewModel(all, dao)
        advanceUntilIdle()
        assertEquals(2, second.state.value.sweptCount)
        assertEquals(2, second.state.value.currentIndex)
    }

    /**
     * Documents the one thing restore cannot recover, so it is a known cost rather than a
     * surprise: keeps made after the final sweep are not persisted and come round again.
     */
    @Test
    fun `keeps after the last sweep are re-offered on restore`() = runTest {
        val all = photos(10)
        val dao = FakePendingMarkDao()

        val first = viewModel(all, dao)
        advanceUntilIdle()
        first.sweep()
        first.keep()
        first.keep()
        first.flush()
        assertEquals(3, first.state.value.currentIndex)

        val second = viewModel(all, dao)
        advanceUntilIdle()
        assertEquals(1, second.state.value.currentIndex)
        assertEquals(1, second.state.value.sweptCount)
        assertEquals(all[1].id, second.state.value.current?.id)
    }

    // ---- review again ------------------------------------------------------------------------

    @Test
    fun `review again rewinds to index zero with every mark intact`() = runTest {
        val all = photos(20)
        val dao = FakePendingMarkDao()
        val vm = viewModel(all, dao)
        advanceUntilIdle()

        // Sweep the even indices, keep the odd ones, all the way through.
        repeat(20) { if (it % 2 == 0) vm.sweep() else vm.keep() }
        vm.flush()
        val marksBefore = dao.marksForPile(pileId).map { it.mediaId }.sorted()
        assertEquals(10, marksBefore.size)

        vm.restartReview()
        vm.flush()

        // Back at the first card, counters reset because they describe this pass...
        assertEquals(0, vm.state.value.currentIndex)
        assertEquals(0, vm.state.value.sweptCount)
        assertEquals(0, vm.state.value.keptCount)
        assertEquals(all.first().id, vm.state.value.current?.id)
        // ...but not one mark was thrown away.
        assertEquals(marksBefore, dao.marksForPile(pileId).map { it.mediaId }.sorted())
    }

    @Test
    fun `re-deciding during a review overwrites that photo's mark and leaves the rest`() = runTest {
        val all = photos(10)
        val dao = FakePendingMarkDao()
        val vm = viewModel(all, dao)
        advanceUntilIdle()

        repeat(10) { vm.sweep() }
        vm.flush()
        assertEquals(10, dao.marksForPile(pileId).size)

        vm.restartReview()
        // Change your mind about the first two only.
        vm.keep()
        vm.keep()
        vm.flush()

        val remaining = dao.marksForPile(pileId).map { it.mediaId }.sorted()
        assertEquals(8, remaining.size)
        assertEquals(all.drop(2).map { it.id }.sorted(), remaining)
    }

    @Test
    fun `the counter invariant survives a review restart`() = runTest {
        val vm = viewModel(photos(30))
        advanceUntilIdle()

        repeat(30) { vm.sweep() }
        vm.restartReview()
        repeat(12) { if (it % 3 == 0) vm.keep() else vm.sweep() }

        val state = vm.state.value
        assertEquals(state.currentIndex, state.sweptCount + state.keptCount)
        assertEquals(12, state.currentIndex)
    }

    @Test
    fun `writes are debounced so a fast run does not write once per swipe`() = runTest {
        val dao = FakePendingMarkDao()
        val vm = viewModel(photos(50), dao)
        advanceUntilIdle()

        repeat(20) {
            vm.sweep()
            advanceTimeBy(50)
        }
        assertEquals("no write should have landed mid-run", 0, dao.writeCount)

        advanceTimeBy(SweepViewModel.PERSIST_DEBOUNCE_MS + 50)
        assertEquals(1, dao.writeCount)
        assertEquals(20, dao.marksForPile("screenshots").size)
    }

    @Test
    fun `an empty pile is finished immediately and cannot be swiped`() = runTest {
        val vm = viewModel(emptyList())
        advanceUntilIdle()

        vm.sweep(); vm.keep()

        assertEquals(0, vm.state.value.currentIndex)
        assertTrue(vm.state.value.isFinished)
    }
}

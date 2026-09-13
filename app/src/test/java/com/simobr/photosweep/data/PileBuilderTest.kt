package com.simobr.photosweep.data

import com.simobr.photosweep.data.media.Photo
import com.simobr.photosweep.data.piles.Pile
import com.simobr.photosweep.data.piles.PileBuilder
import com.simobr.photosweep.data.piles.PileKind
import com.simobr.photosweep.data.piles.isOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.YearMonth
import java.time.ZoneId

/**
 * Pile assembly, and the safety floor.
 *
 * The favourite test is the important one. If a user starred a photo, this app must never
 * put it in front of them with a delete button under it — not by default, not behind a
 * setting, not ever. That is enforced in exactly one place, [PileBuilder], and asserted here
 * from several directions so that removing the filter cannot pass the suite.
 */
class PileBuilderTest {

    private val utc = ZoneId.of("UTC")

    private var nextId = 1L

    private fun photo(
        bucket: String? = "Camera",
        path: String? = "DCIM/Camera/",
        sizeBytes: Long = 1_000L,
        takenMs: Long? = ms(2026, 3, 15),
        favourite: Boolean = false,
    ) = Photo(
        id = nextId++,
        displayName = "img_$nextId.jpg",
        sizeBytes = sizeBytes,
        bucketDisplayName = bucket,
        relativePath = path,
        width = 100,
        height = 100,
        isFavorite = favourite,
        mimeType = "image/jpeg",
        rawDateTakenMs = takenMs,
        rawDateAddedSec = null,
        rawDateModifiedSec = null,
    )

    private fun ms(year: Int, month: Int, day: Int): Long =
        java.time.LocalDate.of(year, month, day)
            .atStartOfDay(utc).toInstant().toEpochMilli()

    private fun List<Pile>.allPhotos() = flatMap { it.photos }

    /** A fixed "today", so an age assertion does not depend on the day the suite is run. */
    private val now = ms(2026, 9, 12)

    private fun daysAgo(days: Long): Long = now - Duration.ofDays(days).toMillis()

    private fun screenshot(takenMs: Long, sizeBytes: Long = 1_000L, favourite: Boolean = false) =
        photo(
            bucket = "Screenshots",
            path = "DCIM/Screenshots/",
            sizeBytes = sizeBytes,
            takenMs = takenMs,
            favourite = favourite,
        )

    // ---- the safety floor ---------------------------------------------------------------

    @Test
    fun `a favourite never appears in any pile`() {
        val starred = photo(favourite = true)
        val piles = PileBuilder.build(listOf(starred, photo(), photo()), utc)

        assertTrue(
            "a favourited photo was offered for sweeping",
            piles.allPhotos().none { it.id == starred.id },
        )
        assertTrue(piles.allPhotos().none { it.isFavorite })
    }

    @Test
    fun `favourites are excluded from every category not just month piles`() {
        val input = listOf(
            photo(bucket = "Screenshots", path = "DCIM/Screenshots/", favourite = true),
            photo(bucket = "WhatsApp Images", path = "Pictures/WhatsApp/Media/WhatsApp Images/", favourite = true),
            photo(bucket = "Download", path = "Download/", favourite = true),
            photo(bucket = "Camera", path = "DCIM/Camera/", favourite = true),
            photo(bucket = "لقطات الشاشة", path = "Pictures/لقطات الشاشة/", favourite = true),
        )
        val piles = PileBuilder.build(input, utc)
        assertEquals(emptyList<Pile>(), piles)
    }

    @Test
    fun `an all-favourites gallery produces no piles at all`() {
        val input = List(50) { photo(favourite = true) }
        assertEquals(0, PileBuilder.build(input, utc).size)
    }

    @Test
    fun `favourite bytes are not counted towards pile totals`() {
        val piles = PileBuilder.build(
            listOf(
                photo(sizeBytes = 100L),
                photo(sizeBytes = 999_999L, favourite = true),
            ),
            utc,
        )
        assertEquals(1, piles.size)
        assertEquals(100L, piles.single().totalBytes)
    }

    // ---- grouping --------------------------------------------------------------------------

    @Test
    fun `folder rules take photos out of month piles`() {
        val piles = PileBuilder.build(
            listOf(
                photo(bucket = "Screenshots", path = "DCIM/Screenshots/"),
                photo(bucket = "WhatsApp Images", path = "Pictures/WhatsApp/Media/WhatsApp Images/"),
                photo(bucket = "Download", path = "Download/"),
                photo(bucket = "Camera", path = "DCIM/Camera/"),
            ),
            utc,
        )
        val kinds = piles.map { it.kind }.toSet()
        assertTrue(PileKind.Screenshots in kinds)
        assertTrue(PileKind.WhatsApp in kinds)
        assertTrue(PileKind.Downloads in kinds)
        assertTrue(kinds.any { it is PileKind.Month })
        assertEquals(4, piles.size)
    }

    @Test
    fun `leftovers group by year and month of captured time`() {
        val piles = PileBuilder.build(
            listOf(
                photo(takenMs = ms(2026, 3, 1)),
                photo(takenMs = ms(2026, 3, 31)),
                photo(takenMs = ms(2026, 4, 1)),
                photo(takenMs = ms(2025, 3, 1)),
            ),
            utc,
        )
        val months = piles.map { it.kind }.filterIsInstance<PileKind.Month>().map { it.yearMonth }
        assertEquals(
            setOf(YearMonth.of(2026, 3), YearMonth.of(2026, 4), YearMonth.of(2025, 3)),
            months.toSet(),
        )
        val march2026 = piles.single { it.kind == PileKind.Month(YearMonth.of(2026, 3)) }
        assertEquals(2, march2026.count)
    }

    @Test
    fun `month grouping follows the supplied zone not the machine's`() {
        // 2026-04-01T00:30 UTC is still 31 March in New York.
        val instant = java.time.ZonedDateTime.of(2026, 4, 1, 0, 30, 0, 0, utc)
            .toInstant().toEpochMilli()
        val inUtc = PileBuilder.build(listOf(photo(takenMs = instant)), utc)
        val inNy = PileBuilder.build(listOf(photo(takenMs = instant)), ZoneId.of("America/New_York"))

        assertEquals(PileKind.Month(YearMonth.of(2026, 4)), inUtc.single().kind)
        assertEquals(PileKind.Month(YearMonth.of(2026, 3)), inNy.single().kind)
    }

    // ---- ordering ------------------------------------------------------------------------

    @Test
    fun `piles are ordered by total bytes descending`() {
        val piles = PileBuilder.build(
            listOf(
                photo(bucket = "Screenshots", path = "DCIM/Screenshots/", sizeBytes = 10L),
                photo(bucket = "Download", path = "Download/", sizeBytes = 5_000L),
                photo(bucket = "WhatsApp Images", path = "Pictures/WhatsApp/Media/WhatsApp Images/", sizeBytes = 900L),
            ),
            utc,
        )
        assertEquals(listOf(5_000L, 900L, 10L), piles.map { it.totalBytes })
        assertEquals(PileKind.Downloads, piles.first().kind)
    }

    @Test
    fun `totalBytes is the sum of the pile's photos`() {
        val piles = PileBuilder.build(
            List(4) { photo(sizeBytes = 250L) },
            utc,
        )
        assertEquals(1_000L, piles.single().totalBytes)
        assertEquals(4, piles.single().count)
    }

    @Test
    fun `photos inside a pile are newest first`() {
        val old = photo(takenMs = ms(2026, 3, 1))
        val new = photo(takenMs = ms(2026, 3, 28))
        val middle = photo(takenMs = ms(2026, 3, 14))
        val pile = PileBuilder.build(listOf(old, new, middle), utc).single()
        assertEquals(listOf(new.id, middle.id, old.id), pile.photos.map { it.id })
    }

    @Test
    fun `ordering is stable across identical input`() {
        val input = List(20) {
            photo(sizeBytes = 100L, takenMs = ms(2026, (it % 12) + 1, 5))
        }
        val first = PileBuilder.build(input, utc).map { it.kind }
        val second = PileBuilder.build(input.shuffled(), utc).map { it.kind }
        assertEquals(first, second)
    }

    @Test
    fun `an empty gallery produces no piles`() {
        assertEquals(emptyList<Pile>(), PileBuilder.build(emptyList(), utc))
    }

    // ---- overlay piles ---------------------------------------------------------------------

    @Test
    fun `BigFiles offers the largest hundred, or everything if there are fewer`() {
        val fourHundred = List(400) { photo(sizeBytes = (it + 1) * 1_000L) }
        val capped = PileBuilder.build(fourHundred, utc, nowMs = now)
            .single { it.kind == PileKind.BigFiles }

        assertEquals(100, capped.count)
        // The largest hundred specifically, not an arbitrary hundred.
        assertEquals(400_000L, capped.photos.first().sizeBytes)
        assertEquals(301_000L, capped.photos.last().sizeBytes)

        val forty = List(40) { photo(sizeBytes = (it + 1) * 1_000L) }
        val uncapped = PileBuilder.build(forty, utc, nowMs = now)
            .single { it.kind == PileKind.BigFiles }

        assertEquals(40, uncapped.count)
    }

    @Test
    fun `OldShots takes a screenshot at 181 days and leaves one at 179`() {
        val fresh = PileBuilder.build(List(5) { screenshot(daysAgo(179)) }, utc, nowMs = now)
        assertTrue(
            "a 179-day-old screenshot is not old yet",
            fresh.none { it.kind == PileKind.OldShots },
        )

        val stale = PileBuilder.build(List(5) { screenshot(daysAgo(181)) }, utc, nowMs = now)
        assertEquals(5, stale.single { it.kind == PileKind.OldShots }.count)
    }

    /**
     * The safety floor again, through the new doors. An overlay is a different query over the
     * same library, and a query is exactly the kind of thing that gets written without
     * remembering the filter the first one had.
     */
    @Test
    fun `a favourite appears in neither overlay pile`() {
        // Deliberately the largest and the oldest thing in the library, so a missing filter
        // puts it at the top of both overlays rather than somewhere in the middle.
        val starred = screenshot(daysAgo(400), sizeBytes = 9_999_999L, favourite = true)
        val rest = List(8) { screenshot(daysAgo(400)) }

        val piles = PileBuilder.build(rest + starred, utc, nowMs = now)
        val big = piles.single { it.kind == PileKind.BigFiles }
        val old = piles.single { it.kind == PileKind.OldShots }

        assertTrue("a favourite was offered in Biggest files", big.photos.none { it.id == starred.id })
        assertTrue("a favourite was offered in Old screenshots", old.photos.none { it.id == starred.id })
        assertEquals(8, big.count)
        assertEquals(8, old.count)
        assertTrue(piles.allPhotos().none { it.isFavorite })
    }

    /**
     * A photo swept in one pile must not come back unmarked in another. Overlays share their
     * members with the partitions, so without this the same photo is offered twice — the second
     * time as though the first decision had not happened.
     */
    @Test
    fun `a marked photo appears in no pile at all`() {
        val marked = screenshot(daysAgo(400), sizeBytes = 9_999_999L)
        val rest = List(8) { screenshot(daysAgo(400)) }

        val piles = PileBuilder.build(
            rest + marked,
            utc,
            markedMediaIds = setOf(marked.id),
            nowMs = now,
        )

        assertTrue(
            "a photo already marked for sweeping was offered again",
            piles.allPhotos().none { it.id == marked.id },
        )
        assertEquals(8, piles.single { it.kind == PileKind.BigFiles }.count)
        assertEquals(8, piles.single { it.kind == PileKind.OldShots }.count)
        assertEquals(8, piles.single { it.kind == PileKind.Screenshots }.count)
    }

    @Test
    fun `overlays come before the byte-sorted piles, BigFiles first`() {
        // The month pile is two orders of magnitude bigger than the screenshots, so byte order
        // alone would put it above both overlays.
        val camera = List(6) { photo(sizeBytes = 50_000_000L, takenMs = ms(2026, 8, 1)) }
        val shots = List(6) { screenshot(daysAgo(400)) }

        val kinds = PileBuilder.build(camera + shots, utc, nowMs = now).map { it.kind }

        assertEquals(PileKind.BigFiles, kinds[0])
        assertEquals(PileKind.OldShots, kinds[1])
        assertTrue("there should be partitions after the overlays", kinds.size > 2)
        assertTrue("an overlay escaped into the byte-sorted tail", kinds.drop(2).none { it.isOverlay })
    }

    @Test
    fun `an overlay of four photos is suppressed and one of five is not`() {
        val withFour = PileBuilder.build(List(4) { screenshot(daysAgo(400)) }, utc, nowMs = now)
        assertTrue(
            "a four-photo overlay is noise rather than a shortcut",
            withFour.none { it.kind.isOverlay },
        )
        // The partition is still offered — the minimum applies only to the lenses.
        assertEquals(4, withFour.single { it.kind == PileKind.Screenshots }.count)

        val withFive = PileBuilder.build(List(5) { screenshot(daysAgo(400)) }, utc, nowMs = now)
        assertEquals(
            listOf(PileKind.BigFiles, PileKind.OldShots),
            withFive.map { it.kind }.filter { it.isOverlay },
        )
    }
}

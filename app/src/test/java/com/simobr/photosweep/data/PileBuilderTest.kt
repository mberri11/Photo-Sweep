package com.simobr.photosweep.data

import com.simobr.photosweep.data.media.Photo
import com.simobr.photosweep.data.piles.Pile
import com.simobr.photosweep.data.piles.PileBuilder
import com.simobr.photosweep.data.piles.PileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
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
}

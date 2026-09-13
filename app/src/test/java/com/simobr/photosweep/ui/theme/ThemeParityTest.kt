package com.simobr.photosweep.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One palette, one font family.
 *
 * The app used to carry a second palette whose only job was to fill `MaterialTheme`, and it
 * disagreed with the frozen tokens while doing it. It is gone, and this test is what stops it
 * coming back — in either of the two shapes the bug actually takes:
 *
 *  1. **A colour Material chose.** Any `darkColorScheme` slot left unset keeps Material's
 *     baseline, which includes a purple and a pink. Nothing in the app paints `tertiary`
 *     today, so nobody would notice until the first component that does.
 *  2. **A font Material chose.** Every default `Typography` style is Roboto, so one `Text`
 *     without an explicit `style=` renders Roboto next to Manrope.
 *
 * Both sweeps are done by reflection over the whole type rather than over a list written out
 * here. A hand-written list is a list that goes stale the next time Material adds a slot, and
 * a slot added later is exactly the slot nobody remembers to map.
 */
class ThemeParityTest {

    /** The nine frozen colours, by ARGB. Pinned literally in `TokensResolutionTest`. */
    private val palette: Map<Long, String> = mapOf(
        PsColor.Midnight.argb() to "Midnight",
        PsColor.Panel.argb() to "Panel",
        PsColor.Frame.argb() to "Frame",
        PsColor.Keep.argb() to "Keep",
        PsColor.Sweep.argb() to "Sweep",
        PsColor.Gold.argb() to "Gold",
        PsColor.GoldHot.argb() to "GoldHot",
        PsColor.Steel.argb() to "Steel",
        PsColor.SteelDim.argb() to "SteelDim",
    )

    // ---- colour ----------------------------------------------------------------------------

    /**
     * Every slot in the scheme, not only the ones the app currently paints.
     *
     * `Color` is an inline value class, so its property getters compile to mangled
     * `long`-returning methods — `getPrimary-0d7_KjU()`. That mangling is what identifies a
     * colour slot here, and it is also why the scheme is read back through reflection rather
     * than through a list of property references.
     */
    @Test
    fun `every colour in the scheme is one of the nine PsColor values`() {
        val slots = colourSlots()
        assertTrue("reflection found no colour slots on ColorScheme", slots.size >= 29)

        val offenders = slots
            .filterValues { it !in palette }
            .map { (slot, argb) -> "$slot = #%08X".format(argb) }
            .sorted()

        assertEquals(
            "these scheme slots are not PsColor values — an unset slot keeps Material's " +
                "baseline, and the first component to use one paints it",
            emptyList<String>(),
            offenders,
        )
    }

    /**
     * Eight of the nine tokens reach the scheme, and the ninth is kept out on purpose.
     *
     * [PsColor.GoldHot] is the "+1" rise — it fires on a sweep and means "that just
     * happened". A Material component sitting in it at rest spends that signal, so `tertiary`
     * doubles [PsColor.Gold] and GoldHot is reachable only from `SweepScreen`'s `PlusOne`.
     * GoldHot *was* on the `tertiary*` slots; this is what stops it drifting back there.
     */
    @Test
    fun `the scheme uses eight of the nine tokens and reserves GoldHot`() {
        val used = colourSlots().values.toSet()

        assertTrue(
            "GoldHot is in the Material scheme; it is the +1 animation colour and belongs " +
                "to no component at rest",
            PsColor.GoldHot.argb() !in used,
        )

        val unreachable = palette.keys
            .filterNot { it == PsColor.GoldHot.argb() }
            .filterNot { it in used }
            .map { palette.getValue(it) }
            .toSet()

        assertEquals(
            "every frozen colour except GoldHot should be reachable through the scheme",
            emptySet<String>(),
            unreachable,
        )
    }

    /** The assignments the stage specified, restated rather than derived. */
    @Test
    fun `the named Material roles resolve to the specified tokens`() {
        val s = PhotoSweepColorScheme
        assertEquals(PsColor.Gold, s.primary)
        assertEquals(PsColor.Midnight, s.onPrimary)
        assertEquals(PsColor.Keep, s.secondary)
        assertEquals(PsColor.Midnight, s.onSecondary)
        // Doubles primary. Deliberately not GoldHot — see the reservation test above.
        assertEquals(PsColor.Gold, s.tertiary)
        assertEquals(PsColor.Midnight, s.onTertiary)
        assertEquals(PsColor.Sweep, s.error)
        assertEquals(PsColor.Midnight, s.onError)
        assertEquals(PsColor.Midnight, s.background)
        assertEquals(PsColor.Frame, s.onBackground)
        assertEquals(PsColor.Panel, s.surface)
        assertEquals(PsColor.Frame, s.onSurface)
        assertEquals(PsColor.Panel, s.surfaceVariant)
        assertEquals(PsColor.Steel, s.onSurfaceVariant)
        assertEquals(PsColor.SteelDim, s.outline)
        assertEquals(PsColor.SteelDim, s.outlineVariant)
        assertEquals(PsColor.Midnight, s.scrim)
    }

    /**
     * The deleted palette's two disagreements with the frozen tokens, pinned as absences so
     * the old values cannot drift back in through a merge.
     */
    @Test
    fun `the deleted palette's values are absent from the scheme`() {
        val present = colourSlots().values.toSet()
        assertTrue("PsSurface #101828 is back in the scheme", 0xFF101828L !in present)
        assertTrue("PsOutline #26344A is back in the scheme", 0xFF26344AL !in present)
        assertTrue("pure black is back in the scheme", 0xFF000000L !in present)
    }

    // ---- type ------------------------------------------------------------------------------

    @Test
    fun `every Typography slot is Manrope`() {
        val slots = typographySlots()
        assertTrue("reflection found no TextStyle slots on Typography", slots.size >= 15)

        val offenders = slots
            .filterValues { it.fontFamily !== Manrope }
            .map { (slot, style) -> "$slot -> ${style.fontFamily}" }
            .sorted()

        assertEquals(
            "these Typography slots are not Manrope; a Text with no style= renders Roboto",
            emptyList<String>(),
            offenders,
        )
    }

    /** The three the stage named, by identity rather than by equality. */
    @Test
    fun `titleLarge bodyLarge and labelLarge are the Manrope family instance`() {
        assertSame(Manrope, PhotoSweepTypography.titleLarge.fontFamily)
        assertSame(Manrope, PhotoSweepTypography.bodyLarge.fontFamily)
        assertSame(Manrope, PhotoSweepTypography.labelLarge.fontFamily)
    }

    /**
     * The mapping itself: headings take the screen-title metrics, body takes body, labels take
     * the button label. Asserted against [PsType] so that moving a token moves the floor with
     * it instead of leaving the two to disagree silently.
     */
    @Test
    fun `each Typography group takes its PsType metrics`() {
        val t = PhotoSweepTypography

        val headings = listOf(
            "displayLarge" to t.displayLarge, "displayMedium" to t.displayMedium,
            "displaySmall" to t.displaySmall,
            "headlineLarge" to t.headlineLarge, "headlineMedium" to t.headlineMedium,
            "headlineSmall" to t.headlineSmall,
            "titleLarge" to t.titleLarge, "titleMedium" to t.titleMedium,
            "titleSmall" to t.titleSmall,
        )
        headings.forEach { (name, style) -> assertMetrics(name, PsType.screenTitle, style) }

        listOf("bodyLarge" to t.bodyLarge, "bodyMedium" to t.bodyMedium, "bodySmall" to t.bodySmall)
            .forEach { (name, style) -> assertMetrics(name, PsType.body, style) }

        listOf(
            "labelLarge" to t.labelLarge, "labelMedium" to t.labelMedium,
            "labelSmall" to t.labelSmall,
        ).forEach { (name, style) -> assertMetrics(name, PsType.buttonLabel, style) }
    }

    /**
     * `display*` must not pick up tabular figures. [PsType.bigNumber] is the obvious-looking
     * home for the largest tier and the wrong one: `tnum` belongs to numbers that change on
     * screen, not to arbitrary display text.
     */
    @Test
    fun `no Typography slot requests font features`() {
        val offenders = typographySlots()
            .filterValues { it.fontFeatureSettings != null }
            .map { (slot, style) -> "$slot -> ${style.fontFeatureSettings}" }
            .sorted()

        assertEquals(emptyList<String>(), offenders)
    }

    // ---- plumbing --------------------------------------------------------------------------

    private fun Color.argb(): Long = toArgb().toLong() and 0xFFFFFFFFL

    private fun assertMetrics(slot: String, expected: TextStyle, actual: TextStyle) {
        assertEquals("$slot fontSize", expected.fontSize, actual.fontSize)
        assertEquals("$slot fontWeight", expected.fontWeight, actual.fontWeight)
        assertEquals("$slot letterSpacing", expected.letterSpacing, actual.letterSpacing)
        assertEquals("$slot lineHeight", expected.lineHeight, actual.lineHeight)
    }

    /** Slot name → ARGB, for every `Color` property on [ColorScheme]. */
    private fun colourSlots(): Map<String, Long> =
        ColorScheme::class.java.methods
            .filter {
                it.parameterCount == 0 &&
                    it.returnType == java.lang.Long.TYPE &&
                    it.name.startsWith("get") &&
                    // The value-class mangling suffix. A plain `long` getter is not a colour.
                    it.name.contains('-')
            }
            .associate { method ->
                val name = method.name.removePrefix("get").substringBefore('-')
                    .replaceFirstChar { it.lowercase() }
                val raw = method.invoke(PhotoSweepColorScheme) as Long
                name to Color(raw.toULong()).argb()
            }

    /** Slot name → style, for every `TextStyle` property on [Typography]. */
    private fun typographySlots(): Map<String, TextStyle> =
        Typography::class.java.methods
            .filter {
                it.parameterCount == 0 &&
                    it.returnType == TextStyle::class.java &&
                    it.name.startsWith("get")
            }
            .associate { method ->
                val name = method.name.removePrefix("get").replaceFirstChar { it.lowercase() }
                name to method.invoke(PhotoSweepTypography) as TextStyle
            }
}

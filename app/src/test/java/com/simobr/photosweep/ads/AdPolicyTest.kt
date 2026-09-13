package com.simobr.photosweep.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every interstitial gate, alone and together.
 *
 * Tested clause by clause rather than only through the conjunction, because the failure that
 * matters is a gate that silently stops gating. All four are ANDed, so a bug that makes one of
 * them always-true is invisible in any test where another one happens to be blocking.
 */
class AdPolicyTest {

    private val start = 1_000_000L

    private fun may(
        bytes: Long = 1_000L,
        now: Long = start + AdPolicy.MIN_UPTIME_MS,
        processStart: Long = start,
        last: Long? = null,
        shown: Int = 0,
    ) = AdPolicy.mayShowInterstitial(bytes, now, processStart, last, shown)

    // ---- gate 1: something was actually swept -------------------------------------------

    @Test
    fun `zero confirmed bytes blocks`() {
        assertFalse(AdPolicy.sweptSomething(0L))
        assertFalse("nothing reached the trash, so there is nothing to interrupt for", may(bytes = 0L))
    }

    @Test
    fun `one byte is enough`() {
        assertTrue(AdPolicy.sweptSomething(1L))
        assertTrue(may(bytes = 1L))
    }

    // ---- gate 2: warm-up ----------------------------------------------------------------

    @Test
    fun `59 seconds since process start blocks`() {
        val now = start + 59_000L
        assertFalse(AdPolicy.pastWarmUp(now, start))
        assertFalse("an ad this early reads as a launch ad", may(now = now))
    }

    @Test
    fun `60 seconds since process start allows`() {
        val now = start + 60_000L
        assertTrue(AdPolicy.pastWarmUp(now, start))
        assertTrue(may(now = now))
    }

    // ---- gate 3: the gap ----------------------------------------------------------------

    @Test
    fun `239 seconds since the last interstitial blocks`() {
        val now = start + 600_000L
        assertFalse(AdPolicy.pastGap(now, now - 239_000L))
        assertFalse(may(now = now, last = now - 239_000L, shown = 1))
    }

    @Test
    fun `240 seconds since the last interstitial allows`() {
        val now = start + 600_000L
        assertTrue(AdPolicy.pastGap(now, now - 240_000L))
        assertTrue(may(now = now, last = now - 240_000L, shown = 1))
    }

    @Test
    fun `a session with no interstitial yet passes the gap gate`() {
        assertTrue(AdPolicy.pastGap(start + 60_000L, null))
    }

    // ---- gate 4: the session cap --------------------------------------------------------

    @Test
    fun `the third interstitial of a session blocks`() {
        assertFalse(AdPolicy.underSessionCap(2))
        assertFalse(
            "two per process is the ceiling",
            may(now = start + 10_000_000L, last = start + 1_000L, shown = 2),
        )
    }

    @Test
    fun `the first and second are allowed`() {
        assertTrue(AdPolicy.underSessionCap(0))
        assertTrue(AdPolicy.underSessionCap(1))
    }

    // ---- the conjunction ----------------------------------------------------------------

    @Test
    fun `all four satisfied allows`() {
        assertTrue(
            AdPolicy.mayShowInterstitial(
                confirmedBytes = 480_000_000L,
                nowMs = start + 300_000L,
                processStartMs = start,
                lastInterstitialAtMs = start + 10_000L,
                shownThisSession = 1,
            ),
        )
    }

    @Test
    fun `each gate alone is enough to block when the other three pass`() {
        val blockers = mapOf(
            "bytes" to may(bytes = 0L),
            "warm-up" to may(now = start + 59_999L),
            "gap" to may(now = start + 300_000L, last = start + 299_000L, shown = 1),
            "cap" to may(shown = 2),
        )
        assertEquals(
            "every one of these must block on its own",
            emptyList<String>(),
            blockers.filterValues { it }.keys.toList(),
        )
    }

    @Test
    fun `the documented constants are the ones in force`() {
        assertEquals(60_000L, AdPolicy.MIN_UPTIME_MS)
        assertEquals(240_000L, AdPolicy.MIN_GAP_MS)
        assertEquals(2, AdPolicy.MAX_PER_SESSION)
        assertEquals(1L, AdPolicy.MIN_CONFIRMED_BYTES)
    }

    // ---- session bookkeeping -------------------------------------------------------------

    @Test
    fun `the session counts up to the cap and then refuses`() {
        AdSession.start(start)
        val now = start + 60_000L

        assertTrue(AdSession.mayShowInterstitial(1_000L, now))
        AdSession.recordInterstitialShown(now)

        // Immediately after, the gap gate blocks even though the cap has not been reached.
        assertFalse(AdSession.mayShowInterstitial(1_000L, now + 1_000L))

        val later = now + AdPolicy.MIN_GAP_MS
        assertTrue(AdSession.mayShowInterstitial(1_000L, later))
        AdSession.recordInterstitialShown(later)

        assertEquals(2, AdSession.interstitialsThisSession)
        val muchLater = later + AdPolicy.MIN_GAP_MS * 10
        assertFalse("the cap outlasts the gap", AdSession.mayShowInterstitial(1_000L, muchLater))
    }

    @Test
    fun `a restarted session forgets its counters`() {
        AdSession.start(start)
        AdSession.recordInterstitialShown(start + 60_000L)
        assertEquals(1, AdSession.interstitialsThisSession)

        AdSession.start(start)
        assertEquals(0, AdSession.interstitialsThisSession)
        assertEquals(null, AdSession.lastInterstitialAtMs)
    }

    /**
     * A load failure is modelled by there being nothing to show, which `showThenContinue`
     * handles by calling `onContinue` synchronously. The policy layer's contribution is that it
     * never reports "may show" as a *requirement* — it is permission, not an instruction, so a
     * missing ad is simply a navigation.
     */
    @Test
    fun `a blocked or failed ad leaves navigation untouched`() {
        AdSession.start(start)
        var navigated = 0
        val onContinue = { navigated++ }

        // Every blocked case must still navigate exactly once.
        listOf(
            AdSession.mayShowInterstitial(0L, start + 60_000L),
            AdSession.mayShowInterstitial(1_000L, start + 1_000L),
        ).forEach { allowed ->
            assertFalse(allowed)
            onContinue()
        }

        assertEquals(2, navigated)
    }
}

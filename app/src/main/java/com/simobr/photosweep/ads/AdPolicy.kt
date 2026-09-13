package com.simobr.photosweep.ads

/**
 * When an interstitial is allowed. Pure, so every clause can be asserted on its own.
 *
 * The policy is deliberately mean. An interstitial in a storage cleaner is interrupting someone
 * who has just done a chore, and the four gates each block a specific way of being obnoxious:
 *
 *  1. **[minConfirmedBytes]** — nothing was actually swept, so there is nothing to celebrate
 *     and no reason to interrupt. A user who cancelled the system dialog gets no ad.
 *  2. **[MIN_UPTIME_MS]** — a cold start followed immediately by a full-screen ad reads as a
 *     launch ad, which is the thing that gets apps uninstalled on day one.
 *  3. **[MIN_GAP_MS]** — four minutes between interruptions, so sweeping three piles in a row
 *     is not three ads in five minutes.
 *  4. **[MAX_PER_SESSION]** — two, ever, per process. After that the session is the user's.
 *
 * Every gate is an AND. None of them is a frequency *target*; they are all ceilings.
 */
object AdPolicy {

    /** Strictly more than zero bytes must have reached the trash. */
    const val MIN_CONFIRMED_BYTES = 1L

    /** One minute of uptime, so an interstitial can never be mistaken for an app-open ad. */
    const val MIN_UPTIME_MS = 60_000L

    /** Four minutes between interstitials. */
    const val MIN_GAP_MS = 240_000L

    /** Two per process, and no more. */
    const val MAX_PER_SESSION = 2

    /**
     * @param confirmedBytes bytes MediaStore confirmed it trashed, never the requested figure.
     * @param nowMs current wall clock.
     * @param processStartMs when this process started.
     * @param lastInterstitialAtMs when the last interstitial was shown, or null if none has been.
     * @param shownThisSession how many have been shown in this process.
     */
    fun mayShowInterstitial(
        confirmedBytes: Long,
        nowMs: Long,
        processStartMs: Long,
        lastInterstitialAtMs: Long?,
        shownThisSession: Int,
    ): Boolean =
        sweptSomething(confirmedBytes) &&
            pastWarmUp(nowMs, processStartMs) &&
            pastGap(nowMs, lastInterstitialAtMs) &&
            underSessionCap(shownThisSession)

    /** Gate 1, on its own. */
    fun sweptSomething(confirmedBytes: Long): Boolean = confirmedBytes >= MIN_CONFIRMED_BYTES

    /** Gate 2, on its own. */
    fun pastWarmUp(nowMs: Long, processStartMs: Long): Boolean =
        nowMs - processStartMs >= MIN_UPTIME_MS

    /** Gate 3, on its own. A session with no interstitial yet passes. */
    fun pastGap(nowMs: Long, lastInterstitialAtMs: Long?): Boolean =
        lastInterstitialAtMs == null || nowMs - lastInterstitialAtMs >= MIN_GAP_MS

    /** Gate 4, on its own. */
    fun underSessionCap(shownThisSession: Int): Boolean = shownThisSession < MAX_PER_SESSION
}

package com.simobr.photosweep.ads

/**
 * Interstitial bookkeeping for the life of the process, and nowhere else.
 *
 * Not a ViewModel: the cap is per *process*, and a ViewModel dies with its screen, so sweeping
 * two piles would reset the count. Not DataStore either, and that is the more interesting
 * choice — persisting the count across launches would let the app remember "I already showed
 * two" after a restart, which sounds stricter but means a user who relaunches the app tomorrow
 * might get no ads at all until some reset scheme fires. A process is the honest unit: it is
 * one sitting with the app.
 *
 * Mutable global state, deliberately, with the decision itself kept out in [AdPolicy] so the
 * rules can be tested without touching any of this.
 */
object AdSession {

    /** Set once from `PhotoSweepApplication.onCreate`. */
    @Volatile
    var processStartMs: Long = 0L
        private set

    @Volatile
    var lastInterstitialAtMs: Long? = null
        private set

    @Volatile
    var interstitialsThisSession: Int = 0
        private set

    fun start(nowMs: Long) {
        processStartMs = nowMs
        lastInterstitialAtMs = null
        interstitialsThisSession = 0
    }

    fun recordInterstitialShown(nowMs: Long) {
        lastInterstitialAtMs = nowMs
        interstitialsThisSession++
    }

    /** [AdPolicy.mayShowInterstitial] against this session's counters. */
    fun mayShowInterstitial(confirmedBytes: Long, nowMs: Long = System.currentTimeMillis()): Boolean =
        AdPolicy.mayShowInterstitial(
            confirmedBytes = confirmedBytes,
            nowMs = nowMs,
            processStartMs = processStartMs,
            lastInterstitialAtMs = lastInterstitialAtMs,
            shownThisSession = interstitialsThisSession,
        )
}

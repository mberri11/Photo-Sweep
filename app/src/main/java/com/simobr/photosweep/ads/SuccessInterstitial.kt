package com.simobr.photosweep.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.simobr.photosweep.BuildConfig

/**
 * The interstitial shown when the user *leaves* the success screen.
 *
 * Two properties are load-bearing:
 *
 *  1. **It never delays navigation.** [showThenContinue] either has an ad in hand already or it
 *     calls `onContinue` synchronously. There is no waiting, no spinner, and no "just a moment"
 *     — a failed load, a slow network, an un-initialised SDK and a blocked policy gate are all
 *     indistinguishable to the user, because all four navigate immediately. That is why the ad
 *     is preloaded when the screen *composes* rather than requested when the user taps.
 *  2. **It is never shown before the number has been read.** The trigger is leaving, not
 *     arriving. The whole point of the success screen is the figure in the middle of it, and an
 *     ad on the way in would cover the one thing the user came for.
 */
object SuccessInterstitial {

    @Volatile
    private var loaded: InterstitialAd? = null

    @Volatile
    private var loading = false

    /**
     * Starts a load if one is not already in flight or in hand.
     *
     * Called when the success screen composes, so by the time the user has read the figure and
     * reached for Back there is either an ad ready or there never will be.
     */
    fun preload(context: Context) {
        if (!ConsentGate.adsAllowed || loading || loaded != null) return
        loading = true

        InterstitialAd.load(
            context,
            BuildConfig.INTERSTITIAL_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loaded = ad
                    loading = false
                }

                // Silent on purpose. A failed ad load is not a user-facing event.
                override fun onAdFailedToLoad(error: LoadAdError) {
                    loaded = null
                    loading = false
                }
            },
        )
    }

    /**
     * Shows the ad if every gate in [AdPolicy] allows it and one is ready; otherwise continues
     * at once.
     *
     * `onContinue` is called exactly once on every path — on dismissal, on a show failure, and
     * synchronously when there is nothing to show. Navigation cannot be lost to an ad callback
     * that never fires.
     */
    fun showThenContinue(
        activity: Activity,
        confirmedBytes: Long,
        nowMs: Long = System.currentTimeMillis(),
        onContinue: () -> Unit,
    ) {
        val ad = loaded
        if (ad == null || !ConsentGate.adsAllowed ||
            !AdSession.mayShowInterstitial(confirmedBytes, nowMs)
        ) {
            onContinue()
            return
        }

        loaded = null
        var continued = false
        fun continueOnce() {
            if (!continued) {
                continued = true
                onContinue()
            }
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = continueOnce()

            /** A show failure is still a navigation. The user asked to leave. */
            override fun onAdFailedToShowFullScreenContent(error: AdError) = continueOnce()

            override fun onAdShowedFullScreenContent() {
                AdSession.recordInterstitialShown(nowMs)
            }
        }

        runCatching { ad.show(activity) }.onFailure { continueOnce() }
    }

    /** Debug-only, paired with `ConsentGate.resetForDebug`. */
    fun clearForDebug() {
        loaded = null
        loading = false
    }
}

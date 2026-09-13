package com.simobr.photosweep

import android.app.Application
import com.simobr.photosweep.ads.AdSession

/**
 * Process-wide entry point.
 *
 * It does one thing: stamp the moment the process started, because [AdSession] measures the
 * interstitial warm-up against it. That has to happen here — a timestamp taken when the first
 * screen composes would be wrong by however long the splash and the permission gate took, and
 * the whole point of the warm-up gate is that it measures from process start.
 *
 * The Mobile Ads SDK is **not** initialised here. It is initialised by `ConsentGate`, after the
 * UMP flow resolves and on `Dispatchers.IO` — initialising it in `onCreate` would do disk and
 * network work on the main thread before the first frame, and would do it before the user has
 * been asked about consent.
 */
class PhotoSweepApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AdSession.start(System.currentTimeMillis())
    }
}

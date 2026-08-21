package com.simobr.photosweep

import android.app.Application

/**
 * Process-wide entry point.
 *
 * Deliberately empty for now. This is where the Mobile Ads SDK and the UMP consent flow
 * will be initialised once there is a screen to show them on — never on a background
 * thread that could race the first frame.
 */
class PhotoSweepApplication : Application()

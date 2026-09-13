package com.simobr.photosweep.ads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.simobr.photosweep.BuildConfig

/**
 * The only banner in the app, and the only ad view of any kind outside the interstitial.
 *
 * It lives on the success screen, below "Back to piles", and nowhere else. The two screens it is
 * deliberately absent from:
 *
 *  - **Sweep.** A banner under the thumb during a fast horizontal drag is an accidental-click
 *    factory, and the screen runs on a rhythm that a fresh creative loading mid-swipe destroys.
 *  - **Trash.** Every action on that screen is destructive or nearly so. A misclick there costs
 *    somebody their photos, and no ad revenue is worth that trade.
 *
 * **The height is reserved whether or not an ad ever arrives.** The outer [Box] is given the
 * adaptive banner's height up front, so a creative that lands two seconds late drops into a gap
 * that was already there instead of shoving the figure the user is reading up the screen.
 */
@Composable
fun SuccessBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    val adsAllowed by rememberAdsAllowed()

    // Anchored adaptive: the height is a function of the width, so it can be reserved before
    // any request is made.
    val adSize = remember(widthDp) {
        AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
    }

    Box(modifier = modifier.fillMaxWidth().height(adSize.height.dp)) {
        if (adsAllowed) {
            AndroidView(
                factory = { ctx ->
                    AdView(ctx).apply {
                        setAdSize(adSize)
                        adUnitId = BuildConfig.BANNER_UNIT_ID
                        loadAd(AdRequest.Builder().build())
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

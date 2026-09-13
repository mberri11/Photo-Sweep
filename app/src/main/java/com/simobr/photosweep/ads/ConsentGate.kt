package com.simobr.photosweep.ads

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.activity.compose.LocalActivity
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * The UMP consent flow, and the only place `MobileAds.initialize` is called.
 *
 * Two rules shape all of this, and both are about the app being more important than the ads:
 *
 *  1. **Nothing here can block the app.** A consent failure, a network timeout, a user who
 *     dismisses the form, a UMP bug — every one of them lands on "ads do not load" and the
 *     gallery is reachable anyway. There is deliberately no code path in which an unhappy ad
 *     SDK makes a photo unreachable. Every SDK call is inside `runCatching`, the whole flow is
 *     inside [TIMEOUT_MS], and the failure value is simply `false`.
 *  2. **`MobileAds.initialize` never runs on the main thread.** It touches disk and the
 *     network; on main it stalls the first frame of whatever screen happens to be composing.
 *     It also never runs *before* consent resolves, which is the GDPR requirement rather than
 *     a preference.
 */
object ConsentGate {

    /**
     * Long enough for a real form fetch on a slow connection, short enough that a hung SDK
     * does not keep the banner slot empty for the whole session.
     */
    const val TIMEOUT_MS = 8_000L

    @Volatile
    private var started = false

    /** Whether ads may be requested. False until the flow resolves, and false on any failure. */
    @Volatile
    var adsAllowed: Boolean = false
        private set

    /**
     * Runs the flow once per process. Safe to call from every composition.
     *
     * Must be called from the main thread: UMP requires it. Only the Mobile Ads init is moved.
     */
    suspend fun ensureResolved(activity: Activity) {
        if (started) return
        started = true

        val allowed = withTimeoutOrNull(TIMEOUT_MS) { resolve(activity) } ?: false
        adsAllowed = allowed

        if (allowed) {
            withContext(Dispatchers.IO) {
                runCatching { MobileAds.initialize(activity.applicationContext) }
            }
        }
    }

    private suspend fun resolve(activity: Activity): Boolean {
        val info = runCatching { UserMessagingPlatform.getConsentInformation(activity) }
            .getOrNull() ?: return false

        // Both steps swallow their own failures. `canRequestAds` is still asked afterwards,
        // because a failed *update* does not necessarily mean consent is unknown — the SDK may
        // already hold a valid answer from a previous launch.
        runCatching { requestUpdate(activity, info) }
        runCatching { showFormIfRequired(activity) }

        return runCatching { info.canRequestAds() }.getOrDefault(false)
    }

    private suspend fun requestUpdate(activity: Activity, info: ConsentInformation) =
        suspendCancellableCoroutine { cont ->
            info.requestConsentInfoUpdate(
                activity,
                ConsentRequestParameters.Builder().build(),
                { if (cont.isActive) cont.resume(Unit) },
                { if (cont.isActive) cont.resume(Unit) },
            )
        }

    /**
     * Shows the form when UMP says one is required.
     *
     * A `formError` is resumed exactly like a success: the user dismissing the form, or the form
     * failing to load, both mean "carry on without consent", not "stop".
     */
    private suspend fun showFormIfRequired(activity: Activity) =
        suspendCancellableCoroutine { cont ->
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                if (cont.isActive) cont.resume(Unit)
            }
        }

    /**
     * Debug-only. Clears the stored consent so the form can be shown again without reinstalling.
     *
     * Release builds never call this; there is no user-facing control for it, because a user who
     * wants to change their choice does it through the privacy options form, not a hidden reset.
     */
    fun resetForDebug(context: Context) {
        runCatching { UserMessagingPlatform.getConsentInformation(context).reset() }
        started = false
        adsAllowed = false
    }
}

/**
 * Resolves consent once for the granted tree and reports whether ads may load.
 *
 * Returns a `State<Boolean>` rather than suspending the caller: the screen under it composes
 * immediately with `false`, and flips to `true` later if and only if consent allows it. Nothing
 * waits.
 */
@Composable
fun rememberAdsAllowed(): State<Boolean> {
    val activity = LocalActivity.current
    val allowed = remember { mutableStateOf(ConsentGate.adsAllowed) }

    LaunchedEffect(activity) {
        if (activity == null) return@LaunchedEffect
        ConsentGate.ensureResolved(activity)
        allowed.value = ConsentGate.adsAllowed
    }

    return allowed
}

package com.simobr.photosweep.ui.sweep

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simobr.photosweep.ui.settings.DataStoreHapticsPreference
import com.simobr.photosweep.ui.settings.HapticsPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Touch feedback for the two gestures.
 *
 * Uses `View.performHapticFeedback` rather than the Vibrator API on purpose: the vibrator
 * needs the `VIBRATE` permission, and adding a permission to this app's manifest to make a
 * card feel nice would be a bad trade. The view route needs none, and respects the user's
 * system haptics setting, which the vibrator route would override.
 *
 * [enabled] is the Settings → Sweeping toggle, read as a lambda rather than a value so the
 * switch takes effect on the next gesture instead of the next time this object is rebuilt.
 */
class SweepHaptics(
    private val view: View,
    private val scope: CoroutineScope,
    private val enabled: () -> Boolean = { true },
) {
    /** Sweep: a double tick. Two beats, because something was decided. */
    fun sweep() {
        if (!enabled()) return
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        scope.launch {
            delay(DOUBLE_TICK_GAP_MS)
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    /** Keep: a single tick. */
    fun keep() {
        if (!enabled()) return
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    private companion object {
        /** Short enough to read as one gesture, long enough to be two distinct taps. */
        const val DOUBLE_TICK_GAP_MS = 55L
    }
}

/**
 * Wires the haptics to the stored preference.
 *
 * `collectAsStateWithLifecycle` is given [HapticsPreference.DEFAULT] as its initial value, so
 * the first composition has an answer without waiting on disk — the sweep screen draws a card
 * before DataStore has opened its file, and a gesture made in that window ticks rather than
 * throwing.
 */
@Composable
fun rememberSweepHaptics(): SweepHaptics {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val preference = remember(context) { DataStoreHapticsPreference(context) }
    val enabled by preference.enabled
        .collectAsStateWithLifecycle(initialValue = HapticsPreference.DEFAULT)

    // `enabled` is read inside the lambda, so the live value is used at gesture time.
    return remember(view, scope) { SweepHaptics(view, scope) { enabled } }
}

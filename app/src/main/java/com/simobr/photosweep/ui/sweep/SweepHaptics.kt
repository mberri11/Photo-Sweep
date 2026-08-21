package com.simobr.photosweep.ui.sweep

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalView
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
 */
class SweepHaptics(
    private val view: View,
    private val scope: CoroutineScope,
) {
    /** Sweep: a double tick. Two beats, because something was decided. */
    fun sweep() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        scope.launch {
            delay(DOUBLE_TICK_GAP_MS)
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    /** Keep: a single tick. */
    fun keep() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    private companion object {
        /** Short enough to read as one gesture, long enough to be two distinct taps. */
        const val DOUBLE_TICK_GAP_MS = 55L
    }
}

@Composable
fun rememberSweepHaptics(): SweepHaptics {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    return remember(view, scope) { SweepHaptics(view, scope) }
}

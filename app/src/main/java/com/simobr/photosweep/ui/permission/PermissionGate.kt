package com.simobr.photosweep.ui.permission

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.simobr.photosweep.data.permission.MediaAccess
import com.simobr.photosweep.data.permission.MediaPermission

/**
 * Decides which of the three permission states the user is in, and shows the matching screen.
 *
 * State is re-read on every resume rather than cached, because the user can change the grant
 * from Settings while the app is in the background — and after being sent to Settings by the
 * partial-access screen, they usually do.
 */
@Composable
fun PermissionGate(
    grantedContent: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    var access by remember { mutableStateOf(MediaPermission.current(context)) }

    // Set the first time the system dialog is shown. Combined with
    // shouldShowRequestPermissionRationale it distinguishes "not asked yet" from "asked and
    // permanently refused", which are the same denial to checkSelfPermission but need
    // different buttons: one leads to a prompt, the other has to lead to Settings.
    var hasRequested by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // The contract reports only the permission that was asked for. On API 34+ a "Select
        // photos" tap reports false while granting partial access, so the state has to be
        // re-read rather than inferred from this boolean.
        access = MediaPermission.current(context)
    }

    LifecycleResumeEffect(Unit) {
        access = MediaPermission.current(context)
        onPauseOrDispose { }
    }

    val canPrompt = !hasRequested ||
        activity?.shouldShowRequestPermissionRationale(MediaPermission.requiredPermission) == true

    when (access) {
        MediaAccess.Full -> grantedContent()

        MediaAccess.PartialSelectionOnly -> PartialAccessScreen(
            visibleCount = rememberVisiblePhotoCount(),
            onOpenSettings = { MediaPermission.openAppSettings(context) },
        )

        MediaAccess.None -> PermissionRationaleScreen(
            canPrompt = canPrompt,
            onAllow = {
                if (canPrompt) {
                    hasRequested = true
                    launcher.launch(MediaPermission.requiredPermission)
                } else {
                    MediaPermission.openAppSettings(context)
                }
            },
            // There is nothing else in the app to fall back to: without the permission every
            // screen would be empty. Closing is the honest response to "not now".
            onNotNow = { activity?.finish() },
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

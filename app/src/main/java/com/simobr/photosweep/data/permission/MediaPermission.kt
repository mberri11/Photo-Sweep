package com.simobr.photosweep.data.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/** How much of the gallery the app can currently see. */
enum class MediaAccess {

    /** Every photo. The only state in which piles mean anything. */
    Full,

    /**
     * Android 14+ partial access: the user picked specific photos, so MediaStore returns a
     * handful of rows and nothing else exists as far as this app is concerned.
     */
    PartialSelectionOnly,

    /** Nothing. */
    None,
}

/**
 * Resolves what the app is allowed to read, and nothing else.
 *
 * The interesting case is [MediaAccess.PartialSelectionOnly]. On Android 14+ the permission
 * dialog offers "Select photos", and a user who taps it hands a gallery *cleaner* a view of
 * twelve pictures. Every pile would be tiny, the duplicate finder would find nothing, and the
 * app would look like it simply did not work — with no clue as to why. Reporting that state
 * plainly is the only honest option, so it gets its own value here and its own screen.
 *
 * Note that `READ_MEDIA_VISUAL_USER_SELECTED` is not declared in the manifest. It does not
 * need to be: on API 34+ the platform adds it implicitly to any app requesting
 * READ_MEDIA_IMAGES, which is confirmed by `dumpsys package` on a device. Checking a
 * permission the app never declared is legal and returns DENIED where the platform did not
 * add it, so the API 30-33 path below is unaffected.
 */
object MediaPermission {

    /**
     * The permission to request from the user.
     *
     * READ_MEDIA_IMAGES does not exist before API 33 and is inert if requested there, so
     * Android 11 and 12 ask for the old storage permission instead.
     */
    val requiredPermission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            @Suppress("DEPRECATION")
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    /** Reads the live grant state off the platform. */
    fun current(context: Context): MediaAccess = resolve(
        sdkInt = Build.VERSION.SDK_INT,
        fullAccessGranted = context.isGranted(requiredPermission),
        userSelectedGranted = context.isGranted(USER_SELECTED_PERMISSION),
    )

    /**
     * The decision itself, with no Android types in sight so it can be tested exhaustively.
     *
     * @param sdkInt the running API level.
     * @param fullAccessGranted whether [requiredPermission] is granted.
     * @param userSelectedGranted whether `READ_MEDIA_VISUAL_USER_SELECTED` is granted.
     */
    internal fun resolve(
        sdkInt: Int,
        fullAccessGranted: Boolean,
        userSelectedGranted: Boolean,
    ): MediaAccess = when {
        // Full access wins wherever it is present. On API 34+ the platform grants the
        // user-selected permission alongside a full grant, so this order matters: checking
        // partial first would report every fully-permitted user as partial.
        fullAccessGranted -> MediaAccess.Full

        // Partial access only exists from API 34. Below it, the user-selected permission is
        // not a platform concept and must never be allowed to produce a partial verdict.
        sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && userSelectedGranted ->
            MediaAccess.PartialSelectionOnly

        else -> MediaAccess.None
    }

    /**
     * Opens this app's own settings page, where the media permission can be changed.
     *
     * There is no public intent that lands directly on the permission list, so this is the
     * closest reachable target — one tap from "Permissions".
     */
    fun openAppSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
    }

    /**
     * Not declared in the manifest on purpose — the platform adds it implicitly on API 34+.
     * Referenced by string so the constant does not have to exist on older API levels.
     */
    internal const val USER_SELECTED_PERMISSION =
        "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

    private fun Context.isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

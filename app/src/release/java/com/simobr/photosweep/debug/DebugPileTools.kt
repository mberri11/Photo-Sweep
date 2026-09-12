package com.simobr.photosweep.debug

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.simobr.photosweep.data.media.Photo

/**
 * The release build's half of the pile-host development tools: nothing.
 *
 * The debug twin in `src/debug/` seeds a synthetic gallery and filters the pile query down to
 * it. Neither belongs in a shipped build, and neither is present in one — not stripped by
 * R8, not gated behind a flag, simply not compiled. There is no seeder here to call and no
 * scope flag to read, so [scopePhotos] is the identity function and [Chips] draws nothing.
 *
 * `DebugIsolationTest` asserts this file stays empty of both.
 */
object DebugPileTools {

    /** The release build sweeps the whole gallery. There is no other scope. */
    fun scopePhotos(photos: List<Photo>): List<Photo> = photos

    /** No development chrome ships. */
    @Composable
    @Suppress("UNUSED_PARAMETER")
    fun Chips(
        busy: String?,
        onBusy: (String?) -> Unit,
        onGalleryChanged: () -> Unit,
        onScopeChanged: () -> Unit,
        modifier: Modifier = Modifier,
    ) = Unit
}

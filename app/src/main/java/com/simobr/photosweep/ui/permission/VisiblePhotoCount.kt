package com.simobr.photosweep.ui.permission

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.simobr.photosweep.data.media.MediaRepository

/**
 * How many photos MediaStore will actually hand over right now.
 *
 * Under partial access this is the number the user needs to see — "12 photos" is what makes
 * the situation legible, where "limited access" alone does not.
 *
 * Returns null while the query is in flight.
 */
@Composable
internal fun rememberVisiblePhotoCount(): Int? {
    val context = LocalContext.current
    val count: Int? by produceState<Int?>(initialValue = null, context) {
        value = MediaRepository(context.contentResolver).queryPhotos().size
    }
    return count
}

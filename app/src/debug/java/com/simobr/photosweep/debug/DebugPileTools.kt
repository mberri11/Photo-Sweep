package com.simobr.photosweep.debug

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.simobr.photosweep.R
import com.simobr.photosweep.data.media.Photo
import com.simobr.photosweep.ui.theme.PsColor
import com.simobr.photosweep.ui.theme.PsType
import kotlinx.coroutines.launch

/**
 * The debug build's half of the pile-host development tools: seed, scope, wipe.
 *
 * This file has a no-op twin in `src/release/`. The split is the point — the release variant
 * has no seeder, no scope flag, and no strings for either, so there is nothing to strip and
 * nothing to accidentally ship. `DebugIsolationTest` holds the line.
 */
object DebugPileTools {

    /**
     * Debug default: only the seeded test gallery. Without it the piles are the tester's own
     * 6,000 photos, and a session spent exercising the sweep gesture leaves marks all over
     * their real camera roll. Nothing is deleted at this stage, but "nothing is deleted yet"
     * is a poor reason to make someone's own library the test fixture.
     *
     * Held on the object rather than in composition because [scopePhotos] is called from the
     * host's loading effect, which is keyed on a scope token the chip bumps.
     */
    private var testGalleryOnly: Boolean = true

    /** Narrows the gallery to the seeded test tree while the scope chip says so. */
    fun scopePhotos(photos: List<Photo>): List<Photo> = if (testGalleryOnly) {
        photos.filter { it.relativePath.orEmpty().startsWith(GallerySeeder.ROOT_RELATIVE_PATH) }
    } else {
        photos
    }

    /**
     * The chip row.
     *
     * It scrolls horizontally. Three chips do not fit the width of a phone, and a plain Row
     * resolves that by squeezing the last one to a single column of stacked letters rather
     * than by overflowing — so the row is given somewhere to overflow *to*, and every label
     * is pinned to one unwrapped line. The labels are not shortened: this is debug chrome,
     * and a chip that says "Wipe" is a chip somebody will one day mistake for something else.
     */
    @Composable
    fun Chips(
        busy: String?,
        onBusy: (String?) -> Unit,
        onGalleryChanged: () -> Unit,
        onScopeChanged: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var scopeIsTestGallery by remember { mutableStateOf(testGalleryOnly) }

        Row(
            modifier = modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DebugAction(
                label = stringResource(R.string.piles_seed),
                enabled = busy == null,
            ) {
                scope.launch {
                    onBusy("0 / 500")
                    runCatching {
                        GallerySeeder.seed(context) { written, total -> onBusy("$written / $total") }
                    }.onFailure { onBusy("seed failed: ${it.message}") }
                        .onSuccess { onBusy(null); onGalleryChanged() }
                }
            }
            DebugAction(
                label = if (scopeIsTestGallery) {
                    stringResource(R.string.piles_scope_test)
                } else {
                    stringResource(R.string.piles_scope_all)
                },
                enabled = busy == null,
            ) {
                scopeIsTestGallery = !scopeIsTestGallery
                testGalleryOnly = scopeIsTestGallery
                onScopeChanged()
            }
            DebugAction(
                label = stringResource(R.string.piles_wipe),
                enabled = busy == null,
                danger = true,
            ) {
                scope.launch {
                    onBusy("wiping…")
                    runCatching { GallerySeeder.dangerouslyWipeTestGallery(context) }
                        .onFailure { onBusy("wipe failed: ${it.message}") }
                        .onSuccess { onBusy(null); onGalleryChanged() }
                }
            }
        }
    }
}

@Composable
private fun DebugAction(
    label: String,
    enabled: Boolean,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (danger) PsColor.Sweep else PsColor.Gold
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, tint.copy(alpha = if (enabled) 0.6f else 0.2f), RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = PsType.pileMeta,
            color = tint.copy(alpha = if (enabled) 1f else 0.4f),
            maxLines = 1,
            softWrap = false,
        )
    }
}

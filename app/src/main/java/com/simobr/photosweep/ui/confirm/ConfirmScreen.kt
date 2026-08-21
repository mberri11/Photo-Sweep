package com.simobr.photosweep.ui.confirm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.simobr.photosweep.R
import com.simobr.photosweep.data.media.Photo
import com.simobr.photosweep.data.media.contentUri
import com.simobr.photosweep.ui.format.PhotoFormat
import com.simobr.photosweep.ui.theme.PsColor
import com.simobr.photosweep.ui.theme.PsDim
import com.simobr.photosweep.ui.theme.PsType

object ConfirmTags {
    const val GRID = "confirm_grid"
    const val TOTAL = "confirm_total"
    const val ACTION = "confirm_action"
    const val REVIEW = "confirm_review"
}

/** Condemned thumbnails sit at 34%: visible enough to catch a mistake, dim enough to read as gone. */
private const val CONDEMNED_ALPHA = 0.34f
private const val GRID_COLUMNS = 5
private const val THUMB_PX = 192

/**
 * Screen 08 — the one place the app destroys anything, and the only solid Sweep-coral button
 * in it. Coral is filled here and nowhere else, so the colour means exactly one thing.
 *
 * The grid scrolls and shows every condemned photo. The mockup's "+38" overflow tile works
 * for sixty-two marks and is useless for three hundred: a user cannot audit a decision they
 * are not shown, and this is the screen where auditing matters.
 */
@Composable
fun ConfirmScreen(
    state: ConfirmUiState,
    onUnmark: (Long) -> Unit,
    onConfirm: () -> Unit,
    onReviewAgain: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PsColor.Midnight)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = PsDim.screenPadH),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "‹",
                style = PsType.screenTitle,
                color = PsColor.Steel,
                modifier = Modifier.clickable(onClick = onBack).padding(horizontal = 8.dp),
            )
            Text(
                text = stringResource(R.string.confirm_eyebrow),
                style = PsType.sectionCaption,
                color = PsColor.Steel,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(28.dp))
        }

        Column(modifier = Modifier.padding(horizontal = PsDim.screenPadH)) {
            Text(
                text = buildAnnotatedString {
                    append(stringResource(R.string.confirm_title_lead))
                    withStyle(SpanStyle(color = PsColor.Gold)) {
                        append(
                            pluralStringResource(
                                R.plurals.confirm_title_photos,
                                state.count,
                                state.count,
                            ),
                        )
                    }
                    append(stringResource(R.string.confirm_title_join))
                    withStyle(SpanStyle(color = PsColor.Gold)) {
                        append(PhotoFormat.bytes(state.totalBytes))
                    }
                },
                style = PsType.screenTitle,
                color = PsColor.Frame,
                modifier = Modifier.testTag(ConfirmTags.TOTAL),
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = stringResource(
                    R.string.confirm_sub,
                    state.pileTitle,
                    pluralStringResource(R.plurals.confirm_sub_kept, state.keptCount, state.keptCount),
                ),
                style = PsType.body,
                color = PsColor.Steel,
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.confirm_pull_back),
                style = PsType.photoMeta,
                color = PsColor.SteelDim,
            )

            Spacer(Modifier.height(14.dp))
        }

        if (state.condemned.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.confirm_empty),
                    style = PsType.body,
                    color = PsColor.Steel,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(GRID_COLUMNS),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag(ConfirmTags.GRID),
                contentPadding = PaddingValues(
                    horizontal = PsDim.screenPadH,
                    vertical = 4.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(state.condemned, key = { it.id }) { photo ->
                    CondemnedThumb(photo = photo, onClick = { onUnmark(photo.id) })
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = PsDim.screenPadH)) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.confirm_note),
                style = PsType.photoMeta,
                color = PsColor.SteelDim,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            // The only solid-fill coral in the app.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (state.count == 0) PsColor.SteelDim else PsColor.Sweep)
                    .clickable(enabled = state.count > 0, onClick = onConfirm)
                    .testTag(ConfirmTags.ACTION),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = pluralStringResource(R.plurals.confirm_action, state.count, state.count),
                    style = PsType.buttonLabel,
                    color = PsColor.Midnight,
                )
            }

            Spacer(Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, PsColor.SteelDim, RoundedCornerShape(18.dp))
                    .clickable(onClick = onReviewAgain)
                    .testTag(ConfirmTags.REVIEW),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.confirm_review),
                    style = PsType.buttonLabel,
                    color = PsColor.Frame,
                )
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun CondemnedThumb(photo: Photo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(photo.contentUri())
            .size(THUMB_PX, THUMB_PX)
            .build(),
        contentDescription = photo.displayName,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .aspectRatio(0.78f)
            .clip(shape)
            .background(PsColor.Panel)
            .alpha(CONDEMNED_ALPHA)
            .clickable(onClick = onClick),
    )
}
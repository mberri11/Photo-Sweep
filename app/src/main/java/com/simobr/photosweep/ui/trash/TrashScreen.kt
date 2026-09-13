package com.simobr.photosweep.ui.trash

import android.app.PendingIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.simobr.photosweep.R
import com.simobr.photosweep.data.media.MediaRepository
import com.simobr.photosweep.data.media.Photo
import com.simobr.photosweep.data.media.contentUri
import com.simobr.photosweep.data.trash.MediaStoreTrash
import com.simobr.photosweep.ui.format.PhotoFormat
import com.simobr.photosweep.ui.theme.PsColor
import com.simobr.photosweep.ui.theme.PsDim
import com.simobr.photosweep.ui.theme.PsType
import kotlinx.coroutines.launch

/** Tiles are small. A 128px decode is already more than three columns can show. */
private const val THUMB_PX = 128

private const val GRID_COLUMNS = 3
private val GridGap = 4.dp
private val TileShape = RoundedCornerShape(10.dp)

/** The selection rim. 2dp so it reads at tile size without an overlay dimming the photo. */
private val SelectionRim = 2.dp

/**
 * Screens 10 and 11 — the system trash, listed.
 *
 * Two things about this screen are deliberate and should not be softened later:
 *
 *  1. **Nothing here is ever behind an ad.** Restore and Delete are safety actions. A user who
 *     has just swept the wrong photo is the last person who should meet an interstitial, and an
 *     ad between a mistake and its undo is the kind of thing that earns an app a reputation.
 *  2. **Coral appears exactly once**, on Delete. It is the app's destructive colour and it
 *     means one thing. Restore is not coral, the header is not coral, and the selection rim is
 *     `Frame`, not coral — a selected photo is not a condemned one.
 *
 * Both actions send **one** IntentSender for the whole selection, so the user sees one system
 * dialog rather than one per photo.
 */
@Composable
fun TrashScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val media = remember(context) { MediaRepository(context.contentResolver) }
    val trash = remember(context) { MediaStoreTrash(context.contentResolver) }

    val viewModel: TrashViewModel = viewModel(factory = TrashViewModel.factory(media))
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The result code is deliberately ignored. What matters is what MediaStore says now.
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) {
        viewModel.reconcileAfterDialog()
    }

    fun send(request: () -> PendingIntent) {
        scope.launch {
            launcher.launch(IntentSenderRequest.Builder(request().intentSender).build())
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TrashHeader(
            count = state.photos.size,
            bytes = state.totalBytes,
            showRestoreAll = !state.hasSelection && state.photos.isNotEmpty(),
            onRestoreAll = { send { trash.buildRestoreRequest(state.allIds) } },
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoading -> Unit

                state.isEmpty -> EmptyTrash(
                    modifier = Modifier.padding(horizontal = PsDim.screenPadH),
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(GRID_COLUMNS),
                    horizontalArrangement = Arrangement.spacedBy(GridGap),
                    verticalArrangement = Arrangement.spacedBy(GridGap),
                    contentPadding = PaddingValues(
                        horizontal = PsDim.screenPadH,
                        vertical = 4.dp,
                    ),
                ) {
                    items(state.photos, key = { it.id }) { photo ->
                        TrashTile(
                            photo = photo,
                            selected = photo.id in state.selected,
                            onClick = { viewModel.toggle(photo.id) },
                        )
                    }
                }
            }
        }

        if (state.hasSelection) {
            SelectionBar(
                count = state.selectionCount,
                onRestore = { send { trash.buildRestoreRequest(state.selected.toList()) } },
                onDelete = { send { trash.dangerouslyBuildDeleteRequest(state.selected.toList()) } },
            )
        }
    }
}

@Composable
private fun TrashHeader(
    count: Int,
    bytes: Long,
    showRestoreAll: Boolean,
    onRestoreAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = PsDim.screenPadH)) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.trash_title),
                style = PsType.screenTitle,
                color = PsColor.Frame,
                modifier = Modifier.weight(1f),
            )
            if (showRestoreAll) {
                Text(
                    text = stringResource(R.string.trash_restore_all),
                    style = PsType.buttonLabel,
                    color = PsColor.Gold,
                    modifier = Modifier
                        .clickable(onClick = onRestoreAll)
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.trash_subtitle, count, PhotoFormat.bytes(bytes)),
            style = PsType.pileMeta,
            color = PsColor.Steel,
        )
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun TrashTile(
    photo: Photo,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(TileShape)
            .background(PsColor.Panel)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(photo.contentUri())
                .size(THUMB_PX, THUMB_PX)
                .build(),
            contentDescription = photo.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(TileShape),
        )
        if (selected) {
            // A rim, not a scrim: the user is choosing photos and needs to still see them.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(SelectionRim, PsColor.Frame, TileShape),
            )
        }
    }
}

@Composable
private fun EmptyTrash(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.trash_empty_title),
            style = PsType.pileName,
            color = PsColor.Frame,
        )
        Spacer(Modifier.height(6.dp))
        // The same sentence Settings shows. Whose trash this is, stated once.
        Text(
            text = stringResource(R.string.settings_trash_line),
            style = PsType.body,
            color = PsColor.Steel,
        )
    }
}

/**
 * Only present while something is selected.
 *
 * Delete carries the only coral fill on this screen. Restore sits next to it in `Frame` on
 * `Panel`: equally reachable, and visibly not the same kind of action.
 */
@Composable
private fun SelectionBar(
    count: Int,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().background(PsColor.Panel)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(PsColor.SteelDim))
        Column(modifier = Modifier.padding(horizontal = PsDim.screenPadH, vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.trash_selected, count),
                style = PsType.pileMeta,
                color = PsColor.Steel,
            )
            Spacer(Modifier.height(10.dp))
            Row {
                BarAction(
                    label = stringResource(R.string.trash_restore_selected, count),
                    fill = PsColor.Panel,
                    rim = PsColor.SteelDim,
                    labelColour = PsColor.Frame,
                    onClick = onRestore,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                BarAction(
                    label = stringResource(R.string.trash_delete_selected, count),
                    fill = PsColor.Sweep,
                    rim = PsColor.Sweep,
                    labelColour = PsColor.Midnight,
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BarAction(
    label: String,
    fill: Color,
    rim: Color,
    labelColour: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(fill)
            .border(1.dp, rim, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = PsType.buttonLabel, color = labelColour, maxLines = 1)
    }
}

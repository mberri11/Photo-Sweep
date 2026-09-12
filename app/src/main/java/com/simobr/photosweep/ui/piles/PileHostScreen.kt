package com.simobr.photosweep.ui.piles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.simobr.photosweep.BuildConfig
import com.simobr.photosweep.R
import com.simobr.photosweep.data.db.PhotoSweepDatabase
import com.simobr.photosweep.data.media.MediaRepository
import com.simobr.photosweep.data.piles.Pile
import com.simobr.photosweep.data.piles.PileBuilder
import com.simobr.photosweep.debug.DebugPileTools
import com.simobr.photosweep.ui.format.PhotoFormat
import com.simobr.photosweep.ui.theme.PsColor
import com.simobr.photosweep.ui.theme.PsDim
import com.simobr.photosweep.ui.theme.PsType

/**
 * Temporary host for the sweep loop.
 *
 * This is scaffolding, not the designed home screen — it exists so the sweep gesture can be
 * driven on a real device against real piles before the home screen is built. The debug
 * seeding controls are the point: they are how the destructive stages get exercised without
 * pointing them at anybody's actual camera roll. They live in `DebugPileTools`, which has a
 * do-nothing twin in `src/release/` — this screen calls the same two entry points in both
 * variants and the release build compiles neither the seeder nor the scope flag.
 */
@Composable
fun PileHostScreen(
    refreshKey: Int = 0,
    onOpenPile: (Pile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var piles by remember { mutableStateOf<List<Pile>?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf<String?>(null) }

    // Bumped by the debug scope chip so the pile query re-runs. In a release build nothing
    // ever bumps it, because there is no chip and no scope to change.
    var scopeKey by remember { mutableIntStateOf(0) }
    var lifetime by remember { mutableStateOf<Triple<Long, Int, Long?>?>(null) }

    LaunchedEffect(refreshKey) {
        val stats = PhotoSweepDatabase.get(context).sweepStatDao()
        lifetime = Triple(stats.lifetimeBytes(), stats.lifetimePhotos(), stats.firstSweepAtMs())
    }

    LaunchedEffect(reloadKey, refreshKey, scopeKey) {
        piles = null
        val photos = MediaRepository(context.contentResolver).queryPhotos()
        // Identity in a release build: `DebugPileTools` there has no seeder and no flag.
        piles = PileBuilder.build(DebugPileTools.scopePhotos(photos))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PsColor.Midnight)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = PsDim.screenPadH),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.piles_title),
            style = PsType.screenTitle,
            color = PsColor.Frame,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = busy ?: piles?.let { list ->
                stringResource(
                    R.string.piles_subtitle,
                    list.sumOf { it.count },
                    PhotoFormat.bytes(list.sumOf { it.totalBytes }),
                )
            } ?: stringResource(R.string.gallery_counting),
            style = PsType.pileMeta,
            color = PsColor.Steel,
        )
        LifetimeStat(lifetime)

        Spacer(Modifier.height(16.dp))

        if (BuildConfig.DEBUG) {
            DebugPileTools.Chips(
                busy = busy,
                onBusy = { busy = it },
                onGalleryChanged = { reloadKey++ },
                onScopeChanged = { scopeKey++ },
            )
            Spacer(Modifier.height(16.dp))
        }

        val current = piles
        when {
            current == null -> Unit
            current.isEmpty() -> Text(
                text = stringResource(R.string.piles_empty),
                style = PsType.body,
                color = PsColor.Steel,
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(current, key = { it.kind.id }) { pile ->
                    PileRow(pile = pile, onClick = { onOpenPile(pile) })
                }
            }
        }
    }
}

@Composable
private fun PileRow(pile: Pile, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(PsDim.pileRowHeight)
            .clip(RoundedCornerShape(16.dp))
            .background(PsColor.Panel)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = pileTitle(pile.kind), style = PsType.pileName, color = PsColor.Frame)
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(
                R.string.piles_pile_line,
                pile.count,
                PhotoFormat.bytes(pile.totalBytes),
            ),
            style = PsType.pileMeta,
            color = PsColor.Steel,
        )
    }
}

/**
 * Screen 03's lifetime figure.
 *
 * Labelled SWEPT rather than SPACE FREED, with the caveat spelled out underneath. Trashed
 * photos hold their storage for thirty days, and a counter that claims otherwise is a
 * counter the user can disprove in two taps of the Settings app.
 */
@Composable
private fun LifetimeStat(lifetime: Triple<Long, Int, Long?>?, modifier: Modifier = Modifier) {
    if (lifetime == null) return
    val (bytes, photos, firstAt) = lifetime
    if (firstAt == null) return

    Column(modifier = modifier.padding(top = 14.dp)) {
        Text(
            text = stringResource(R.string.home_stat_caption),
            style = PsType.sectionCaption,
            color = PsColor.Steel,
        )
        Spacer(Modifier.height(4.dp))
        Text(text = PhotoFormat.bytes(bytes), style = PsType.screenTitle, color = PsColor.Gold)
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.home_stat_note),
            style = PsType.photoMeta,
            color = PsColor.SteelDim,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(
                R.string.success_lifetime,
                String.format(java.util.Locale.getDefault(), "%,d", photos),
                java.time.format.DateTimeFormatter
                    .ofPattern("LLLL yyyy", java.util.Locale.getDefault())
                    .format(java.time.Instant.ofEpochMilli(firstAt).atZone(java.time.ZoneId.systemDefault())),
            ),
            style = PsType.photoMeta,
            color = PsColor.Steel,
        )
    }
}

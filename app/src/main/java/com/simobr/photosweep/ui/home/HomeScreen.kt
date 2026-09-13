package com.simobr.photosweep.ui.home

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.simobr.photosweep.BuildConfig
import com.simobr.photosweep.R
import com.simobr.photosweep.data.db.PhotoSweepDatabase
import com.simobr.photosweep.data.media.MediaRepository
import com.simobr.photosweep.data.media.Photo
import com.simobr.photosweep.data.media.contentUri
import com.simobr.photosweep.data.piles.Pile
import com.simobr.photosweep.data.piles.PileBuilder
import com.simobr.photosweep.data.piles.isOverlay
import com.simobr.photosweep.debug.DebugPileTools
import com.simobr.photosweep.ui.format.PhotoFormat
import com.simobr.photosweep.ui.piles.pileTitle
import com.simobr.photosweep.ui.settings.LicencesScreen
import com.simobr.photosweep.ui.settings.SettingsScreen
import com.simobr.photosweep.ui.trash.TrashScreen
import com.simobr.photosweep.ui.theme.PsColor
import com.simobr.photosweep.ui.theme.PsDim
import com.simobr.photosweep.ui.theme.PsType

/** Thumbnails are decoded at 128px. A 92dp fan has no use for a 4032px bitmap. */
private const val THUMB_PX = 128

/** One thumbnail in the fan. */
private val ThumbSize = 56.dp

/** How far each card in the fan is pushed past the one in front of it. */
private val FanStep = 18.dp

/**
 * Fixed fan width: one thumbnail plus two steps.
 *
 * Fixed, and not derived from how many thumbnails were actually drawn. A pile holding one photo
 * would otherwise lay its row out 36dp narrower than its neighbours, and the whole list would
 * step in and out as it scrolled.
 */
private val FanWidth = ThumbSize + FanStep * 2

/** Depth 0 is the front card. Deeper cards tilt further and sit further back. */
private data class FanDepth(val rotation: Float, val alpha: Float)

private val FanDepths = listOf(
    FanDepth(rotation = 0f, alpha = 1f),
    FanDepth(rotation = 5f, alpha = 0.75f),
    FanDepth(rotation = -6f, alpha = 0.55f),
)

private val RowShape = RoundedCornerShape(16.dp)
private val ThumbShape = RoundedCornerShape(8.dp)

/**
 * The wide layout starts where `WindowWidthSizeClass` leaves `Compact`, which is **600dp** —
 * the platform's own landscape-phone / small-tablet boundary. Recorded here because SCREENS.md
 * pins the number and the code reads it from the size class rather than restating it.
 */
private const val WIDE_BREAKPOINT_DP = 600

/** The rail's width in the wide layout. Matches the bar's height, so the two read as one bar. */
private val RailWidth = 88.dp

/** Skeleton rows shown while MediaStore is being read. */
private const val SKELETON_ROWS = 4
private const val SHIMMER_MS = 1_100
private const val SHIMMER_STAGGER_MS = 120

/** The three destinations. There is no fourth, and no overflow menu. */
private enum class HomeTab { Piles, Trash, Settings }

/**
 * The home screen, and the shell the other two destinations live in.
 *
 * It replaces `PileHostScreen`, which was scaffolding: a title, a debug row and a list, with a
 * blank screen while the gallery loaded and no way to reach anything else. The three things
 * that changed are all about the first ten seconds of the app — a loading state that is not a
 * black rectangle, rows that show the photos rather than describing them, and navigation that
 * exists.
 *
 * Tab state is held here rather than in `PhotoSweepRoot`: Piles, Trash and Settings are
 * siblings under one bar, and the sweep and confirm screens take over the whole window instead
 * of appearing inside it. Lifting them into the route graph would mean a bottom bar that has to
 * be hidden by every other screen.
 */
@Composable
fun HomeScreen(
    refreshKey: Int = 0,
    onOpenPile: (Pile) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.Piles) }

    // Licences is a leaf of Settings, not a fourth tab. System back pops it.
    var showLicences by rememberSaveable { mutableStateOf(false) }

    /**
     * The one size-class decision in the app.
     *
     * `Compact` is a phone in portrait. Anything wider — a phone turned sideways, or a tablet
     * at sw600dp — gets the rail and the two-column list, which is the same branch for both
     * because it is the same problem: a 64dp bar across the bottom of a short, wide window
     * wastes the scarce axis, and one column of 92dp rows across 800dp wastes the plentiful one.
     */
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    val wide = calculateWindowSizeClass(LocalActivity.current!!)
        .widthSizeClass != WindowWidthSizeClass.Compact

    // Back from a tab that is not Piles returns to Piles. The licences handler is composed
    // deeper, so it is offered the gesture first and this one only sees what it leaves.
    BackHandler(enabled = tab != HomeTab.Piles) { tab = HomeTab.Piles }

    // One content tree, placed by either shell. Neither branch knows what is inside it.
    val content: @Composable () -> Unit = {
        when (tab) {
            HomeTab.Piles -> PilesTab(
                refreshKey = refreshKey,
                onOpenPile = onOpenPile,
                columns = if (wide) 2 else 1,
            )

            HomeTab.Trash -> InsetTab { TrashScreen() }

            HomeTab.Settings -> InsetTab {
                if (showLicences) {
                    BackHandler { showLicences = false }
                    LicencesScreen()
                } else {
                    SettingsScreen(
                        onOpenTrash = { tab = HomeTab.Trash },
                        onOpenLicences = { showLicences = true },
                    )
                }
            }
        }
    }

    // One set of destinations, laid out along whichever axis the shell runs.
    val navItems: @Composable (Modifier) -> Unit = { itemModifier ->
        NavItem(HomeTab.Piles, R.string.nav_piles, tab, { tab = it }, itemModifier)
        NavItem(HomeTab.Trash, R.string.nav_trash, tab, { tab = it }, itemModifier)
        NavItem(HomeTab.Settings, R.string.nav_settings, tab, { tab = it }, itemModifier)
    }

    Box(modifier = modifier.fillMaxSize().background(PsColor.Midnight)) {
        if (wide) {
            Row(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .background(PsColor.Panel)
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing
                                .only(WindowInsetsSides.Start + WindowInsetsSides.Vertical),
                        ),
                ) {
                    Column(
                        modifier = Modifier.fillMaxHeight().width(RailWidth),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        navItems(Modifier.fillMaxWidth().height(PsDim.bottomNavHeight))
                    }
                    Box(Modifier.fillMaxHeight().width(1.dp).background(PsColor.SteelDim))
                }
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) { content() }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) { content() }
                Column(modifier = Modifier.fillMaxWidth().background(PsColor.Panel)) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(PsColor.SteelDim))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .height(PsDim.bottomNavHeight),
                    ) {
                        navItems(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// ---- piles -------------------------------------------------------------------------------

@Composable
private fun PilesTab(
    refreshKey: Int,
    onOpenPile: (Pile) -> Unit,
    columns: Int,
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
        val db = PhotoSweepDatabase.get(context)
        val photos = MediaRepository(context.contentResolver).queryPhotos()
        val marked = db.pendingMarkDao().markedMediaIds().toSet()
        // Identity in a release build: `DebugPileTools` there has no seeder and no flag.
        piles = PileBuilder.build(
            photos = DebugPileTools.scopePhotos(photos),
            markedMediaIds = marked,
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeDrawing
                    .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
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
            text = busy ?: piles?.let { list -> librarySummary(list) }
                ?: stringResource(R.string.gallery_counting),
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
            current == null -> SkeletonList()
            current.isEmpty() -> EmptyState()
            // A grid at every width, with the column count as a parameter rather than a
            // second layout: one column is a list, and the only thing that changes at sw600
            // is the number.
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(current, key = { it.kind.id }) { pile ->
                    PileRow(pile = pile, onClick = { onOpenPile(pile) })
                }
            }
        }
    }
}

/**
 * Total photos and bytes across the list.
 *
 * Overlay piles are excluded: their photos are also in a folder or month pile, so counting
 * every pile would report a library half again as large as it is.
 */
@Composable
private fun librarySummary(piles: List<Pile>): String {
    val partitions = piles.filterNot { it.kind.isOverlay }
    return stringResource(
        R.string.piles_subtitle,
        partitions.sumOf { it.count },
        PhotoFormat.bytes(partitions.sumOf { it.totalBytes }),
    )
}

@Composable
private fun PileRow(pile: Pile, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(PsDim.pileRowHeight)
            .clip(RowShape)
            .background(PsColor.Panel)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThumbnailFan(photos = pile.photos)

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pileTitle(pile.kind),
                style = PsType.pileName,
                color = PsColor.Frame,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.piles_pile_line,
                    pile.count,
                    PhotoFormat.bytes(pile.totalBytes),
                ),
                style = PsType.pileMeta,
                color = PsColor.Steel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(12.dp))

        // The number that decides whether the row is worth tapping, alone and in the accent.
        Text(
            text = PhotoFormat.bytes(pile.totalBytes),
            style = PsType.pileName,
            color = PsColor.Gold,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * Three photos from the pile, newest first, fanned.
 *
 * Drawn back to front so the newest photo ends up on top. Fewer than three photos draws fewer
 * cards; the width does not change either way — see [FanWidth].
 */
@Composable
private fun ThumbnailFan(photos: List<Photo>, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Box(modifier = modifier.width(FanWidth).height(ThumbSize)) {
        photos.take(FanDepths.size).withIndex().reversed().forEach { (depth, photo) ->
            val spec = FanDepths[depth]
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(photo.contentUri())
                    .size(THUMB_PX, THUMB_PX)
                    .build(),
                // Decorative: the row already says which pile this is and how big it is.
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .offset(x = FanStep * depth)
                    .size(ThumbSize)
                    .rotate(spec.rotation)
                    .alpha(spec.alpha)
                    .clip(ThumbShape)
                    .background(PsColor.Midnight),
            )
        }
    }
}

// ---- loading and empty -------------------------------------------------------------------

/**
 * What the user looks at while MediaStore is read.
 *
 * This replaces a blank screen. On a 20,000-photo library the cursor walk is seconds long, and
 * a black rectangle for seconds after a tap is indistinguishable from a hang — the user's next
 * move is to kill the app, and they are not wrong to.
 */
@Composable
private fun SkeletonList(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(SKELETON_ROWS) { index ->
            // Staggered, so the rows read as one sweep crossing the list rather than four
            // rectangles pulsing in unison.
            val progress by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(SHIMMER_MS, easing = LinearEasing),
                    initialStartOffset = StartOffset(SHIMMER_STAGGER_MS * index),
                ),
                label = "skeleton-$index",
            )
            SkeletonRow(progress = { progress })
        }
    }
}

/** `progress` is a lambda so the shimmer frame never recomposes the row. */
@Composable
private fun SkeletonRow(progress: () -> Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(PsDim.pileRowHeight)
            .clip(RowShape)
            .background(PsColor.Panel.copy(alpha = 0.6f))
            .drawBehind {
                val band = size.width * 0.35f
                val travel = size.width + band * 2f
                val start = -band + travel * progress()
                drawRect(
                    brush = Brush.linearGradient(
                        0f to Color.Transparent,
                        0.5f to PsColor.SteelDim.copy(alpha = 0.45f),
                        1f to Color.Transparent,
                        start = Offset(start, 0f),
                        end = Offset(start + band, 0f),
                    ),
                )
            },
    )
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.home_empty_title),
            style = PsType.pileName,
            color = PsColor.Frame,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.home_empty_body),
            style = PsType.body,
            color = PsColor.Steel,
        )
    }
}

// ---- the other two destinations -----------------------------------------------------------

/**
 * Top and side insets for a tab that is not the piles list.
 *
 * The bottom inset is not consumed here: the navigation bar is padded clear of it by
 * [HomeBottomNav], and a tab that also padded the bottom would float above its own bar.
 */
@Composable
private fun InsetTab(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeDrawing
                    .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            ),
    ) {
        content()
    }
}

// ---- bottom navigation --------------------------------------------------------------------

@Composable
private fun NavItem(
    tab: HomeTab,
    labelRes: Int,
    selected: HomeTab,
    onSelect: (HomeTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelected = tab == selected
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = { onSelect(tab) }),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(labelRes),
            style = PsType.buttonLabel,
            color = if (isSelected) PsColor.Frame else PsColor.Steel,
            maxLines = 1,
            softWrap = false,
        )
    }
}

// ---- lifetime stat ------------------------------------------------------------------------

/**
 * Screen 03's lifetime figure. Moved across from `PileHostScreen` unchanged.
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

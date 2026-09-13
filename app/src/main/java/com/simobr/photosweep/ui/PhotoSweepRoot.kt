package com.simobr.photosweep.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.simobr.photosweep.R
import com.simobr.photosweep.ads.SuccessBanner
import com.simobr.photosweep.ads.SuccessInterstitial
import com.simobr.photosweep.ads.rememberAdsAllowed
import com.simobr.photosweep.data.db.PhotoSweepDatabase
import com.simobr.photosweep.data.media.MediaRepository
import com.simobr.photosweep.data.piles.Pile
import com.simobr.photosweep.data.piles.PileBuilder
import com.simobr.photosweep.debug.DebugPileTools
import com.simobr.photosweep.data.trash.MediaStoreTrash
import com.simobr.photosweep.ui.confirm.ConfirmScreen
import com.simobr.photosweep.ui.confirm.ConfirmViewModel
import com.simobr.photosweep.ui.confirm.TrashOutcome
import com.simobr.photosweep.ui.home.HomeScreen
import com.simobr.photosweep.ui.permission.PermissionGate
import com.simobr.photosweep.ui.piles.pileTitle
import com.simobr.photosweep.ui.success.SuccessScreen
import com.simobr.photosweep.ui.success.SuccessUiState
import com.simobr.photosweep.ui.sweep.SweepScreen
import com.simobr.photosweep.ui.sweep.SweepViewModel
import com.simobr.photosweep.ui.theme.PsColor
import kotlinx.coroutines.launch

/**
 * Root of the Compose tree.
 *
 * The Scaffold takes zero window insets on purpose: the app draws edge to edge and each
 * screen consumes the insets it actually needs, so a full-bleed photo can reach the
 * status bar while a button row still clears the gesture bar.
 *
 * `containerColor` is [PsColor.Midnight] and not black, because this colour is not only a
 * backstop. [SuccessHost] draws nothing at all while its state loads, so the Scaffold is what
 * is on screen for the gap between the system trash dialog closing and the success screen
 * painting — and a black flash in the middle of the one destructive flow reads as a crash.
 */
@Composable
fun PhotoSweepRoot(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = PsColor.Midnight,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            PermissionGate { GrantedContent() }
        }
    }
}

private enum class Destination { Home, Sweep, Confirm, Success }

/**
 * Where the user is, in a form that survives a process death.
 *
 * The old `Route` carried the [Pile] itself, and a Pile carries every [com.simobr.photosweep
 * .data.media.Photo] in it — thousands of objects, far past anything that belongs in a saved
 * instance state bundle. So the route is reduced to primitives plus the pile's **id**, and the
 * Pile is looked up again on the other side of the death. `PileKind.id` was already documented
 * as a stable, storable identity because `pending_mark.pileId` depends on it; this is the
 * second thing that depends on it.
 *
 * Every field here is a primitive or a String, which is what makes [NavSaver] possible.
 */
private data class Nav(
    val destination: Destination = Destination.Home,
    val pileId: String? = null,
    val reviewToken: Int = 0,
    val keptCount: Int = 0,
    val requestedCount: Int = 0,
    val confirmedCount: Int = 0,
    val confirmedBytes: Long = 0L,
) {
    fun outcome(): TrashOutcome = TrashOutcome(
        requestedCount = requestedCount,
        confirmedCount = confirmedCount,
        confirmedBytes = confirmedBytes,
    )
}

private val NavSaver = listSaver<Nav, Any?>(
    save = {
        listOf(
            it.destination.name, it.pileId, it.reviewToken, it.keptCount,
            it.requestedCount, it.confirmedCount, it.confirmedBytes,
        )
    },
    restore = {
        Nav(
            destination = Destination.valueOf(it[0] as String),
            pileId = it[1] as String?,
            reviewToken = it[2] as Int,
            keptCount = it[3] as Int,
            requestedCount = it[4] as Int,
            confirmedCount = it[5] as Int,
            confirmedBytes = it[6] as Long,
        )
    },
)

/**
 * Four destinations and one saved value. A navigation library is not in the dependency list.
 *
 * Back is handled per destination rather than by letting the dispatcher fall through to the
 * Activity. The one that matters is Sweep: a stray back gesture two hundred swipes into a pile
 * used to leave with no warning, and while nothing was *lost* — the marks are in Room — the
 * user had no way to know that. Every other destination goes where the screen's own back
 * affordance already goes, so back and the on-screen control cannot disagree.
 */
@Composable
private fun GrantedContent() {
    val context = LocalContext.current

    // Resolves the UMP flow once per process and initialises the Mobile Ads SDK off the main
    // thread, if and only if consent allows it. Nothing below waits on it: the whole tree
    // composes while this is still in flight, and a failure or a timeout simply means no ads.
    rememberAdsAllowed()

    var nav by rememberSaveable(stateSaver = NavSaver) { mutableStateOf(Nav()) }
    var pilesRefresh by rememberSaveable { mutableIntStateOf(0) }
    var reviewCount by rememberSaveable { mutableIntStateOf(0) }

    // Deliberately NOT saveable — see [Nav]. After a process death this is null while the id
    // in `nav` is not, and the effect below rebuilds it.
    var pile by remember { mutableStateOf<Pile?>(null) }

    fun home() {
        nav = Nav()
    }

    val pileId = nav.pileId
    LaunchedEffect(pileId) {
        if (pileId == null || pile?.kind?.id == pileId) return@LaunchedEffect

        // The lookup half of "a saveable key plus a lookup". Built the same way the home
        // screen builds it, debug scope included, so a restored pile is the same pile the
        // user was looking at rather than a differently-filtered one with the same name.
        val photos = MediaRepository(context.contentResolver).queryPhotos()
        val marked = PhotoSweepDatabase.get(context).pendingMarkDao().markedMediaIds().toSet()
        val rebuilt = PileBuilder
            .build(DebugPileTools.scopePhotos(photos), markedMediaIds = marked)
            .firstOrNull { it.kind.id == pileId }

        pile = rebuilt
        // The pile can legitimately be gone: every photo in it may have been swept and
        // confirmed while the process was dead. Home is the only honest place to land.
        if (rebuilt == null) home()
    }

    when (nav.destination) {
        Destination.Home -> HomeScreen(
            refreshKey = pilesRefresh,
            onOpenPile = { opened ->
                pile = opened
                nav = Nav(destination = Destination.Sweep, pileId = opened.kind.id)
            },
        )

        // While `pile` is null the lookup is still running. Drawing nothing for a frame or two
        // beats drawing a half-built pile.
        Destination.Sweep -> pile?.let { current ->
            SweepHost(
                pile = current,
                reviewToken = nav.reviewToken,
                onClose = { home() },
                onConfirm = { kept ->
                    nav = nav.copy(destination = Destination.Confirm, keptCount = kept)
                },
            )
        }

        Destination.Confirm -> pile?.let { current ->
            val backToSweep = {
                nav = nav.copy(destination = Destination.Sweep, reviewToken = ++reviewCount)
            }
            ConfirmHost(
                pile = current,
                keptCount = nav.keptCount,
                onBack = backToSweep,
                onReviewAgain = backToSweep,
                onFinished = { outcome ->
                    pilesRefresh++
                    nav = nav.copy(
                        destination = Destination.Success,
                        requestedCount = outcome.requestedCount,
                        confirmedCount = outcome.confirmedCount,
                        confirmedBytes = outcome.confirmedBytes,
                    )
                },
            )
            // Back matches the screen's own Back control: to Sweep, marks intact.
            BackHandler(onBack = backToSweep)
        }

        Destination.Success -> {
            val activity = LocalActivity.current
            val outcome = nav.outcome()

            // Preloaded on composition, not on the tap, so leaving is never delayed by a load.
            LaunchedEffect(Unit) { SuccessInterstitial.preload(context) }

            /**
             * Both exits run through here: the on-screen "Back to piles" and the system back
             * gesture. Either is a valid trigger, and neither waits — if no ad is ready, or any
             * gate in `AdPolicy` says no, this navigates synchronously.
             */
            val leave: () -> Unit = {
                val go = {
                    pilesRefresh++
                    home()
                }
                if (activity == null) {
                    go()
                } else {
                    SuccessInterstitial.showThenContinue(
                        activity = activity,
                        confirmedBytes = outcome.confirmedBytes,
                        onContinue = go,
                    )
                }
            }

            SuccessHost(
                outcome = outcome,
                onBack = leave,
                banner = { SuccessBanner() },
            )
            BackHandler(onBack = leave)
        }
    }
}

/**
 * The back policy for the sweep screen, and nothing else.
 *
 * Extracted from [SweepHost] for one reason: it is the only back behaviour in the app that can
 * cost the user their session, so it is the only one worth testing through the real
 * `OnBackPressedDispatcher` — and [SweepHost] builds its own ViewModel from the real database,
 * which a test cannot stand in front of. Here the decision depends on one Int.
 *
 * With nothing marked, back leaves at once: a dialog about zero photos only teaches the user to
 * dismiss dialogs. With marks standing, the question is asked once and both answers are spelled
 * out — nothing is destroyed either way, but only one of them throws away the work.
 *
 * "Keep marks" is the confirm button because keeping is the recoverable half.
 */
@Composable
internal fun SweepBackGuard(
    sweptCount: Int,
    onLeave: () -> Unit,
    onDiscard: () -> Unit,
) {
    var asking by remember { mutableStateOf(false) }

    BackHandler {
        if (sweptCount > 0) asking = true else onLeave()
    }

    if (!asking) return

    AlertDialog(
        onDismissRequest = { asking = false },
        title = { Text(stringResource(R.string.sweep_back_title)) },
        text = {
            Text(pluralStringResource(R.plurals.sweep_back_body, sweptCount, sweptCount))
        },
        confirmButton = {
            TextButton(onClick = { asking = false; onLeave() }) {
                Text(stringResource(R.string.sweep_back_keep))
            }
        },
        dismissButton = {
            TextButton(onClick = { asking = false; onDiscard() }) {
                Text(stringResource(R.string.sweep_back_discard))
            }
        },
    )
}

@Composable
private fun SweepHost(
    pile: Pile,
    reviewToken: Int,
    onClose: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember(context) { PhotoSweepDatabase.get(context).pendingMarkDao() }
    val title = pileTitle(pile.kind)

    val viewModel: SweepViewModel = viewModel(
        key = "sweep-${pile.kind.id}",
        factory = SweepViewModel.factory(pile.kind.id, title, pile.photos, dao),
    )
    val state by viewModel.state.collectAsState()

    // "Review again" rewinds the same session rather than building a new one, so the marks
    // ahead of the cursor stay exactly as the user left them.
    LaunchedEffect(reviewToken) {
        if (reviewToken > 0) viewModel.restartReview()
    }

    // The 300ms persistence debounce must not become a window in which backgrounding the app
    // loses the last swipe.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        scope.launch { viewModel.flush() }
    }

    SweepBackGuard(
        sweptCount = state.sweptCount,
        onLeave = { scope.launch { viewModel.flush(); onClose() } },
        onDiscard = { scope.launch { viewModel.discardMarks(); onClose() } },
    )

    SweepScreen(
        state = state,
        onSweep = viewModel::sweep,
        onKeep = viewModel::keep,
        onUndo = viewModel::undo,
        onDismissToast = viewModel::dismissToast,
        onClose = {
            scope.launch { viewModel.flush() }
            onClose()
        },
        onConfirmPile = {
            val kept = state.keptCount
            scope.launch {
                viewModel.flush()
                onConfirm(kept)
            }
        },
    )
}

@Composable
private fun ConfirmHost(
    pile: Pile,
    keptCount: Int,
    onBack: () -> Unit,
    onReviewAgain: () -> Unit,
    onFinished: (TrashOutcome) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember(context) { PhotoSweepDatabase.get(context) }
    val trash = remember(context) { MediaStoreTrash(context.contentResolver) }
    val title = pileTitle(pile.kind)

    val viewModel: ConfirmViewModel = viewModel(
        key = "confirm-${pile.kind.id}",
        factory = ConfirmViewModel.factory(
            pileId = pile.kind.id,
            pileTitle = title,
            pilePhotos = pile.photos,
            keptCount = keptCount,
            markDao = db.pendingMarkDao(),
            statDao = db.sweepStatDao(),
            trash = trash,
        ),
    )
    val state by viewModel.state.collectAsState()

    // The result code is deliberately ignored. What matters is what MediaStore says now.
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) {
        scope.launch { onFinished(viewModel.reconcileAfterDialog()) }
    }

    ConfirmScreen(
        state = state,
        onUnmark = viewModel::unmark,
        onConfirm = {
            scope.launch {
                val ids = viewModel.beginTrashRequest()
                if (ids.isEmpty()) return@launch
                val request = trash.buildTrashRequest(ids)
                launcher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            }
        },
        onReviewAgain = onReviewAgain,
        onBack = onBack,
    )
}

@Composable
private fun SuccessHost(
    outcome: TrashOutcome,
    onBack: () -> Unit,
    banner: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember(context) { PhotoSweepDatabase.get(context) }
    val trash = remember(context) { MediaStoreTrash(context.contentResolver) }

    var refresh by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<SuccessUiState?>(null) }

    LaunchedEffect(refresh) {
        val stats = db.sweepStatDao()
        val contents = trash.trashContents()
        state = SuccessUiState(
            batchBytes = outcome.confirmedBytes,
            batchCount = outcome.confirmedCount,
            requestedCount = outcome.requestedCount,
            lifetimeBytes = stats.lifetimeBytes(),
            lifetimePhotos = stats.lifetimePhotos(),
            firstSweepAtMs = stats.firstSweepAtMs(),
            trashBytes = contents.sumOf { it.sizeBytes },
            trashCount = contents.size,
        )
    }

    val emptyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) {
        // Same rule as trashing: re-read rather than believe the result code.
        refresh++
    }

    state?.let { loaded ->
        SuccessScreen(
            state = loaded,
            banner = banner,
            onEmptyTrash = {
                scope.launch {
                    val ids = trash.trashContents().map { it.id }
                    if (ids.isEmpty()) return@launch
                    val request = trash.dangerouslyBuildDeleteRequest(ids)
                    emptyLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                }
            },
            onBack = onBack,
        )
    }
}

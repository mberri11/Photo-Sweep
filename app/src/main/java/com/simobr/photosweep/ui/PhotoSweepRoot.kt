package com.simobr.photosweep.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simobr.photosweep.data.db.PhotoSweepDatabase
import com.simobr.photosweep.data.piles.Pile
import com.simobr.photosweep.data.trash.MediaStoreTrash
import com.simobr.photosweep.ui.confirm.ConfirmScreen
import com.simobr.photosweep.ui.confirm.ConfirmViewModel
import com.simobr.photosweep.ui.confirm.TrashOutcome
import com.simobr.photosweep.ui.permission.PermissionGate
import com.simobr.photosweep.ui.piles.PileHostScreen
import com.simobr.photosweep.ui.piles.pileTitle
import com.simobr.photosweep.ui.success.SuccessScreen
import com.simobr.photosweep.ui.success.SuccessUiState
import com.simobr.photosweep.ui.sweep.SweepScreen
import com.simobr.photosweep.ui.sweep.SweepViewModel
import kotlinx.coroutines.launch

/**
 * Root of the Compose tree.
 *
 * The Scaffold takes zero window insets on purpose: the app draws edge to edge and each
 * screen consumes the insets it actually needs, so a full-bleed photo can reach the
 * status bar while a button row still clears the gesture bar.
 */
@Composable
fun PhotoSweepRoot(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            PermissionGate { GrantedContent() }
        }
    }
}

private sealed interface Route {
    data object Piles : Route
    data class Sweep(val pile: Pile, val reviewToken: Int = 0) : Route
    data class Confirm(val pile: Pile, val keptCount: Int) : Route
    data class Success(val pile: Pile, val outcome: TrashOutcome) : Route
}

/**
 * Four screens and a variable. A navigation library is not in the dependency list, and the
 * real screen graph replaces this.
 */
@Composable
private fun GrantedContent() {
    var route by remember { mutableStateOf<Route>(Route.Piles) }
    var pilesRefresh by remember { mutableIntStateOf(0) }
    var reviewCount by remember { mutableIntStateOf(0) }

    when (val current = route) {
        Route.Piles -> PileHostScreen(
            refreshKey = pilesRefresh,
            onOpenPile = { route = Route.Sweep(it) },
        )

        is Route.Sweep -> SweepHost(
            pile = current.pile,
            reviewToken = current.reviewToken,
            onClose = { route = Route.Piles },
            onConfirm = { kept -> route = Route.Confirm(current.pile, kept) },
        )

        is Route.Confirm -> ConfirmHost(
            pile = current.pile,
            keptCount = current.keptCount,
            onBack = { route = Route.Sweep(current.pile, reviewToken = ++reviewCount) },
            onReviewAgain = { route = Route.Sweep(current.pile, reviewToken = ++reviewCount) },
            onFinished = { outcome ->
                pilesRefresh++
                route = Route.Success(current.pile, outcome)
            },
        )

        is Route.Success -> SuccessHost(
            outcome = current.outcome,
            onBack = {
                pilesRefresh++
                route = Route.Piles
            },
        )
    }
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
private fun SuccessHost(outcome: TrashOutcome, onBack: () -> Unit) {
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

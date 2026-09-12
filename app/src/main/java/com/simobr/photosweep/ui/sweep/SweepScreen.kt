package com.simobr.photosweep.ui.sweep

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.launch

/** Test tags, so the instrumented drag test addresses the card rather than a coordinate. */
object SweepTags {
    const val CARD = "sweep_card"
    const val GHOST = "sweep_ghost"
    const val UNDO = "sweep_undo"
    const val INDEX = "sweep_index"
}

/** Decoded at 512px on the long edge. Never the full 4032×3024 — that is 48MB of bitmap. */
private const val DECODE_PX = 512
private const val THUMB_PX = 128

private const val COMMIT_EXIT_MS = 220
private const val PLUS_ONE_MS = 620
private const val TOAST_VISIBLE_MS = 4_000

/** Reserved lane at the bottom of the swipe surface. The toast lives here and only here. */
private val ToastLane = 96.dp

/**
 * The core loop.
 *
 * Layout note that is a requirement, not a preference: the undo toast occupies a reserved
 * lane below the card. The card's date strip sits inside the card at its bottom edge, and the
 * toast must never cover it — reserving the lane makes that true by geometry rather than by
 * hoping the screen is tall enough.
 */
@Composable
fun SweepScreen(
    state: SweepUiState,
    onSweep: () -> Unit,
    onKeep: () -> Unit,
    onUndo: () -> Unit,
    onDismissToast: () -> Unit,
    onClose: () -> Unit,
    onConfirmPile: () -> Unit = onClose,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberSweepHaptics()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }

    val photo = state.current

    // Reset per photo: each card starts at rest with its own gesture history.
    val offsetX = remember(photo?.id) { Animatable(0f) }
    var cardWidthPx by remember { mutableFloatStateOf(0f) }

    // Release effects are keyed by a token so a second sweep restarts them.
    var effect by remember { mutableStateOf<SweepDirection?>(null) }
    var effectToken by remember { mutableStateOf(0L) }
    val effectProgress = remember { Animatable(0f) }
    var plusOneToken by remember { mutableStateOf(0L) }

    LaunchedEffect(effectToken) {
        if (effect == null) return@LaunchedEffect
        val duration = if (effect == SweepDirection.Sweep) COMET_TOTAL_MS else KEEP_PULSE_MS
        effectProgress.snapTo(0f)
        effectProgress.animateTo(1f, tween(duration, easing = LinearEasing))
        effect = null
    }

    fun commit(direction: SweepDirection) {
        effect = direction
        effectToken++
        if (direction == SweepDirection.Sweep) {
            plusOneToken++
            haptics.sweep()
            onSweep()
        } else {
            haptics.keep()
            onKeep()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PsColor.Midnight)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        SweepTopBar(
            state = state,
            plusOneToken = plusOneToken,
            onClose = onClose,
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {

            EdgeCapsule(
                alignment = Alignment.CenterStart,
                colour = PsColor.Sweep,
                glow = { SweepGesture.edgeGlow(offsetX.value.coerceAtMost(0f), cardWidthPx) },
            )
            EdgeCapsule(
                alignment = Alignment.CenterEnd,
                colour = PsColor.Keep,
                glow = { SweepGesture.edgeGlow(offsetX.value.coerceAtLeast(0f), cardWidthPx) },
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = ToastLane),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    state.isLoading -> Unit

                    state.isFinished -> FinishedPanel(
                        state = state,
                        onReview = onConfirmPile,
                        onBack = onClose,
                    )

                    photo == null -> Text(
                        text = stringResource(R.string.sweep_empty),
                        style = PsType.body,
                        color = PsColor.Steel,
                    )

                    else -> {
                        // Exactly one ghost behind the top card. A fanned deck of three looks
                        // busy and implies a depth the user cannot act on.
                        state.next?.let { GhostCard(it) }

                        Box(
                            modifier = Modifier
                                .size(PsDim.photoCardW, PsDim.photoCardH)
                                .drawBehind { }
                        ) {
                            when (effect) {
                                SweepDirection.Sweep -> CometTrail(
                                    progress = effectProgress.value,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                SweepDirection.Keep -> KeepPulse(
                                    progress = effectProgress.value,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                null -> Unit
                            }
                        }

                        PhotoCard(
                            photo = photo,
                            modifier = Modifier
                                .testTag(SweepTags.CARD)
                                .onSizeChanged { cardWidthPx = it.width.toFloat() }
                                .graphicsLayer {
                                    translationX = offsetX.value
                                    rotationZ = SweepGesture.rotation(offsetX.value, cardWidthPx / 2f)
                                    // Pivot low, so the card tips about the hand holding it
                                    // rather than spinning about its middle.
                                    transformOrigin = TransformOrigin(0.5f, 0.85f)
                                }
                                .pointerInput(photo.id) {
                                    val flingThreshold = SweepGesture
                                        .FLING_VELOCITY_DP_PER_SECOND.dp.toPx()
                                    val exitDistance = screenWidthPx + size.width
                                    val tracker = VelocityTracker()

                                    detectHorizontalDragGestures(
                                        onDragStart = { tracker.resetTracking() },
                                        onHorizontalDrag = { change, dragAmount ->
                                            change.consume()
                                            tracker.addPosition(change.uptimeMillis, change.position)
                                            scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                                        },
                                        onDragCancel = {
                                            scope.launch { offsetX.animateTo(0f, SpringBack) }
                                        },
                                        onDragEnd = {
                                            val velocity = tracker.calculateVelocity().x
                                            val decision = SweepGesture.decide(
                                                offsetX = offsetX.value,
                                                cardWidthPx = size.width.toFloat(),
                                                velocityXPxPerSecond = velocity,
                                                flingThresholdPxPerSecond = flingThreshold,
                                            )
                                            scope.launch {
                                                if (decision == null) {
                                                    offsetX.animateTo(0f, SpringBack)
                                                } else {
                                                    val target = if (decision == SweepDirection.Sweep) {
                                                        -exitDistance
                                                    } else {
                                                        exitDistance
                                                    }
                                                    offsetX.animateTo(
                                                        target,
                                                        tween(COMMIT_EXIT_MS, easing = FastOutLinearInEasing),
                                                    )
                                                    commit(decision)
                                                }
                                            }
                                        },
                                    )
                                },
                        )
                    }
                }
            }

            UndoToast(
                state = state,
                onUndo = onUndo,
                onExpire = onDismissToast,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = PsDim.screenPadH, vertical = 12.dp),
            )
        }

        BottomRow(keptCount = state.keptCount)
    }
}

/** dampingRatio 0.7, StiffnessMediumLow: settles without wobbling like jelly. */
private val SpringBack = spring<Float>(
    dampingRatio = 0.7f,
    stiffness = Spring.StiffnessMediumLow,
)

@Composable
private fun SweepTopBar(
    state: SweepUiState,
    plusOneToken: Long,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = PsDim.screenPadH)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "✕",
                style = PsType.screenTitle,
                color = PsColor.Steel,
                modifier = Modifier
                    .clickable(onClick = onClose)
                    .padding(8.dp)
                    .semantics { contentDescription = "Close" },
            )

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.sweep_index, state.displayIndex, state.total),
                    style = PsType.pileName,
                    color = PsColor.Frame,
                    modifier = Modifier.testTag(SweepTags.INDEX),
                )
                Text(
                    text = state.pileTitle.uppercase(),
                    style = PsType.sectionCaption,
                    color = PsColor.Steel,
                )
            }

            Box(contentAlignment = Alignment.Center) {
                GoldChip(count = state.sweptCount, bytes = state.sweptBytes)
                PlusOne(token = plusOneToken, modifier = Modifier.align(Alignment.TopCenter))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(PsColor.SteelDim.copy(alpha = 0.35f))
                .drawBehind {
                    drawRect(
                        color = PsColor.Frame,
                        size = size.copy(width = size.width * state.progress),
                    )
                },
        )
    }
}

/**
 * Swept count and bytes, rolling.
 *
 * Both numbers use tabular figures. Without them the chip re-measures on every frame of the
 * roll and shuffles sideways, which is the single most obvious way to make a polished screen
 * look broken.
 */
@Composable
private fun GoldChip(count: Int, bytes: Long, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(PsColor.Gold.copy(alpha = 0.16f))
            .border(1.dp, PsColor.Gold.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        AnimatedContent(
            targetState = count to bytes,
            transitionSpec = {
                (slideInVertically(tween(250)) { it } + fadeIn(tween(250)))
                    .togetherWith(slideOutVertically(tween(250)) { -it } + fadeOut(tween(250)))
            },
            label = "chip-roll",
        ) { (rollingCount, rollingBytes) ->
            Text(
                text = stringResource(
                    R.string.sweep_chip,
                    rollingCount,
                    PhotoFormat.bytes(rollingBytes),
                ),
                style = PsType.pileMeta,
                color = PsColor.Gold,
            )
        }
    }
}

/** "+1", rising 12dp as it fades. Fires on sweep only. */
@Composable
private fun PlusOne(token: Long, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(token) {
        if (token == 0L) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(PLUS_ONE_MS, easing = LinearEasing))
    }
    if (progress.value >= 1f) return

    Text(
        text = stringResource(R.string.sweep_plus_one),
        style = PsType.pileMeta,
        color = PsColor.GoldHot,
        modifier = modifier.graphicsLayer {
            translationY = -12.dp.toPx() * progress.value
            alpha = 1f - progress.value
        },
    )
}

/** The ghost behind: 0.94 scale, 0.30 alpha. Not interactive. */
@Composable
private fun GhostCard(photo: Photo, modifier: Modifier = Modifier) {
    PhotoCard(
        photo = photo,
        showDateStrip = false,
        modifier = modifier
            .testTag(SweepTags.GHOST)
            .graphicsLayer {
                scaleX = PsDim.ghostScale
                scaleY = PsDim.ghostScale
                alpha = PsDim.ghostAlpha
            },
    )
}

@Composable
private fun PhotoCard(
    photo: Photo,
    modifier: Modifier = Modifier,
    showDateStrip: Boolean = true,
) {
    val shape = RoundedCornerShape(PsDim.photoCardRadius)
    Box(
        modifier = modifier
            .size(PsDim.photoCardW, PsDim.photoCardH)
            .clip(shape)
            .background(PsColor.Panel)
            .border(PsDim.cardRim, PsColor.Frame.copy(alpha = 0.9f), shape),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(photo.contentUri())
                .size(DECODE_PX, DECODE_PX)
                .build(),
            contentDescription = photo.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(shape),
        )

        if (showDateStrip) {
            // The gradient container is taller than its text by DateStripSpec.gradientRunUp,
            // so the first line sits inside the scrim rather than at its transparent edge.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(*DateStripSpec.stops))
                    .padding(
                        start = DateStripSpec.textPadH,
                        end = DateStripSpec.textPadH,
                        top = DateStripSpec.topPad,
                        bottom = DateStripSpec.textPadV,
                    ),
            ) {
                Text(
                    text = PhotoFormat.captureStamp(photo),
                    style = PsType.pileName,
                    color = DateStripSpec.stampColour,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = PhotoFormat.detailLine(photo),
                    style = PsType.photoMeta,
                    color = DateStripSpec.detailColour,
                )
            }
        }
    }
}

/** Rests at 9% fill and lights up as the card approaches the commit threshold. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.EdgeCapsule(
    alignment: Alignment,
    colour: Color,
    glow: () -> Float,
) {
    Box(
        modifier = Modifier
            .align(alignment)
            .width(28.dp)
            .height(96.dp)
            .clip(RoundedCornerShape(14.dp))
            .drawBehind {
                val lit = PsDim.edgeCapsuleRestAlpha +
                    (1f - PsDim.edgeCapsuleRestAlpha) * glow() * 0.7f
                drawRect(color = colour.copy(alpha = lit))
            },
    )
}

@Composable
private fun UndoToast(
    state: SweepUiState,
    onUndo: () -> Unit,
    onExpire: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val action = state.lastAction

    // Keyed on the token, so a second swipe restarts the four seconds instead of inheriting
    // whatever was left of the previous one.
    val countdown = remember { Animatable(1f) }
    LaunchedEffect(action?.token) {
        if (action == null) return@LaunchedEffect
        countdown.snapTo(1f)
        countdown.animateTo(0f, tween(TOAST_VISIBLE_MS, easing = LinearEasing))
        onExpire()
    }

    AnimatedVisibility(
        visible = action != null,
        enter = fadeIn(tween(160)) + slideInVertically(tween(160)) { it / 2 },
        exit = fadeOut(tween(140)),
        modifier = modifier,
    ) {
        val shown = action ?: return@AnimatedVisibility
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(PsColor.Panel),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(shown.photo.contentUri())
                        .size(THUMB_PX, THUMB_PX)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            1.dp,
                            if (shown.direction == SweepDirection.Sweep) PsColor.Sweep else PsColor.Keep,
                            RoundedCornerShape(12.dp),
                        ),
                )

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            if (shown.direction == SweepDirection.Sweep) {
                                R.string.sweep_marked
                            } else {
                                R.string.sweep_kept
                            },
                        ),
                        style = PsType.pileName,
                        color = PsColor.Frame,
                    )
                    Text(
                        text = stringResource(
                            R.string.sweep_toast_detail,
                            PhotoFormat.shortDate(shown.photo),
                            PhotoFormat.bytes(shown.photo.sizeBytes),
                        ),
                        style = PsType.photoMeta,
                        color = PsColor.Steel,
                    )
                }

                // Green, because undoing a sweep is a keep.
                Text(
                    text = stringResource(R.string.sweep_undo),
                    style = PsType.buttonLabel,
                    color = PsColor.Keep,
                    modifier = Modifier
                        .testTag(SweepTags.UNDO)
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, PsColor.Keep.copy(alpha = 0.55f), RoundedCornerShape(50))
                        .clickable(onClick = onUndo)
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .drawBehind {
                        drawRect(
                            color = PsColor.Keep.copy(alpha = 0.55f),
                            size = size.copy(width = size.width * countdown.value),
                        )
                    },
            )
        }
    }
}

@Composable
private fun BottomRow(keptCount: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(PsDim.bottomNavHeight)
            .padding(horizontal = PsDim.screenPadH),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.sweep_left_label),
            style = PsType.sectionCaption,
            color = PsColor.Sweep,
        )
        Text(
            text = stringResource(R.string.sweep_kept_tally, keptCount),
            style = PsType.pileMeta,
            color = PsColor.Steel,
        )
        Text(
            text = stringResource(R.string.sweep_right_label),
            style = PsType.sectionCaption,
            color = PsColor.Keep,
        )
    }
}

@Composable
private fun FinishedPanel(
    state: SweepUiState,
    onReview: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = PsDim.screenPadH),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.sweep_finished_title),
            style = PsType.screenTitle,
            color = PsColor.Frame,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(
                R.string.sweep_finished_body,
                state.sweptCount,
                state.keptCount,
            ),
            style = PsType.body,
            color = PsColor.Steel,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))
        Text(
            text = stringResource(R.string.sweep_finished_action),
            style = PsType.buttonLabel,
            color = PsColor.Midnight,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(PsColor.Frame)
                .clickable(onClick = onReview)
                .padding(horizontal = 28.dp, vertical = 14.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.success_back),
            style = PsType.buttonLabel,
            color = PsColor.Steel,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onBack)
                .padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}

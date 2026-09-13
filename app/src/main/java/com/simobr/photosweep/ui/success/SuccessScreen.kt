package com.simobr.photosweep.ui.success

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.simobr.photosweep.R
import com.simobr.photosweep.ui.format.PhotoFormat
import com.simobr.photosweep.ui.theme.PsColor
import com.simobr.photosweep.ui.theme.PsDim
import com.simobr.photosweep.ui.theme.PsType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object SuccessTags {
    const val SWEPT_TOTAL = "success_swept_total"
    const val EMPTY_TRASH = "success_empty_trash"
}

/** Screen 09. */
data class SuccessUiState(
    val batchBytes: Long,
    val batchCount: Int,
    val requestedCount: Int,
    val lifetimeBytes: Long,
    val lifetimePhotos: Int,
    val firstSweepAtMs: Long?,
    val trashBytes: Long,
    val trashCount: Int,
) {
    val nothingHappened: Boolean get() = batchCount == 0
    val partial: Boolean get() = batchCount in 1 until requestedCount
}

/**
 * The score.
 *
 * The caption reads **SWEPT**, not "SPACE FREED", and that is not a wording preference. A
 * trashed photo keeps occupying storage for thirty days. A user who sweeps 480 MB, opens
 * Settings → Storage, and sees no change concludes the app did nothing — and they are right
 * to, because the app would have told them the space was already back.
 *
 * So the number stays, the label tells the truth, one line explains when the space actually
 * arrives, and the problem turns into an offer: "Empty Trash now" hands over the space
 * immediately, in one tap, which makes the figure literally true for anyone who wants it to be.
 */
@Composable
fun SuccessScreen(
    state: SuccessUiState,
    onEmptyTrash: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The app's one banner slot, passed in rather than constructed here.
     *
     * A slot, so this file imports nothing from `ads/` and the screen stays testable without an
     * ad SDK — and so that the *only* call site of the banner is the success route in
     * `PhotoSweepRoot`, which `AdPlacementTest` asserts by reading the source.
     *
     * It sits below "Back to piles" and inside the screen's `safeDrawing` padding, so it is
     * above the navigation-bar inset rather than underneath it.
     */
    banner: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PsColor.Midnight)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = PsDim.screenPadH),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        if (state.nothingHappened) {
            Text(
                text = stringResource(R.string.success_nothing_title),
                style = PsType.screenTitle,
                color = PsColor.Frame,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.success_nothing_body),
                style = PsType.body,
                color = PsColor.Steel,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                text = stringResource(R.string.success_caption),
                style = PsType.sectionCaption,
                color = PsColor.Steel,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = PhotoFormat.bytes(state.batchBytes),
                style = PsType.bigNumber,
                color = PsColor.Gold,
                modifier = Modifier.testTag(SuccessTags.SWEPT_TOTAL),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = pluralStringResource(R.plurals.success_sub, state.batchCount, state.batchCount),
                style = PsType.pileMeta,
                color = PsColor.Steel,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.success_note),
                style = PsType.photoMeta,
                color = PsColor.SteelDim,
                textAlign = TextAlign.Center,
            )

            if (state.partial) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(
                        R.string.success_partial,
                        state.batchCount,
                        state.requestedCount,
                    ),
                    style = PsType.photoMeta,
                    color = PsColor.Sweep,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        HorizontalDivider(
            modifier = Modifier.width(120.dp),
            color = PsColor.SteelDim.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(22.dp))

        Text(
            text = stringResource(R.string.success_alltime),
            style = PsType.sectionCaption,
            color = PsColor.Steel,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = PhotoFormat.bytes(state.lifetimeBytes),
            style = PsType.screenTitle,
            color = PsColor.Gold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = lifetimeLine(state),
            style = PsType.photoMeta,
            color = PsColor.Steel,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.weight(1f))

        // Outlined, never solid coral: the confirm screen owns that fill. This is still a
        // permanent delete, so it is offered, not pushed.
        if (state.trashCount > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, PsColor.Sweep.copy(alpha = 0.7f), RoundedCornerShape(18.dp))
                    .clickable(onClick = onEmptyTrash)
                    .testTag(SuccessTags.EMPTY_TRASH),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(
                        R.string.success_empty_trash,
                        PhotoFormat.bytes(state.trashBytes),
                    ),
                    style = PsType.buttonLabel,
                    color = PsColor.Sweep,
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        Text(
            text = stringResource(R.string.success_back),
            style = PsType.buttonLabel,
            color = PsColor.Steel,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onBack)
                .padding(horizontal = 22.dp, vertical = 12.dp),
        )
        Spacer(Modifier.height(20.dp))

        // Last thing on the screen, under the action. The slot reserves its own height, so an
        // ad that arrives late cannot push the figure above it.
        banner()
    }
}

/**
 * "3,244 photos swept since March 2024".
 *
 * The month comes from the earliest recorded batch. Hardcoding it would make the line false
 * on every device except the one it was typed on.
 */
@Composable
private fun lifetimeLine(state: SuccessUiState): String {
    val since = state.firstSweepAtMs ?: return stringResource(R.string.home_stat_never)
    val month = MONTH_YEAR.format(Instant.ofEpochMilli(since).atZone(ZoneId.systemDefault()))
    return stringResource(
        R.string.success_lifetime,
        String.format(Locale.getDefault(), "%,d", state.lifetimePhotos),
        month,
    )
}

private val MONTH_YEAR: DateTimeFormatter =
    DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())

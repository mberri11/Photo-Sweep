package com.simobr.photosweep.ui.piles

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.simobr.photosweep.R
import com.simobr.photosweep.data.piles.PileKind
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Human-readable pile name. Titles are a UI concern; the data layer only carries [PileKind]. */
@Composable
fun pileTitle(kind: PileKind): String = when (kind) {
    PileKind.Screenshots -> stringResource(R.string.pile_screenshots)
    PileKind.WhatsApp -> stringResource(R.string.pile_whatsapp)
    PileKind.Downloads -> stringResource(R.string.pile_downloads)
    PileKind.Duplicates -> stringResource(R.string.pile_duplicates)
    PileKind.BigFiles -> stringResource(R.string.pile_bigfiles)
    PileKind.OldShots -> stringResource(R.string.pile_oldshots)
    is PileKind.Month -> MONTH.format(kind.yearMonth)
}

private val MONTH: DateTimeFormatter =
    DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())

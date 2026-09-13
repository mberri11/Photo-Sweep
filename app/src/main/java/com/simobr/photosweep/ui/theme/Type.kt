package com.simobr.photosweep.ui.theme

import androidx.compose.material3.Typography

/**
 * The fallback floor, not the type scale.
 *
 * [PsType] stays the source of truth for screen work: every `Text` that matters names its
 * style explicitly. This exists for the ones that do not. `Typography()` with no arguments is
 * fifteen Material defaults, and every one of them is Roboto — so a single `Text("…")` written
 * without a `style=` renders Roboto directly beside Manrope, and the two are different enough
 * that it reads as a rendering bug rather than an oversight.
 *
 * So all fifteen slots are mapped onto Manrope, drawing their metrics from [PsType]:
 *
 *  - `display*`, `headline*`, `title*` → [PsType.screenTitle]
 *  - `body*` → [PsType.body]
 *  - `label*` → [PsType.buttonLabel]
 *
 * The Large/Medium/Small tiers within each group are deliberately identical. This is a floor:
 * its job is to be the right family at a plausible size, not to invent a three-step scale
 * that no screen has been sized against. When a screen needs a size, it names a [PsType]
 * style — which is the same rule as before, now with a safe default underneath it.
 *
 * `display*` is mapped with the headings rather than onto [PsType.bigNumber]: bigNumber
 * carries `tnum`, and tabular figures on arbitrary display text is a side effect nobody asked
 * for.
 */
val PhotoSweepTypography = Typography(
    displayLarge = PsType.screenTitle,
    displayMedium = PsType.screenTitle,
    displaySmall = PsType.screenTitle,

    headlineLarge = PsType.screenTitle,
    headlineMedium = PsType.screenTitle,
    headlineSmall = PsType.screenTitle,

    titleLarge = PsType.screenTitle,
    titleMedium = PsType.screenTitle,
    titleSmall = PsType.screenTitle,

    bodyLarge = PsType.body,
    bodyMedium = PsType.body,
    bodySmall = PsType.body,

    labelLarge = PsType.buttonLabel,
    labelMedium = PsType.buttonLabel,
    labelSmall = PsType.buttonLabel,
)

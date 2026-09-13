package com.simobr.photosweep.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Photo Sweep has exactly one theme: dark, on Midnight. There is no light scheme and no
 * dynamic colour — the UI is a frame around the user's photos, and a tinted frame would
 * change how their photos read.
 *
 * One palette feeds it: [PsColor]. There used to be a second, `Color.kt`, which existed only
 * to fill this scheme and disagreed with [PsColor] while doing it — `PsSurface` #101828 where
 * the cards are actually [PsColor.Panel] #141D2E, plus a `PsOutline` #26344A that belonged to
 * no token set at all. It is deleted.
 *
 * **Every slot is filled, including the ones nothing in the app draws yet.** An unset slot is
 * not an unused one: it keeps Material's baseline, which is a purple (`primaryContainer`
 * #4F378B) and a pink (`tertiary` #EFB8C8). The first component added without explicit
 * colours — a Snackbar, a Switch, a FilledTonalButton — paints one of those. That is the same
 * failure as a `Text` with no `style=`, and it is closed the same way: leave Material nothing
 * to fall back to. `ThemeParityTest` walks the finished scheme and fails on any colour that
 * is not one of the nine [PsColor] values.
 *
 * [PsColor] is frozen, so nine colours have to cover 48 slots. The rules used, once:
 *  - **Containers are [PsColor.Panel].** The app has exactly one raised surface, at one
 *    depth, and the accent sits on it as text.
 *  - **The surface ramp collapses to two grounds** — page below, Panel at and above it. The
 *    design has no third step, and inventing one would mean a tenth token.
 *  - **`tertiary` is [PsColor.Gold], the same as `primary`.** There is no third accent in the
 *    design: Gold is primary, Keep is secondary, Sweep is the error colour. That leaves
 *    [PsColor.GoldHot] as the only unspent token, and it is deliberately *not* spent here —
 *    GoldHot is the "+1" rise, a transient animation colour that fires on a sweep and on
 *    nothing else. A static component sitting in it all day dilutes a signal whose whole job
 *    is to mean "that just happened". Doubling Gold is the lesser cost: a duplicated accent
 *    is a design the app already has, whereas a devalued flourish cannot be got back.
 *  - **The `*Fixed` roles** exist so an accent can survive a light/dark switch. There is no
 *    switch here, so fixed and fixed-dim are the plain accent.
 *  - **The inverse roles** describe a light surface this app never shows. They are mapped to
 *    the only light-on-dark pair that exists — Frame ground, Midnight ink — rather than left
 *    to Material to guess at.
 */
internal val PhotoSweepColorScheme = darkColorScheme(
    // Accent — the keep/primary action.
    primary = PsColor.Gold,
    onPrimary = PsColor.Midnight,
    primaryContainer = PsColor.Panel,
    onPrimaryContainer = PsColor.Gold,

    // Keep — the swipe-right affordance.
    secondary = PsColor.Keep,
    onSecondary = PsColor.Midnight,
    secondaryContainer = PsColor.Panel,
    onSecondaryContainer = PsColor.Keep,

    // No third accent exists in the design, so tertiary doubles primary. Not GoldHot: that
    // is the "+1" rise and belongs to an animation, not to a component at rest.
    tertiary = PsColor.Gold,
    onTertiary = PsColor.Midnight,
    tertiaryContainer = PsColor.Panel,
    onTertiaryContainer = PsColor.Gold,

    // Sweep — the destructive affordance. Nothing else may use this colour.
    error = PsColor.Sweep,
    onError = PsColor.Midnight,
    errorContainer = PsColor.Panel,
    onErrorContainer = PsColor.Sweep,

    // The page.
    background = PsColor.Midnight,
    onBackground = PsColor.Frame,

    // The one raised surface.
    surface = PsColor.Panel,
    onSurface = PsColor.Frame,
    surfaceVariant = PsColor.Panel,
    onSurfaceVariant = PsColor.Steel,

    // Tonal elevation overlays surfaceTint on surface. Panel over Panel is Panel, which is
    // the point: a card does not drift lighter because something was raised above it.
    surfaceTint = PsColor.Panel,

    // Hairlines and dividers.
    outline = PsColor.SteelDim,
    outlineVariant = PsColor.SteelDim,

    scrim = PsColor.Midnight,

    // The surface ramp. Two grounds, seven slots: below Panel is the page, Panel and above
    // is Panel.
    surfaceDim = PsColor.Midnight,
    surfaceBright = PsColor.Panel,
    surfaceContainerLowest = PsColor.Midnight,
    surfaceContainerLow = PsColor.Midnight,
    surfaceContainer = PsColor.Panel,
    surfaceContainerHigh = PsColor.Panel,
    surfaceContainerHighest = PsColor.Panel,

    // A light surface the app never shows. Mapped rather than defaulted.
    inversePrimary = PsColor.Midnight,
    inverseSurface = PsColor.Frame,
    inverseOnSurface = PsColor.Midnight,

    // Accents that would survive a light/dark switch. There is no switch.
    primaryFixed = PsColor.Gold,
    primaryFixedDim = PsColor.Gold,
    onPrimaryFixed = PsColor.Midnight,
    onPrimaryFixedVariant = PsColor.Midnight,
    secondaryFixed = PsColor.Keep,
    secondaryFixedDim = PsColor.Keep,
    onSecondaryFixed = PsColor.Midnight,
    onSecondaryFixedVariant = PsColor.Midnight,
    tertiaryFixed = PsColor.Gold,
    tertiaryFixedDim = PsColor.Gold,
    onTertiaryFixed = PsColor.Midnight,
    onTertiaryFixedVariant = PsColor.Midnight,
)

@Composable
fun PhotoSweepTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PhotoSweepColorScheme,
        typography = PhotoSweepTypography,
        content = content,
    )
}

package com.simobr.photosweep.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Photo Sweep has exactly one theme: dark, on black. There is no light scheme and no
 * dynamic colour — the UI is a frame around the user's photos, and a tinted frame would
 * change how their photos read.
 */
private val PhotoSweepColorScheme = darkColorScheme(
    primary = PsAmber,
    onPrimary = PsBlack,
    secondary = PsSuccess,
    onSecondary = PsBlack,
    error = PsDanger,
    onError = PsBlack,
    background = PsBlack,
    onBackground = PsTextPrimary,
    surface = PsSurface,
    onSurface = PsTextPrimary,
    surfaceVariant = PsSurfaceHigh,
    onSurfaceVariant = PsTextMuted,
    outline = PsOutline,
    outlineVariant = PsOutline,
    scrim = PsBlack,
)

@Composable
fun PhotoSweepTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PhotoSweepColorScheme,
        typography = PhotoSweepTypography,
        content = content,
    )
}

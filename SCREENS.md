# Photo Sweep — measurable screen blocks

One block per screen, holding only what is *measurable*: a number, a token name, a colour, a
duration. Nothing here says how a screen looks — that is decided on a device, by a human.

The point of a block is that a reviewer can put it next to the code and see, without running
anything, whether the two still agree. When a value changes, this file changes in the same
commit, and so does the test that pins it.

Screens are added as they land. Blocks below cover only what is built.

---

## Sweep — screens 04–07

Source: `ui/sweep/SweepScreen.kt`, `ui/sweep/SweepGesture.kt`, `ui/sweep/DateStripSpec.kt`.
Tokens: `ui/theme/Tokens.kt`, pinned by `TokensResolutionTest`.

### Frame

| Thing | Value | Where |
|---|---|---|
| Ground | `PsColor.Midnight` `#080C14` | `SweepScreen` root |
| Insets | `WindowInsets.safeDrawing` | root column |
| Top bar height | 56 dp | `SweepTopBar` |
| Horizontal screen padding | `PsDim.screenPadH` = 20 dp | `SweepTopBar` |
| Progress hairline | 2 dp tall, 1 dp radius, track `PsColor.SteelDim` @ 0.35, fill `PsColor.Frame` | `SweepTopBar` |
| Reserved toast lane | 96 dp below the card | `ToastLane` |

### Counter

| Thing | Value |
|---|---|
| Format | `"%1$d / %2$d"` (`R.string.sweep_index`) |
| Numerator | `SweepUiState.displayIndex` = `min(currentIndex + 1, total)` |
| First card of a 1,721-photo pile | `1 / 1721` — **not** `0 / 1721` |
| Finished pile of 218 | `218 / 218` |
| Empty pile | `0 / 0` |
| Denominator | `SweepUiState.total` |
| Style | `PsType.pileName`, `PsColor.Frame`, tabular figures |

`currentIndex` remains the internal cursor and still drives `progress`, `current`, `next` and
`isFinished`. `displayIndex` exists only to be printed. Pinned by `SweepUiStateTest`.

### Photo card

| Thing | Value |
|---|---|
| Size | `PsDim.photoCardW` × `PsDim.photoCardH` = 306 × 448 dp |
| Corner radius | `PsDim.photoCardRadius` = 20 dp |
| Rim | `PsDim.cardRim` = 2 dp, `PsColor.Frame` @ 0.9 |
| Fill behind the image | `PsColor.Panel` `#141D2E` |
| Decode size | 512 px long edge (128 px for the confirm-grid thumb) |
| Ghost card | exactly one, scale 0.94, alpha 0.30 |

### Date strip — `DateStripSpec`

Sits inside the card, pinned to its bottom edge, full card width. The undo toast never
overlaps it: the toast has its own 96 dp lane below the card, so this is true by geometry.

| Thing | Value |
|---|---|
| Gradient run-up above the first text line | `gradientRunUp` = 28 dp of pure gradient |
| Container top inset | `topPad` = 42 dp (run-up + the text's own 14 dp) |
| Container bottom inset | `textPadV` = 14 dp |
| Container side insets | `textPadH` = 16 dp |
| Gradient stop 0 | `0.00` → `Color.Transparent` |
| Gradient stop 1 | `0.45` → `PsColor.Midnight` @ **0.72** |
| Gradient stop 2 | `1.00` → `PsColor.Midnight` @ **0.92** |
| Capture stamp | `PsType.pileName`, `stampColour` = `PsColor.Frame` |
| Gap between the two lines | 2 dp |
| Detail line | `PsType.photoMeta`, `detailColour` = `PsColor.Frame` @ **0.8** |

Why three stops and a run-up: the previous strip was a two-stop gradient sized to the text
column's own height, which put the first line of text at roughly a tenth of the scrim's
opacity. Over a bright photo it disappeared. The run-up moves the text out of the
transparent edge of the gradient; the middle stop carries the darkening.

The detail line was `PsColor.Steel` `#6B7A8C`. Steel is a mid-grey chosen against a known
dark panel; over an arbitrary photo it is unreadable however much scrim sits behind it, so
the strip now uses a dimmed `Frame` instead. Pinned by `DateStripSpecTest`.

### Gesture

| Thing | Value |
|---|---|
| Commit distance | `SweepGesture.COMMIT_FRACTION` = 0.28 of card width |
| Commit velocity | `FLING_VELOCITY_DP_PER_SECOND` = 800 dp/s |
| Rotation at full sweep (left) | `PsDim.maxRotationSweep` = −11° |
| Rotation at full keep (right) | `PsDim.maxRotationKeep` = +8° |
| Edge capsules | 28 × 96 dp, rest alpha `PsDim.edgeCapsuleRestAlpha` = 0.09 |
| Sweep edge colour | `PsColor.Sweep` `#FF6A55` |
| Keep edge colour | `PsColor.Keep` `#2BC493` |
| Commit exit | 220 ms |
| "+1" rise | 620 ms, 12 dp, `PsColor.GoldHot` |
| Undo toast | 4,000 ms |

Pinned by `SweepGestureTest` (arithmetic) and `SweepScreenTest` (the arithmetic is actually
wired to the pointer input).

### System bars

Edge-to-edge, both bars transparent, both forced to light content:

```
enableEdgeToEdge(
    statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
    navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
)
```

`SystemBarStyle.dark` means "dark background, therefore light icons" —
`isAppearanceLightStatusBars = false`. The app is `Midnight` in every configuration, so this
is fixed rather than following the system theme. `minSdk = 30` is above the API 29 cutoff
where `enableEdgeToEdge` falls back to a scrim on the navigation bar, so the passed scrim
colour is never applied and both bars are genuinely transparent. Each screen paints
`PsColor.Midnight` *behind* `windowInsetsPadding`, so the bar areas carry the page colour,
not a system band.

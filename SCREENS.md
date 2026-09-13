# Photo Sweep — measurable screen blocks

One block per screen, holding only what is *measurable*: a number, a token name, a colour, a
duration. Nothing here says how a screen looks — that is decided on a device, by a human.

The first block is the exception: the theme is not a screen, it is what every screen is
drawn on, so it sits ahead of them.

The point of a block is that a reviewer can put it next to the code and see, without running
anything, whether the two still agree. When a value changes, this file changes in the same
commit, and so does the test that pins it.

Screens are added as they land. Blocks below cover only what is built.

---

## Theme and window — every screen

Source: `ui/theme/Theme.kt`, `ui/theme/Type.kt`, `ui/PhotoSweepRoot.kt`,
`res/values/colors.xml`, `res/values/themes.xml`. Pinned by `ThemeParityTest`.

One palette feeds the app: `PsColor`, in `ui/theme/Tokens.kt`. There was a second, `Color.kt`,
whose only job was to fill `MaterialTheme` and which disagreed with `PsColor` while doing it —
`PsSurface` #101828 where the cards are actually `PsColor.Panel` #141D2E, plus a `PsOutline`
#26344A that belonged to no token set at all. It is deleted.

### The three grounds Compose does not paint

| Thing | Value | Where |
|---|---|---|
| Window background | `@color/ps_midnight` #080C14 | `themes.xml` → `Theme.PhotoSweep` |
| Splash background | `@color/ps_midnight` #080C14 | `themes.xml` → `Theme.PhotoSweep.Starting` |
| Scaffold `containerColor` | `PsColor.Midnight` #080C14 | `PhotoSweepRoot.kt` |

All three were **#000000** before. The first Compose frame is #080C14, so launch went
#000000 → #080C14 at the handover — a step that is invisible on an LCD panel and plainly
visible on OLED. The Scaffold colour is not only a backstop: `SuccessHost` draws nothing while
its state loads, so the Scaffold is what is on screen between the system trash dialog closing
and the success screen painting.

`ps_white` #FFFFFFFF is retained in `colors.xml` and referenced by nothing.

### Slot coverage

| Thing | Count |
|---|---|
| `darkColorScheme` slots set explicitly | 48 of 48 |
| `Typography` slots mapped to Manrope | 15 of 15 |
| Distinct `PsColor` values used by the scheme | 8 of 9 |

Both counts are totals, not subsets. An unset `ColorScheme` slot keeps Material's baseline,
which includes `primaryContainer` #4F378B and `tertiary` #EFB8C8; an unset `Typography` slot
keeps Roboto. Either one surfaces the first time a component is added without explicit
colours or without a `style=`, which is long after the commit that caused it.

### Mapping rules — nine colours over 48 slots

| Rule | Mapping |
|---|---|
| Containers are the one raised surface | every `*Container` → `PsColor.Panel`, with the accent on it as text |
| The surface ramp collapses to two grounds | `surfaceDim`/`surfaceContainerLowest`/`surfaceContainerLow` → `Midnight`; `surface`/`surfaceBright`/`surfaceContainer`/`High`/`Highest` → `Panel` |
| `tertiary` doubles `primary` | `tertiary*` → `PsColor.Gold`, **not** `GoldHot` — GoldHot is the "+1" rise, an animation token, and a component at rest in it dilutes the signal |
| The `*Fixed` roles are the plain accent | no light/dark switch exists, so fixed and fixed-dim equal the accent they belong to |
| The inverse roles describe a surface the app never shows | `inverseSurface` → `Frame`, `inverseOnSurface`/`inversePrimary` → `Midnight` |
| Elevation does not tint | `surfaceTint` → `Panel`, so Panel over Panel is Panel |

`PsColor.GoldHot` is the one frozen colour deliberately absent from the scheme. It is reached
only from `SweepScreen`'s `PlusOne`.

### Typography floor

`PsType` stays the source of truth for screen work; this is the fallback under it.

| Group | Metrics from |
|---|---|
| `display*`, `headline*`, `title*` | `PsType.screenTitle` — 22sp / W800 / −0.01em |
| `body*` | `PsType.body` — 14sp / W500 / 0em / 21.7sp line height |
| `label*` | `PsType.buttonLabel` — 15sp / W700 / 0.01em |

Large/Medium/Small are identical within each group on purpose: a floor's job is the right
family at a plausible size, not a three-step scale no screen has been sized against.
`display*` goes with the headings rather than onto `PsType.bigNumber`, which carries `tnum` —
tabular figures on arbitrary display text is a side effect nobody asked for. No slot in the
floor requests font features.

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

### Card sizing

The card is the one measurable on this screen that is not a literal. Everything else here —
radius, rim, ghost scale and alpha, the rotations — stays unscaled at every window size.

| Thing | Value |
|---|---|
| Frozen card | `PsDim.photoCardW` × `PsDim.photoCardH` = 306 × 448 dp |
| Frozen aspect ratio | **306 : 448** (0.68304), never varied |
| Width | `sweepCardWidth(available)` = `min(306dp, available × 0.78)` |
| Width fraction | `CARD_WIDTH_FRACTION` = **0.78f** |
| Height | `sweepCardHeight(width)` = `width × 448 / 306` — derived, never measured separately |
| Cap engages at | 306 / 0.78 = **392.3dp** of available width |
| Measured by | `BoxWithConstraints` around the card area, once, inside the toast-lane padding |

Worked examples, pinned by `SweepCardSizeTest`:

| Available width | Card width | Card height |
|---|---|---|
| 400dp | 306dp (capped) | 448dp |
| 392.3dp | 306dp (exactly at the cap) | 448dp |
| 380dp | 296.4dp | 434.0dp |

Why the height is derived and not scaled on its own: the card crops the photo to its own aspect
ratio. A card that grew taller on one device would show a different crop of the same photo than
on another, and the date strip's gradient run-up is measured against a known card height.

The ghost card is given the same two values as the card in front of it, from the same
`BoxWithConstraints` pass, so the two can never drift out of register.

### Adaptive shell — every screen

One `WindowWidthSizeClass` decision, made once in `HomeScreen`.

| Thing | Value |
|---|---|
| Breakpoint | `WindowWidthSizeClass.Compact` ends at **600dp** |
| Below 600dp | bottom bar, `PsDim.bottomNavHeight` 64dp, pile list 1 column |
| 600dp and above | start-edge rail, `RailWidth` 88dp, pile list **2 columns** |
| Rail hairline | 1dp `PsColor.SteelDim` on the end edge (the bar's is on the top edge) |
| Pile grid spacing | 10dp both axes, at either column count |

A landscape phone and a small tablet take the same branch because they pose the same problem: a
64dp bar across a short wide window spends the scarce axis, and one column of 92dp rows across
800dp wastes the plentiful one. The column count is a parameter to one grid, not a second
layout; the tab content is one composable placed by either shell.

`android:configChanges` is **not** declared, so a rotation recreates the Activity. Navigation
state lives in `rememberSaveable` and survives it, and process death, via a pile **id** plus a
lookup — `Pile` itself carries every `Photo` in it and has no business in a saved-state bundle.

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

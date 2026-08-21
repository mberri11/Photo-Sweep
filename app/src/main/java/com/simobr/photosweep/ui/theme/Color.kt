package com.simobr.photosweep.ui.theme

import androidx.compose.ui.graphics.Color

// Palette lifted from design/"Photo Sweep - standalone.html" so the code and the mockups
// agree on one set of values. The mockups use a blue-tinted near-black (#080C14) for the
// page; the app Scaffold is pure black, because a photo shown edge to edge against true
// black is the whole point of the product. Which of the two wins on a real screen is a
// call for a human with a device, not something that can be settled here.

/** Scaffold ground. Pure black — every screen sits on this. */
val PsBlack = Color(0xFF000000)

/** The mockups' page background, one step above black. */
val PsInk = Color(0xFF080C14)

/** Raised surfaces: cards, sheets, the trash list. */
val PsSurface = Color(0xFF101828)

/** Surfaces one step above [PsSurface]: pressed states, nested rows. */
val PsSurfaceHigh = Color(0xFF141D2E)

/** Hairlines and dividers. */
val PsOutline = Color(0xFF26344A)

/** Primary text on dark. */
val PsTextPrimary = Color(0xFFE6EDF5)

/** Secondary text, captions, counts. */
val PsTextMuted = Color(0xFF6B7A8C)

/** Accent — the keep/primary action. */
val PsAmber = Color(0xFFFFC94D)

/** Destructive — sweep, trash, "Empty now". Nothing else may use this colour. */
val PsDanger = Color(0xFFFF6A55)

/** Confirmation — freed space, completed sweep. */
val PsSuccess = Color(0xFF2BC493)

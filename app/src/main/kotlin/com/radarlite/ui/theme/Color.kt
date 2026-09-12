package com.radarlite.ui.theme

import androidx.compose.ui.graphics.Color

// Bold & playful palette: near-black ground, a hot-coral accent for the primary action,
// and a bright signal-green reserved for "monitoring is actively working".
val Background = Color(0xFF121116)
val Surface = Color(0xFF1C1B22)
val SurfaceRaised = Color(0xFF24222C)
val SurfaceHigh = Color(0xFF2C2A36)

val Coral = Color(0xFFFF5D73)
val CoralDim = Color(0xFF4A2530)
val Amber = Color(0xFFFFB238)
val Sun = Color(0xFFFFD166)
val Teal = Color(0xFF3DDC97)
val TealDim = Color(0xFF1B3F31)
val Sky = Color(0xFF4FC3F7)
val Violet = Color(0xFFB98BFF)

val TextPrimary = Color(0xFFFAF9FC)
val TextSecondary = Color(0xFFA6A3B0)
val TextMuted = Color(0xFF716E7D)

val StatusStopped = Color(0xFF716E7D)
val StatusIdle = Color(0xFFFFB238)
val StatusActive = Color(0xFF3DDC97)

// One vivid color per alert category keeps the settings list glanceable and fun rather than a
// wall of identical rows.
val AlertColorSpeed = Coral
val AlertColorRedLight = Color(0xFFFF6B6B)
val AlertColorAverageSpeed = Amber
val AlertColorCurve = Sky
val AlertColorJunction = Violet
val AlertColorCrossing = Color(0xFFFF9F5A)
val AlertColorCalming = Teal
val AlertColorOverspeed = Sun

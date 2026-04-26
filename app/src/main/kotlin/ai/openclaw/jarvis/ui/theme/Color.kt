package ai.openclaw.jarvis.ui.theme

import androidx.compose.ui.graphics.Color

// ─── Final Jarvis Mobile palette (per UI spec) ───────────────────────────────
//
// Backgrounds / surfaces
val BlueprintBackground   = Color(0xFF0A0F16)   // App background
val BlueprintSurface      = Color(0xFF111821)   // Surface
val BlueprintCard         = Color(0xFF18212C)   // Elevated surface / card
val BlueprintBorder       = Color(0xFF1E2A38)   // Card border
val GridLineColor         = Color(0xFF142030)   // faint blueprint grid

// Primary cobalt
val CobaltPrimary         = Color(0xFF00A7FF)   // Cobalt blue (info / primary)
val CobaltBright          = Color(0xFF139DFF)   // Bright blue glow
val CobaltDeep            = Color(0xFF005DFF)   // Deep blue (pressed / depth)
val CobaltGlow            = Color(0xFF00A7FF)   // glow / orb pulse (alias of primary)

// Orb mood map
val OrbIdle               = Color(0xFF005DFF)   // calm cobalt
val OrbListening          = Color(0xFF139DFF)   // bright listening
val OrbProcessing         = Color(0xFF005DFF)   // processing
val OrbSpeaking           = Color(0xFF00A7FF)   // speaking
val OrbError              = Color(0xFFFF4D4F)   // red pulse
val OrbOffline            = Color(0xFFFFB020)   // amber dim

// Status palette
val StatusConnected       = Color(0xFF14E1A0)   // success green
val StatusWarning         = Color(0xFFFFB020)   // amber
val StatusOffline         = Color(0xFFFF4D4F)   // danger red
val StatusInfo            = Color(0xFF00A7FF)   // info blue

// Aliases used by older screens
val StatusPairing         = StatusWarning
val StatusQueued          = StatusWarning

// Route palette (debug only — user-facing text uses words, not colors)
val RouteAndroid          = StatusConnected
val RouteOpenClaw         = CobaltPrimary
val RouteMixed            = Color(0xFFB39DDB)   // soft purple

// Text
val TextPrimary           = Color(0xFFE6F1FF)   // near-white with blue tint
val TextSecondary         = Color(0xFF8AA2B8)   // muted cobalt-grey
val TextDim               = Color(0xFF5E7185)   // very muted

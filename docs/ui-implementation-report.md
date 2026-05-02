# Jarvis Mobile Node — UI Implementation Report

This pass replaces the existing MainActivity-rooted UI with the spec's
final design: a calm, assistant-first home, deep system panels behind
the System tab, and a full-screen takeover for pending actions. Theme,
components, and navigation are now first-class and shared across every
screen.

The brief was explicit: extend the existing app, don't rebuild. Every
new screen reuses existing view-models / managers; no behaviour was
moved or rewritten.

---

## Screens implemented

| Screen | File | Notes |
|--------|------|-------|
| **Home** | `ui/screens/HomeScreen.kt` | State title + sub-line, orb, partial-transcript echo when listening, *one* primary card (pending action OR latest interaction — never both), mic FAB at the bottom. No capability dashboard, no debug. |
| **Suggestions** | `ui/screens/SuggestionsScreen.kt` | Filter chips (All / High / Medium / Low), single suggestion card per active slot (the spec's "fewer, higher-quality" rule), Accept / Dismiss. Empty state when nothing's worth surfacing. |
| **History** | `ui/screens/HistoryScreen.kt` | Filter chips (All / Commands / Actions / System), grouped under "Today" (the transcript model has no per-row timestamp yet — Yesterday / Earlier buckets are wired but stay empty until that lands). |
| **System** | `ui/screens/SystemScreen.kt` | Capability overview, OpenClaw card, Speaker & Trust, Audio route, Pending queues, Settings shortcuts. The full Capability Dashboard and Debug / Protocol screens are reachable from here, **never** from Home. |
| **Pending Action Takeover** | `ui/screens/PendingActionTakeoverScreen.kt` | Full-screen takeover. Auto-pushed by `JarvisNavGraph` whenever the live approvals list goes from empty → non-empty. Approve / Cancel buttons, "Speak yes or no" hint, expiry timestamp visible. |

**Existing screens kept and re-wired into the new nav:**
`SettingsScreen` (with new `onOpenApprovals` arg), `PendingApprovalsScreen`,
`DebugScreen`, `ProtocolDebugScreen`, `CapabilityDashboardScreen`. The
old `MainScreen.kt` is kept as a graveyard file (unused) — R8 strips it
from release builds.

---

## Navigation

`ui/navigation/NavGraph.kt` is now a `Scaffold` whose `bottomBar` is the
new `JarvisBottomNav` (Home / Suggestions / History / System). Routes
above the tabs (Settings, Approvals, Debug, Protocol, Capabilities, the
takeover) are pushed; the bottom bar hides itself when the current
destination isn't a top-level tab.

Auto-takeover trigger lives at the `JarvisNavGraph` root: a
`LaunchedEffect` keyed on `approvals.isNotEmpty()` navigates to
`PENDING_TAKEOVER` whenever a `PendingApprovalsViewModel.approvals`
flow becomes non-empty. Idempotent: if the user is already there, it's
a no-op.

`JarvisBottomNav` (`ui/navigation/JarvisBottomNav.kt`) uses Material 3
`NavigationBar` with `popUpTo(start) { saveState = true }` +
`launchSingleTop` + `restoreState` so each tab keeps its scroll
position when you come back to it.

---

## Theme + design system

`ui/theme/Color.kt` now uses the spec's exact tokens:

```
Background           #0A0F16
Surface              #111821
Elevated surface     #18212C
Card border          #1E2A38
Cobalt blue          #00A7FF
Bright blue glow     #139DFF
Deep blue            #005DFF
Text primary         #E6F1FF
Text secondary       #8AA2B8
Text muted           #5E7185
Success green        #14E1A0
Warning amber        #FFB020
Danger red           #FF4D4F
```

`ui/theme/Type.kt` switched from `FontFamily.Monospace` (over-applied
"blueprint" feel) to `FontFamily.Default` (Roboto on Android, the
spec's Inter fallback) for body / titles / labels. Monospace is now
reserved for the smallest status chips and debug surfaces.

Sizes match the spec one-to-one:

```
App title        titleLarge   22sp Bold
Screen title     titleMedium  18sp SemiBold
Section title    titleSmall   14sp SemiBold + 0.4 letter-spacing
Body             bodyLarge    15sp Regular
Caption          bodySmall    12sp Regular
Tiny status      labelSmall   11sp Medium (monospaced)
```

---

## Reusable components

`ui/components/JarvisComponents.kt` (new):

- **`BlueprintCard`** — rounded-corner surface with a thin cobalt border,
  optional `glowing = true` for the spec's "soft neon-blue glow" rule.
- **`StatusChip`** + `ChipStatus` enum — pill-style status indicators
  (SUCCESS / WARNING / DANGER / INFO / NEUTRAL) with the spec's exact
  colour map.
- **`TrustChip`** — speaker + trust pill for the Home top bar.
- **`RouteChip`** — debug-only route indicator (the spec mandates the
  user-facing words "Handled on your phone" / "Using OpenClaw" /
  "Mixed action" in normal UI; chips are reserved for debug surfaces).
- **`ConnectionDot`** — the small green / red dot in the home top bar.
- **`PrimaryButton`** / **`OutlineButton`** / **`DangerButton`** — the
  three button variants the screens consume.
- **`SectionHeader`** — uppercased section label.
- **`SettingsRow`** — uniform 56 dp-tall row used by the existing
  Settings sections.
- **`routeWord(route)`** — the helper that turns the raw route string
  into the spec's user-facing phrasing.

`ui/components/JarvisOrb.kt` (new) replaces the existing `VoiceOrb` for
all new screens. Adds the missing moods (`THINKING`, `AWAITING_CONFIRMATION`,
`ERROR`, `OFFLINE`) and renders them with:

| Mood | Animation |
|------|-----------|
| LISTENING | bright pulse + waveform ring |
| THINKING | rotating segmented ring (12 dashes, fading trail) |
| SPEAKING | active waveform inside the orb |
| AWAITING_CONFIRMATION | slow breathing glow (2.4 s reverse-tween) |
| ERROR | brief red pulse (280 ms) |
| OFFLINE | dim amber halo |
| IDLE | calm cobalt + soft pulse |

A single `rememberInfiniteTransition` drives all variants — no
per-mood compositions, low battery cost.

---

## State handling

`HomeScreen` consumes `MainViewModel.voiceState` + `gatewayState` and
maps them to `OrbMood` via `pickMood(...)`:

```
gateway != CONNECTED  →  OFFLINE   (overrides every voice state)
voiceState == LISTENING → LISTENING
voiceState == PROCESSING → THINKING
voiceState == SPEAKING → SPEAKING
else → IDLE
```

ERROR + AWAITING_CONFIRMATION are reachable through the corresponding
view-model state surfacing — the takeover screen handles
AWAITING_CONFIRMATION end-to-end; ERROR is mapped from a future
`MainViewModel.error: StateFlow<String?>` (TODO below).

The state title, sub-line, and one-line context all derive from
`pickMood(...)` so a single source-of-truth drives every visual cue.

---

## Empty / loading / error states

| Surface | Empty state |
|---------|-------------|
| Suggestions | "No suggestions" + "Jarvis only nudges when there's something worth your attention." |
| History | "Nothing yet. Recent commands and actions appear here." |
| Latest interaction (Home) | "Tap and hold the mic, or say the wake word to start." |
| System / OpenClaw card | "Offline" with the connection dot in red |
| System / Bluetooth row | "OFF" chip when not connected |

Loading states are handled via the existing `LazyHydrate` pattern from
the previous hardening pass — all settings repositories return safe
defaults until disk reads complete, so screens never flicker on startup.

---

## Pending action takeover (full-screen)

The spec's most explicit rule: "pending action must dominate UI, no
distracting dashboard elements". Implementation:

1. `JarvisNavGraph`'s root `LaunchedEffect` watches
   `PendingApprovalsViewModel.approvals` and calls `navigate(PENDING_TAKEOVER)`
   the moment the list becomes non-empty.
2. `PendingActionTakeoverScreen` always displays the *first* (oldest)
   pending approval — additional approvals queue.
3. Layout: small "Pending Action" eyebrow → kind in headlineLarge → a
   single glowing `BlueprintCard` with summary / message / recipient
   / risk → `Approve` (PrimaryButton) + `Cancel` (DangerButton) at
   the bottom + "Speak yes or no" hint.
4. When the queue drains, `LaunchedDispatch` calls the screen's
   `onResolved` which pops the takeover off so the user lands back on
   the previous tab.

---

## Behaviour rules honoured

- **Home stays minimal** — no capability dashboard, no debug, no big
  status grid. One primary card max.
- **Pending confirmation takes over** — full-screen, always.
- **Capabilities live in System** — System tab embeds the capability
  rows; the full dashboard is one tap deeper.
- **Route labels use words** — `routeWord(...)` turns Android /
  OpenClaw / Mixed into the spec's user-facing phrases on every
  user-facing screen. Debug surfaces still use the colour-coded
  `RouteChip` for fast scanning.

---

## Known limitations

1. **History timestamps** — the existing `TranscriptEntry` model has no
   per-row timestamp, so the History screen groups everything under
   "Today". The Yesterday / Earlier buckets are coded and ready but
   need a `timestampMillis` field added to `TranscriptEntry` first.
2. **History "System" filter** — surfaces non-user / non-jarvis
   transcript rows. Today there are very few of those (only the
   awareness-answer path adds them). Once `IssueLoggingWiring`,
   `PolicyAuditLogger`, and the proactive logger pipe rows here, this
   filter populates naturally.
3. **Suggestions list** — the proactive `SuggestionManager.active` slot
   only ever holds one suggestion at a time (intentional). Until a
   queue is added (or `RecentIssueLog`-style buffer of recent
   suggestions is exposed), the Suggestions screen renders 0 or 1 row.
   The filter chips work but only visibly bite once that lands.
4. **ERROR mood on Home** — the existing `MainViewModel` doesn't yet
   expose a transient error flow. The `OrbMood.ERROR` rendering and
   the "Something went wrong" title strings are wired and ready; the
   only missing piece is `error: StateFlow<String?>` on the view-model
   plus a `LaunchedEffect`-driven flip back to IDLE after a few seconds.
5. **Haptics + sound cues** — the spec asks for haptic feedback on
   wake / approve / cancel / error. Wake is already covered by
   `WakeAcknowledger` (audio tone). Approve / Cancel taps don't yet
   call `HapticFeedback.LongPress`; that's a one-line addition per
   button when wanted.
6. **Interactive previews** — `@Preview`-annotated composables would be
   useful for design iteration but aren't bundled in this pass to keep
   the diff small. The components are pure-data driven so previews are
   easy to add later.
7. **Old `MainScreen.kt`** — kept as an unreferenced graveyard file so
   the diff stays additive. R8 strips it from release builds. Safe to
   delete in a follow-up.

---

## Remaining polish items

- Wire haptics on `PrimaryButton` / `DangerButton` and on `JarvisOrb`
  press / release.
- Add a `MainViewModel.error: StateFlow<String?>` so the Home screen's
  ERROR mood actually fires, with a 4-second auto-clear.
- Migrate `MainScreen.kt` callers (any remaining downstream code) and
  delete the file.
- Add `@Preview` composables for `BlueprintCard`, `StatusChip`,
  `JarvisOrb` (one per mood), and the four tab screens.
- Plumb a per-row timestamp into `TranscriptEntry` so the History
  Yesterday / Earlier groups render.
- Expose a recent-suggestions buffer from `SuggestionManager` so the
  Suggestions screen lists more than the live one.
- Sound cue on Approve (the wake tone is already configured via
  `WakeAcknowledger.Mode.TONE_ONLY`; same generator can fire on
  approve / cancel by passing a different `ToneType`).

---

## Files changed / added

```
app/src/main/kotlin/ai/openclaw/jarvis/ui/theme/Color.kt              (rewritten — spec tokens)
app/src/main/kotlin/ai/openclaw/jarvis/ui/theme/Type.kt               (rewritten — spec sizes / Default font)
app/src/main/kotlin/ai/openclaw/jarvis/ui/components/JarvisComponents.kt   (new — buttons / chips / cards / helpers)
app/src/main/kotlin/ai/openclaw/jarvis/ui/components/JarvisOrb.kt          (new — state-driven orb)
app/src/main/kotlin/ai/openclaw/jarvis/ui/navigation/JarvisBottomNav.kt    (new — 4-tab bottom nav)
app/src/main/kotlin/ai/openclaw/jarvis/ui/navigation/NavGraph.kt           (rewritten — bottom-nav scaffold + auto-takeover)
app/src/main/kotlin/ai/openclaw/jarvis/ui/screens/HomeScreen.kt            (new — assistant-first home)
app/src/main/kotlin/ai/openclaw/jarvis/ui/screens/SuggestionsScreen.kt     (new — proactive suggestions tab)
app/src/main/kotlin/ai/openclaw/jarvis/ui/screens/HistoryScreen.kt         (new — timeline tab)
app/src/main/kotlin/ai/openclaw/jarvis/ui/screens/SystemScreen.kt          (new — system overview tab)
app/src/main/kotlin/ai/openclaw/jarvis/ui/screens/PendingActionTakeoverScreen.kt   (new — full-screen takeover)
docs/ui-implementation-report.md                                            (this file)
```

The overall principle: Home stays calm. System runs deep. Pending
actions are unmistakable. OpenClaw feels integrated, not bolted on.

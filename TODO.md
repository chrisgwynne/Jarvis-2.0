# Jarvis Android — TODO

> **Status update (post-hardening pass):** several P1 items are now fixed.
> Marked items below are kept for traceability; new items appear at the
> top of P1 / P4 as needed. Full bug list in `docs/android-hardening-report.md`.

## P0 — Must complete before first real use

- [x] **Wake word integration** — implemented via `WakeWordController` +
  `AlwaysListeningService` foreground service + `WakeAcknowledger` for the
  sub-150 ms tone. Picovoice / Vosk swap is still optional refinement.

- [ ] **Launcher icons** — replace placeholder `ic_launcher` with custom Jarvis blueprint orb icon.

- [ ] **Mipmap drawable resources** — add `ic_launcher.png` / `ic_launcher_round.png` at all densities,
  or switch to adaptive icon XML.

- [ ] **Full end-to-end test** — connect to real OpenClaw gateway, send a message, verify session event
  arrives in OpenClaw session history.

---

## P1 — Improve reliability

- [x] **`node.invoke` dispatcher** — `NodeInvokeDispatcher` (added in the
  hardening pass) subscribes to `GatewayEvent.InvokeCommand`, dispatches to
  the legacy `AndroidActionExecutor.execute` entry point, and answers with
  `node.invoke.result`. Started from `JarvisApp.onCreate`.

- [x] **Handle `pairing.challenge` in UI** — `MainViewModel.pairingChallenge`
  surfaces the code; `MainScreen` renders a `ConfirmationDialog`.

- [ ] **STT must run on Main thread** — `SpeechRecognizer` requires Main thread; ensure
  `VoiceFrontend.startListening()` always dispatches on `Dispatchers.Main`.

- [ ] **Screenshot flow full wiring** — `ScreenshotCapability` needs `setProjectionResult()` called
  from `MainActivity` after the `startActivityForResult` callback. Add the result handler in
  `MainActivity` and connect it to `ScreenshotCapabilityImpl`.

- [ ] **Camera flow full wiring** — similarly, `CameraCapabilityImpl.buildTakePhotoIntent()` needs
  a `FileProvider` content URI, not a raw file path. Add `FileProvider` to `AndroidManifest.xml`
  and a `file_paths.xml` resource.

---

## P2 — Advanced screen awareness

- [ ] **Screen context capture** — after screenshot, encode as JPEG/base64 and include in the
  `user.message` frame or as a separate `jarvis.context_frame` to OpenClaw.

- [ ] **Accessibility service** — optional `AccessibilityService` to read foreground app name and
  on-screen text. Populate `androidContext.foregroundApp` and optionally pass content to OpenClaw.

- [ ] **System overlay / heads-up** — floating mic button that remains accessible from any app.

---

## P3 — Voice quality

- [ ] **Proper wake word** (see P0 above).

- [ ] **Noise gate / silence detection** — auto-stop STT after configurable silence threshold.

- [ ] **Multiple TTS engines** — support selecting system TTS engine or a cloud TTS (ElevenLabs,
  Play.ht) via the TTS abstraction interface.

- [ ] **Interrupt / barge-in** — if the user presses PTT while TTS is speaking, stop TTS immediately
  and start STT. Currently TTS must finish before PTT is accepted.

---

## P4 — Production hardening

- [x] **Retry policy for offline queue** — `IssueQueueWorker` does capped
  exponential backoff per draft; transient vs terminal failure is decided
  at the API client.

- [x] **ProGuard / R8** — `proguard-rules.pro` keeps every serialization-
  annotated model and Hilt-injected class, AND uses `-assumenosideeffects`
  to strip `Log.d` / `Log.v` from release builds (defence-in-depth on top
  of `LogRedaction`).

- [x] **Lazy SharedPreferences hydration** — `LazyHydrate` defers all four
  repository disk reads to a background coroutine so `JarvisApp.onCreate`
  no longer blocks on prefs file I/O.

- [ ] **DataStore migration versioning** — add a schema version to `OfflineQueueStore` so
  future model changes don't corrupt the queue.

- [ ] **Unit tests** — `IntentClassifier`, `MemoryCandidateDetector`, `LocalIntentRouter`,
  `OfflineQueueStore` are all pure logic and easy to unit-test.

- [ ] **Instrumented tests** — capability availability checks, DataStore round-trip.

- [ ] **CI pipeline** — `./gradlew lint test assembleRelease` on every push.

---

## P5 — Stretch features

- [ ] **OpenClaw memory viewer** — a screen that reads back the user's OpenClaw memory/session
  history directly from the Gateway.

- [ ] **Multiple gateway profiles** — store and switch between several gateways (home LAN,
  Tailscale, cloud fallback).

- [ ] **WhatsApp integration** — `WhatsApp Business API` or `WA link` scheme for sending messages.

- [ ] **Notification listener** — read notification contents and optionally relay to OpenClaw
  (e.g. "read my latest message from Sarah").

- [ ] **Calendar voice read-back** — "what's on my calendar today?" handled locally.

- [ ] **Smart home pass-through** — if user says "turn off the living room lights",
  route to OpenClaw (which has Home Assistant / smart home skills).

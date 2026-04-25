# Jarvis Android

**A modern Android voice frontend for OpenClaw.**

Jarvis is a thin Android shell with instant local phone reflexes.  
OpenClaw Gateway handles sessions, memory, agents, skills, and routing.

---

## Architecture

```
User Voice ──► VoiceFrontend (STT)
                   │
                   ▼
           LocalIntentRouter
           ┌──────┴──────────────────┐
           │                         │
    ANDROID_LOCAL              OPENCLAW / MIXED
           │                         │
    AndroidActionExecutor      OpenClawClient (WebSocket)
           │                         │
           └──────────┬──────────────┘
                      │
               SessionEventLogger ──► OpenClaw Gateway
                      │
               OfflineQueueStore (if offline)
```

**Rule:** Routing decides who acts. Logging **always** goes to OpenClaw.

---

## Components

| Component | Purpose |
|---|---|
| `VoiceFrontend` | Push-to-talk, STT/TTS, flow coordinator |
| `LocalIntentRouter` | Routes to ANDROID\_LOCAL / OPENCLAW / MIXED |
| `IntentClassifier` | Keyword rules for phone reflex detection |
| `AndroidActionExecutor` | Executes local Android intents via capabilities |
| `CapabilityRegistry` | 11 capability implementations with permission guards |
| `OpenClawClient` | Ktor WebSocket client with reconnect/heartbeat/pairing |
| `SessionEventLogger` | Logs every interaction to OpenClaw + offline queue |
| `MemoryCandidateDetector` | Marks `memoryCandidate: true` for meaningful context |
| `OfflineQueueStore` | DataStore-backed JSONL queue, flushed on reconnect |
| `SettingsDataStore` | All settings persisted via DataStore Preferences |
| `PairingStore` | Pairing token in EncryptedSharedPreferences |

---

## Setup

### Prerequisites

- Android Studio Ladybug / Panda 4 or later  
- Android SDK 36 installed  
- JDK 17  
- An OpenClaw Gateway running and accessible

### Build

```bash
git clone https://github.com/chrisgwynne/jarvis-2.0.git
cd jarvis-2.0
./gradlew assembleDebug
```

Install to device:

```bash
./gradlew installDebug
```

### Run

1. Launch the app — it requests permissions on first run (mic, location, contacts, camera).
2. Open **Settings** (gear icon top-right).
3. Set **Gateway URL** to your OpenClaw gateway, e.g.:
   - LAN: `ws://192.168.1.100:8765`
   - Tailscale: `wss://your-node.tailnet.ts.net:8765`
4. Set **Session Key** and **Speaker Name**.
5. Return to main screen — the status badge shows `Connected` when the gateway is reachable.
6. Hold the **orb** to speak. Release to submit.

---

## Pairing Flow

On first connect, the gateway may respond with a `pairing.challenge` frame:
- The pairing code is displayed in the UI (Status: "Pairing…").
- Confirm the code in the OpenClaw admin UI or `openclaw pair <code>` CLI.
- The gateway replies with a `gateway.connected` frame containing a pairing token.
- The token is stored encrypted in `EncryptedSharedPreferences` and sent on every reconnect.

To reset pairing: Settings → Debug → **Clear Pairing Token**.

---

## OpenClaw WebSocket Protocol

### Connect frame (Android → Gateway)
```json
{
  "type": "node.connect",
  "role": "node",
  "deviceId": "<uuid>",
  "deviceName": "Jarvis Android",
  "pairingToken": "<token or null>",
  "capabilities": [
    { "id": "device",   "description": "…", "available": true,  "requiresPermission": false },
    { "id": "location", "description": "…", "available": false, "requiresPermission": true  }
  ]
}
```

### User message (Android → Gateway)
```json
{
  "type": "user.message",
  "sessionKey": "jarvis:chris:android",
  "text": "How is my Etsy shop doing?",
  "mode": "voice",
  "eventId": "<uuid>"
}
```

### Session event (Android → Gateway)
```json
{
  "type": "jarvis.session_event",
  "eventId": "…",
  "sessionKey": "jarvis:chris:android",
  "timestamp": "2026-04-25T10:00:00Z",
  "speaker": "chris",
  "input": { "mode": "voice", "text": "Turn on torch" },
  "route": { "chosen": "ANDROID_LOCAL", "intent": "torch_on", "confidence": 0.97 },
  "androidContext": { "device": "Google Pixel 9", "battery": "82%", "screenState": "on", "foregroundApp": "jarvis", "locationLabel": "London, UK" },
  "result": { "status": "success", "spokenReply": "Torch on.", "error": null },
  "memoryCandidate": false
}
```

### Gateway replies (Gateway → Android)
```json
{ "type": "gateway.connected", "sessionKey": "…", "token": "…", "nodeId": "…" }
{ "type": "assistant.reply",   "eventId": "…", "spokenReply": "…", "sessionKey": "…" }
{ "type": "node.invoke",       "correlationId": "…", "action": "torch_on", "params": {} }
{ "type": "heartbeat.ack" }
```

---

## Local Android Examples

| Utterance | Route | Notes |
|---|---|---|
| "Turn on torch" | `ANDROID_LOCAL` | Instant. Logged to OpenClaw. |
| "Volume up" | `ANDROID_LOCAL` | Instant. |
| "Open Spotify" | `ANDROID_LOCAL` | Fuzzy app match. |
| "Set a 10 minute timer" | `ANDROID_LOCAL` | Delegates to system alarm. |
| "Take a photo" | `ANDROID_LOCAL` | Opens camera intent. |
| "Look at this / screenshot" | `MIXED` | Capture locally, send to OpenClaw. |
| "Call Mum" | `ANDROID_LOCAL` | Requires confirmation unless trusted mode. |
| "Text Sarah saying I'm 5 min late" | `ANDROID_LOCAL` | Requires confirmation unless trusted mode. |

## OpenClaw Examples

| Utterance | Route |
|---|---|
| "How is my Etsy shop doing?" | `OPENCLAW` |
| "What should I work on today?" | `OPENCLAW` |
| "Remember that my supplier is Dave at Acme" | `OPENCLAW` (memoryCandidate: true) |
| "Write a product description for my new mug" | `OPENCLAW` |
| Anything ambiguous | `OPENCLAW` |

---

## Permissions

| Permission | Capability |
|---|---|
| `RECORD_AUDIO` | Voice / STT |
| `ACCESS_FINE_LOCATION` | LocationCapability |
| `CAMERA` | CameraCapability |
| `READ_CONTACTS` | ContactsCapability |
| `SEND_SMS` | SmsCapability |
| `CALL_PHONE` | CallsCapability |
| `READ_CALENDAR` / `WRITE_CALENDAR` | CalendarCapability |
| `POST_NOTIFICATIONS` | NotificationCapability |
| `FOREGROUND_SERVICE` | ScreenshotCapability |
| `SET_ALARM` | Timer/Alarm actions |

All permissions fail safely — if denied, the capability reports `available: false`
and returns a structured `CapabilityError`. Nothing pretends to succeed.

---

## Offline Behaviour

When the Gateway is unreachable:
- All local Android actions still work instantly.
- Session events queue in `OfflineQueueStore` (DataStore-backed JSONL).
- The UI status badge shows `Offline (N queued)`.
- On reconnect, queued events are flushed automatically.

---

## Security Notes

- SMS and phone calls require explicit confirmation unless **Trusted Mode** is enabled.
- Destructive actions require confirmation (configurable).
- No private context (location, screen) is sent unless the corresponding setting is explicitly enabled.
- Pairing token stored in `EncryptedSharedPreferences` (AES-256-GCM).
- No arbitrary file access or deletion.

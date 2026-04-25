# Jarvis Android — Manual Test Plan

Phase 4: State Machine + Reliability

---

## 1. State Machine Transitions

### 1.1 Normal PTT session
1. Open app, ensure state is `IDLE_LISTENING`.
2. Press and hold the orb. Verify state transitions to `WAKE_DETECTED` → `CAPTURING_COMMAND`.
3. Say "turn on the torch". Release button.
4. Verify: `TRANSCRIBING` → `ROUTING` → `EXECUTING_ANDROID` → `SPEAKING` → `RETURNING_TO_LISTENING` → `IDLE_LISTENING`.
5. Torch should activate; spoken reply heard.

### 1.2 Cancel mid-session
1. Start PTT session.
2. While in `CAPTURING_COMMAND`, say "cancel".
3. Verify state returns to `IDLE_LISTENING`; TTS stops.

### 1.3 Invalid state attempt
1. Open Debug screen (enable Debug Logs in Settings first).
2. Verify no red error entries in the event log for normal sessions.
3. If any invalid transitions appear, the error row is shown in red with the transition detail.

---

## 2. Bluetooth Audio

### 2.1 BT headset as mic + speaker
1. Connect a Bluetooth headset (SCO-capable).
2. Verify Debug screen shows `AUDIO: bluetooth_sco`.
3. Press PTT and speak — voice should route through BT mic.
4. Spoken reply should play through BT headset.

### 2.2 BT disconnect mid-session
1. Start a session (waiting for STT).
2. Turn off BT headset mid-capture.
3. Verify error recovery: spoken reply through phone speaker, state returns to `IDLE_LISTENING`.

### 2.3 A2DP output
1. Connect BT headset (A2DP).
2. Verify TTS reply plays through headset.
3. Debug screen shows `AUDIO: bluetooth_a2dp`.

---

## 3. Android Local Actions

### 3.1 Torch control
- "Turn on the torch" → torch activates, spoken confirmation.
- "Turn off the flashlight" → torch deactivates.

### 3.2 Volume control
- "Volume up" / "Volume down" / "Mute" / "Unmute" → respective audio change.

### 3.3 Send SMS
- "Send a message to [contact] saying [message]".
- If `confirmDestructive = true`: confirmation dialog appears.
- Say "yes" → SMS sent, or say "no" → cancelled.

### 3.4 WhatsApp message
- "Send WhatsApp to [contact] saying [message]".
- Falls back to SMS if WhatsApp not installed.

### 3.5 Make call
- "Call [contact]" → dialler opens or call placed (trust-dependent).

### 3.6 Open app
- "Open Spotify" / "Open Maps" → correct app launches.

### 3.7 Set timer
- "Set a timer for 5 minutes" → timer set, spoken confirmation.

### 3.8 Take photo / selfie
- "Take a photo" / "Take a selfie" → camera activates.

---

## 4. OpenClaw Integration

### 4.1 Gateway connected
1. Configure correct WebSocket URL in Settings.
2. Confirm gateway badge shows green "CONNECTED".
3. Say an open-ended query: "What's the weather like tomorrow?"
4. Verify: state reaches `WAITING_OPENCLAW`; reply spoken after response arrives.

### 4.2 Gateway offline → queue
1. Disconnect from gateway (turn off server).
2. Say a query. Verify: queue badge shows count ≥ 1, spoken reply confirms queued.
3. Reconnect gateway. Verify: queued messages sent, count resets.

### 4.3 Speaker context forwarded
1. Ensure voice profile enrolled and identity active.
2. Issue a query to OpenClaw.
3. On server side, verify `speaker`, `trustLevel`, and `identityConfidence` in the received frame.

---

## 5. Speaker Identity & Trust

### 5.1 Voice enrolment
1. Say "enrol my voice".
2. Follow prompts — speak 5 phrases, provide name and trust level.
3. After completion: Settings → Identity shows the new profile.

### 5.2 Identity recognition
1. Enrol at least one profile.
2. Start a new session (after session timeout).
3. Speak normally. Verify: Main screen badge shows `[name] · [trust]`.
4. Debug screen shows `SPEAKER: [name]`, `TRUST: owner` (or relevant level).

### 5.3 Low-confidence fallback
1. Speak quietly or with background noise.
2. Verify: session proceeds as `UNKNOWN`; restricted actions denied.

### 5.4 Trust-level permission enforcement
- **UNKNOWN**: DEVICE_CONTROL (torch) allowed; COMMUNICATION_SEND denied.
- **GUEST**: DEVICE_CONTROL allowed; COMMUNICATION_SEND denied.
- **TRUSTED**: COMMUNICATION_SEND allowed; OPENCLAW_REQUEST denied.
- **OWNER**: all actions allowed.

---

## 6. Confirmation Flow

### 6.1 Voice yes/no confirmation
1. Trigger a destructive action (SMS with `confirmDestructive = true`).
2. Confirmation dialog appears on screen.
3. Say "yes" → action executes.
4. Repeat, say "no" → action cancelled.

### 6.2 Speaker binding
1. Two profiles enrolled.
2. Profile A triggers a confirmation.
3. Profile B speaks "yes" → WrongSpeaker, action not executed.

### 6.3 Timeout
1. Trigger confirmation.
2. Wait 30 seconds without responding.
3. Verify: pending action cleared; "timed out" message spoken.

---

## 7. Error Recovery

### 7.1 STT failure
1. Deny microphone permission mid-session (via Android settings).
2. Verify: error recovery spoken, state returns to `IDLE_LISTENING`.

### 7.2 Malformed OpenClaw response
1. Configure gateway to return malformed JSON.
2. Verify: spoken error "I received an unexpected response"; state recovers to idle.

### 7.3 Contact ambiguity
1. Have two contacts with the same first name.
2. Say "Call [name]".
3. Verify: spoken message asks for clarification; no call placed.

---

## 8. Conversation Recording

### 8.1 Enable recording
1. Enable "Conversation Recording" in Settings.
2. Run several sessions.
3. Verify WAV files appear in app's files directory.

### 8.2 Auto-delete
1. Set retention to 1 hour.
2. Fast-forward device clock by 2 hours (or wait).
3. Verify: recordings older than 1 hour deleted on next session start.

---

## 9. Debug Screen

1. Enable "Debug Logs" in Settings.
2. Bug icon appears in top bar of Main screen.
3. Tap bug icon → Debug screen opens.
4. Run a full session; verify:
   - State updates correctly in real time.
   - Event log shows all transitions with timestamps.
   - Speaker/trust/gateway/audio rows update.
5. Tap CLEAR → event log empties.
6. Error entry shows red when invalid transition attempted.

---

## 10. Always-Listening Service

### 10.1 Wake word activation
1. Enable "Always Listening" and set wake phrase (e.g. "hey jarvis").
2. Lock screen / background app.
3. Say wake phrase → notification dismisses, session starts, orb animates.

### 10.2 Boot restart
1. Enable Always Listening.
2. Reboot device.
3. Verify: AlwaysListeningService starts automatically (check notification).

---

## Pass Criteria

- All state transitions visible in Debug screen.
- No unhandled crashes during any scenario.
- Trust-level restrictions enforced correctly in all combinations.
- BT audio routes correctly on both SCO and A2DP paths.
- Gateway offline triggers queue; queue drains on reconnect.
- Confirmation flow: correct speaker binding and timeout behaviour.

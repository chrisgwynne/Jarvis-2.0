# GitHub Issue Logging — Implementation TODO

This document tracks incremental progress on the GitHub Issue Logging feature.
Each item is committed individually so partial work survives if a later step crashes.

## Phases

### Phase 1 — Scaffolding
- [x] Create this todo file and module skeleton
- [x] Create package layout under `app/src/main/java/com/jarvis/githubissues/`

### Phase 2 — Settings model
- [x] `GitHubIssueLoggingSettings` data class
- [x] `Severity` enum (info / warning / error / critical)
- [x] `DedupeWindow` enum (1h / 24h / 7d)
- [x] `FailureCategory` enum (errors / unsupported / permission / openclaw_offline / openclaw_malformed / cant_do_that / repeated_stt_tts / etc.)
- [x] Encrypted token storage (`SecureTokenStore`)
- [x] Persistence layer (`GitHubIssueSettingsRepository`)

### Phase 3 — Data model
- [x] `IssueDraft` data class (title, body, labels, fingerprint, severity, category, timestamps)
- [x] `IssueContext` (state, route, intent, capability snapshot, device info, session)
- [x] `IssueEvent` sealed class (failure events that can become issues)
- [x] `RedactionPolicy` for transcripts, contacts, tokens, etc.

### Phase 4 — Redaction
- [x] `Redactor` — phone, email, location, tokens, contact names
- [x] Configurable allowances (message body, transcripts)
- [x] Never include raw audio / full auth tokens / OpenClaw memory

### Phase 5 — Deduplication
- [x] `IssueDeduplicator` — fingerprinting (category + intent + errorCode + actionType + state)
- [x] In-memory + persisted dedupe table
- [x] Window check (1h / 24h / 7d)
- [x] Optional comment-on-existing-issue with occurrence count

### Phase 6 — Issue body builder
- [x] `IssueBodyBuilder` — Markdown template per spec
- [x] Title formatter `[Jarvis][severity][category] short description`

### Phase 7 — GitHub REST client
- [x] `GitHubApiClient` — POST /repos/{owner}/{repo}/issues, POST issue comments
- [x] Auth via Bearer token (read from SecureTokenStore)
- [x] Test-connection endpoint (GET /repos/{owner}/{repo})

### Phase 8 — Offline queue
- [x] `IssueQueue` — JSON-on-disk persistence, survives app restart
- [x] Retry with backoff
- [x] Queue counter for settings UI

### Phase 9 — Logger orchestrator
- [x] `GitHubIssueLogger` — central facade
- [x] Hooks: `onErrorRecovery`, `onUnsupported`, `onActionFailure`,
      `onOpenClawFailure`, `onVoiceFailure`, `onRoutingFailure`,
      `onUserCorrection`, `onCantDoThat`
- [x] Severity gating
- [x] Category gating

### Phase 10 — Integrations
- [x] State machine hook → ERROR_RECOVERY transitions
- [x] OpenClaw bridge → `jarvis.github_issue_created` session event
- [x] Voice pipeline hooks (STT/TTS repeated failure tracker)
- [x] Action executor hooks (SMS / WhatsApp / call / app / contact / screenshot / location)
- [x] Routing/intent hooks (low confidence, route changed, user-correction phrases)

### Phase 11 — User-correction detector
- [x] `UserCorrectionDetector` — phrase matching for "that's wrong", "you misunderstood", etc.
- [x] Captures previous command / route / result + correction transcript

### Phase 12 — Settings UI
- [x] Toggle, repo, token field, labels, categories, severity, dedupe window
- [x] Test connection / create test issue buttons
- [x] Queued issue count + recent issue log
- [x] Redaction options

### Phase 13 — Debug screen
- [x] "Create issue from this event" button on debug events

### Phase 14 — Tests
- [x] Redactor unit tests
- [x] Deduplicator unit tests
- [x] Title/body formatting tests
- [x] Logger gating tests

## Out of scope
- Rebuilding any existing Jarvis subsystem
- Direct GitHub OAuth (token-based only)
- Multi-repo fan-out (single repo per device)

## How to wire this into the existing Jarvis app

1. Construct once at app startup:

       val repo = GitHubIssueSettingsRepository(appContext)
       val client = GitHubApiClient(repo)
       val queue = IssueQueue(File(appContext.filesDir, "jarvis_github_issue_queue.json"))
       val deduper = IssueDeduplicator(
           PersistedDedupeStore(File(appContext.filesDir, "jarvis_github_dedupe.json"))
       )
       val redactor = Redactor(RedactionPolicy(repo.current().redaction))
       val builder = IssueBodyBuilder(redactor, settings = { repo.current() })
       val logger = GitHubIssueLogger(repo, deduper, builder, client, queue, openClawBridge)
       IssueQueueWorker(queue, client, onIssueCreated = logger::onQueuedIssuePosted).start()

2. From the existing state-machine emitter, voice pipeline, action
   executor, intent router, and OpenClaw bridge, call the matching
   integration hook (StateMachineHook / VoicePipelineHook /
   ActionExecutorHook / RoutingHook / OpenClawHook). Each hook is a
   thin adapter — none of them depend on Jarvis-specific types.

3. From the post-TTS transcript handler, push transcripts through
   `UserCorrectionDetector.maybeReport` and call
   `rememberLastCommand` after each successful command execution.

4. Drop `GitHubIssueLoggingScreen(viewModel)` into the existing
   settings navigation graph.

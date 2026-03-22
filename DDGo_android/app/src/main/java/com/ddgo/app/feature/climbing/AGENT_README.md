# Climbing README

AI agent working guide for `feature/climbing`.

If this document conflicts with code, code wins.

## System Map
- `climbing = record + upload`
- Root menu entry: [`ClimbingScreen.kt`](./ClimbingScreen.kt)
- `record/`
  - Purpose: live recording, live pose analysis, realtime AI session prework
  - Shape: `Route + ViewModel + Page`
- `upload/`
  - Purpose: managed video flow, hold detection, pre-pose, submit, result, final analysis
  - Shape: `UploadViewModel + delegates + analysis ui`

## Production Flows
### Record Flow
1. Enter `recordGraph`
2. Bind CameraX
3. Start live pose analyzer
4. Start recording
5. Stream pose frames during recording
6. Start realtime AI session when possible
7. Stop recording
8. Build `RecordedAttemptDraft`
9. Navigate to upload with `recordedVideoUri + realtimeSessionId`

### Upload Flow: Full Challenge
1. `ATTEMPT_UPLOAD`
2. `CHALLENGE_CREATE`
3. `CHALLENGE_HOLD`
4. `HOLD_SELECT`
5. `ANALYSIS_LOADING (AttemptResultPreparation)`
6. `ATTEMPT_RESULT`
7. `ANALYSIS_LOADING (FinalAnalysisPreparation)`
8. `FINAL_ANALYSIS`

### Upload Flow: Attempt Only
1. `ADDITIONAL_UPLOAD`
2. `ANALYSIS_LOADING (AttemptResultPreparation)`
3. `ATTEMPT_RESULT`
4. `FINAL_ANALYSIS`

### Upload Flow: Local / Dev Variants
- `CHALLENGE_COLOR`: local-analysis path that skips gym/level entry
- `DEV_IMAGE_PICKER`: debug path that bypasses best-frame extraction from video

## Record Subsystem
### Source of Truth
- Route / camera / permission / CameraX binding: [`record/ui/RecordRoute.kt`](./record/ui/RecordRoute.kt)
- Recording session state: [`record/presentation/RecordViewModel.kt`](./record/presentation/RecordViewModel.kt)
- Stateless UI: [`record/ui/RecordPage.kt`](./record/ui/RecordPage.kt)
- Contracts: [`record/presentation/RecordContract.kt`](./record/presentation/RecordContract.kt)
- Navigation bridge to upload: [`record/RecordNavigation.kt`](./record/RecordNavigation.kt)

### Owner
- `RecordRoute`
  - Owner of CameraX, `PreviewView`, `ProcessCameraProvider`, `VideoCapture`, analyzer executor
- `RecordViewModel`
  - Owner of recording state, live pose state, realtime AI session state, buffered frames, recorded draft

### Reads
- Camera permission
- CameraX preview / frames
- Live pose analyzer repository
- User body profile / realtime AI session use cases

### Writes
- `RecordUiState`
- `RecordedAttemptDraft`
- Realtime session handle / buffered chunk state

### Triggers
- `onCameraBound`
- `onRecordingStarted`
- `submitLivePoseFrame`
- `onRecordingStopped`
- `onRecordingFailed`

### Contracts
- Record to upload handoff currently uses only:
  - `recordedVideoUri`
  - `realtimeSessionId`
- `thumbnailFrame`, `frameWidthPx`, `frameHeightPx` exist on `RecordedAttemptDraft` but are not part of the upload navigation contract yet.

### Do Not Put Logic Here
- Do not move upload business logic into `RecordPage`
- Do not make `RecordRoute` own long-lived session state
- Do not assume record and upload share the same delegate architecture

## Upload Subsystem
### Public Facade
- Screen-facing entrypoint: [`upload/UploadViewModel.kt`](./upload/UploadViewModel.kt)
- Screens should call `UploadViewModel`, not delegates directly.

### Async State Contracts
- [`upload/UploadAsyncUiStates.kt`](./upload/UploadAsyncUiStates.kt)
- Important types:
  - `UploadSubmissionUiState`
  - `AnalysisLoadingPhase`
  - `FinalAnalysisPreparationUiState`
  - `BackgroundUploadState`
  - `BackgroundUploadNotice`

### Important Meanings
- `UploadSubmissionUiState.Success`
  - Means `attempt_result` can open
  - Does not mean all background upload and all final AI work are finished
- `AnalysisLoadingPhase`
  - `AttemptResultPreparation`
  - `FinalAnalysisPreparation`
- `publishedAttemptResultSession`
  - Source of truth for currently restorable attempt-result session
- `selectionGeneration`
  - Stale protection key for selection-bound caches and async completions

## Upload Owners
### `UploadViewModel`
- File: [`upload/UploadViewModel.kt`](./upload/UploadViewModel.kt)
- Owner
  - Public facade
  - Cross-delegate orchestration
  - Route phase control
- Reads
  - Delegate-owned state from session / hold / submission / challenge
- Writes
  - Public screen-facing state
  - `analysisLoadingPhase`
  - High-level orchestration decisions
- Triggers
  - `onPrimaryVideoPrepared`
  - `submitUpload`
  - `prepareAttemptResultAnalysisLoading`
  - `prepareFinalAnalysisLoading`
- Do not put logic here
  - Do not copy delegate-owned caches into parallel ViewModel-owned structures
  - Do not bypass delegates from screens

### `UploadSessionDelegate`
- File: [`upload/UploadSessionDelegate.kt`](./upload/UploadSessionDelegate.kt)
- Owner
  - Managed video / temp file state
  - Pre-pose queue / worker / cache
  - Result playback URIs
  - `publishedAttemptResultSession`
- Reads
  - Current attempt URIs
  - Selection generation
- Writes
  - `PrePoseCacheEntry`
  - `PrePoseBatchState`
  - Managed video mappings
  - Published attempt session
- Triggers
  - `refreshCurrentSelectionPrePoseTargets`
  - `awaitSubmitReadyPrePose`
  - Cleanup / retention flows
- Do not put logic here
  - Do not move submit policy here
  - Do not duplicate result-session state in submission code

### `UploadHoldDetectionDelegate`
- File: [`upload/UploadHoldDetectionDelegate.kt`](./upload/UploadHoldDetectionDelegate.kt)
- Owner
  - Hold precompute cache
  - Best frame extraction
  - Raw YOLO holds
  - Rich color classification cache
  - Selected start / end hold
  - Hold numbering
- Reads
  - Current source video URI or debug best-frame image
  - Current hold color
- Writes
  - `HoldDetectionPrecomputeEntry`
  - `detectedHolds`
  - `allRawHolds`
  - `numberedHolds`
- Triggers
  - `requestHoldPrecompute`
  - `precomputeHoldDetection`
  - `applyHoldColorFilter`
  - `runHoldDetection` fallback
- Do not put logic here
  - Do not start pre-pose directly from this delegate
  - Do not put submit/final-analysis policy here

### `UploadSubmissionDelegate`
- File: [`upload/UploadSubmissionDelegate.kt`](./upload/UploadSubmissionDelegate.kt)
- Owner
  - Attempt-result preparation
  - Final-analysis preparation
  - Batch AI prewarm
  - Background upload
  - Result publish
- Reads
  - Reusable pre-pose snapshot
  - Numbered holds
  - Best frame bitmap
  - AI profile
- Writes
  - `UploadSubmissionUiState`
  - `FinalAnalysisPreparationUiState`
  - `BackgroundUploadState`
  - `attemptHoldReachResults`
  - `attemptAiAnalysisResults`
  - Published attempt-result session
- Triggers
  - `submitUploadForAttemptResult`
  - `requestAnalysisPrewarm`
  - `ensureFinalAnalysisReady`
  - `retryBackgroundAttemptUpload`
- Do not put logic here
  - Do not own temp-file / pre-pose cache lifecycle
  - Do not treat attempt-result and final-analysis as the same stage

## Hold / Pre-pose / AI Pipeline
### Current Active Order
1. `onPrimaryVideoPrepared`
2. Hold precompute requested
3. Hold precompute runs
   - best frame
   - raw YOLO
   - classify-all-rich
4. Hold precompute reaches terminal
5. Pre-pose queue starts
6. User picks start / end holds
7. Numbered holds become ready
8. Analysis prewarm can start
9. `AttemptResultPreparation` waits for reusable pre-pose and computes hold reach
10. `FinalAnalysisPreparation` consumes batch AI prewarm

### Hold Detection Notes
- Hold detection is no longer "always run everything on hold screen".
- Current model:
  - precompute cache
  - color projection
  - same-input ready reuse
  - running dedupe
  - fallback full run only when needed
- `ChallengeHoldScreen` is a cached-result consumer plus fallback launcher.
- `HoldSelectScreen` is a two-phase selection UI. It is not the owner of numbering logic.

### Pre-pose Notes
- Pre-pose does not start from `HoldSelectScreen`.
- Pre-pose usually starts after hold precompute becomes terminal.
- `analysis_loading` is usually a wait/consume phase, not the original start point.

## Attempt Result vs Final Analysis
### `ATTEMPT_RESULT`
- Main file: [`upload/AttemptResultScreen.kt`](./upload/AttemptResultScreen.kt)
- Purpose
  - Fast per-attempt result screen
  - Playback + hold reach + pose timeline
- Dependency rule
  - Must remain batch-AI-optional
  - Can render from pre-pose timeline or fallback state even when batch AI is not ready

### `FINAL_ANALYSIS`
- Main route: [`upload/ui/analysis/route/FinalAnalysisRoute.kt`](./upload/ui/analysis/route/FinalAnalysisRoute.kt)
- Main page: [`upload/ui/analysis/page/FinalAnalysisPage.kt`](./upload/ui/analysis/page/FinalAnalysisPage.kt)
- Summary helper: [`upload/FinalAnalysisSummary.kt`](./upload/FinalAnalysisSummary.kt)
- Purpose
  - Cross-attempt AI summary
  - Stability / failure reason / aggregate metrics
- Dependency rule
  - Depends on batch AI results
  - Route builds page state from `UploadViewModel`
  - Lower analysis UI should stay pure and consume derived state only

## Mutation Guide
### Change recording / live pose / realtime session behavior
- Edit:
  - [`record/ui/RecordRoute.kt`](./record/ui/RecordRoute.kt)
  - [`record/presentation/RecordViewModel.kt`](./record/presentation/RecordViewModel.kt)
- Avoid:
  - Pushing CameraX logic into upload

### Change route flow or analysis-loading phase transitions
- Edit:
  - [`upload/UploadNavigation.kt`](./upload/UploadNavigation.kt)
  - [`upload/UploadViewModel.kt`](./upload/UploadViewModel.kt)
  - [`upload/UploadAsyncUiStates.kt`](./upload/UploadAsyncUiStates.kt)

### Change hold best-frame / YOLO / color filter / numbering
- Edit:
  - [`upload/UploadHoldDetectionDelegate.kt`](./upload/UploadHoldDetectionDelegate.kt)
- UI-only edits:
  - [`upload/ChallengeCreateScreen.kt`](./upload/ChallengeCreateScreen.kt)
  - [`upload/ChallengeHoldScreen.kt`](./upload/ChallengeHoldScreen.kt)
  - [`upload/HoldSelectScreen.kt`](./upload/HoldSelectScreen.kt)

### Change pre-pose queue / retry / stale protection / result-session retention
- Edit:
  - [`upload/UploadSessionDelegate.kt`](./upload/UploadSessionDelegate.kt)

### Change attempt-result gating / final-analysis gating / background upload / AI prewarm
- Edit:
  - [`upload/UploadSubmissionDelegate.kt`](./upload/UploadSubmissionDelegate.kt)

### Change final-analysis UI layout only
- Edit:
  - [`upload/ui/analysis`](./upload/ui/analysis)
- Avoid:
  - Reaching back into delegates from page/organism code

## Invariants
- Screens call `UploadViewModel` or `RecordViewModel`, not delegates directly.
- Delegate-owned state should have one owner.
- `selectionGeneration` must guard selection-bound async completions.
- `analysis_loading` is phase-based. Do not treat it as one undifferentiated state.
- `attempt_result` and `final_analysis` are different products with different data guarantees.
- Background upload is non-blocking for result navigation.
- `publishedAttemptResultSession` remains session-owned state, not submission-owned state.

## Anti-patterns
- Do not copy session state into submission state just for convenience.
- Do not put business logic into composables.
- Do not make `AttemptResultScreen` depend on batch AI readiness.
- Do not make `FinalAnalysisRoute` rebuild ownership that belongs in `UploadViewModel`.
- Do not document dead code or historical flow as if it were active flow.

## Debug-only Tooling
- These are not production climbing flow components.
- Useful files:
  - `app/src/debug/java/com/ddgo/app/feature/debug/PrePoseLandmarker*`
  - `app/src/debug/java/com/ddgo/app/feature/debug/DebugPose*`
  - [`upload/UploadAiTraceLogger.kt`](./upload/UploadAiTraceLogger.kt)
- Use cases:
  - Pre-pose path comparison
  - GPU toggle experiments
  - FPS-limit comparison
  - Pose / pre-pose JSON export
  - Upload flow trace logging
- Logcat filter for upload AI trace:
  - `tag:UploadAiTrace`

## Quick Start for AI Agents
- Need to change recording? Start in `record/`.
- Need to change route flow or loading phase? Start in `UploadNavigation` and `UploadViewModel`.
- Need to change hold detection or numbering? Start in `UploadHoldDetectionDelegate`.
- Need to change pre-pose lifecycle? Start in `UploadSessionDelegate`.
- Need to change result gating, final-analysis gating, background upload, or AI prewarm? Start in `UploadSubmissionDelegate`.
- Need to change final-analysis visuals only? Stay inside `upload/ui/analysis`.

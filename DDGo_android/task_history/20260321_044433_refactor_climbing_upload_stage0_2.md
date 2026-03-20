# Upload Refactor Stage 0-2 Stabilization Log

## Summary

- Goal: keep upload behavior unchanged while hardening Stage 0 regression coverage and extracting the Stage 1 shared handoff contract.
- Scope in this workspace: Stage 0 test stabilization, Stage 1 shared contract extraction, plus climbing docs.
- `UploadViewModel.kt` remains untouched in this phase.

## Baseline

Current workspace baseline:

- `UploadViewModel.kt`: 2757 lines
- `ChallengeCreateScreen.kt`: 2214 lines
- `AttemptResultScreen.kt`: 742 lines
- `AnalysisLoadingScreen.kt`: 163 lines

## What Was Stabilized

- `UploadViewModelTest` helper now matches the current upload ViewModel state shape.
  - `MutableStateFlow` fields are updated via `.value`
  - `MutableState` fields are still supported
- Duplicate-submit regression is now pinned in unit tests.
  - `submitUpload()` must no-op when `_uploadSubmissionUiState` is already `Loading`
  - the test verifies zero calls to save/upload/end submission use cases
- Temp-file cleanup regression remains pinned in unit tests.
  - referenced selection/result/pre-pose files are preserved
  - orphan temp files are removed
- Existing selection/reselection tests continue to guard pre-pose and session reuse behavior.
- A targeted Compose UI regression test was added for `AnalysisLoadingScreen`.
  - it verifies the `Idle -> submitUpload()` path stays exactly-once across re-entry
  - it uses a mocked `UploadViewModel` and a real Compose rule
- Stage 1 shared handoff contracts were extracted into `feature/climbing/shared/*`.
  - `ClimbingRecordThumbnailFrame`
  - `ClimbingRecordedAttemptDraft`
  - `ClimbingUploadEntryArgs`
  - upload route building and parsing helpers
  - record/upload consumers were updated to use the shared contract while keeping behavior identical

## Current Decisions

- `UploadViewModel` stays a single graph-scoped shell.
- `record` and `upload` do not share a ViewModel.
- Cross-feature sharing is limited to `feature/climbing/shared/*` contracts and handoff payloads.
- `Stage 2` production seam extraction remains planned, not implemented here.

## Checkpoints

- Checkpoint A
  - Stage 0 test hardening in place
  - duplicate-submit guard pinned
  - temp cleanup guard pinned

- Checkpoint B
  - Stage 1 shared handoff contract extraction completed

- Checkpoint C
  - reserved for Stage 2 session seam extraction

## Stage Log

### Stage 0

- Status: completed for test/docs stabilization
- Added:
  - duplicate-submit regression in `UploadViewModelTest`
  - `MutableStateFlow`-aware test helper
  - targeted `AnalysisLoadingScreen` Compose UI regression test
  - climbing feature guide refresh
- Notes:
  - `AnalysisLoadingScreen` exactly-once behavior is still guarded at unit-test level
  - the Compose UI test lives in `androidTest` so the screen-level exact-once contract is covered in addition to the unit guard

### Stage 1

- Status: completed
- Focus:
  - shared record-upload contract
  - upload type/contract extraction
- Notes:
  - implemented via `ClimbingRecordThumbnailFrame`, `ClimbingRecordedAttemptDraft`, and `ClimbingUploadEntryArgs`
  - record/upload navigation now share a single route-arg contract
  - `RecordContract.kt` uses `typealias` bridges so existing record callers remain stable

### Stage 2

- Status: planned
- Focus:
  - `PrePoseSessionManager`
  - `AttemptResultSessionStore`
  - temp-file retention and published result session preservation

## Risks To Watch

- duplicate submit from loading-screen recomposition
- stale selection-generation overwriting current session state
- temp-file cleanup accidentally dropping referenced playback files
- accidental `record` -> `UploadViewModel` coupling

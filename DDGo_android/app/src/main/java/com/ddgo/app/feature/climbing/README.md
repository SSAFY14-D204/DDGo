# Climbing Feature Guide

## Purpose

This guide documents the working rules for `feature/climbing/*` so upload and record can evolve safely without reintroducing cross-feature coupling.

The current priority is not architectural cleanup for its own sake. The priority is preserving behavior while separating responsibilities in ways that are easy to review, test, and bisect.

## Feature Boundaries

- `record` and `upload` keep their own `ViewModel`.
- Do not make `RecordViewModel` depend on `UploadViewModel`.
- Do not make `UploadViewModel` depend on `RecordViewModel`.
- Cross-feature sharing is allowed only through `feature/climbing/shared/*` contracts, draft payloads, and navigation args.

## Upload Rules

- `UploadViewModel` stays a single flow-shell until submission orchestration is extracted in the final stage.
- The upload nav graph keeps a graph-scoped `UploadViewModel`.
- `submitUpload()` stays in `UploadViewModel` until the last refactor stage.
- `_uploadSubmissionUiState` is owned by `UploadViewModel`, then later by an orchestrator entrypoint if one is introduced.

## Session Seam Rules

- `PrePoseSessionManager` owns only the session kernel:
  - managed video normalization
  - selection preparation
  - pre-pose queue/cache/worker
  - terminal snapshot calculation
- `PrePoseSessionManager` must not own screen loading messages.
- `AttemptResultSessionStore` owns:
  - result playback ownership
  - published result session capture/restore
  - cleanup keep-set participation
- `PrePoseSessionManager` and `AttemptResultSessionStore` must be introduced together because temp-file cleanup depends on both active pre-pose work and published result playback.

## UI Rules

- `Route` owns side effects only:
  - ExoPlayer setup/dispose
  - auto-seek
  - polling loop
  - listener registration
  - seek side effects
- `Page`, `Organism`, `Molecule`, and `Atom` receive only `UiState` and callbacks.
- Grouped `UiState` is introduced only when the corresponding screen is actually split.
- Do not introduce micro-state files early if the screen still has a single source of truth in the ViewModel.

## Shared Contracts

- Shared climbing contracts belong under `feature/climbing/shared/*`.
- Candidate shared artifacts:
  - record-to-upload draft payloads
  - realtime session handoff payloads
  - upload entry args
- Do not move upload-only session managers or orchestration into `shared`.

## QA Gates

- Stage 0/1:
  - graph-scoped `UploadViewModel` is preserved
  - `AnalysisLoadingScreen` still triggers upload exactly once
- Stage 2:
  - pre-pose reuse works
  - pre-pose failure does not auto-retry
  - attempt-only cancel restores published result session
  - temp files are kept for active pre-pose, result playback, and published result playback
- Later stages:
  - player side effects remain route-owned
  - hold reselection keeps pre-pose and resets hold reach only

## Refactor Stop Points

- Recommended default stop point: Stage 2
- Structure-first stop point: Stage 3
- Submission orchestration split stays last

# Upload Refactor Stage 0~2 Task History

## Summary

- Goal: refactor upload safely through Stage 2 while keeping behavior unchanged.
- Strategy: keep `UploadViewModel` as the graph-scoped single flow-shell, then extract stateful seams before UI-heavy restructuring.
- Default stop point: Stage 2.

## Baseline

Workspace baseline captured on this branch:

- `UploadViewModel.kt`: 2757 lines
- `ChallengeCreateScreen.kt`: 2214 lines
- `AttemptResultScreen.kt`: 742 lines
- `AnalysisLoadingScreen.kt`: 163 lines

## Core Decisions

- `record` and `upload` do not share a `ViewModel`.
- Cross-feature sharing is limited to `feature/climbing/shared/*`.
- `PrePoseSessionManager` and `AttemptResultSessionStore` must ship in the same stage.
- `submitUpload()` remains in `UploadViewModel` through Stage 2.
- `Route` owns player/seek/listener/polling side effects.

## Checkpoints

- Checkpoint A
  - baseline docs and regression guards
- Checkpoint B
  - upload type/contract extraction only
- Checkpoint C
  - session seam extracted through Stage 2

## Stage Log

### Stage 0

- Status: in progress
- Focus:
  - baseline docs
  - regression gates
  - shared boundary rules

### Stage 1

- Planned focus:
  - shared record-upload handoff contract
  - upload contract/type extraction

### Stage 2

- Planned focus:
  - `PrePoseSessionManager`
  - `AttemptResultSessionStore`
  - temp-file cleanup keep-set preservation

## Risks To Watch

- duplicate submit from loading screen recomposition
- pre-pose cache invalidation drift
- published result playback loss during cleanup
- accidental `record` -> `UploadViewModel` coupling

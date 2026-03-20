# Upload Refactor Stage 0~2 Task History

## Summary

- Goal: refactor upload safely through Stage 2 while keeping behavior unchanged.
- Strategy: keep `UploadViewModel` as the graph-scoped single flow-shell, then extract stateful seams before UI-heavy restructuring.
- Default stop point: Stage 2.

## Baseline

Workspace baseline captured on this branch:

- `UploadViewModel.kt`: 2456 lines
- `ChallengeCreateScreen.kt`: 2038 lines
- `AttemptResultScreen.kt`: 696 lines
- `AnalysisLoadingScreen.kt`: 151 lines

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
- Added regression gates:
  - `submitUpload` loading-guard regression remains pinned in `UploadViewModelTest`
  - `cleanupUnusedManagedTempFiles` now verifies referenced temp-file preservation and orphan deletion
  - record-to-upload handoff codec is pinned with `ClimbingUploadEntryArgsTest`
- Notes:
  - upload graph-scoped `UploadViewModel` sharing is still verified structurally in `UploadNavigation.kt`
  - automated navigation-scope verification is deferred until a dedicated test harness is worth the churn

### Stage 1

- Status: completed
- Focus:
  - shared record-upload handoff contract
  - upload contract/type extraction
- Added:
  - `ClimbingUploadNavigator` to remove `record -> upload.navigateToUpload` direct dependency
  - extracted pure upload types/helpers into `upload/presentation/*`
  - kept moved files on the existing root package to minimize import churn during Stage 1
- Current branch effect:
  - `UploadViewModel.kt` reduced from `2456` lines to `2321` lines before Stage 2 seam work
  - Checkpoint B is now the last known good refactor point before session seam extraction

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

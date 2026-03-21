# Upload Refactor Stage 0-2 Stabilization Log

## Summary

- Goal: recover a buildable Stage 2 checkpoint and lock the regression surface before any Stage 3 UI split.
- Scope in this pass: shared navigation contract fix, pre-pose/session lifecycle stabilization, Stage 2 regression test hardening, and climbing guide/task history sync.
- Out of scope: Stage 3 route/page split, submission orchestration extraction, deeper manager/store encapsulation.

## Current Checkpoint

Current workspace counts after this stabilization pass:

- `UploadViewModel.kt`: 2286 lines
- `ChallengeCreateScreen.kt`: 2214 lines
- `AttemptResultScreen.kt`: 742 lines
- `AnalysisLoadingScreen.kt`: 163 lines
- `PrePoseSessionManager.kt`: 558 lines
- `AttemptResultSessionStore.kt`: 69 lines

## What Changed In This Pass

- Shared upload entry parsing now decodes both `recordedVideoUri` and `realtimeSessionId` consistently.
- Blank route values are normalized back to `null` before they reach upload entry state.
- The upload-local `navigateToUpload()` helper was removed.
  - the only public record-to-upload entrypoint is now `navigateToClimbingUpload()` under `feature/climbing/shared/navigation/*`
- `PrePoseSessionManager` now remembers the latest cleanup keep-set.
  - when an active pre-pose worker leaves the retention set, cleanup is re-evaluated immediately
  - orphan temp files are no longer left behind waiting for a later cleanup trigger
- `UploadViewModelTest` was hardened to match the current constructor and delegated state shape.
  - AI realtime use cases are now mocked in the test factory
  - delegated `MutableStateFlow` / `MutableState` properties are updated through helpers instead of replacing backing objects
- Stage 2 regression tests were extended.
  - duplicate submit guard
  - stale selection generation keeps the latest pre-pose entry active
  - stale worker completion cleans orphan temp files after reselection
- `AnalysisLoadingScreenTest` was added in `androidTest`.
  - it locks the screen-level `Idle -> submitUpload()` exactly-once behavior across re-entry
- `ClimbingUploadEntryArgsTest` was moved to `androidTest`.
  - route encoding/decoding depends on Android `Uri`/`Bundle`, so instrumentation is the correct layer for that contract
- `FinalAnalysisSummaryTest` was updated to assert the current summary contract instead of stale narrative substrings.

## Validation

Commands successfully completed in this pass:

- `./gradlew.bat :app:compileDebugKotlin --no-daemon`
- `./gradlew.bat :app:compileDebugAndroidTestKotlin --no-daemon "-Pksp.incremental=false"`
- `./gradlew.bat testDebugUnitTest --no-daemon "-Pksp.incremental=false" --tests "com.ddgo.app.feature.climbing.upload.UploadViewModelTest" --tests "com.ddgo.app.feature.climbing.upload.FinalAnalysisSummaryTest"`
- `./gradlew.bat :app:assembleDebug --no-daemon "-Pksp.incremental=false"`

Instrumentation execution status:

- `connectedDebugAndroidTest` reached device install but failed before execution with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
- Cause: the connected device already had `com.ddgo.app` installed with a different signing key.
- Result: `AnalysisLoadingScreenTest` and `ClimbingUploadEntryArgsTest` were compiled but not executed on-device in this pass.

## Checkpoints

- Checkpoint A
  - baseline upload regression guards are in place
- Checkpoint B
  - shared handoff contract and extracted upload/session types are in place
- Checkpoint C
  - Stage 2 session seam is stabilized, buildable, and ready for QA review

## Risks Still Deferred

- Stage 3 route/page split is intentionally not started here.
- `PrePoseSessionManager` / `AttemptResultSessionStore` still expose some mutable state through `UploadViewModel` delegation.
- On-device instrumentation execution still needs either uninstall/reinstall approval or a matching-signed debug build on the connected device.

## Next Recommended Step

- Stop at Stage 2.
- Run manual app QA on the current checkpoint.
- Only after QA approval, begin Stage 3 route/page separation.
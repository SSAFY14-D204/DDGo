# DDGo Android Final Architecture Reorg Plan

> This document consolidates the team decisions from the UI architecture discussion.
> Goal: apply clean architecture correctly, separate presentation from UI, and use Atomic Design only inside the UI layer.

---

## 1. Principles

### 1.1 What we are optimizing for

- Functional composables
- Small and reusable UI components
- Separation of UI and business logic
- Clean architecture with correct dependency direction
- Atomic Design applied only to the UI layer
- Code that is easy to read, test, and change

### 1.2 What each concept means in this project

- Clean Architecture:
  domain and data are separated by interfaces, and dependencies always point inward.
- Presentation:
  `ViewModel`, `UiState`, `UiEvent`, and screen-level state mapping live here.
- UI:
  Compose rendering and user interaction live here.
- Atomic Design:
  `atom -> molecule -> organism -> page` is used only inside the UI layer.

### 1.3 Core rule

```text
data -> domain
feature/presentation -> domain
feature/ui -> feature/presentation
feature/ui -> core/ui
domain -X-> data
domain -X-> feature
```

---

## 2. Final Top-Level Structure

```text
app/src/main/java/com/ddgo/app/
  core/
    datastore/
    network/
    ui/
      tokens/
      atom/
      molecule/
      components/
      theme/

  data/
    remote/
    local/
    ml/
    mapper/
    repository/
    work/

  domain/
    model/
    repository/
    usecase/

  feature/
    auth/
    splash/
    main/
    climbing/
      upload/
      record/
    analysis/
    calendar/
    community/
    profile/

  navigation/
  di/
```

---

## 3. Feature Internal Structure

Each feature follows this shape:

```text
feature/<feature-name>/
  presentation/
    <Feature>ViewModel.kt
    <Feature>Contract.kt
    mapper/
    state/
    event/

  ui/
    shared/
      tokens/
      atom/
      molecule/
      organism/
      template/

    <screen-a>/
      route/
      page/
      organism/
      molecule/
      atom/

    <screen-b>/
      route/
      page/
      organism/
      molecule/
      atom/
```

### 3.1 Responsibility by layer

- `presentation/`
  - owns `ViewModel`
  - collects domain data
  - converts domain model to screen state
  - handles business state, loading, errors, one-shot events
- `ui/route/`
  - connects `ViewModel` to Compose
  - collects `UiState`
  - handles navigation and side effects
- `ui/page/`
  - assembles the screen
  - receives only `UiState + callbacks`
- `ui/organism/`
  - one meaningful screen section
- `ui/molecule/`
  - small combined UI block
- `ui/atom/`
  - smallest reusable UI element
- `ui/template/`
  - reusable page skeleton

---

## 4. Final Structure for Climbing Upload

```text
feature/climbing/upload/
  presentation/
    UploadViewModel.kt
    UploadContract.kt
    mapper/
      UploadUiMapper.kt
    state/
      UploadFlowState.kt
      GymNameUiState.kt
      GymLevelUiState.kt
      GymColorUiState.kt
      AttemptResultUiState.kt
      FinalAnalysisUiState.kt
    event/
      UploadUiEvent.kt

  ui/
    shared/
      tokens/
        UploadTokens.kt
      atom/
        UploadInfoChip.kt
        AttemptBadge.kt
        HoldNumberBadge.kt
      molecule/
        AttemptMetaHeader.kt
        FailureCauseCard.kt
        LevelChoiceCard.kt
        DetailStatCard.kt
        ColorPickerButton.kt
      organism/
        DifficultyReferenceBar.kt
        HoldColorSelectionPanel.kt
        AttemptSelectorRow.kt
        StabilityChartPanel.kt
        AnalysisTabBar.kt
      template/
        SelectionStepTemplate.kt
        AnalysisTemplate.kt

    attempt/
      route/
        AttemptResultRoute.kt
      page/
        AttemptResultPage.kt
      organism/
        AttemptVideoSection.kt
        AnalysisScrubberSection.kt
        FailureCauseCarousel.kt
      molecule/
        PoseOverlayCanvas.kt
        VideoTimeLabel.kt
      atom/
        ScrubberMarker.kt

    analysis/
      route/
        FinalAnalysisRoute.kt
      page/
        FinalAnalysisPage.kt
      organism/
        AttemptPreviewHero.kt
        ProblemStatsPanel.kt
        StabilityPanel.kt
        FailureCausePanel.kt
      molecule/
        MetricHeadline.kt
        HeaderChip.kt

    create/
      route/
        ChallengeCreateRoute.kt
      page/
        GymNamePage.kt
        GymLevelPage.kt
        GymColorPage.kt
      organism/
        GymSearchSection.kt
        NearbyPlaceListSection.kt
        LevelSelectionSection.kt
        HoldColorHeroSection.kt
        ColorPickerSheet.kt
      molecule/
        NearbyPlaceItem.kt
        SelectedGymSummaryCard.kt
      atom/
        HoldColorTile.kt
        ColorCircleButton.kt
```

---

## 5. Screen Trees

### 5.1 Attempt Result

```text
AttemptResultRoute
-> AttemptResultPage
-> AttemptMetaHeader
-> AttemptVideoSection
-> AnalysisScrubberSection
-> FailureCauseCarousel
-> FailureCauseCard
-> DdgoGradientButton
```

### 5.2 Final Analysis

```text
FinalAnalysisRoute
-> FinalAnalysisPage
-> AttemptMetaHeader
-> AttemptPreviewHero
-> AnalysisTabBar
-> ProblemStatsPanel
-> StabilityPanel
-> FailureCausePanel
-> AttemptSelectorRow
-> DdgoGradientButton
```

### 5.3 Challenge Create

```text
ChallengeCreateRoute
-> GymNamePage
-> GymLevelPage
-> GymColorPage
```

```text
GymLevelPage
-> SelectionStepTemplate
-> DifficultyReferenceBar
-> LevelSelectionSection
-> LevelChoiceCard
-> DdgoPrimaryButton
```

```text
GymColorPage
-> SelectionStepTemplate
-> HoldColorHeroSection
-> ColorPickerSheet
-> ColorCircleButton
-> DdgoGradientButton
```

---

## 6. Current File -> Target File Mapping

### 6.1 Upload feature

- `feature/climbing/upload/UploadViewModel.kt`
  - move to `feature/climbing/upload/presentation/UploadViewModel.kt`
  - split screen states into `presentation/state/*`
- `feature/climbing/upload/UploadAnalysisUi.kt`
  - split into `ui/shared/tokens`, `ui/shared/molecule`, `ui/shared/organism`
- `feature/climbing/upload/AttemptResultScreen.kt`
  - split into:
    - `ui/attempt/route/AttemptResultRoute.kt`
    - `ui/attempt/page/AttemptResultPage.kt`
    - `ui/attempt/organism/AttemptVideoSection.kt`
    - `ui/attempt/organism/AnalysisScrubberSection.kt`
    - `ui/attempt/organism/FailureCauseCarousel.kt`
- `feature/climbing/upload/FinalAnalysisScreen.kt`
  - split into:
    - `ui/analysis/route/FinalAnalysisRoute.kt`
    - `ui/analysis/page/FinalAnalysisPage.kt`
    - `ui/analysis/organism/ProblemStatsPanel.kt`
    - `ui/analysis/organism/StabilityPanel.kt`
    - `ui/analysis/organism/FailureCausePanel.kt`
- `feature/climbing/upload/ChallengeCreateScreen.kt`
  - split into:
    - `ui/create/route/ChallengeCreateRoute.kt`
    - `ui/create/page/GymNamePage.kt`
    - `ui/create/page/GymLevelPage.kt`
    - `ui/create/page/GymColorPage.kt`
    - shared pieces into `ui/shared/*`

### 6.2 Core UI

- `core/ui/components/CommonComponents.kt`
  - split into:
    - `core/ui/atom/DdgoPrimaryButton.kt`
    - `core/ui/atom/DdgoOutlinedButton.kt`
    - `core/ui/components/DdgoFullScreenLoading.kt`

### 6.3 Tokens

- app-wide tokens stay in `core/ui/tokens`
- upload-flow-only colors and gradients stay in `feature/climbing/upload/ui/shared/tokens`

Rule:

- if used in 2 or more unrelated features -> promote to `core/ui`
- if used only in climbing upload flow -> keep inside upload `ui/shared`

---

## 7. Naming Rules

### 7.1 Presentation

- `ViewModel`: `<Feature>ViewModel`
- state file: `<Screen>UiState`
- contract file: `<Feature>Contract`
- mapper file: `<Feature>UiMapper`

### 7.2 UI

- route file: `<Screen>Route`
- page file: `<Screen>Page`
- organism file: descriptive section name
- molecule file: descriptive combined block name
- atom file: descriptive primitive name

Examples:

- `AttemptResultRoute`
- `AttemptResultPage`
- `FailureCauseCarousel`
- `FailureCauseCard`
- `UploadInfoChip`

---

## 8. Hard Rules

### 8.1 UI rules

- `Page`, `Organism`, `Molecule`, `Atom` must not receive `ViewModel`
- UI receives only state and callbacks
- do not call repository or use case directly from composables
- do not keep business calculations inside page-level UI

### 8.2 Presentation rules

- `ViewModel` may depend on `domain/usecase`
- `ViewModel` exposes `StateFlow` or immutable screen state
- one-shot navigation or toast events are exposed separately
- state mapping from domain to UI model happens here

### 8.3 Domain rules

- no `android.*`
- no Retrofit DTO
- no Room entity
- repository definitions are interfaces only

### 8.4 Data rules

- implements domain repository interfaces
- maps DTO/entity/model to domain
- contains network, local storage, ML implementation details

---

## 9. Recommended Migration Order

### Phase 1. Foundation

- create `core/ui/tokens`
- split `CommonComponents.kt`
- create upload feature `presentation/` and `ui/` folders

### Phase 2. Final Analysis first

- refactor `FinalAnalysisScreen.kt` first
- easiest screen to split into `Route + Page + Organisms`
- use this as the reference pattern for the team

### Phase 3. Attempt Result

- separate player logic from UI rendering
- move overlay, scrubber, and cause carousel into distinct organisms
- keep media side effects in `Route`

### Phase 4. Challenge Create

- split large step file into `GymNamePage`, `GymLevelPage`, `GymColorPage`
- extract shared step template and reusable selection components

### Phase 5. ViewModel cleanup

- replace mixed mutable states with screen-specific `UiState`
- move formatting and screen mapping to `presentation/mapper`

---

## 10. What Not To Do

- do not put Atomic Design folders in `domain` or `data`
- do not pass `UploadViewModel` into `HeaderSection`, `Card`, `Panel`, or `Chip`
- do not move everything to `core/ui` too early
- do not create pass-through use cases without product meaning
- do not keep feature-specific design tokens in app-wide theme by default

---

## 11. Final Summary

The target architecture is:

```text
data -> domain <- presentation <- ui
```

Inside `ui`, use:

```text
route -> page -> organism -> molecule -> atom
```

This keeps:

- clean architecture rules correct
- `ViewModel` separated from rendering
- composables functional and testable
- Atomic Design scoped to the place where it actually helps


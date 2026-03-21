# Climbing Upload README

## 목적
- 이 문서는 `climbing/upload` 기능을 수정하는 팀원을 위한 사용 설명서입니다.
- 이번 구조의 목표는 “완벽한 아키텍처”보다 `UI flow 유지 + Git 충돌 감소 + 책임 분리`입니다.

## 업로드 UX 흐름
1. 업로드 진입
2. 첫 영상 선택
3. 암장 검색/선택
4. 난이도/색상 선택
5. 홀드 탐지 및 시작/종료 홀드 선택
6. 분석 로딩
7. 시도 결과 / 최종 분석
8. 필요하면 attempt-only 추가 업로드

## 지금 구조를 보는 법
- `UploadViewModel.kt`
  - screen이 붙는 facade입니다.
  - public `viewModel.xxx` 계약을 유지하고, delegate 간 orchestration을 담당합니다.
- `UploadChallengeDelegate.kt`
  - gym search/resolve, grade/color, challenge 생성 담당입니다.
- `UploadHoldDetectionDelegate.kt`
  - best frame, person detect, YOLO, manual hold edit, start/end hold, numbering 담당입니다.
- `UploadSessionDelegate.kt`
  - video/temp/pre-pose/result-session state owner입니다.
  - cleanup keep-set과 retention 규칙도 여기서 관리합니다.
- `UploadSubmissionDelegate.kt`
  - submit/AI/result publishing logic owner입니다.
  - hold reach, upload API, realtime finalize/fallback도 여기서 담당합니다.

## 왜 UploadViewModel이 아직 남아 있나
- 현재 화면들은 graph-scoped `UploadViewModel`을 직접 공유합니다.
- 이번 단계 목표는 UI churn 없이 내부 충돌을 줄이는 것이므로, facade는 유지하고 내부 owner만 분리했습니다.
- 화면 계약을 깨지 않고 충돌을 줄이는 데 가장 실무적인 선택이었습니다.

## 철학
- 상태 owner는 하나만 둡니다.
- delegate끼리 직접 호출하지 않습니다.
- `UploadViewModel` 전체를 delegate에 넘기지 않습니다.
- command를 중심으로 생각하고, raw setter 증식을 피합니다.
- cleanup은 util이 아니라 retention policy입니다.

## 꼭 기억할 개념
- `selectionGeneration`
  - stale pre-pose discard를 위한 기준입니다.
  - submit/retry/publish/restore 전체를 대표하는 transaction id가 아닙니다.
- result session state
  - `resultPlaybackUris`와 `publishedAttemptResultSession`의 상태 owner는 session 쪽입니다.
  - publish/restore 로직은 submission 쪽이지만, state source of truth는 session입니다.
- 상태를 3종류로 나눠서 생각합니다.
  - workflow state: 현재 플로우 단계
  - artifact state: video/temp/pre-pose/result session
  - presentation state: `analysisPoints`, `currentPoseLandmarks`, `currentAttemptIndex`

## 어디를 수정해야 하나
- YOLO / person detect / best frame / manual hold
  - `UploadHoldDetectionDelegate.kt`
- pre-pose / temp file / result playback / published session / cleanup keep-set
  - `UploadSessionDelegate.kt`
- submit / upload API / hold reach / AI / realtime finalize / result publish
  - `UploadSubmissionDelegate.kt`
- 화면이 보는 public property나 navigation contract
  - 정말 필요할 때만 `UploadViewModel.kt`

## 금지 규칙
- delegate끼리 직접 호출하지 않기
- `UploadViewModel` 전체를 delegate에 넘기지 않기
- session state owner를 submission 쪽으로 다시 복제하지 않기
- cleanup 로직을 단순 파일 삭제 helper로 축소하지 않기

## 안전한 수정 순서
1. 먼저 바꾸려는 상태가 workflow/artifact/presentation 중 어디인지 정합니다.
2. 그 상태의 최종 owner가 어느 delegate인지 먼저 정합니다.
3. delegate 내부 command를 추가하고, `UploadViewModel`에서는 최소 wrapper만 연결합니다.
4. screen 계약 변경은 마지막 선택지로 둡니다.
5. 최소 테스트와 핵심 flow를 함께 확인합니다.

## 현재 ownership 한 줄 정리
- `UploadSessionDelegate`는 retention/pre-pose/result-session state owner다.
- `UploadSubmissionDelegate`는 submit/AI/result publishing logic owner다.
- `UploadViewModel`은 cross-delegate orchestration owner다.

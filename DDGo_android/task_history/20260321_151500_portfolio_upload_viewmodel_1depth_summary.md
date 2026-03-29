# Portfolio Summary: UploadViewModel 1-depth 리팩토링

## 프로젝트 한 줄 소개

기존 `UploadViewModel.kt` 하나에 몰려 있던 업로드 플로우, 홀드 탐지, MediaPipe pre-pose, 결과 복원, submit, AI 분석 로직을 UI 계약은 유지한 채 1-depth 수준으로 분리해 협업 충돌을 줄인 작업이다.

## 문제 상황

- `UploadViewModel.kt`가 `2456줄`까지 증가
- YOLO, person detect, MediaPipe, upload API, realtime 분석, challenge 생성 로직이 한 파일에 혼재
- 여러 담당자가 동시에 같은 파일을 수정하면서 merge conflict가 반복
- 화면 계약까지 바꾸는 구조 개편은 리스크가 커서, 실무적으로는 **UX 유지 + 내부 ownership 분리**가 더 중요했음

## 목표

이번 작업의 목표는 아키텍처를 완성하는 것이 아니라 아래 네 가지를 만족하는 것이었다.

1. UI 수정 최소화
2. 기존 UX flow 유지
3. 1-depth 수준의 큰 책임만 분리
4. Git 충돌 hotspot 감소

## 내가 한 일

### 1. 실제 코드 결합을 먼저 조사

리팩토링을 바로 시작하지 않고, 업로드 플로우와 AI 흐름을 먼저 분석했다.

- record -> upload handoff
- attempt-only 플로우
- person detect 기반 best frame
- YOLO hold detect
- MediaPipe pre-pose
- submit / AI / realtime finalize / result restore

이 과정을 통해 `UploadViewModel`이 단순 화면 ViewModel이 아니라 업로드 도메인 전체 오케스트레이터라는 점을 확인했다.

### 2. 기능 이름이 아니라 ownership seam 기준으로 분리

초기에는 `Challenge / HoldDetection / Flow / Analysis`로 나누는 안을 검토했지만, 실제 코드를 보니 아래가 강하게 결합되어 있었다.

- `selectionGeneration`
- temp cleanup
- result session / playback restore
- pre-pose cache/worker
- submit 직전 await와 publish/restore

그래서 계획을 수정해 최종적으로 아래 4축으로 정리했다.

- `UploadChallengeDelegate`
- `UploadHoldDetectionDelegate`
- `UploadSessionDelegate`
- `UploadSubmissionDelegate`

### 3. UI 계약은 유지하고 내부만 분리

screen의 `viewModel.xxx` 호출은 유지하고, `UploadViewModel`은 facade/orchestrator로 남겼다.  
즉, 화면을 거의 수정하지 않고 내부 책임만 분산했다.

### 4. 단계별로 테스트와 수동 검증을 삽입

한 번에 끝까지 밀지 않고, 각 단계마다:

- 작은 커밋
- 자동 테스트
- 수동 flow 체크

를 반복했다. 이 방식으로 어디서 회귀가 생겼는지 추적 가능하게 만들었다.

## 결과

### 정량 결과

- `UploadViewModel.kt`: `2456줄 -> 1119줄`
- 감소량: `-1337줄`
- 감소율: `54.4%`
- 관련 핵심 파일 수: `1개 -> 7개`
- Git diff: `3098 insertions(+)`, `2125 deletions(-)`

### 최종 구조

- `UploadChallengeDelegate`
  - 암장 검색, 난이도/색 선택, challenge 생성
- `UploadHoldDetectionDelegate`
  - person detect, YOLO hold detect, 홀드 편집, 시작/종료 홀드, numbering
- `UploadSessionDelegate`
  - 영상 선택, temp file, MediaPipe pre-pose, result session, cleanup retention
- `UploadSubmissionDelegate`
  - submit, hold reach, upload API, AI 분석, realtime finalize/fallback
- `UploadViewModel`
  - facade + cross-delegate orchestration

### 유지된 것

- 기존 UX flow
- Compose screen의 `viewModel.xxx` 계약
- graph-scoped 단일 `UploadViewModel`

## 이 작업에서 중요한 판단

### 1. 파일 분리보다 ownership 분리가 중요했다

단순히 코드를 다른 파일로 옮기는 것이 아니라, 어떤 상태와 로직의 최종 owner가 누구인지 고정하는 것이 중요했다.

### 2. result session state와 publish logic을 분리했다

- state owner: `UploadSessionDelegate`
- logic owner: `UploadSubmissionDelegate`

이 경계 덕분에 cleanup / restore / publish seam이 흔들리지 않도록 막을 수 있었다.

### 3. cleanup을 util이 아니라 retention policy로 다뤘다

temp file cleanup은 단순 파일 삭제가 아니라, 어떤 영상과 세션 상태를 살아 있게 유지할지 결정하는 도메인 로직으로 취급했다.

## AI를 어떻게 활용했는가

이 작업은 AI에게 설계를 맡긴 것이 아니라, 사람이 설계를 통제하면서 AI를 활용한 작업이었다.

- 먼저 조사하게 했다
- 리팩토링 범위를 1-depth로 제한했다
- UI 수정 최소화, UX 유지, 충돌 감소라는 제약을 명시했다
- AI가 제안한 경계가 실제 코드 결합과 맞지 않으면 수정했다
- 커밋 단위, 테스트 범위, 검증 순서를 계속 통제했다

즉, AI는 설계 대체물이 아니라 탐색과 정리를 보조하는 도구로 사용했다.

## 검증

자동 테스트:

- `UploadViewModelTest`
- `UploadViewModelRotationTest`
- `AttemptResultScreenTest`
- `AttemptPosePlaybackTest`
- `FinalAnalysisSummaryTest`
- `:app:compileDebugKotlin`

수동 검증:

- 기본 업로드 플로우
- 홀드 수정 후 재분석
- attempt-only 추가 업로드 / 취소 후 복원
- record -> upload handoff
- realtime finalize / fallback

## 커밋 참고

- [8356b35](https://lab.ssafy.com/s14-ai-image-sub1/S14P21D204/-/commit/8356b35a2f410b6492f04f5500dd406215b2326f)
- [159cf97](https://lab.ssafy.com/s14-ai-image-sub1/S14P21D204/-/commit/159cf970891ad78784959e397155745831f2ee27)
- [c0a5f53](https://lab.ssafy.com/s14-ai-image-sub1/S14P21D204/-/commit/c0a5f536644501d562b26e644b8b9d1234b41573)
- [f6728b0](https://lab.ssafy.com/s14-ai-image-sub1/S14P21D204/-/commit/f6728b025f3f3ab92995dfbfdbabef6c19bc5a80)
- [0a83d03](https://lab.ssafy.com/s14-ai-image-sub1/S14P21D204/-/commit/0a83d0315c00c79b865f4e57f4f59d36ff6e701d)
- [4c62196](https://lab.ssafy.com/s14-ai-image-sub1/S14P21D204/-/commit/4c62196663fa0580b16de7607e633cd03fbc9ca2)
- [df77918](https://lab.ssafy.com/s14-ai-image-sub1/S14P21D204/-/commit/df77918177371ff53da5bf4b5c9287ded21d0a66)
- [2c88bec](https://lab.ssafy.com/s14-ai-image-sub1/S14P21D204/-/commit/2c88bec10965d70fda5b696eb15af2a7be9f6063)

## 포트폴리오용 마무리 문장

이 작업은 단순한 코드 정리가 아니라, 대형 ViewModel에 얽힌 협업 충돌 문제를 실제 코드 결합 기준으로 재정의하고, UI 계약은 유지한 채 ownership을 기능 축으로 분리한 리팩토링이었다. 특히 AI에게 구조를 맡긴 것이 아니라, 사람이 범위와 제약, 검증 절차를 설계하고 AI를 보조 도구로 활용했다는 점이 이 작업의 핵심 가치였다.

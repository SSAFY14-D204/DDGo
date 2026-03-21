# Upload 1-depth 리팩토링 기록

## 배경
- `UploadViewModel.kt` 하나에 업로드 UX, 챌린지 생성, 홀드 탐지, pre-pose, 결과 세션, submit, AI 분석이 모두 모여 있었습니다.
- YOLO, person detect, MediaPipe, upload API 담당자가 같은 파일을 동시에 수정하면서 충돌이 자주 발생했습니다.
- 이번 브랜치의 목표는 아키텍처 완성이 아니라 `동작 보존 + 충돌 감소 + ownership 분리`였습니다.

## 원칙
- `UploadNavigation`과 screen의 `viewModel.xxx` 계약은 유지한다.
- graph-scoped 단일 `UploadViewModel`은 facade로 유지한다.
- delegate끼리 직접 호출하지 않는다.
- `UploadViewModel` 전체를 delegate에 넘기지 않는다.
- cleanup은 단순 housekeeping이 아니라 retention policy로 다룬다.

## 진행 순서
1. UI state / session model을 별도 파일로 분리했다.
2. 챌린지 생성 축을 `UploadChallengeDelegate`로 분리했다.
3. best frame, person detect, YOLO, 수동 홀드 편집 축을 `UploadHoldDetectionDelegate`로 분리했다.
4. 영상 선택, managed file, pre-pose, result session, cleanup 축을 `UploadSessionDelegate`로 분리했다.
5. submit, hold reach, AI, result publish/restore 축을 `UploadSubmissionDelegate`로 분리했다.
6. 마지막으로 `UploadViewModel`에 남은 session-side 저수준 구현, 중복 await, legacy 주석 블록을 제거해 facade로 마감했다.

## 주요 커밋
- `8356b35` 테스트 생성자 동기화
- `159cf97` upload ui state / session model 분리
- `c0a5f53` challenge delegate 분리
- `f6728b0` hold detection delegate 분리
- `0a83d03` session delegate 분리
- `4c62196` submission delegate 분리
- 현재 단계: facade 마감 정리 및 문서화

## 최종 구조
- `UploadViewModel`
  - graph-scoped facade
  - cross-delegate orchestration owner
  - screen 계약 유지
- `UploadChallengeDelegate`
  - gym search/resolve, grade/color, challenge 생성
- `UploadHoldDetectionDelegate`
  - best frame, person detect, YOLO, manual hold edit, start/end hold, numbering
- `UploadSessionDelegate`
  - retention / pre-pose / result-session state owner
  - managed video, selection generation, temp cleanup keep-set, result playback/session
- `UploadSubmissionDelegate`
  - submit / AI / result publishing logic owner
  - hold reach, upload API, realtime finalize/fallback, result publish/restore

## 수치 변화
- 시작 시점 `UploadViewModel.kt`: `2456줄`
- 1-depth 마감 시점 `UploadViewModel.kt`: `1119줄`
- 분리된 주요 파일
  - `UploadChallengeDelegate.kt`: `293줄`
  - `UploadHoldDetectionDelegate.kt`: `337줄`
  - `UploadSessionDelegate.kt`: `643줄`
  - `UploadSubmissionDelegate.kt`: `664줄`

## 이번 브랜치에서 일부러 하지 않은 것
- screen split
- multi-VM
- navigation 경계 재설계
- delegate 내부 2-depth 재분리
- callback protocol 재설계

## 남은 2-depth 후보
- `UploadSessionDelegate` 내부에서 selection / pre-pose / retention 추가 분리
- `UploadSubmissionDelegate` 내부에서 hold reach / upload / AI 추가 분리
- `UploadHoldDetectionDelegate` 내부에서 person detect / YOLO / manual edit 추가 분리

## 종료선
- 이번 브랜치는 1-depth 리팩토링을 마감하는 브랜치입니다.
- 기준은 “VM 줄이기”보다 “핫스팟 ownership을 메인 VM 밖으로 빼고, UI flow를 유지하는 것”입니다.

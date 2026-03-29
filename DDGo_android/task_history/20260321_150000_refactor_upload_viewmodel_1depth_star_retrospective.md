# Task History: UploadViewModel 1-depth 리팩토링 회고

> AI에 맡긴 리팩토링이 아니라, AI를 활용해 설계하고 검증한 분리 작업의 원천 기록

- 날짜: 2026-03-21
- 작업명: UploadViewModel 1-depth 리팩토링
- 브랜치: `refactor/uploadViewmodel-first-depth-refatoring`
- 저장소/모듈: `DDGo_android/app/src/main/java/com/ddgo/app/feature/climbing/upload`
- 작업 방식: 사용자 주도 설계 + AI 보조 탐색/정리/검증

## 한 줄 요약

`UploadViewModel.kt` 하나에 몰려 있던 업로드 UX, 챌린지 생성, 홀드 탐지, MediaPipe pre-pose, 결과 세션 복원, submit, AI 분석 로직을 UI 계약은 유지한 채 1-depth 수준으로 분리했다. 이 작업의 핵심은 "AI가 대신 설계한 리팩토링"이 아니라, 사람이 문제를 정의하고 리팩토링 범위를 통제하고 의사결정 경계를 수정하면서 AI를 설계 보조 도구로 활용했다는 점에 있다.

## 핵심 수치

- `UploadViewModel.kt`: `2456줄 -> 1119줄`
- 감소량: `-1337줄`
- 감소율: `54.4%`
- 관련 핵심 파일 수: `1개 -> 7개`
- Git diff 기준: `10 files changed`, `3098 insertions(+)`, `2125 deletions(-)`

세부 파일 구성:

- `UploadViewModel.kt`: `1119줄`
- `UploadChallengeDelegate.kt`: `293줄`
- `UploadHoldDetectionDelegate.kt`: `337줄`
- `UploadSessionDelegate.kt`: `643줄`
- `UploadSubmissionDelegate.kt`: `664줄`
- `UploadAsyncUiStates.kt`: `35줄`
- `UploadSessionModels.kt`: `89줄`

총 관련 코드량은 `2456줄 -> 3180줄`로 증가했다. 이 증가는 설계 실패가 아니라 의도된 결과였다. facade wrapper를 남겨 UI 계약을 유지했고, 상태/모델과 delegate ownership을 분리했기 때문이다. 이번 리팩토링의 목표는 총 줄 수 축소가 아니라 팀 충돌이 집중되던 메인 hotspot을 줄이는 것이었다.

---

## STAR 요약

### Situation

기존 `UploadViewModel.kt`는 2456줄까지 커져 있었다. 이 파일 안에는 아래 축이 거의 모두 섞여 있었다.

- 업로드 진입과 영상 선택
- 암장 검색, 난이도 선택, challenge 생성
- person detect 기반 best frame 추출
- YOLO 기반 홀드 탐지와 홀드 색 분류
- MediaPipe pre-pose 준비와 캐시
- 결과 playback session 복원
- submitUpload, hold reach 분석, AI 분석, realtime finalize/fallback

이 구조에서는 YOLO 담당자, person detect 담당자, MediaPipe 담당자, upload API 담당자, 실시간 분석 담당자가 모두 같은 파일을 동시에 수정하게 된다. 기능의 결합도도 높았지만, 협업 충돌 비용도 높았다.

### Task

이번 작업의 목표는 아래 네 가지였다.

1. UI 수정은 최대한 피할 것
2. 기존 UX flow를 유지할 것
3. 1-depth 수준의 큰 책임만 분리할 것
4. Git 충돌이 덜 나는 구조로 ownership을 나눌 것

여기서 중요한 점은, 이번 작업이 "최종 아키텍처 완성"이 아니라는 점이다. multi-VM, screen split, navigation boundary 재설계까지 가면 범위가 커지고 회귀 위험도 커진다. 그래서 이번 브랜치의 종료선을 1-depth 분리로 명확히 제한했다.

### Action

실행 방식은 즉흥적이지 않았다.

- 업로드 UX와 AI 흐름을 먼저 조사했다.
- `UploadNavigation`, `RecordNavigation`, `UploadViewModel`, 관련 screen과 test를 보고 실제 플로우를 확인했다.
- 초기에 `Challenge / HoldDetection / Flow / Analysis`로 나누는 안을 세웠다.
- 이후 실제 코드 결합을 다시 읽으며 `selectionGeneration`, temp cleanup, result session, attempt-only restore seam을 재평가했다.
- 그 결과 `Flow`보다 `Session + Submission`이 실제 핫스팟이라는 결론으로 계획을 수정했다.
- 커밋 단위를 작게 고정하고, 각 단계마다 테스트와 수동 flow 체크를 끼워 넣었다.

### Result

- 메인 hotspot이던 `UploadViewModel.kt`는 `2456줄 -> 1119줄`로 축소됐다.
- 기능별 owner 파일이 아래와 같이 분리됐다.
  - `UploadChallengeDelegate`
  - `UploadHoldDetectionDelegate`
  - `UploadSessionDelegate`
  - `UploadSubmissionDelegate`
- screen의 `viewModel.xxx` 계약과 기존 UX flow는 유지했다.
- 테스트와 수동 검증을 거쳐 마감했다.
- 구조 설명용 문서까지 같이 남겼다.

---

## 왜 이 작업이 "AI에 맡긴 리팩토링"이 아니었는가

이 작업을 포트폴리오에서 설명할 때 가장 중요한 문장은 다음이다.

> AI는 설계 대체물이 아니라, 탐색·정리·검증 보조 도구로 사용했다.

이번 리팩토링에서 사람의 역할은 단순히 "프롬프트를 던지는 것"이 아니었다. 아래 제약과 기준을 계속 유지하면서 AI의 행동 범위를 통제했다.

### 1. 처음부터 범위를 제한했다

- "1-depth만 분리한다"
- "UI는 최대한 건드리지 않는다"
- "기존 UX flow는 유지한다"
- "AI 호출 최적화나 중복 제거는 이번 범위가 아니다"

이 제한이 없었다면 AI는 구조적으로 더 예뻐 보이는 방향, 예를 들어 multi-VM이나 screen split 쪽으로 쉽게 흘렀을 가능성이 높다. 하지만 실제 팀 문제는 아키텍처 완성이 아니라 충돌 감소였기 때문에, 문제 정의부터 사람 손으로 강하게 제한해야 했다.

### 2. AI의 제안을 그대로 실행하지 않았다

초기에는 `Challenge / HoldDetection / Flow / Analysis` 같은 설계가 자연스럽게 보였다. 하지만 실제 코드를 다시 읽어보니 아래가 강하게 결합되어 있었다.

- `selectionGeneration`
- temp file cleanup
- result playback/session restore
- pre-pose cache/worker
- submit 직전 await와 session publish/restore

이때 중요한 깨달음은 "파일을 나누는 일"과 "상태 ownership을 나누는 일"이 다르다는 점이었다. AI는 분류를 잘 도와줬지만, 어느 경계가 실제로 안전한지는 사람이 코드 결합을 읽고 고쳐야 했다.

### 3. 검증 순서를 사람이 설계했다

이번 작업은 한 번에 끝까지 구현하지 않았다. 각 단계마다:

- 계획 수립
- 코드 분리
- 자동 테스트
- 수동 flow 체크
- 다음 단계 진행

순서로 진행했다. 이 checkpoint를 넣은 이유는 "어디서 깨졌는지"를 추적 가능하게 하기 위해서였다. AI에게 맡기면 보통 한 번에 더 많은 변경을 제안하지만, 그럴수록 회귀 원인이 섞인다. 이번에는 그 리스크를 사람 쪽에서 관리했다.

### 4. 커밋 메시지, 브랜치명, 테스트 범위까지 계속 제어했다

실제 진행 중에는 아래까지 모두 사람이 방향을 수정했다.

- 커밋 메시지를 영어에서 한글 중심으로 바꾸기
- 커밋 본문에 ownership과 테스트 범위까지 명시하기
- 브랜치명을 "1-depth UploadViewModel 리팩토링" 의미가 드러나게 정리하기
- 단순 compile이 아니라 특정 테스트까지 통과시키기
- 문서화 범위를 README와 task history까지 확장하기

즉, AI는 코드를 만들었지만 일의 정의, 중간 stop/go 기준, 완료 기준은 사람이 계속 쥐고 있었다.

---

## 시간순 의사결정 기록

이 섹션은 실제로 어떤 사고 흐름을 거쳤는지 남기는 원천 데이터다. 나중에 포트폴리오로 줄이더라도 이 순서를 유지하면 이야기 구조가 잘 잡힌다.

### 1. 문제 인식: 거대한 ViewModel이 협업 비용을 만들고 있었다

처음 문제 인식은 단순히 "파일이 길다"가 아니었다. 사용자의 인식은 더 실무적이었다.

- `UploadViewModel.kt`가 2000줄이 넘는다
- YOLO, MediaPipe, person detect, record/upload 담당자가 같이 작업한다
- `UploadViewModel.kt`에서 계속 충돌이 난다

즉, 이 문제는 "코드가 못생겼다"가 아니라 "팀이 동시에 일하기 어렵다"는 문제였다. 그래서 처음부터 해결 기준도 달랐다.

- 구조적으로 가장 예쁜 분리를 찾는 것보다
- 팀이 덜 부딪히도록 큰 덩어리만 먼저 나누는 것

이게 더 중요한 목표가 됐다.

### 2. 첫 요청과 리팩토링 범위 설정: 1-depth만 하기로 했다

초기 요청에서 가장 중요했던 문장은 "정말 큰 단위에서 딱 1 depth 정도만 분리할 것"이었다. 여기에는 두 가지 현실 인식이 담겨 있었다.

1. 지금 당장 완벽한 아키텍처 개편을 하면 범위가 너무 커진다.
2. 현재 가장 큰 pain point는 성능이 아니라 merge conflict다.

이 때문에 일부러 범위를 줄였다.

- AI를 두 번 돌리는 비효율
- 최종 아키텍처 완결성
- multi-VM
- screen split

같은 문제는 이번 브랜치의 우선순위에서 제외했다.

### 3. 업로드 UX와 AI 흐름 조사: 먼저 실제 흐름을 이해했다

리팩토링을 시작하기 전에 실제 업로드 플로우를 조사했다. 여기서 중요했던 건 "어디서 어떤 AI가 실제로 호출되는가"였다.

조사 결과 업로드는 아래 축으로 움직이고 있었다.

- 업로드 진입
- 영상 선택
- 클라이밍장/난이도/홀드 색 선택
- person detect 기반 best frame 추출
- YOLO hold detect + hold color 분류
- 시작 홀드 / 종료 홀드 선택
- MediaPipe pre-pose 준비
- 분석 로딩
- hold reach 분석
- AI 분석 / realtime finalize / fallback
- 결과 화면
- attempt-only 추가 업로드
- record -> upload handoff

이 조사 덕분에 "이건 화면 하나의 ViewModel이 아니라 업로드 도메인 전체 오케스트레이터"라는 점이 명확해졌다.

### 4. 초기 설계안: Challenge / HoldDetection / Flow / Analysis

처음에는 상대적으로 자연스러워 보이는 네 축으로 생각했다.

- `Challenge`
- `HoldDetection`
- `Flow`
- `Analysis`

이 설계는 겉으로 보면 깔끔했다. 화면 단위로도 나뉘고, 기능 이름도 그럴듯했다. 하지만 곧 문제가 드러났다.

### 5. 첫 번째 설계 수정: `selectionGeneration`과 cleanup seam이 예상보다 강결합이었다

실제 코드를 읽어보니 `Flow`와 `Analysis`를 깔끔하게 나누기 어려운 이유가 있었다. 핵심은 아래였다.

- `selectionGeneration`은 단순 selection 상태가 아니라 stale pre-pose discard의 기준이었다.
- temp cleanup은 단순 housekeeping이 아니라 keep-set을 계산하는 retention policy였다.
- `publishedAttemptResultSession`, `resultPlaybackUris`, active pre-pose playback이 cleanup과 직접 연결되어 있었다.

이 지점에서 "분리할 수 있는 기능 이름"과 "안전하게 분리할 수 있는 ownership seam"이 다르다는 점을 분명히 인식하게 됐다.

그래서 첫 번째 중요한 보정이 들어갔다.

- `selectionGeneration`은 단순 Flow ownership으로 두지 않는다.
- temp cleanup은 util처럼 취급하지 않는다.
- result session과 restore는 submission만의 문제가 아니라 session retention과도 연결된다.

### 6. Challenge 분리: 독립성이 가장 높은 축부터 빼기

가장 먼저 분리한 것은 `UploadChallengeDelegate`였다.

이유:

- gym search / resolve
- grade / color 선택
- challenge 생성

이 축은 다른 AI 로직과 비교했을 때 상대적으로 독립성이 높았다. 초기 성공 사례를 만들기에도 좋았고, 가장 낮은 위험으로 첫 1-depth 분리를 검증할 수 있었다.

이 판단은 단순히 "쉬워 보이니까"가 아니라, 리팩토링 초기에는 반드시 하나의 낮은 위험 커밋으로 감을 잡아야 한다는 판단이었다.

### 7. HoldDetection 분리: person detect와 YOLO를 하나의 owner로 묶기

두 번째로 분리한 것은 `UploadHoldDetectionDelegate`였다.

여기에 들어간 기능:

- best frame 추출
- person detect
- YOLO hold detect
- hold color filter
- 수동 hold add/remove
- 시작 홀드 / 종료 홀드
- hold numbering

이 단계에서 중요한 판단은 두 가지였다.

1. person detect와 YOLO는 분업상 다른 사람일 수 있지만, 1-depth에서는 함께 묶는다.
2. hold reach 분석은 여기로 넘기지 않는다.

즉, "홀드를 감지하고 편집하는 책임"과 "그 홀드 기준으로 분석 결과를 계산하는 책임"을 구분한 것이다. 그 덕분에 나중에 submission 축과의 경계를 더 분명히 할 수 있었다.

### 8. 두 번째 설계 수정: 남은 진짜 핫스팟은 Flow가 아니라 Session + Submission

Challenge와 HoldDetection을 분리하고 나니, 오히려 남은 덩어리가 더 명확하게 보이기 시작했다.

남은 핫스팟은 실제로 아래였다.

- 영상 선택
- managed temp file
- pre-pose queue/cache/worker
- result playback/session restore
- cleanup keep-set
- submit
- hold reach
- AI 분석
- realtime finalize/fallback

이 시점에서 "Flow를 조금씩 나누자"는 계획은 폐기하는 쪽으로 갔다. 이유는 실제 강결합 단위가 `Flow`라는 이름보다는 `Session + Submission`에 가까웠기 때문이다.

이 판단은 특히 중요했다. 만약 기존 계획을 고집했다면, 파일은 나뉘어도 ownership seam이 어긋나고 다음 리팩토링에서 더 큰 혼란이 생길 수 있었다.

### 9. Session 분리: pre-pose와 result session, cleanup을 같은 owner로 묶기

`UploadSessionDelegate`에는 아래를 모았다.

- 영상 선택
- 추가 시도 영상 선택
- 녹화 후 업로드 handoff
- managed temp file 생성과 registry
- `selectionGeneration`
- pre-pose queue/cache/worker
- result playback/session state
- cleanup retention policy

이 단계에서 특히 중요한 개념은 cleanup이었다.

cleanup은 단순 파일 삭제 함수가 아니다. 무엇을 살아 있게 유지할지 판단하는 로직이다. 현재 선택된 영상, 결과 화면에서 재생 중인 영상, published session이 참조하는 영상, pre-pose worker가 현재 사용 중인 영상은 지우면 안 된다. 이 판단이 바로 session retention policy이고, 그래서 session owner가 직접 가져가야 한다.

### 10. Submission 분리: submit, hold reach, AI, publish logic을 묶기

`UploadSubmissionDelegate`에는 아래를 모았다.

- `submitUpload`
- hold reach 분석
- upload API
- AI 분석
- realtime finalize/fallback
- result publish/restore logic

여기서 가장 조심한 부분은 "result session 상태 owner"를 누구에게 둘 것인가였다.

최종 판단:

- `UploadSessionDelegate`: `resultPlaybackUris`, `publishedAttemptResultSession`의 상태 owner
- `UploadSubmissionDelegate`: publish/restore를 포함한 결과 처리 로직 owner

즉, state owner와 logic owner를 분리했다. 이 결정 덕분에 cleanup keep-set과 result restore가 서로 다른 기준으로 흔들리지 않도록 막을 수 있었다.

### 11. 마지막 facade 정리: UploadViewModel을 진짜 facade로 만들기

마지막 단계에서는 `UploadViewModel` 안에 남아 있던 옛 session helper, 중복 await, legacy comment block 등을 정리했다.

그리고 ownership 문장을 코드에 명시적으로 남겼다.

- `UploadSessionDelegate`는 retention / pre-pose / result-session state owner
- `UploadSubmissionDelegate`는 submit / AI / result publishing logic owner
- `UploadViewModel`은 cross-delegate orchestration owner

이 문장은 단순 주석이 아니라, 다음 리팩토링에서 흔들리기 쉬운 seam을 고정하는 선언이었다.

### 12. 문서화 마감: 구조를 아는 사람이 아니어도 유지보수할 수 있게 만들기

마지막에는 README와 task history를 추가했다.

- climbing README에는 어떤 기능을 어느 파일에서 수정해야 하는지
- task history에는 왜 이런 구조를 선택했는지

를 남겼다.

이 단계는 종종 리팩토링에서 생략되지만, 이번 작업에서는 매우 중요했다. 기능을 나눠도 다음 사람이 "어디가 owner인지" 모르면 다시 facade로 로직이 새어 들어가기 때문이다.

---

## 내가 거친 고민 흐름

이 작업에서 가장 중요했던 사고 흐름은 아래 여섯 번의 전환으로 정리할 수 있다.

### 1. "크게만 나누자"

처음 기준은 단순했다. 지금 필요한 건 완전한 아키텍처 재설계가 아니라 큰 책임만 떼어내는 것이다. 이 기준이 있었기 때문에 과한 설계를 피할 수 있었다.

### 2. "UI는 최대한 유지하자"

실제 앱에서 가장 위험한 부분은 화면 계약이 바뀌는 것이다. 그래서 screen의 `viewModel.xxx` 호출은 유지하고, `UploadViewModel`을 facade로 남기는 방향을 고정했다.

### 3. "강결합이면 같이 떼야 한다"

처음에는 파일 이름 기준으로 나누는 유혹이 컸다. 하지만 `selectionGeneration`, cleanup, result session, pre-pose가 강하게 결합되어 있다는 걸 보면서, 이건 이름이 아니라 결합 기준으로 나눠야 한다는 걸 알게 됐다.

### 4. "하지만 사람 ownership을 보면 1개 거대 파일보다 2개 큰 덩어리가 낫다"

강결합을 이유로 남은 걸 다 한 파일로 몰 수도 있었다. 하지만 그렇게 하면 `UploadViewModel 2`가 될 뿐이었다. 실제 협업자를 생각하면 session과 submission을 두 파일로 나누는 편이 더 실무적이었다.

### 5. "파일 분리와 ownership 분리는 다르다"

이건 이번 작업에서 가장 중요한 깨달음 중 하나였다. 코드를 파일로 옮기는 것만으로는 진짜 분리가 아니다. 누가 최종 state owner인지, 누가 logic owner인지가 분명해야 한다.

### 6. "결국 1-depth의 종료선은 줄 수가 아니라 owner 고정이다"

리팩토링이 끝날 때쯤에는 단순한 줄 수보다 더 중요한 것이 보였다. 진짜 성과는 `UploadViewModel`이 몇 줄이 됐는지가 아니라, 어떤 기능을 어느 파일에서 고쳐야 하는지 팀이 명확히 알 수 있게 된 것이었다.

---

## AI를 어떻게 제어했는가

이 섹션은 포트폴리오에서 "AI를 잘 쓴 개발자"라는 점을 보여줄 수 있는 핵심이다.

### 1. 조사부터 하게 했다

곧바로 리팩토링 구현으로 들어가지 않았다. 먼저 현재 `UploadViewModel`이 무엇을 하고 있는지, 업로드 flow와 AI 흐름이 어떻게 움직이는지 조사하게 했다.

이 단계가 중요했던 이유는, AI는 종종 이름과 구조만 보고 그럴듯한 설계를 제안하지만 실제 결합도를 놓칠 수 있기 때문이다.

### 2. 추측으로 분리하지 못하게 했다

"리팩토링하자"가 아니라, "조사해보고 1-depth 수준으로만, UI 수정 없이, 기존 UX를 유지하는 방향으로 계획을 세워라"는 식으로 범위를 제한했다.

이 제약이 없으면 AI는 구조적으로 더 예쁜 대안을 밀 가능성이 높다. 이번에는 그걸 의도적으로 막았다.

### 3. 중간마다 수동 검증 checkpoint를 넣었다

Challenge 분리 후, HoldDetection 분리 후, Session 분리 후, Submission 분리 후 각각 사용자가 직접 flow를 눌러보게 했다.

이건 중요한 통제 장치였다. 자동 테스트는 중요한 안전망이지만, 업로드/재생/결과 복원처럼 UX 계약이 큰 기능은 수동 검증이 반드시 필요했다.

### 4. 커밋 단위를 작게 고정했다

리팩토링을 한 번에 끝내지 않고 아래처럼 단계별로 끊었다.

- test sync
- state/model 분리
- challenge 분리
- hold detection 분리
- session 분리
- submission 분리
- facade 정리
- 문서화

이렇게 해야 어디서 깨졌는지 추적이 가능했다.

### 5. 브랜치명, 커밋 메시지, 테스트 범위까지 계속 제어했다

작업 중에는 커밋 메시지를 한글로 바꾸고, 본문에 무엇을 옮겼는지와 어떤 테스트를 돌렸는지 명시하게 했다. 브랜치명도 작업 의미가 드러나게 조정했다. 이것도 AI를 "알아서 일하는 개발자"가 아니라 "도구"로 쓰기 위한 통제였다.

### 6. 실제 코드 결합과 안 맞는 제안은 사람 판단으로 수정했다

대표적으로 아래가 그렇다.

- `Flow / Analysis` 분리안
- result session owner 처리
- cleanup을 util처럼 다루는 위험

이런 부분은 AI 초안을 그대로 따르지 않고, 사람이 코드를 읽은 뒤 보정했다. 그래서 이번 작업을 "AI가 설계했다"라고 말하는 건 정확하지 않다. 정확한 표현은 "AI가 설계와 정리를 도왔고, 사람은 경계와 검증을 통제했다"이다.

---

## 실패한 선행 시도 회고: `uploadViewModel 조언자` 스레드

이번 작업은 첫 시도가 아니었다. 사용자 진술 기준으로, 이전에는 `uploadViewModel 조언자` 스레드에서 AI에게 더 넓게 맡긴 상태로 리팩토링을 시도했다가 실패한 경험이 있었다.

이 실패를 여기 남기는 이유는 단순한 반성이 아니라, 이번 성공 방식이 왜 달랐는지를 보여주기 위해서다.

### 당시 무엇이 문제였나

- AI에게 구조 판단을 더 많이 맡겼다.
- 실제 코드 결합보다 이상적인 분리가 앞섰다.
- session / result / cleanup seam을 충분히 통제하지 못했다.
- 결과적으로 "보기에 그럴듯한 구조"는 나왔지만, 실제 업로드 흐름과 충돌 감소라는 목적에는 덜 맞았다.

### 이번에는 무엇이 달랐나

이번에는 아래 순서를 강하게 고정했다.

1. 조사
2. 계획
3. 작은 커밋
4. 자동 테스트
5. 수동 검증
6. 다음 단계 진행

또한 계획도 중간에 여러 번 수정했다. 처음 세운 구조를 끝까지 밀지 않고, 실제 코드 결합을 다시 읽으면서 `Session + Submission`으로 보정했다. 이 차이가 컸다.

### 이 실패에서 배운 점

- AI에게 분리 구조를 통째로 맡기면 drift가 생길 수 있다.
- cleanup / result session / restore seam은 절대 가볍게 보면 안 된다.
- UI를 그대로 두고 싶다면 facade 전략을 의도적으로 유지해야 한다.
- 파일 분리보다 owner 고정이 먼저다.

이 실패 경험이 있었기 때문에, 이번에는 AI를 더 엄격하게 제한하고 결과적으로 더 실무적인 구조를 만들 수 있었다.

---

## 커밋 타임라인

| 커밋 | 링크 | 내용 | 당시 고정한 판단 |
|---|---|---|---|
| `8356b35` | [8356b35](https://lab.ssafy.com/s14-ai-image-sub1/S14P21D204/-/commit/8356b35a2f410b6492f04f5500dd406215b2326f) | 테스트 생성자 동기화 | 리팩토링 전에 baseline test harness부터 맞춘다 |
| `159cf97` | [159cf97](https://lab.ssafy.com/s14-ai-image-sub1/S14P21D204/-/commit/159cf970891ad78784959e397155745831f2ee27) | UI state / session model 분리 | 먼저 타입을 분리해 본체 리팩토링의 발판을 만든다 |
| `c0a5f53` | [c0a5f53](https://lab.ssafy.com/s14-ai-image-sub1/S14P21D204/-/commit/c0a5f536644501d562b26e644b8b9d1234b41573) | challenge delegate 분리 | 독립성이 가장 높은 축부터 낮은 위험으로 시작한다 |
| `f6728b0` | [f6728b0](https://lab.ssafy.com/s14-ai-image-sub1/S14P21D204/-/commit/f6728b025f3f3ab92995dfbfdbabef6c19bc5a80) | hold detection delegate 분리 | person detect + YOLO + manual hold를 하나의 owner로 묶는다 |
| `0a83d03` | [0a83d03](https://lab.ssafy.com/s14-ai-image-sub1/S14P21D204/-/commit/0a83d0315c00c79b865f4e57f4f59d36ff6e701d) | session delegate 분리 | 남은 진짜 핫스팟은 Flow가 아니라 Session이라는 판단을 반영한다 |
| `4c62196` | [4c62196](https://lab.ssafy.com/s14-ai-image-sub1/S14P21D204/-/commit/4c62196663fa0580b16de7607e633cd03fbc9ca2) | submission delegate 분리 | result session state owner와 publish logic owner를 분리한다 |
| `df77918` | [df77918](https://lab.ssafy.com/s14-ai-image-sub1/S14P21D204/-/commit/df77918177371ff53da5bf4b5c9287ded21d0a66) | facade 정리 | `UploadViewModel`을 진짜 facade/orchestrator로 마감한다 |
| `2c88bec` | [2c88bec](https://lab.ssafy.com/s14-ai-image-sub1/S14P21D204/-/commit/2c88bec10965d70fda5b696eb15af2a7be9f6063) | 구조 문서화 | 다음 사람이 ownership을 이해할 수 있도록 문서까지 남긴다 |

---

## 기능별 최종 구조

최종적으로 어떤 기능을 어느 파일에서 다루게 되었는지는 아래처럼 정리할 수 있다.

### `UploadChallengeDelegate`

- 암장 검색
- 암장 선택
- 난이도 선택
- 홀드 색 선택
- challenge 생성

이 영역을 수정할 때는 YOLO나 MediaPipe를 건드릴 필요가 없도록 분리했다.

### `UploadHoldDetectionDelegate`

- person detect 기반 best frame 추출
- YOLO hold 탐지
- 홀드 색 분류 / 필터링
- 수동 hold 추가/삭제
- 시작 홀드 / 종료 홀드 선택
- 홀드 번호 매기기

홀드 탐지 성능 개선, 홀드 색 구별 고도화, best frame 선택 개선 같은 작업은 여기서 이루어진다.

### `UploadSessionDelegate`

- 영상 선택
- 추가 시도 선택
- 녹화 후 업로드 이어받기
- managed temp file 생성과 보존
- MediaPipe pre-pose queue/cache/worker
- 결과 session 상태
- result playback 복원
- cleanup retention policy

MediaPipe pre-pose 캐시, temp file 정리, 결과 세션 복원, attempt-only restore는 여기서 다뤄야 하는 영역이다.

### `UploadSubmissionDelegate`

- `submitUpload`
- hold reach 분석
- 업로드 API
- AI 분석
- realtime finalize/fallback
- 결과 publish/restore logic

실시간 finalize 로직이나 AI 후처리, 업로드 시도 로직을 고칠 때는 이 파일이 중심이 된다.

### `UploadViewModel`

- screen이 바라보는 facade
- cross-delegate orchestration
- UI 계약 유지

즉, 화면 계약은 유지하되 실제 기능 owner는 delegate로 보냈다.

---

## 이번 브랜치에서 하지 않은 것

이번 브랜치가 일부러 하지 않은 일도 중요하다. 범위를 통제하지 않으면 리팩토링은 쉽게 실패한다.

이번에 하지 않은 것:

- multi-VM 분리
- screen split
- navigation boundary 재설계
- 2-depth 세분화
- AI 호출 최적화

왜 하지 않았는가:

- 목적이 "최종 구조 완성"이 아니라 "실무적인 1-depth 분리"였기 때문이다.
- UI 계약을 유지해야 했기 때문이다.
- 협업 충돌 감소가 최우선이었기 때문이다.
- 한 번에 너무 많은 결정을 바꾸면 회귀 위험이 커지기 때문이다.

즉, 이번 브랜치는 일부러 덜 했다. 이건 미완성이 아니라 범위를 의도적으로 통제한 결과다.

---

## 테스트와 검증 기록

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
- attempt-only 추가 업로드
- attempt-only 취소 후 결과 복원
- record -> upload handoff
- realtime finalize / fallback
- hold 재선택 시 pre-pose 유지, hold reach만 초기화

이 기록을 남기는 이유는, 이 작업이 단순 코드 이동이 아니라 "동작 보존"까지 포함한 리팩토링이었다는 점을 보여주기 위해서다.

---

## 포트폴리오용 결론 초안

이 작업은 AI가 대신 구조를 설계해 준 리팩토링이 아니었다. 실제 문제는 거대한 ViewModel 자체보다, 그 파일에 YOLO, person detect, MediaPipe, upload API, 실시간 분석, challenge 생성이 한꺼번에 몰려 협업 충돌이 발생한다는 데 있었다. 그래서 나는 처음부터 1-depth라는 범위를 고정하고, UI 계약과 UX flow는 유지한 채, 기능별 ownership을 나누는 방향으로 문제를 다시 정의했다.

실행 과정에서도 AI에게 구현을 일임하지 않았다. 먼저 업로드 플로우와 코드 결합을 조사하게 했고, 제안된 분리 경계가 실제 코드와 맞지 않을 때는 계획을 직접 수정했다. 특히 `selectionGeneration`, temp cleanup, result session, attempt-only restore처럼 깨지기 쉬운 seam은 사람 판단으로 재설계했다. 그리고 각 단계를 커밋 단위로 끊고, 자동 테스트와 수동 검증을 끼워 넣으며 통제했다.

결과적으로 `UploadViewModel.kt`는 2456줄에서 1119줄로 줄었고, challenge / hold detection / session / submission으로 owner 파일이 분리됐다. 더 중요한 것은 줄 수가 아니라, 어떤 기능을 어느 파일에서 다뤄야 하는지가 명확해졌다는 점이다. 이 프로젝트는 AI에게 맡긴 리팩토링이 아니라, 사람이 설계와 검증을 주도하고 AI를 탐색과 정리에 활용한 리팩토링 사례로 설명할 수 있다.

---

## 다음에 이 문서를 활용하는 방법

이 문서는 최종 완성 글이 아니라 원천 데이터다. 나중에 포트폴리오나 블로그 글로 재가공할 때는 아래 두 방향으로 줄여 쓸 수 있다.

1. 면접/포트폴리오 버전
   - STAR 요약
   - 핵심 수치
   - 내가 내린 설계 판단
   - AI를 어떻게 제어했는가

2. 기술 블로그 버전
   - 시간순 의사결정 기록
   - session/submission seam 보정 과정
   - 실패한 선행 시도와의 비교
   - 커밋 타임라인과 구조 변화

이 문서의 역할은 "예쁜 결과물"이 아니라, 나중에 어떤 형식으로든 다시 꺼내 쓸 수 있는 사실과 판단 근거를 남기는 것이다.

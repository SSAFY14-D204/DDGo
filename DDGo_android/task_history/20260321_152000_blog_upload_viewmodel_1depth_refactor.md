# Blog Draft: UploadViewModel 1-depth 리팩토링을 AI에게 맡기지 않고, AI를 활용해 설계한 방법

## 들어가며

이번 작업은 흔히 말하는 "AI로 리팩토링했다"는 문장으로 설명하면 정확하지 않다.  
더 정확한 표현은 이렇다.

> AI에게 리팩토링을 맡긴 것이 아니라, 사람이 설계와 검증을 주도하고 AI를 탐색과 정리에 활용했다.

이 문장을 굳이 강조하는 이유는, 이전에 비슷한 주제를 AI에게 더 많이 맡겼다가 실패한 경험이 있었기 때문이다. 그 경험 이후로 나는 이번 작업을 완전히 다른 방식으로 접근했다. 먼저 문제를 좁히고, 리팩토링 범위를 제한하고, 각 단계마다 검증 포인트를 끼워 넣었다. 그 결과, `UploadViewModel.kt`라는 거대한 파일을 무리하게 뜯어고치지 않으면서도 협업 충돌을 줄일 수 있는 구조를 만들 수 있었다.

## 시작점: 거대한 ViewModel이 만든 진짜 문제

문제는 단순히 `UploadViewModel.kt`가 길다는 것이 아니었다. 실제 문제는 이 파일 안에 너무 많은 역할이 몰려 있었다는 데 있었다.

- 업로드 진입과 영상 선택
- 암장 검색, 난이도 선택, challenge 생성
- person detect 기반 best frame 추출
- YOLO 기반 홀드 탐지와 홀드 색 분류
- MediaPipe pre-pose 준비
- 결과 세션 복원
- submit, hold reach 분석, AI 분석, realtime finalize/fallback

이런 구조에서는 여러 담당자가 동시에 같은 파일을 수정하게 된다.  
예를 들어:

- YOLO 담당자는 홀드 탐지 로직을 수정하고
- MediaPipe 담당자는 pre-pose 캐시를 수정하고
- 업로드 API 담당자는 submit을 수정하고
- 실시간 분석 담당자는 finalize/fallback을 수정한다

그런데 이 모든 축이 하나의 ViewModel 안에 있으면, 기능이 아무리 달라도 결국 같은 파일, 같은 구간에서 충돌이 발생한다.

그래서 이번 작업의 목표는 "예쁜 구조 만들기"가 아니라 "실제로 덜 부딪히는 구조 만들기"였다.

## 왜 1-depth만 하기로 했나

처음부터 범위를 크게 잡을 수도 있었다.  
예를 들면:

- multi-VM 분리
- screen split
- navigation boundary 재설계
- pre-pose와 submit 전체 재설계

하지만 그런 방식은 리스크가 너무 컸다. 업로드 기능은 화면 흐름과 결과 복원, AI 분석, 영상 재생이 다 얽혀 있어서 한 번에 크게 바꾸면 어디서 깨졌는지 찾기조차 어려워진다.

그래서 이번 작업에서는 아예 종료선을 먼저 정했다.

- 1-depth만 한다
- UI는 최대한 안 건드린다
- 기존 UX flow는 유지한다
- 성능 최적화는 다음 문제로 넘긴다
- 이번 목적은 충돌 감소와 ownership 분리다

이렇게 범위를 제한한 것이 오히려 성공의 핵심이었다.

## 첫 번째 접근: 기능 이름으로 나누려다 멈춘 이유

처음엔 누구나 생각할 수 있는 방향으로 출발했다.

- Challenge
- HoldDetection
- Flow
- Analysis

겉보기에는 나쁘지 않았다. 화면 흐름과도 얼추 맞고, 기능 이름도 익숙했다. 하지만 실제 코드를 읽어보니 이 구조는 위험했다.

특히 아래가 걸렸다.

- `selectionGeneration`
- temp cleanup
- result session restore
- pre-pose cache/worker
- attempt-only restore

이 부분은 이름상으로는 Flow와 Analysis에 나뉠 수 있어 보였지만, 실제로는 서로 강하게 붙어 있었다.  
예를 들어 temp cleanup은 단순 파일 삭제가 아니라,

- 현재 선택된 영상
- 결과 화면에서 재생 중인 영상
- published result session이 참조하는 영상
- pre-pose worker가 현재 쓰는 영상

을 모두 보고 keep-set을 계산해야 했다. 이걸 "Flow helper"처럼 가볍게 빼면 언젠가 결과 복원이 깨진다.

이 지점에서 중요한 전환이 있었다.

> 기능 이름으로 나누는 것과, 안전한 ownership seam으로 나누는 것은 다르다.

## 실제로 맞는 경계: Challenge / HoldDetection / Session / Submission

초기 설계를 수정한 결과, 최종적으로는 아래 네 축으로 가는 게 맞았다.

### 1. `UploadChallengeDelegate`

- 암장 검색
- 암장 선택
- 난이도 선택
- 홀드 색 선택
- challenge 생성

이 영역은 상대적으로 독립성이 높아서 가장 먼저 뺄 수 있었다.

### 2. `UploadHoldDetectionDelegate`

- person detect 기반 best frame 추출
- YOLO hold detect
- 홀드 색 분류
- 수동 hold 추가/삭제
- 시작 홀드 / 종료 홀드 선택
- 홀드 numbering

여기는 홀드를 "감지하고 편집하는 책임"을 하나로 묶은 영역이다.

### 3. `UploadSessionDelegate`

- 영상 선택
- 추가 시도 선택
- 녹화 후 업로드 이어받기
- managed temp file
- MediaPipe pre-pose queue/cache/worker
- 결과 playback/session state
- cleanup retention policy

처음엔 가장 헷갈렸던 부분인데, 실제로는 여기서 진짜 강결합이 드러났다.

### 4. `UploadSubmissionDelegate`

- `submitUpload`
- hold reach 분석
- upload API
- AI 분석
- realtime finalize/fallback
- 결과 publish/restore logic

여기는 "결과를 만드는 책임"을 모아 둔 영역이다.

이렇게 나누고 나니 `UploadViewModel`은 화면이 붙는 facade/orchestrator로 남길 수 있었다.

## 가장 중요했던 판단: result session의 state owner와 logic owner를 분리한 것

이번 작업에서 가장 중요한 설계 포인트를 하나만 고르라면 이거다.

result session 관련 로직을 submit 쪽으로 빼더라도,  
result session의 **상태 owner 자체는 session 쪽에 남겨야 한다**는 판단이다.

최종 구조는 이렇게 잡았다.

- `UploadSessionDelegate`
  - `resultPlaybackUris`
  - `publishedAttemptResultSession`
  - cleanup keep-set 기준

- `UploadSubmissionDelegate`
  - publish / restore logic
  - submit / hold reach / AI / realtime finalize

왜냐하면 cleanup은 단순 파일 관리가 아니라 retention policy이고, 그 기준이 흔들리면 결과 복원이 깨질 수 있기 때문이다.  
이 지점은 AI가 알아서 해주길 기대하기보다, 사람이 코드를 읽고 경계를 못 박아야 하는 부분이었다.

## "AI에게 맡기지 않았다"는 건 구체적으로 무슨 뜻인가

나는 이번 작업에서 AI를 많이 활용했다.  
하지만 그 사용 방식은 "알아서 리팩토링해"가 아니었다.

### 1. 먼저 조사부터 하게 했다

업로드 흐름, 화면 계약, pre-pose, attempt-only, record->upload handoff, 테스트 상태까지 먼저 조사하게 했다.  
추측으로 경계를 자르지 않게 하기 위해서였다.

### 2. 범위를 계속 제한했다

- 1-depth만
- UI는 그대로
- UX 유지
- 충돌 감소 최우선
- 큰 뭉텅이 분리

이 제약이 없으면 AI는 더 구조적으로 예쁜 대안을 쉽게 제안한다. 하지만 실무적으로는 예쁜 구조보다 안전한 구조가 먼저였다.

### 3. 잘못된 설계는 수정하게 했다

초기에는 Flow / Analysis 같은 분리안이 그럴듯했지만, 실제로는 session seam이 더 강했다. 이런 부분은 AI 제안을 그대로 실행하지 않고, 코드 결합을 다시 보고 수정했다.

### 4. 커밋 단위와 검증 순서를 통제했다

리팩토링을 한 번에 끝내지 않았다.

- 작은 커밋
- 자동 테스트
- 수동 flow 체크
- 다음 단계 진행

이 순서를 강제한 이유는, 실패했을 때 원인을 추적할 수 있게 하기 위해서였다.

즉, AI를 사용했지만 리팩토링의 방향, 속도, 종료 기준은 사람이 설계했다.

## 이전 실패 경험이 이번 성공에 준 영향

이번 작업 전에 `uploadViewModel 조언자` 스레드에서 AI에게 더 넓은 구조 판단을 맡겼다가 실패한 경험이 있었다.

그 실패에서 배운 점은 분명했다.

- AI는 그럴듯한 구조를 제안할 수 있다.
- 하지만 실제 코드 결합과 실무 제약을 충분히 반영하지 못하면 drift가 생긴다.
- 특히 session / cleanup / result restore 같은 seam은 가볍게 다루면 안 된다.

그래서 이번에는 방식 자체를 바꿨다.

- 조사 먼저
- 범위 제한
- 작은 커밋
- 테스트
- 수동 검증
- 문서화

이 순서가 결국 이전 시도와의 가장 큰 차이였다.

## 결과

정량적으로 보면 결과는 명확했다.

- `UploadViewModel.kt`: `2456줄 -> 1119줄`
- 감소량: `-1337줄`
- 감소율: `54.4%`
- 관련 핵심 파일 수: `1개 -> 7개`
- Git diff: `3098 insertions(+)`, `2125 deletions(-)`

새로 분리된 핵심 파일은 아래와 같다.

- `UploadChallengeDelegate.kt`
- `UploadHoldDetectionDelegate.kt`
- `UploadSessionDelegate.kt`
- `UploadSubmissionDelegate.kt`
- `UploadAsyncUiStates.kt`
- `UploadSessionModels.kt`

총 코드량은 늘었다. 하지만 이건 실패 신호가 아니라 의도된 결과였다.  
UI 계약을 유지하기 위한 facade wrapper와 ownership 분리 비용이 들어갔기 때문이다.

더 중요한 것은, 이제 YOLO 담당자, MediaPipe 담당자, upload API 담당자가 서로 다른 owner 파일에서 작업할 수 있게 되었다는 점이다.

## 무엇이 남았고, 왜 여기서 멈췄나

이번 브랜치에서 일부러 하지 않은 것도 있다.

- multi-VM
- screen split
- navigation boundary 재설계
- 2-depth 세분화
- AI 호출 최적화

왜냐하면 이번 브랜치의 목적은 "완벽한 구조"가 아니라 "실무적으로 안전한 1-depth 분리"였기 때문이다.

여기서 더 깊게 들어가면 다음 작업의 범위가 된다. 이번 작업은 그 다음 리팩토링을 가능하게 만드는 기반을 만든 것이다.

## 마무리

이번 리팩토링은 AI가 대신 설계해준 구조를 적용한 사례가 아니다.  
오히려 사람이 문제를 다시 정의하고, 범위를 줄이고, 실제 코드 결합을 읽고, 설계안을 수정하고, 검증 절차를 설계한 사례에 가깝다.

AI는 이 과정에서 유용했다.  
탐색을 빠르게 만들고, 정리를 잘 해주고, 반복 작업을 줄여줬다.  
하지만 어디까지나 도구였다.

결국 이 작업의 핵심 성과는 `UploadViewModel.kt`의 줄 수를 줄인 데만 있지 않다.  
어떤 기능을 어느 파일에서 다뤄야 하는지, 어떤 상태의 owner가 누구인지, 어떤 seam이 위험한지를 팀이 더 명확하게 이해할 수 있게 만든 데 있다.

그리고 나는 이 작업을 "AI에게 맡긴 리팩토링"이 아니라,  
"AI를 활용해 설계하고 검증한 리팩토링"이라고 설명할 수 있게 되었다.

## 참고 커밋

- [8356b35](https://lab.ssafy.com/s14-ai-image-sub1/S14P21D204/-/commit/8356b35a2f410b6492f04f5500dd406215b2326f)
- [159cf97](https://lab.ssafy.com/s14-ai-image-sub1/S14P21D204/-/commit/159cf970891ad78784959e397155745831f2ee27)
- [c0a5f53](https://lab.ssafy.com/s14-ai-image-sub1/S14P21D204/-/commit/c0a5f536644501d562b26e644b8b9d1234b41573)
- [f6728b0](https://lab.ssafy.com/s14-ai-image-sub1/S14P21D204/-/commit/f6728b025f3f3ab92995dfbfdbabef6c19bc5a80)
- [0a83d03](https://lab.ssafy.com/s14-ai-image-sub1/S14P21D204/-/commit/0a83d0315c00c79b865f4e57f4f59d36ff6e701d)
- [4c62196](https://lab.ssafy.com/s14-ai-image-sub1/S14P21D204/-/commit/4c62196663fa0580b16de7607e633cd03fbc9ca2)
- [df77918](https://lab.ssafy.com/s14-ai-image-sub1/S14P21D204/-/commit/df77918177371ff53da5bf4b5c9287ded21d0a66)
- [2c88bec](https://lab.ssafy.com/s14-ai-image-sub1/S14P21D204/-/commit/2c88bec10965d70fda5b696eb15af2a7be9f6063)

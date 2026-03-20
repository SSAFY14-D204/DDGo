# Task History: Pre-Pose Offline No-Lag Filter Debug

- **날짜**: 2026-03-20
- **작업자**: Codex (AI Assistant)
- **커밋 메시지 제안**: `feat: add offline no-lag pre-pose filter debug playback`
- **주요 대상 파일**
  - `DebugNavGraph.kt`
  - `DebugPoseScreen.kt`
  - `PrePoseLandmarkerScreen.kt`

## 1. 문제 정의

이번 작업에서 해결한 핵심 문제는 단순히 "필터 버튼을 추가했다"가 아니라, 아래 3개의 기술 문제를 하나의 디버그 워크플로우로 풀어낸 것입니다.

### A. 정지 프레임 비교만으로는 노이즈를 판단하기 어려움
Pose landmark의 품질 문제는 대부분 시간축에서 드러납니다. 한 프레임만 보면 좋아 보이지만, 실제 재생 시에는 팔, 손, 어깨가 미세하게 떨리거나 뒤늦게 따라오는 현상이 발생합니다.

### B. pre-pose는 이미 전체 시퀀스를 가지고 있는데도 필터링 후 lag가 발생함
처음에는 "재생이 느린가?"처럼 보였지만, 실제 원인은 playback이 아니라 smoothing sequence 생성 방식이었습니다.  
현재 필터는 모두 `previousPose` 또는 `history`만 사용하는 causal 필터였기 때문에, precomputed data를 재생하고 있어도 결과 자체에 phase lag가 들어가 있었습니다.

### C. debug UI가 "문제 재현"보다 "정적인 결과 확인"에 가까웠음
기존 비교 화면은 여러 필터 결과를 카드 형태로 보여주는 방식이어서, 기술 검증 관점에서는 예쁘지만 실제 의사결정에는 약했습니다.  
필요한 것은 "어떤 필터가 얼마나 덜 흔들리고, 얼마나 늦게 따라오는지"를 한 영상에서 바로 비교하는 playback 디버거였습니다.

## 2. 기술적 원인 분석

### A. playback 로직은 지연의 원인이 아니었음
재생 로직은 현재 재생 시간에 가장 가까운 `frameTimeMs`를 가진 pose를 찾아 overlay 하는 구조였습니다.  
즉, player 자체가 skeleton을 늦게 그리는 것이 아니라, "이미 늦어진 pose sequence"를 정확히 재생하고 있던 것이었습니다.

### B. 현재 smoothing은 모두 forward-only causal filter였음
- `EMA`: 이전 pose만 사용
- `Moving Average`: 최근 N개 history만 사용
- `Median`: 최근 N개 history만 사용
- `One Euro`: 이전 값과 이전 derivative만 사용

이 구조는 실시간 입력 처리에서는 자연스럽지만, pre-pose처럼 전체 시퀀스를 이미 확보한 오프라인 playback에는 최적이 아닙니다.

### C. 실시간용 필터와 오프라인용 필터를 같은 기준으로 비교하고 있었음
문제의 본질은 "필터가 좋냐 나쁘냐"가 아니라 "현재 목적이 live smoothing인지, offline playback smoothing인지"가 구분되지 않았다는 점이었습니다.  
그래서 이번 작업에서는 필터를 **Causal / Live-like**와 **Offline / No-Lag**로 분리하는 방향으로 구조를 재정의했습니다.

## 3. 해결 전략

### A. 디버그 화면을 재생 기반 비교 도구로 재구성
- 정지 프레임/멀티카드 중심 UI를 재생 중심 디버거로 전환
- 동일한 원본 영상 위에 선택한 필터 결과만 실시간 overlay
- `RadioButton`으로 필터를 하나씩 선택하고, `ExoPlayer + scrubber`는 유지

이렇게 바꾸면서 "노이즈 감소"와 "반응 지연"을 실제 사용자 체감 기준으로 검증할 수 있게 만들었습니다.

### B. playback 로직과 smoothing 로직을 분리
재생은 그대로 유지하고, 재생에 들어가는 `List<DebugPoseFrameResult>`를 mode별로 다르게 생성하도록 설계를 바꿨습니다.

- playback 책임: 현재 시간에 맞는 pose를 찾고 그린다
- smoothing 책임: 어떤 pose sequence를 미리 만들어 둘지 결정한다

이 분리를 통해 UI/재생 계층을 건드리지 않고도 필터 알고리즘을 자유롭게 실험할 수 있게 했습니다.

### C. causal과 offline filter를 모두 유지하는 모드 구조 도입
- `Causal / Live-like`
  - `Raw`
  - `EMA`
  - `Moving Average`
  - `Median`
  - `One Euro`
- `Offline / No-Lag`
  - `Raw`
  - `Zero-Phase EMA`
  - `Centered Moving Average`
  - `Centered Median`

즉, "실시간처럼 동작하는 필터"와 "미리 계산된 시퀀스에 최적화된 필터"를 같은 화면에서 비교할 수 있게 했습니다.

## 4. 알고리즘 설계와 구현 포인트

### A. Zero-Phase EMA
기존 EMA는 forward pass만 수행하므로 위상 지연이 생깁니다.  
이를 해결하기 위해:

1. 정방향 EMA 1회
2. 역방향 EMA 1회

를 적용하는 forward-backward 구조로 바꿨습니다. 이 방식은 noise smoothing은 유지하면서, onset/offset 시점이 한쪽으로 밀리는 현상을 크게 줄여줍니다.

### B. Centered Moving Average / Centered Median
기존 trailing window 대신, 현재 프레임 기준 좌우 대칭 window를 사용하도록 바꿨습니다.

- `window = 5`면 `[i-2, i-1, i, i+1, i+2]`
- 즉 현재 시점 양옆의 정보를 함께 사용

이 구조는 pre-pose처럼 전체 시퀀스를 다 알고 있는 경우에만 가능하며, live pipeline에서는 사용할 수 없습니다.

### C. Mirror Padding으로 edge 안정화
Centered window는 시퀀스 양 끝에서 boundary 처리가 필요합니다.  
단순 clamp나 shrink보다 시각적으로 더 자연스럽게 보이도록 mirror padding을 적용해 시작/끝 프레임에서 skeleton이 급격히 무너지지 않게 했습니다.

### D. timestamp는 건드리지 않고 좌표만 보정
가장 위험한 설계는 "lag가 있으니 timestamp를 앞당기자"는 보정 방식입니다.  
이번 작업에서는 그 접근을 피하고, `frameTimeMs`는 그대로 유지한 채 pose 좌표 시퀀스만 zero-phase 방식으로 재계산했습니다.

이 선택 덕분에 playback / scrubber / nearest pose lookup 규칙을 안전하게 재사용할 수 있었습니다.

## 5. 구현 결과

### A. 기술적으로 해결된 점
- pre-pose 시퀀스를 기반으로 한 playback debug 환경 구축
- causal filter와 offline no-lag filter를 명확히 분리
- 실시간 계열과 오프라인 계열의 trade-off를 눈으로 비교 가능한 구조 완성
- `One Euro`는 online adaptive filter라는 특성을 유지하면서 causal reference로 남김

### B. 디버깅/문제 해결 역량이 드러나는 포인트
- 증상을 playback 문제로 오해하지 않고 sequence 생성 단계의 위상 지연 문제로 분해함
- 필터를 "좋다/나쁘다"가 아니라 "사용 맥락이 다르다"로 재정의함
- UI 변경에 그치지 않고, signal processing 관점에서 알고리즘 설계를 바꿈
- timestamp를 억지로 조작하지 않고 데이터 생성 계층을 교체하는 안전한 구조로 정리함

### C. 검증
- `:app:compileDebugKotlin --rerun-tasks` 기준 빌드 성공
- 기존 pre-pose playback / JSON export / debug navigation 흐름 유지
- 선택한 mode와 filter가 바뀌어도 player를 재생성하지 않고 overlay sequence만 교체되도록 유지

## 6. 포트폴리오 관점에서 강조할 수 있는 내용

이 작업은 단순한 UI 개선이 아니라 아래 역량을 보여줍니다.

1. **문제의 층위를 정확히 분리하는 능력**
   - "느려 보인다"를 rendering, playback, data generation, filtering 중 어디 문제인지 분리해서 접근

2. **실시간 시스템과 오프라인 시스템의 알고리즘 차이를 이해하는 능력**
   - 같은 smoothing이라도 causal/live와 offline/no-lag는 설계가 달라야 한다는 점을 구조로 반영

3. **기존 코드를 최대한 재사용하면서 아키텍처를 확장하는 능력**
   - `ExoPlayer`, scrubber, overlay는 유지
   - sequence cache 계층만 mode-aware로 확장

4. **수학적 trade-off를 제품 검증 도구로 연결하는 능력**
   - zero-phase EMA
   - centered moving average / median
   - mirror padding
   - mode 기반 비교 UI

5. **디버그 도구를 제품 품질 개선용 자산으로 승격시키는 능력**
   - 개발 편의성용 화면이 아니라, 실제 pose 품질 의사결정을 도와주는 분석 툴로 발전시킴

## 7. 다음 개발자에게 남기는 메모

- offline no-lag 필터는 precomputed playback 전용입니다. 실시간 camera/live inference 경로에 그대로 가져가면 안 됩니다.
- `One Euro`를 offline 계열에 억지로 맞추기보다, causal reference로 유지하는 편이 기술적으로 정직합니다.
- 다음 단계로는 `Raw vs Selected` 동시 overlay, landmark별 heatmap, mode별 metric 로그 저장까지 확장하면 더 강력한 debug tool이 됩니다.

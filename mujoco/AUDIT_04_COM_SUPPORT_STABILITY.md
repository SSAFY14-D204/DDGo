# AUDIT 04: CoM / Support / Stability

선정 이유 한 줄:
**손발 상태와 하체 자세를 먼저 다듬었으니, 이제 그 결과가 실제 `CoM(무게중심)`과 `support/stability(지지/안정도)`에 납득 가능하게 반영되는지 확인해야 하기 때문입니다.**

## 기준 입력
- 기준 리포트: [json_service_benchmark_report_audit_final_10fps_corrected_v11_grip.json](C:/ssafy/project-2/S14P21D204/mujoco/json_service_benchmark/json_service_benchmark_report_audit_final_10fps_corrected_v11_grip.json)
- 기준 영상: [audit_10fps.mp4](C:/ssafy/project-2/S14P21D204/mujoco/video/audit_10fps.mp4)

## 코드 기준 해석 전제
현재 `support_stability(지지 안정도)`는 [support_stability.py](C:/ssafy/project-2/S14P21D204/mujoco/custom_articulated_human/support_stability.py) 기준으로 계산된다.

중요한 점:
- `inside_support(지지영역 안 여부)`와 `stability_margin_m(안정 여유)`는 **`YZ projection(YZ 평면 투영)`의 convex hull(볼록 껍질)** 로 계산된다.
- 즉 현재 지표는:
  - 손/발 접점의 `y,z` 위치
  - CoM의 `y,z` 투영 위치
를 비교하는 구조다.
- 반면 아래 요소는 직접 반영되지 않는다.
  - `wall reaction(벽 반력)`
  - `hand pulling effect(손으로 당기는 효과)`
  - 완전한 `3D equilibrium(3차원 평형)`

한 줄 해석:
**지금 `inside_support`는 “클라이밍 전용 완전한 안정 판정”이라기보다, `손발 지지점 대비 CoM이 얼마나 무리하게 벗어나 있는가`를 보는 상대 지표에 가깝다.**

## 전체 수치
- `processed_frames = 642`
- `phase_counts`
  - `recovery = 44`
  - `dynamic_transition = 160`
  - `loaded_transition = 222`
  - `static_support = 216`

- `support_type_counts`
  - `quad_support = 222`
  - `tri_support = 178`
  - `line_support = 170`
  - `point_support = 72`

- `inside_support_count = 204`
- `outside_support_count = 438`
- `stability_margin_summary_m`
  - `mean = -0.1762m`
  - `median = -0.1248m`
  - `min = -1.0768m`
  - `max = 0.3344m`

겉으로만 보면 전체는 좋지 않다.
하지만 이 raw count(전체 raw 개수)는 `dynamic_transition(이동 구간)`, `low confidence(낮은 신뢰도)` 프레임까지 모두 포함한다.

## 서비스 해석용 필터링 결과
### `static_support + high_confidence`
- 총 `216프레임`
- `inside_support = 122프레임`
- `inside ratio = 56.48%`
- `mean margin = -0.0130m`
- `median margin = 0.0745m`

### `loaded_transition + high_confidence`
- 총 `53프레임`
- `inside_support = 28프레임`
- `inside ratio = 52.83%`
- `mean margin = -0.0269m`
- `median margin = 0.0435m`

### 합산: `high_confidence + (static_support or loaded_transition)`
- 총 `269프레임`
- `inside_support = 150프레임`
- `inside ratio = 55.76%`

해석:
- `overall(전체)`로 보면 음수 쪽이 많다
- 하지만 `서비스에서 실제로 믿고 쓸 프레임(high confidence + 정적/준정적 구간)`만 보면
  - `inside ratio`가 절반을 넘고
  - `median margin`도 양수다
- 즉 **이 지표는 전체 raw count로 보기보다, 신뢰도 필터를 걸고 보는 것이 맞다**

## 대표 `Pass` 구간
### 구간 A: `frame 433~437`
- `phase = static_support`
- `analysis_confidence = high`
- `support_type = quad_support`
- `active_holds = {left_hand:11, right_hand:11, left_foot:8, right_foot:5}`
- `mean stability_margin ≈ +0.3298m`

해석:
- 양손 + 양발 4점 지지
- `inside_support = true`
- `contact_force_status = ok`
- 이 구간은 현재 지표가 매우 납득 가능하다

### 구간 B: `frame 446~462`
- `phase = static_support`
- `analysis_confidence = high`
- `support_type = quad_support`
- `active_holds = {left_hand:11, right_hand:9, left_foot:8, right_foot:5}`
- `mean stability_margin ≈ +0.3033m`

해석:
- 4점 지지에서 CoM가 안정적으로 support 안쪽에 들어온다
- 이런 구간은 서비스에서 `안정함`으로 표시해도 무리가 적다

### 구간 C: `frame 282~307`
- `phase = static_support`
- `analysis_confidence = high`
- `support_type = quad_support`
- `active_holds = {left_hand:6, right_hand:6, left_foot:8, right_foot:1}`
- `mean stability_margin ≈ +0.0782m`

해석:
- margin은 위 두 구간보다 작지만
- 그래도 `inside_support = true`
- 비교적 무난한 안정 구간으로 볼 수 있다

## 대표 `Borderline / Fail` 구간
### 구간 D: `frame 153~165`
- `phase = static_support`
- `analysis_confidence = high`
- `support_type = tri_support`
- `active_holds = {right_hand:4, left_foot:1, right_foot:2}`
- `mean stability_margin ≈ -0.3678m`

대표 프레임 `153`:
- `inside_support = false`
- `stability_margin_m = -0.3168`
- `contact_force_status = ok`
- `relative_residual ≈ 0.2856`

해석:
- 수치만 보면 명확히 불안정
- 하지만 이 구간은 `tri_support(3점 지지)`이며 손 지지가 포함된다
- 클라이밍에서는 손 당김과 벽 반력이 있기 때문에, `YZ polygon` 기준 음수라고 바로 “말이 안 된다”고 보긴 어렵다
- 다만 `margin`이 꽤 크게 음수라서, **현재 지표를 절대 안정성 판정으로 쓰기엔 위험한 구간**

### 구간 E: `frame 378~408`
- `phase = static_support`
- `analysis_confidence = high`
- `support_type = tri_support`
- `active_holds = {right_hand:6, left_foot:8, right_foot:5}`
- `mean stability_margin ≈ -0.2743m`

대표 프레임 `378`:
- `contact_force_status = ok`
- `relative_residual ≈ 0.2160`
- `body_loads.core ≈ 71.4`

해석:
- 하중과 손 지지는 말이 되는데 `inside_support`는 계속 false
- 이 역시 `tri_support + hand pull` 구간에서 현재 지표의 한계가 드러나는 케이스다

### 구간 F: `frame 505~513`
- `phase = static_support`
- `analysis_confidence = high`
- `support_type = tri_support`
- `active_holds = {right_hand:9, left_foot:8, right_foot:5}`
- `mean stability_margin ≈ -0.1957m`

대표 프레임 `505`:
- `inside_support = false`
- `contact_force_status = ok`
- `relative_residual ≈ 0.0211`
- `body_loads.core ≈ 142.4`

해석:
- 반력 residual은 매우 좋다
- 그런데 stability는 음수다
- 즉 **`support polygon` 지표와 `force plausibility(반력 설명 가능성)`가 어긋나는 경우**다

## 해석 요약
### 잘 나오는 부분
- `quad_support(4점 지지)` + `high_confidence` 구간에서는 꽤 잘 나온다
- 이런 구간은
  - `inside_support = true`
  - `stability_margin > 0`
  - `contact_force_status = ok`
가 함께 나오는 경우가 많다

### 애매한 부분
- `tri_support(3점 지지)` + 손 포함 구간에서는 `inside_support = false`가 자주 나온다
- 그런데 같은 프레임에서
  - `contact_force_status = ok`
  - `relative_residual`도 나쁘지 않을 수 있다
- 즉 현재 `support_stability`는 tri-support 클라이밍 자세를 완전하게 설명하지 못한다

### 왜 그렇게 나오나
- 현재 알고리즘은 `YZ support polygon` 기반이다
- 클라이밍에서는:
  - 손으로 당김
  - 벽면 마찰/반력
  - 몸이 벽에 붙은 상태의 3차원 평형
이 중요한데,
이 요소가 직접 반영되지 않는다

## audit 판정
- `CoM 절대 위치`: `Pass`
  - 높이/방향 자체는 최근 자세 보정 이후 꽤 상식적이다
- `Support / Stability 지표 전체`: `Borderline`
  - 4점 지지에서는 잘 맞지만
  - 3점 지지 클라이밍 구간에서는 절대 판정용으로 쓰기엔 약하다

## 서비스 사용 기준
### 바로 써도 되는 것
- `quad_support + high_confidence` 구간에서의 `inside_support`, `stability_margin`
- `안정 구간 vs 불안정 구간의 상대 비교`

### 조심해서 써야 하는 것
- `tri_support + hand contact` 구간의 `inside_support = false`
- 이 값은 “절대적으로 불안정”이라기보다
  - “지지 다각형 기준으로는 무게중심이 바깥쪽이다”
정도로 해석해야 한다

### 현재 추천 노출 방식
- raw `inside_support`를 그대로 “가능/불가능”으로 보여주지 말고
- `stability_level(안정도 수준)` 같은 질적 라벨로 바꿔서 노출
  - 예: `stable / caution / unstable`
- 필터:
  - `analysis_confidence = high`
  - `phase in (static_support, loaded_transition)`
일 때만 강하게 사용

## 다음 보정 우선순위
1. `CoM / Support`는 당장 대수술보다 **서비스 해석 규칙 정리**가 먼저
2. 그 다음 `Body / Joint Load(신체 부위 / 관절 부하)` audit
3. 마지막으로 필요하면 `support polygon`을 클라이밍 전용으로 더 고도화 검토

## 최종 판단
**현재 `CoM / Support / Stability`는 `quad_support(4점 지지)` 구간에서는 충분히 납득 가능하고, `tri_support(3점 지지)` 구간에서는 상대 지표로는 쓸 수 있지만 절대 안정 판정 지표로 쓰기엔 아직 부족하다.**

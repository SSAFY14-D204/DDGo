# AUDIT 06: Estimated Contact Force

선정 이유 한 줄:
**`Grip/Step(손잡기/발디딤 상태)`, `Pose fitting(자세 맞춤)`, `CoM / Support / Stability(무게중심 / 지지 / 안정도)`, `Body / Joint Load(신체 부위 / 관절 부하)`를 본 뒤, 이제 실제로 손과 발에 힘이 어떻게 분배되는지가 서비스에서 설명 가능한지 확인해야 하기 때문입니다.**

## 기준 입력
- 최신 기준 리포트: [json_service_benchmark_report_audit_final_10fps_corrected_v17_force_axis_smoothing_safe.json](C:/ssafy/project-2/S14P21D204/mujoco/json_service_benchmark/json_service_benchmark_report_audit_final_10fps_corrected_v17_force_axis_smoothing_safe.json)
- 비교 기준 리포트: [json_service_benchmark_report_audit_final_10fps_corrected_v13_force_confidence.json](C:/ssafy/project-2/S14P21D204/mujoco/json_service_benchmark/json_service_benchmark_report_audit_final_10fps_corrected_v13_force_confidence.json)
- 기준 영상: [audit_10fps.mp4](C:/ssafy/project-2/S14P21D204/mujoco/video/audit_10fps.mp4)

## 이번 라운드에서 추가한 것
- `force decomposition(힘 성분 분해)` 추가
  - `compressive_wall_normal_force_n(벽 법선 방향 압축 반력)`
  - `wall_tangential_force_n(벽 접선 방향 힘)`
  - `vertical_force_n(상하 방향 힘)`
  - `lateral_force_n(좌우 방향 힘)`
- `axis-wise contact weighting(축별 접점 가중치)` 추가
  - `STEP(발 디딤)`은 벽 법선 방향 힘을 조금 더 쉽게 받도록
  - `GRIP(손 잡기)`은 일부 축 힘을 조금 더 보수적으로 분배
- `temporal smoothing(시간 축 안정화)` 추가
  - `analysis_confidence = high`
  - `contact_force_status = ok`
  - 같은 `active_hold_id(활성 홀드)`를 유지하는 경우에만
  - 표시용 force를 짧게 부드럽게 연결

## 먼저 결론
`Estimated contact force(추정 손발 반력)`는 현재 단계에서 다음처럼 정리할 수 있습니다.

- **절대 실측 반력이라고 말할 수는 없다**
- 하지만 **좋은 조건의 프레임에서는 충분히 납득 가능한 `proxy(근사값)`**이다
- 이번 라운드의 개선은
  - **계산 정확도를 크게 바꾸기보다는**
  - **반력을 더 해석 가능하게 만들고, 표시 깜빡임을 줄이는 방향**에 의미가 있었다

최종 판정:
- `Estimated contact force(추정 손발 반력)` 전체: `Borderline`
- `high_confidence(높은 신뢰도) + contact_force_status = ok + tri/quad_support(3점/4점 지지)` 구간: `Pass`

즉 서비스에서는:
- 아무 프레임이나 raw force를 보여주면 안 되고
- **좋은 조건의 프레임만 사용**
- **절대 힘 진실값이 아니라 설명 가능한 분배값으로 해석**
하는 게 맞습니다.

---

## 이 값이 정확히 무엇인가

현재 `estimated_contact_forces_n(추정 손발 반력)`는
- 손/발의 `active contact(활성 접촉)` 상태
- MuJoCo `inverse dynamics(역동역학)`
- 현재 `required wrench(필요한 몸통 기준 힘/모멘트)`
를 바탕으로

**현재 손발 접점들에 힘을 어떻게 나누면 이 프레임을 설명할 수 있는지 계산한 추정치**입니다.

중요한 점:
- 단위는 이름 그대로 `N(뉴턴)`입니다
- 하지만 측정값이 아니라 **추정값**입니다
- 따라서 서비스에서는 항상:
  - `contact_force_status(반력 계산 상태)`
  - `contact_force_relative_residual(설명 오차 비율)`
과 같이 봐야 합니다

---

## raw 계산 품질 요약

리포트 `contact_force_distribution_summary(손발 반력 분배 요약)`:

- `status_counts(상태 개수)`
  - `ok = 393`
  - `high_residual = 118`
  - `no_active_contacts = 131`

서비스용 필터 기준:
- `analysis_confidence = high`
- `phase in (static_support, loaded_transition)`

필터 후:
- `filtered frame count = 269`
- `ok = 263`
- `high_residual = 6`
- `relative_residual median(설명 오차 중앙값) = 0.02290`
- `relative_residual p95(상위 5% 경계) = 0.29527`

비교 기준 `v13(신뢰도 기반 접점 가중 분배)`와 비교하면:
- `ok / high_residual = 263 / 6 -> 263 / 6`
- `relative_residual median = 0.02266 -> 0.02290`

해석:
- `axis-wise contact weighting(축별 접점 가중치)`는 **계산 품질을 크게 올리진 못했다**
- 하지만 **좋은 상태를 망치지도 않았다**

---

## 표시 품질 요약

이번에 `estimated_contact_forces_raw_n(원시 반력)`와 `estimated_contact_forces_n(표시용 반력)`를 분리했습니다.

`contact_force_display_smoothing(표시용 시간 축 안정화)` 결과:
- `alpha = 0.4`
- `max_gap_ms = 220`
- `smoothed_frame_count = 256`
- `smoothed_limb_count = 793`

프레임 간 힘 변화량 비교:
- raw `frame-to-frame delta(프레임 간 변화량) median = 2.82N`
- display `frame-to-frame delta median = 2.31N`
- raw `p95 = 40.22N`
- display `p95 = 32.53N`

해석:
- **표시용 smoothing(시간 축 안정화)은 실제로 깜빡임을 줄였다**
- 따라서 서비스 UI에는 `estimated_contact_forces_n(표시용 반력)`을 쓰고,
- 내부 검증/디버깅에는 `estimated_contact_forces_raw_n(원시 반력)`을 쓰는 것이 맞다

---

## 힘 성분 분해가 왜 의미가 있나

기존에는 `force_norm_n(전체 힘 크기)`만 보면
- 왜 손 힘이 큰지
- 발이 실제로 압축 반력을 얼마나 받는지
해석하기 어려웠습니다.

이제는 각 limb에 대해 다음을 같이 볼 수 있습니다.

- `force_norm_n(전체 힘 크기)`
- `compressive_wall_normal_force_n(벽 법선 방향 압축 반력)`
- `wall_tangential_force_n(벽 접선 방향 힘)`
- `vertical_force_n(상하 방향 힘)`
- `lateral_force_n(좌우 방향 힘)`

즉 서비스에서는
- 발은 `compressive_wall_normal_force_n(압축 반발력)`
- 손은 `force_norm_n(전체 지지력)` 또는 `wall_tangential_force_n(벽 접선 방향 힘)`
중심으로 보여주는 것이 더 직관적입니다.

---

## 대표 관찰 1: 매우 잘 맞는 4점 지지 구간
대표 프레임:
- `frame 433`
- `frame 446`

공통 상태:
- `phase = static_support`
- `analysis_confidence = high`
- `support_type = quad_support`
- `inside_support = true`
- `contact_force_status = ok`

오차:
- `frame 433`: `relative_residual = 0.000533`
- `frame 446`: `relative_residual = 0.000630`

`frame 433` 표시용 force:
- `left_hand.force_norm_n = 324.86`
- `right_hand.force_norm_n = 321.00`
- `left_foot.force_norm_n = 137.27`
- `right_foot.force_norm_n = 31.74`
- `left_foot.compressive_wall_normal_force_n = 117.46`
- `right_foot.compressive_wall_normal_force_n = 24.78`

`frame 446` 표시용 force:
- `left_hand.force_norm_n = 324.55`
- `right_hand.force_norm_n = 318.13`
- `left_foot.force_norm_n = 141.09`
- `right_foot.force_norm_n = 17.88`
- `left_foot.compressive_wall_normal_force_n = 115.82`
- `right_foot.compressive_wall_normal_force_n = 13.96`

해석:
- 손이 큰 비중을 갖고, 발은 압축 반발력 중심으로 보조 분담합니다
- `force decomposition(힘 성분 분해)` 덕분에 “발이 실제로 반발력을 받고 있다”를 설명하기 쉬워졌습니다
- residual이 거의 0이라 현재 모델이 이 프레임들을 매우 잘 설명합니다

판정:
- `Pass`

---

## 대표 관찰 2: 납득 가능한 3점 지지 구간
대표 프레임:
- `frame 111`
- `frame 112`
- `frame 505`

### `frame 111`
- `support_type = tri_support`
- `inside_support = false`
- `contact_force_status = ok`
- `relative_residual = 0.03381`

표시용 force:
- `left_hand.force_norm_n = 645.30`
- `left_foot.force_norm_n = 246.39`
- `right_foot.force_norm_n = 0.0`
- `left_foot.compressive_wall_normal_force_n = 192.40`

### `frame 112`
- `support_type = tri_support`
- `inside_support = false`
- `contact_force_status = ok`
- `relative_residual = 0.01256`

표시용 force:
- `left_hand.force_norm_n = 647.74`
- `left_foot.force_norm_n = 239.14`
- `right_foot.force_norm_n = 0.0`
- `left_foot.compressive_wall_normal_force_n = 186.74`

### `frame 505`
- `support_type = tri_support`
- `inside_support = false`
- `contact_force_status = ok`
- `relative_residual = 0.00466`

표시용 force:
- `right_hand.force_norm_n = 690.11`
- `left_foot.force_norm_n = 224.79`
- `right_foot.force_norm_n = 33.77`
- `left_foot.compressive_wall_normal_force_n = 175.80`

해석:
- 3점 지지이면서 손이 큰 역할을 하는 구간은
  - `inside_support = false`여도
  - `contact_force_status = ok`
  - residual도 낮을 수 있습니다
- 즉 `CoM / Support(무게중심 / 지지)`만 보면 불안정해 보여도
  **반력 분배로는 충분히 설명 가능한 프레임**이 존재합니다
- 특히 발의 `compressive_wall_normal_force_n(압축 반발력)`를 같이 보면 “발이 전혀 일을 안 한다”로 오해할 가능성이 줄어듭니다

판정:
- `Pass`

---

## 대표 관찰 3: 문제가 남는 3점 지지 구간
대표 프레임:
- `frame 158~160`

공통 상태:
- `phase = static_support`
- `analysis_confidence = high`
- `support_type = tri_support`
- `inside_support = false`
- `contact_force_status = ok` 또는 `high_residual`

오차:
- `frame 158`: `0.34731`
- `frame 160`: `0.38077`

표시용 force 패턴:
- `right_hand(오른손)`이 `631~653N` 수준으로 매우 큼
- `left_foot(왼발)`은 `0~1N` 수준
- `right_foot(오른발)`은 `13~48N` 수준

해석:
- 이 구간은 여전히 손 과독점이 남아 있습니다
- `confidence weighting(신뢰도 기반 접점 가중 분배)`로 약간 나아졌지만,
  **현재 접점 모델만으로는 손이 대부분의 wrench(힘/모멘트)를 설명하는 해**를 자주 선택합니다
- 이번 라운드의 `axis-wise weighting(축별 접점 가중치)`만으로는 이 문제를 크게 줄이지 못했습니다

판정:
- `Borderline`이 아니라 `Fail`에 가까운 대표 구간

---

## 이번 라운드의 종합 판정

### `force decomposition(힘 성분 분해)`
- `Pass`
- 이유:
  - 발 반발력을 `compressive_wall_normal_force_n(압축 법선 반력)`로 분리해서 설명 가능해짐
  - 손/발 힘 역할을 더 직관적으로 해석할 수 있음

### `axis-wise contact weighting(축별 접점 가중치)`
- `Borderline`
- 이유:
  - 계산 품질을 악화시키진 않았지만
  - 손 과독점 문제를 크게 줄이지도 못함

### `temporal smoothing(시간 축 안정화)`
- `Pass`
- 이유:
  - raw 계산은 유지하면서 표시 깜빡임만 줄임
  - 서비스 노출 관점에서 의미 있음

### 최종 판정
- `Estimated contact force(추정 손발 반력)` 전체: `Borderline`
- 서비스 노출용으로는 `조건부 Pass`

---

## 서비스 적용 규칙

서비스에서는 다음 규칙이 맞습니다.

1. `analysis_confidence = high`
2. `contact_force_status = ok`
3. `phase in (static_support, loaded_transition)`
4. 사용자 화면에는 `estimated_contact_forces_n(표시용 smoothing 반력)` 사용
5. 내부 분석/디버깅에는 `estimated_contact_forces_raw_n(원시 반력)` 사용
6. 절대 힘 진실값처럼 말하지 않고, `dominant support limb(우세 지지 손발)`와 `force share(힘 분배 비중)` 중심으로 해석

---

## 다음 액션

이번 단계에서 바로 이어서 해야 할 일은:
- 서비스 노출용 반력 필드만 따로 정리
- Android / FastAPI 응답에서는
  - 꼭 필요한 값만 전달
  - 내부용 raw / tuning 필드는 숨기기

한 줄 결론:
**이번 라운드로 `Estimated contact force(추정 손발 반력)`는 “더 정확해졌다”기보다, “더 해석 가능하고, 서비스에 보여주기 쉬워졌다”고 보는 게 맞습니다.**

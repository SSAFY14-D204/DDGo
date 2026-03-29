# AUDIT 05: Body / Joint Load

선정 이유 한 줄:
**`Grip/Step(손잡기/발디딤 상태)`, `Pose fitting(자세 맞춤)`, `CoM / Support / Stability(무게중심 / 지지 / 안정도)`가 어느 정도 정리됐으므로, 이제 실제로 어떤 부위가 얼마나 힘든지 해석 가능한지 확인해야 하기 때문입니다.**

## 기준 입력
- 기준 리포트: [json_service_benchmark_report_audit_final_10fps_corrected_v11_grip.json](C:/ssafy/project-2/S14P21D204/mujoco/json_service_benchmark/json_service_benchmark_report_audit_final_10fps_corrected_v11_grip.json)
- 기준 영상: [audit_10fps.mp4](C:/ssafy/project-2/S14P21D204/mujoco/video/audit_10fps.mp4)

## 먼저 결론
`Body / Joint Load(신체 부위 / 관절 부하)`는 현재 단계에서 다음처럼 정리할 수 있습니다.

- `Body Load(신체 부위 부하)`:
  - **상대 순위와 구간별 해석 기준으로는 충분히 납득 가능**
  - 서비스에서 직접 쓰기 가장 좋은 값
- `Joint Load(관절 부하)`:
  - **내부 분석용으로는 의미 있음**
  - 하지만 서비스에서 raw joint 이름과 raw 수치를 바로 노출하기에는 아직 부적절

최종 판정:
- `Body Load(신체 부위 부하)`: `Pass`
- `Joint Load(관절 부하)`: `Borderline`

---

## 이 값이 정확히 무엇인가

### `joint_loads(관절 부하)`
현재 `joint_loads`는 MuJoCo의 `qfrc_inverse(역동역학 일반화 힘)`를 사용합니다.

중요한 점:
- `hinge joint(회전 관절)`는 주로 `N·m(토크)`
- `slide joint(직선 관절)`는 `N(직선 힘)`

즉 `joint_loads`는 관절마다 단위가 완전히 같지 않습니다.

### `body_loads(신체 부위 부하)`
현재 `body_loads`는 여러 joint의 절대값을 신체 부위 그룹별로 더한 값입니다.

즉:
- `core(코어)` = 몸통/목 관련 joint 묶음
- `left_arm(왼팔)` = 왼쪽 어깨/팔꿈치 관련 joint 묶음
- `right_arm(오른팔)` = 오른쪽 어깨/팔꿈치 관련 joint 묶음
- `left_leg(왼다리)` = 왼쪽 고관절/무릎/발목 관련 joint 묶음
- `right_leg(오른다리)` = 오른쪽 고관절/무릎/발목 관련 joint 묶음

그래서 `body_loads`는 순수한 뉴턴값이 아니라:
**“그 부위가 현재 얼마나 부담을 받고 있는지 보는 proxy(근사 부하 점수)”**
로 해석해야 합니다.

즉 이 값은 `N/m`가 아닙니다.

---

## 전체 raw summary가 왜곡되는 이유

리포트의 `body_load_summary(전체 신체 부위 부하 요약)`는 raw 전체 프레임을 다 포함합니다.

예:
- `core.mean_abs_load_proxy = 1647.68`
- `left_arm.mean_abs_load_proxy = 1118.54`
- `right_arm.mean_abs_load_proxy = 756.33`
- `left_leg.mean_abs_load_proxy = 399.59`
- `right_leg.mean_abs_load_proxy = 390.80`

겉보기에는 `core(코어)`와 `left_arm(왼팔)`이 매우 크게 보이지만, 이 값을 그대로 서비스 지표로 쓰면 안 됩니다.

이유:
- `recovery(복구 구간)` 프레임이 포함됩니다
- `low_confidence(낮은 신뢰도)` 프레임이 포함됩니다
- 일부 프레임의 outlier(이상치)가 평균과 최대값을 심하게 끌어올립니다

실제로 `phase(동작 구간)`별 평균을 보면 차이가 극단적입니다.

- `static_support | high`
  - `core ≈ 75.77`
  - `left_arm ≈ 39.16`
  - `right_arm ≈ 43.60`
  - `left_leg ≈ 29.88`
  - `right_leg ≈ 45.29`

- `loaded_transition | high`
  - `core ≈ 64.46`
  - `left_arm ≈ 35.26`
  - `right_arm ≈ 42.57`
  - `left_leg ≈ 26.99`
  - `right_leg ≈ 39.48`

- `recovery | low`
  - `core ≈ 18359.29`
  - `left_arm ≈ 11084.94`
  - `right_arm ≈ 6039.55`
  - `left_leg ≈ 2852.93`
  - `right_leg ≈ 2635.01`

즉 raw summary가 튀는 가장 큰 이유는 `recovery(복구 구간)`입니다.

---

## 실제 min / max / 분포는 어떻게 나오나

### `body_loads(신체 부위 부하)` raw 전체 프레임 기준

- `core(코어)`
  - `min = 12.49`
  - `max = 54221.08`
  - `median = 75.16`
  - `p95 = 9124.22`

- `left_arm(왼팔)`
  - `min = 13.16`
  - `max = 33830.60`
  - `median = 39.52`
  - `p95 = 7684.53`

- `right_arm(오른팔)`
  - `min = 29.04`
  - `max = 33140.84`
  - `median = 44.07`
  - `p95 = 53.63`

- `left_leg(왼다리)`
  - `min = 6.72`
  - `max = 43487.89`
  - `median = 31.72`
  - `p95 = 77.96`

- `right_leg(오른다리)`
  - `min = 1.54`
  - `max = 48960.26`
  - `median = 43.83`
  - `p95 = 104.76`

해석:
- 중앙값은 수십 수준인데
- 최대값은 수만 단위입니다
- 즉 raw `min/max(최솟값/최댓값)`는 거의 서비스용으로 쓸 수 없습니다

### 서비스용 필터 적용 후 분포

필터:
- `analysis_confidence(분석 신뢰도) = high`
- `phase(동작 구간) in static_support / loaded_transition`

이 조건으로 자른 프레임 수:
- `269프레임`

이 필터 기준 `body_loads(신체 부위 부하)` 분포:

- `core(코어)`
  - `min = 12.49`
  - `max = 969.12`
  - `median = 59.21`
  - `p75 = 92.62`
  - `p90 = 109.78`
  - `p95 = 113.50`
  - `p99 = 174.80`

- `left_arm(왼팔)`
  - `min = 24.81`
  - `max = 50.34`
  - `median = 38.04`
  - `p75 = 42.11`
  - `p90 = 44.80`
  - `p95 = 47.27`
  - `p99 = 50.03`

- `right_arm(오른팔)`
  - `min = 31.85`
  - `max = 50.03`
  - `median = 44.57`
  - `p75 = 46.22`
  - `p90 = 47.81`
  - `p95 = 48.78`
  - `p99 = 49.91`

- `left_leg(왼다리)`
  - `min = 7.06`
  - `max = 78.54`
  - `median = 25.98`
  - `p75 = 42.07`
  - `p90 = 58.46`
  - `p95 = 67.36`
  - `p99 = 76.70`

- `right_leg(오른다리)`
  - `min = 3.46`
  - `max = 83.94`
  - `median = 42.09`
  - `p75 = 50.07`
  - `p90 = 59.11`
  - `p95 = 63.76`
  - `p99 = 66.48`

해석:
- 이 분포는 실제 서비스용 지표로 훨씬 납득 가능합니다
- 따라서 `min/max normalization(최솟값/최댓값 정규화)`가 아니라, **필터 이후 percentile(백분위수) 기반 정규화**가 맞습니다

---

## 서비스용 정규화는 어떻게 해야 하나

### 하면 안 되는 방식
- raw 전체 프레임 기준 `min/max normalization(최솟값/최댓값 정규화)`

이유:
- `max`가 recovery/outlier에 끌려 지나치게 큽니다
- 그러면 대부분 정상 프레임이 0 근처로 눌립니다
- 사용자는 차이를 거의 못 느끼게 됩니다

### 추천 방식
`percentile normalization(백분위 기반 정규화)`

권장 기준:
- 대상 프레임:
  - `analysis_confidence = high`
  - `phase in (static_support, loaded_transition)`
- 정규화 구간:
  - `lower = p10` 또는 `median`
  - `upper = p95`

예시:

```text
score = clip((value - lower) / (upper - lower), 0, 1) * 100
```

이렇게 만들면:
- recovery/outlier 영향을 줄일 수 있고
- “낮음 / 보통 / 높음” 구간이 더 자연스럽게 나뉩니다

### 서비스용 추천 출력
- `body_load_score(신체 부위 부하 점수, 0~100)`
- `body_load_share(신체 부위별 부하 비중, 0~1)`
- `dominant_load_region(가장 부담 큰 부위)`

즉 사용자에게는:
- `코어 부담 높음`
- `오른다리 부담 큼`
- `양팔 지지 비중 큼`
같은 식으로 해석해서 보여주는 게 맞습니다.

---

## 대표 관찰 1: high-step(발을 높이 드는 동작) 구간
대표 프레임:
- `frame 111`
- `frame 112`

상태:
- `phase = static_support`
- `support_type = tri_support`
- `active_holds = {left_hand:5, left_foot:3, right_foot:2}`

`frame 111` body loads:
- `core = 969.12`
- `right_leg = 83.94`
- `left_arm = 45.78`
- `right_arm = 45.52`
- `left_leg = 28.22`

`frame 112` body loads:
- `core = 847.92`
- `right_leg = 74.52`
- `left_arm = 43.43`
- `right_arm = 44.54`
- `left_leg = 26.14`

해석:
- 이 구간은 하체 보정에서 계속 보던 `high-step(발을 높이 들어 디디는 자세)` 구간입니다
- 실제로 `core(코어)`와 `right_leg(오른다리)`가 확실히 크게 올라갑니다
- 체감상 오른발을 높게 들어 버티는 자세와 잘 맞습니다

판정:
- `Pass`

---

## 대표 관찰 2: 왼발 지지 비중이 큰 구간
대표 프레임:
- `frame 265~274`

예:
- `frame 265`
- `phase = static_support`
- `active_holds = {left_hand:6, left_foot:8, right_foot:1}`

이 구간의 `left_leg(왼다리 부하)` 상위값:
- `78.54`
- `77.25`
- `76.95`
- `76.70`

같은 구간 특징:
- `inside_support = true`
- `contact_force_status = ok`

해석:
- 왼발이 주요 지지로 작동하는 구간에서 `left_leg`가 확실히 높게 나옵니다
- 이는 사람이 영상으로 봐도 비교적 납득 가능한 패턴입니다

판정:
- `Pass`

---

## 대표 관찰 3: 안정적인 4점 지지 구간
대표 프레임:
- `frame 433`
- `frame 446`

상태:
- `phase = static_support`
- `support_type = quad_support`
- `inside_support = true`
- `contact_force_status = ok`

`frame 433` body loads:
- `core = 94.25`
- `right_arm = 46.35`
- `left_arm = 41.53`
- `right_leg = 35.61`
- `left_leg = 12.73`

`frame 446` body loads:
- `core = 95.33`
- `right_arm = 44.36`
- `left_arm = 33.45`
- `right_leg = 39.91`
- `left_leg = 12.26`

해석:
- 4점 지지인데도 `core`가 가장 큽니다
- 이는 클라이밍에서 몸통 긴장과 자세 유지가 큰 비중을 차지한다는 점과 잘 맞습니다
- 동시에 좌우 팔/다리 순위도 크게 이상하지 않습니다

판정:
- `Pass`

---

## 대표 관찰 4: core(코어) 부담이 큰 tri-support(3점 지지) 구간
대표 프레임:
- `frame 505~513`

예: `frame 505`
- `core = 142.39`
- `right_arm = 44.95`
- `right_leg = 38.68`
- `left_arm = 32.91`
- `left_leg = 23.36`
- `support_type = tri_support`
- `inside_support = false`
- `contact_force_status = ok`

해석:
- 손 하나 + 양발 혹은 유사한 3점 지지에서 몸통 부담이 큰 자세로 보입니다
- `core`가 많이 올라가는 것은 체감과 맞습니다
- 다만 `inside_support = false`는 `CoM / Support(무게중심 / 지지)`와 함께 해석해야 합니다

판정:
- `Pass`에 가깝지만, `CoM / Support`와 같이 봐야 하므로 `Borderline` 성격도 일부 있음

---

## `Joint Load(관절 부하)` 관찰

전체 평균 기준 top joint:
- `shoulder_shrug_left`
- `abdomen_x`
- `abdomen_y`
- `neck_y`
- `abdomen_z`
- `shoulder_shrug_right`

대표 프레임에서 자주 상위로 나오는 joint:
- `abdomen_x / abdomen_y / abdomen_z`
- `shoulder_shrug_left / shoulder_shrug_right`
- `hip_x_right / hip_z_right / hip_y_right`

해석:
- 클라이밍에서 코어와 어깨가 자주 많이 쓰이는 것은 자연스럽습니다
- high-step 구간에서 `hip_x_right`, `hip_z_right`가 상위로 나오는 것도 최근 하체 보정 방향과 맞습니다

주의:
- joint 이름(`abdomen_x`, `shoulder_shrug_left`)을 서비스에서 그대로 보여주면 이해하기 어렵습니다
- 또한 `joint_loads`는 `N·m`와 `N`가 섞여 있어 직접 비교에도 제한이 있습니다

판정:
- `Borderline`

---

## 무엇이 납득 가능하고, 무엇이 아직 어려운가

### 납득 가능한 것
- high-step 구간에서 `core + 작업 다리`가 같이 올라갑니다
- 안정적인 4점 지지 구간에서 `core` 중심 부담이 자연스럽게 보입니다
- 특정 발 지지 구간에서 해당 다리 부하가 상대적으로 높아집니다
- arm/leg/core의 상대 순위가 영상 체감과 크게 어긋나지 않습니다

### 아직 raw 숫자 그대로 쓰기 어려운 것
- `body_load_summary(전체 raw 요약)`의 평균값
- `joint_loads(관절 부하)`를 사용자에게 직접 숫자로 보여주는 방식
- raw `min/max(최솟값/최댓값)` 기반 정규화

---

## 서비스 사용 기준

### 바로 사용해도 되는 것
- `frames[].body_loads`
- 단, `analysis_confidence = high` 프레임 위주
- 가능하면 구간 평균/최대값으로 요약

### 조심해서 써야 하는 것
- `joint_loads` 직접 노출
- `body_load_summary` raw 평균
- raw `min/max` 기반 점수화

### 현재 추천 출력 방식
- `body_load_score(신체 부위 부하 점수, 0~100)`
- `body_load_share(신체 부위 부하 비중)`
- `dominant_load_region(가장 부담 큰 부위)`

예:
- `코어 부담 높음`
- `오른다리 부담 큼`
- `양팔 지지 비중 큼`

---

## 최종 판단
**`Body / Joint Load`는 현재 단계에서 “정확한 절대 힘”이라기보다 “어느 부위가 상대적으로 더 힘든가”를 설명하는 용도로는 충분히 납득 가능합니다.**

정리:
- `Body Load(신체 부위 부하)`는 `Pass`
- `Joint Load(관절 부하)`는 `Borderline`
- 서비스에는 raw 수치보다:
  - `필터된 구간 평균`
  - `0~100 점수`
  - `부위별 비중`
  - `우세 부위`
형태로 내리는 것이 가장 적절합니다.

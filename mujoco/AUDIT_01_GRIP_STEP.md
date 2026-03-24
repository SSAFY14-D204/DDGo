# Audit 01. Grip/Step 상태

선정 이유: **Grip/Step 상태가 틀리면 support, CoM/stability, contact force, crux까지 전부 연쇄적으로 왜곡되기 때문이다.**

---

## 기본 정보

- 대상 로직:
  - polygon hold 기반 grip/step 판정
  - corrected pose + in-memory benchmark 경로
- 기준 리포트:
  - [json_service_benchmark_report_corrected_inmemory.json](C:/ssafy/project-2/S14P21D204/mujoco/json_service_benchmark/json_service_benchmark_report_corrected_inmemory.json)
  - [hold_tracker_comparison_summary.json](C:/ssafy/project-2/S14P21D204/mujoco/json_service_benchmark/hold_tracker_comparison_summary.json)
- 기준 시각화:
  - [hold_tracker_comparison_overlay.mp4](C:/ssafy/project-2/S14P21D204/mujoco/json_service_benchmark/hold_tracker_comparison_overlay.mp4)

---

## 핵심 수치

### 현재 최종 benchmark 기준

- `dynamic_sequence_gate.passed = true`
- `fit_mean_error_m = 0.0877`
- `recovery_ratio = 0.0560`
- `support_mode_counts.active_contacts = 853`
- `support_mode_counts.fallback_all_limbs = 111`

### hold 상태 요약

- `left_hand`
  - `GRIP = 606`
  - `REACH = 323`
  - `FREE = 35`
- `right_hand`
  - `GRIP = 534`
  - `REACH = 383`
  - `FREE = 47`
- `left_foot`
  - `STEP = 692`
  - `REACH = 220`
  - `FREE = 52`
- `right_foot`
  - `STEP = 660`
  - `REACH = 262`
  - `FREE = 42`

### downstream 영향 지표

- `support_type_counts`
  - `quad_support = 334`
  - `tri_support = 377`
  - `line_support = 188`
  - `point_support = 65`
- `contact_force_distribution_summary.status_counts`
  - `ok = 679`
  - `high_residual = 174`
  - `no_active_contacts = 111`

해석:
- active contact가 충분히 많이 잡히고 있다
- support가 3점/4점으로 나오는 프레임이 많다
- downstream 물리 분석은 이전보다 확실히 좋아진 상태다

---

## 비교 결과

### bbox 대비 polygon 차이

`hold_tracker_comparison_summary.json` 기준:

- `left_hand`
  - `same = 99`
  - `different = 455`
- `right_hand`
  - `same = 95`
  - `different = 459`
- `left_foot`
  - `same = 37`
  - `different = 517`
- `right_foot`
  - `same = 79`
  - `different = 475`

해석:
- polygon으로 바꾸면서 hold 판정이 크게 달라졌다
- 특히 발(`STEP`) 쪽 변화가 매우 크다
- 이 변화가 모두 개선이라고 보기는 어렵고, 실제로 사용자가 시각화에서 “엉뚱한 홀드에 초록색이 붙는다”고 확인했다

---

## 시각 검증 관찰

### 손 `GRIP`

관찰:
- bbox 기반보다 polygon 기반이 손 `GRIP`을 더 잘 이어 잡는 경향이 있다
- 실제 downstream 결과에서도 hand grip이 support/candidate로 충분히 반영된다

판단:
- 손 `GRIP`은 **전반적으로 개선된 편**
- 다만 bbox 대비 차이가 너무 커서, 모든 변경이 진짜 개선인지 자동으로 보긴 어렵다

### 발 `STEP`

관찰:
- 사용자가 직접 overlay를 보고, 실제로 디딘 홀드가 아니라 다른 홀드에 `STEP`이 붙는 경우를 확인했다
- 현재 step 로직은 발 전체가 아니라 앞꿈치 대표점 하나를 기준으로 판정한다
- polygon 기반으로 바꾸면서 발 `STEP`이 과하게 많이 잡히는 경향이 있다

근거:
- `left_foot STEP = 692`
- `right_foot STEP = 660`
- fast crux top 3가 모두 발 `STEP` 기반 홀드로 치우친 적이 있었다

판단:
- 발 `STEP`은 **hold 사용 여부는 대략 맞지만, 정확한 hold 선택은 아직 불안정**

---

## 항목별 판정

### 1. 손 `GRIP` 상태

- 판정: `Pass`
- 이유:
  - polygon 기반 이후 손 grip 지속성이 좋아졌다
  - downstream support / physics 결과가 개선됐다
  - 대표 손 크럭스 홀드가 physics 결과에 실제로 반영된다

### 2. 발 `STEP` 상태

- 판정: `Fail`
- 이유:
  - 실제 디딘 홀드가 아닌 다른 홀드에 `STEP`이 붙는 사례가 시각 검증에서 확인됐다
  - 현재 step 판정이 발 전체가 아니라 앞꿈치 대표점 하나에 너무 의존한다
  - 발 상태가 downstream 결과에 과도한 영향을 줄 가능성이 있다

### 3. 전체 `Grip/Step` 상태

- 판정: `Borderline`
- 이유:
  - 손 `GRIP`은 비교적 쓸 만하다
  - 발 `STEP`은 hold identity가 아직 불안정하다
  - 하지만 전체 active contact 품질은 improved benchmark를 만들 정도로 유의미하게 개선됐다

---

## 현재 단계에서의 사용 정책

### 서비스에 바로 써도 되는 것

- 손 `GRIP` 기반 active contact 존재 여부
- 전체 active contact 개수
- `support_type` 계산의 상위 분류
- fast/physics crux의 후보 참고값

### 조심해서 써야 하는 것

- 발 `STEP`의 정확한 hold id
- 발 중심 크럭스 해석
- step hold를 이용한 세부 설명 문장

### 아직 바로 쓰면 안 되는 것

- “이 발은 정확히 이 홀드를 디디고 있다” 식의 강한 단정
- 발 step hold identity를 근거로 한 세밀한 코칭 문장

---

## 원인 정리

가장 큰 원인:

1. 발은 실제 접촉이 점이 아니라 경계/모서리/앞꿈치 영역에 생긴다
2. 현재 step 로직은 발 대표점 1개에 의존한다
3. polygon 기반은 bbox보다 shape는 더 잘 보지만, 대표점이 잘못 놓이면 엉뚱한 polygon을 고를 수 있다
4. 따라서 “발이 어딘가를 디디고 있다”는 것과 “정확히 어떤 hold를 디디고 있다”는 것을 같은 수준으로 신뢰하면 안 된다

---

## 다음 보정 우선순위

1. **문제용 hold subset 적용**
- 주변 불필요한 hold를 줄여 잘못 붙는 경우를 먼저 줄인다

2. **시작/끝 hold 정보 활용**
- route semantics를 넣어 hold 후보를 제한할 수 있는지 검토한다

3. **발 `STEP` 해석 분리**
- step 존재 여부와 step hold identity를 구분해서 신뢰도를 다르게 둔다

4. **downstream 반영 정책 조정**
- 발 `STEP`이 low-confidence일 때는 support / crux 가중치를 낮추는 방식 검토

---

## 결론

- 손 `GRIP`은 현재 서비스 지표의 기반으로 사용 가능하다
- 발 `STEP`은 존재 여부는 참고 가능하지만, 정확한 hold 선택은 아직 보수적으로 해석해야 한다
- 따라서 현재 `Grip/Step` 항목의 전체 판정은 `Borderline`이다
- 다음 1순위 보정은 알고리즘을 무겁게 바꾸는 것보다
  - `문제용 hold subset`
  - `시작/끝 hold 정보 활용`
  - `발 STEP 신뢰도 분리`
  쪽이 맞다

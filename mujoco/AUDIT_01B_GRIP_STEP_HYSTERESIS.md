# AUDIT 01B: Grip/Step 보정 1차

## 목적
- `presence vs identity(디디고 있음 vs 정확한 홀드 식별)`를 실제 판정에 반영
- `11개 hold subset(문제용 홀드 집합)` 기준 `light hysteresis(가벼운 이전 홀드 유지)` 추가
- `start/end hold(시작/종료 홀드)`를 애매한 상황의 보조 신호로 반영

## 입력 세트
- 영상: [audit_10fps.mp4](C:/ssafy/project-2/S14P21D204/mujoco/video/audit_10fps.mp4)
- 홀드: [holds_polygon.json](C:/ssafy/project-2/S14P21D204/mujoco/json_service_benchmark/benchmark_inputs/audit_final_10fps/holds_polygon.json)
- 리포트:
  - 기준: [json_service_benchmark_report_audit_final_10fps_corrected_v10.json](C:/ssafy/project-2/S14P21D204/mujoco/json_service_benchmark/json_service_benchmark_report_audit_final_10fps_corrected_v10.json)
  - 보정 후: [json_service_benchmark_report_audit_final_10fps_corrected_v11_grip.json](C:/ssafy/project-2/S14P21D204/mujoco/json_service_benchmark/json_service_benchmark_report_audit_final_10fps_corrected_v11_grip.json)

## 적용 내용
- `hold_identity_confidence(홀드 식별 신뢰도)`가 낮을 때는 바로 다른 홀드로 갈아타지 않도록 `active/candidate hold(현재 활성/후보 홀드)` 유지
- `start hold(시작 홀드)`는 초반 짧은 구간에서만 tie-breaker(동률 깨기)로 사용
- `end hold(종료 홀드)`는 상단 구간에서 tie-breaker로 사용
- 디버그 필드 추가:
  - `route_bias_applied(시작/종료 홀드 보조 선택)`
  - `identity_hysteresis_applied(낮은 식별 신뢰도에서 이전 홀드 유지)`

## 결과 요약
- `dynamic_sequence_gate(전체 시퀀스 품질 게이트)`는 계속 `passed=true`
- `fit_mean_error_m(자세 fitting 평균 오차)`:
  - `0.11794 -> 0.11673`
- `recovery_ratio(복구 비율)`:
  - `0.06698 -> 0.06854`
  - 사실상 큰 차이는 없음

## 실제로 발동한 보정
- `right_hand`
  - `end hold bias(종료 홀드 보조 선택)` 2회
  - `active_low_identity_keep(낮은 식별 신뢰도에서 현재 홀드 유지)` 1회
- `left_hand`
  - `active_low_identity_keep` 1회
- `left_foot`
  - `candidate_low_identity_keep(낮은 식별 신뢰도에서 후보 홀드 유지)` 1회
- `right_foot`
  - `active_low_identity_keep` 2회

## 실제 상태 변화
- 기준 `v10` 대비 실제 `limb state(손발 상태)` 변화는 매우 작음
- 확인된 차이:
  1. `frame 485`
     - `right_hand`가 `end hold(종료 홀드)`로 더 명확하게 귀속됨
  2. `frame 549`
     - `left_foot` 후보 홀드가 애매한 프레임에서 덜 흔들리게 유지됨

## 해석
- 현재 입력은 이미 `11개 hold subset`으로 좁혀져 있어서 원래도 ambiguity(모호성)가 크지 않음
- 그래서 이번 보정은 판정을 크게 바꾸기보다, 애매한 프레임에서 안전장치로 동작함
- 즉 이번 1차 보정은:
  - `대형 개선`이라기보다
  - `현재 안정된 상태를 덜 흔들리게 만드는 소형 안정화`로 해석하는 것이 맞음

## 현재 판단
- `presence vs identity 분리 활용`: `Pass`
- `light hysteresis`: `Pass`
- `start/end hold 보조 반영`: `Pass`
- 다만 현재 영상에서는 효과가 작아서, `Grip/Step` 핵심 품질을 뒤집는 단계는 아님

## 다음 판단
- 지금 상태에서 `Grip/Step`은 서비스용으로 충분히 쓸 수 있는 쪽에 가까움
- 다음 1순위 검증 항목은 `CoM / Support / Stability(무게중심 / 지지 / 안정도)`가 더 효율적임

# AUDIT 02B. Pose Fitting / Recovery (10fps 입력 재검증)

선정 이유 한 줄:
`10fps 입력(초당 10프레임 입력)`은 실제 서비스 입력 형식과 가장 가까우므로, 이 조건에서 `Pose fitting(자세 맞춤)`과 `Recovery(복구/동결)`가 버티는지 먼저 확인해야 downstream physics를 믿을 수 있다.

---

## 기준 파일

- 기존 30fps 기반 입력 결과:
  - [json_service_benchmark_report_audit_final_corrected.json](C:/ssafy/project-2/S14P21D204/mujoco/json_service_benchmark/json_service_benchmark_report_audit_final_corrected.json)
- 10fps 변환 영상:
  - [audit_10fps.mp4](C:/ssafy/project-2/S14P21D204/mujoco/video/audit_10fps.mp4)
- 10fps 입력 결과:
  - [json_service_benchmark_report_audit_final_10fps_corrected.json](C:/ssafy/project-2/S14P21D204/mujoco/json_service_benchmark/json_service_benchmark_report_audit_final_10fps_corrected.json)

---

## 입력 조건

- 영상:
  - `audit.mp4` -> `audit_10fps.mp4`
- 서비스 가정:
  - MediaPipe 입력은 `10fps`
  - 분석은 `frame_step(입력 프레임 간 샘플 간격)=1`
  - `fit_frame_step(실제 full fit 간격)=1`
- 사용자 프로필:
  - 키 `167cm`
  - 몸무게 `75kg`
  - 윙스팬 `168cm`
- route hold:
  - `hold 1 = start`
  - `hold 11 = end`

---

## 핵심 수치 비교

| 항목 | 기존 30fps 기반 입력 | 10fps 입력 | 해석 |
|---|---:|---:|---|
| 처리 프레임 수 | 1924 | 642 | 10fps로 줄면서 프레임 수 감소 |
| `benchmark_total_s` | 18.58초 | 6.84초 | 속도는 크게 개선 |
| `service_end_to_end_total_s` | 20.29초 | 8.43초 | correction 포함 시간도 크게 개선 |
| `fit_mean_error_m` | 0.0749m | 0.0780m | 오차는 비슷 |
| `recovery_ratio` | 0.9693 | 0.9922 | recovery는 오히려 악화 |
| `fitted` | 30 | 5 | 실제 fitting 성공 프레임 수 감소 |
| `interpolated` | 29 | 0 | `fit_frame_step=1`이라 interpolation 없음 |
| `frozen_glitch` | 1851 | 621 | 절대 수는 줄었지만 비율은 더 큼 |
| `target_jump` freeze | 1851 | 614 | 여전히 주원인 |
| `bad_lower_limb_consistency` freeze | 0 | 7 | 초기 구간에서 추가 발생 |

---

## 주요 관찰

### 1. 속도는 좋아졌지만 품질은 좋아지지 않았다

- 10fps 입력으로 바꾸면서 처리 시간은 크게 줄었다.
- 하지만 `recovery_ratio(복구 비율)`는 `96.9% -> 99.2%`로 더 나빠졌다.
- 즉 지금은 **서비스 입력 형식에 가까워졌지만, physics 품질은 개선되지 않았다.**

### 2. `target_jump(목표 스켈레톤 프레임 간 급격한 변화량)` 문제가 여전히 핵심이다

- 첫 `target_jump` freeze:
  - `frame_index = 28`
  - `target_jump_mean_m = 0.1976`
  - `target_jump_max_m = 0.6432`
- 현재 기준보다 둘 다 큼:
  - `MEAN_TARGET_JUMP_M = 0.16`
  - `MAX_TARGET_JUMP_M = 0.45`

즉 10fps에서도 여전히 **target jump gate(목표 스켈레톤 점프량 게이트)**가 시퀀스를 대부분 막고 있다.

### 3. 이번에는 초반에 `bad_lower_limb_consistency(하체 방향 일관성 실패)`도 먼저 등장한다

- 첫 freeze는 `frame_index = 18`
- 이유: `bad_lower_limb_consistency`
- 그 뒤 `frame 28`부터 본격적으로 `target_jump`가 지배적이 된다.

즉 10fps 조건에서는:
- 초반: 하체 일관성 문제
- 이후 대부분: target jump 문제

### 4. 결론적으로 10fps 전환만으로는 Grip/Step downstream physics가 살아나지 않았다

- hold subset 적용
- start/end hold 반영
- STEP 존재 여부 / hold identity confidence 분리
는 그대로 유효하다.

하지만 `Pose fitting / Recovery`가 거의 전 구간에서 무너져서, 여전히
- support
- CoM / stability
- contact force
- physics crux
를 신뢰할 수 있는 상태는 아니다.

---

## 판정

### Pose fitting / Recovery
- `Fail`

이유:
- `dynamic_sequence_gate.passed = false`
- `recovery_ratio = 0.9922`
- `fitted = 5 / 642`

### 10fps 서비스 입력 전환 자체
- `Pass`

이유:
- 실제 서비스 입력 조건과 일치
- end-to-end 시간도 충분히 줄어듦

### Grip/Step을 physics에 연결해서 해석할 수 있는 상태인가
- `Fail`

이유:
- 현재 문제는 Grip/Step 이전 단계인 `Pose fitting / Recovery`에서 이미 거의 막힘

---

## 이번 라운드 결론

1. `10fps 입력 전환` 자체는 성공했다.
2. 속도는 실서비스에 훨씬 가까워졌다.
3. 그러나 물리량 품질은 좋아지지 않았고, 오히려 recovery 비율은 더 나빠졌다.
4. 따라서 다음 보정 우선순위는 여전히
   - `target_jump(목표 스켈레톤 프레임 간 급격한 변화량)`
   - `bad_lower_limb_consistency(하체 방향 일관성)`
   이다.

한 줄 결론:
**10fps 입력은 서비스 현실성과 속도 면에서는 맞는 방향이지만, 현재 병목은 여전히 `target_jump`와 `하체 일관성`이라서, 지금 단계에서는 Grip/Step보다 pose continuity(자세 연속성) 품질 게이트를 먼저 손봐야 한다.**

# Audit 02. Pose Fitting / Recovery (`audit.mp4`)

선정 이유: **현재 `audit.mp4`에서는 Grip/Step보다 먼저 pose fitting 연속성이 무너져서, downstream 물리량 전체 신뢰도를 결정하는 병목이기 때문입니다.**

---

## 기준 리포트

- [json_service_benchmark_report_audit_final_corrected.json](C:/ssafy/project-2/S14P21D204/mujoco/json_service_benchmark/json_service_benchmark_report_audit_final_corrected.json)
- 입력:
  - [audit.mp4](C:/ssafy/project-2/S14P21D204/mujoco/video/audit.mp4)
  - [hold_final.json](C:/ssafy/project-2/S14P21D204/mujoco/hold_final.json)
  - 사용자 프로필 `167cm / 75kg / wingspan 168cm`

---

## 핵심 수치

### pose detection 자체

- `processed_frames = 1924`
- `pose_detected = 1749`
- `pose_missing = 175`

해석:
- MediaPipe pose가 전혀 안 잡히는 영상은 아님
- 즉 현재 문제의 핵심은 `입력 포즈 부재`보다 `fitting 연속성 붕괴` 쪽에 가까움

### correction

- `filled_from_previous_frame_count = 162`
- `total_low_visibility_joint_count = 10700`
- `total_frozen_joint_count = 10692`
- `total_reconstructed_joint_count = 7`

해석:
- visibility가 낮은 joint가 많아서 correction이 상당히 보수적으로 동작함
- 다만 reconstruction 자체는 거의 안 일어남

### pose mode

- `backfilled_initial = 14`
- `fitted = 30`
- `interpolated = 29`
- `frozen_glitch = 1851`

### freeze reason

- `target_jump = 1851`
- `bad_fit_error = 0`
- `bad_lower_limb_consistency = 0`
- `bad_fit_and_lower_limb = 0`

### gate

- `fit_mean_error_m = 0.0749`
- `recovery_ratio = 0.9693`
- `dynamic_sequence_gate.passed = false`

---

## 가장 중요한 해석

겉으로 보면 `fit_mean_error_m = 7.49cm`라서 좋아 보일 수 있다.

하지만 실제로는:
- 정상적으로 fitted 된 프레임은 `30개`뿐이고
- 그 프레임도 `14 ~ 72` 구간에 몰려 있으며
- `73` 이후에는 거의 전부 `frozen_glitch`로 넘어간다

즉 지금 리포트는
- “잘 맞춘 프레임의 오차”는 낮지만
- “시퀀스 대부분은 이전 pose를 복사해 버틴 상태”다

그래서 현재는 **평균 fit error보다 recovery ratio가 훨씬 중요한 실패 신호**다.

---

## 구간 관찰

### fitted 구간

- fitted 프레임 범위: `14 ~ 72`
- 이후 fitted가 사실상 끊김

### freeze 시작점

- 첫 `frozen_glitch`는 `frame 73`
- 이후 recovery 상태가 대부분을 차지함

해석:
- 첫 freeze부터 거의 끝까지 `target_jump`가 반복됨
- 즉 현재 병목은 `bad fit`이나 `lower-limb consistency`가 아니라 **target jump gate**

초기 target jump 예시:
- `frame 73`
  - `target_jump_mean_m = 0.1763`
  - `target_jump_max_m = 0.5322`
- 현재 threshold
  - `MEAN_TARGET_JUMP_M = 0.16`
  - `MAX_TARGET_JUMP_M = 0.45`

즉 첫 실패 프레임부터 두 기준을 모두 초과한다.

---

## 현재 기준 Pass / Fail

### 1. pose detection availability

- 판정: `Pass`
- 이유:
  - 1924프레임 중 1749프레임에서 pose가 검출됨
  - 완전한 detection failure 영상은 아님

### 2. frame-wise fitting quality

- 판정: `Borderline`
- 이유:
  - fitted 된 소수 프레임의 오차는 낮음 (`7.49cm`)
  - 하지만 이 값만으로 시퀀스 품질을 대표할 수 없음

### 3. temporal continuity

- 판정: `Fail`
- 이유:
  - `fitted = 30`
  - `frozen_glitch = 1851`
  - 시퀀스 대부분이 이전 pose 유지

### 4. service-usable recovery ratio

- 판정: `Fail`
- 이유:
  - `recovery_ratio = 0.9693`
  - 현재 기준으로는 물리량을 downstream에 넘기기 어려움

---

## 왜 이렇게 되었을 가능성이 큰가

현재 근거만 놓고 보면 우선순위 높은 원인은 아래 3개다.

1. `target jump gate`가 `audit.mp4` 동작에 비해 너무 엄격함
- 현재 threshold:
  - `MAX_TARGET_JUMP_M = 0.45`
  - `MEAN_TARGET_JUMP_M = 0.16`
- 실제 freeze reason 집계상 `target_jump = 1851`
- 즉 이번 시퀀스 붕괴의 직접 원인은 사실상 이것으로 확정 가능

2. `profile-only biometrics`와 실제 체형 차이가 jump를 키웠을 가능성
- 이번 user body는 T-pose가 아니라 `height/weight/wingspan` 기반 추정
- 초기 few-shot fitting은 되지만, 동작이 커질수록 오차가 커질 수 있음

3. `corrected pose`가 `audit.mp4`의 움직임에 비해 너무 보수적으로 이어져, 특정 시점 이후 skeleton target 변화가 커졌을 가능성
 - correction 자체는 noise를 줄이지만
 - 구간 전환에서 이전 프레임 유지가 길어지면 jump가 더 크게 보일 수 있음

---

## 현재 결론

이번 `audit.mp4` 기준에서는:
- hold subset 적용 자체는 성공
- Grip/Step 로직 자체도 새 confidence 필드를 제공함
- 하지만 **Pose fitting / Recovery가 먼저 무너져서 downstream 물리량 전체를 신뢰하기 어려움**

즉 현재 1순위 병목은 `Grip/Step`이 아니라 `Pose fitting / Recovery`다.

---

## 다음 액션

1. `run_json_service_benchmark.py`에 `frozen_glitch` 원인 로깅 추가
- 완료
- 현재 결과: `target_jump = 1851`

2. `frame 60 ~ 100` 구간 중심으로 원인 분해
- first failure around `73`
- 이 구간만 집중해서 threshold 민감도 확인

3. 필요하면 다음 순서로 보정 실험
- `target jump threshold` 완화 실험
- `lower limb consistency` 게이트 완화 실험
- `profile-only biometrics` 보정치 조정

4. recovery가 내려오면 그때 다시
- Grip/Step
- Support/Stability
- Contact force
를 새 hold subset 기준으로 재검증

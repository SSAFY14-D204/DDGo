# AUDIT 07: CoM 좌표계 / 스케일 실측 감사

## 결론 요약

- `physics_result.frames[].com_position_m`는 `0~1 normalized` 좌표가 아니다.
- 이 값은 `MediaPipe world landmark -> MuJoCo 축변환 -> 신체 치수 기반 scale -> pelvis 기준 world offset -> MuJoCo fitting -> data.xipos 질량가중 평균`을 거친 **MuJoCo world-space meter 계열 좌표**다.
- 따라서 Android에서 `com_position_m`를 받아도 된다. 다만 **절대 픽셀 위치로 직접 투영하면 안 되고**, `torsoAnchor HUD`처럼 상대적/설명형 overlay로 해석해야 한다.
- 현재 repo 기준으로는 **“상대 변화 해석”에는 충분히 쓸 수 있지만, “정밀 계측/정밀 투영” 용도로는 아직 부족하다.**

이 문서는 `DDgo_AI_Server`와 `mujoco` 폴더를 함께 읽고, CoM이 실제로 어떤 값으로 만들어지는지와 Android가 어떤 전제에서 소비해야 하는지를 정리한 감사 결과다.

---

## 1. CoM 생성 체인

### 1-1. 입력: MediaPipe world landmark

- 원천 입력은 `pose_world_landmarks`다.
- `build_pose_points()`에서 MediaPipe world landmark를 읽어 pelvis, thorax, shoulder, hip, hand, foot 등 target point를 만든다.
- 근거:
  - `DDgo_AI_Server/app/services/mujoco_complete/runtime/custom_skeleton_verify/mediapipe_custom_skeleton_verify.py:308`
  - `DDgo_AI_Server/app/services/mujoco_complete/runtime/custom_skeleton_verify/mediapipe_custom_skeleton_verify.py:544`

### 1-2. 축변환: MediaPipe -> MuJoCo local

- 축변환은 아래 규칙을 사용한다.

```python
mapped[:, 0] = -landmarks_mp[:, 2]
mapped[:, 1] = -landmarks_mp[:, 0]
mapped[:, 2] = vertical_sign * landmarks_mp[:, 1]
```

- 즉 기본 해석은 아래와 같다.
  - `x`: MediaPipe depth의 부호 반전
  - `y`: MediaPipe 좌우축의 부호 반전
  - `z`: MediaPipe 높이축
- 근거:
  - `DDgo_AI_Server/app/services/mujoco_complete/runtime/dynamic_hold_verify/physics_worker.py:252`

### 1-3. scale: 신체 치수 기반 meter 스케일 적용

- `MetricSkeletonMapper.map_frame()`는 shoulder width를 기준으로 local 좌표를 meter 스케일로 맞춘다.
- 공식:

```python
scale_m_per_local = shoulder_width_m / shoulder_width_local
```

- lock 전에는 후보치를 누적하고, `scale_lock_frames = 8` 프레임이 쌓이면 고정한다.
- 근거:
  - `DDgo_AI_Server/app/services/mujoco_complete/runtime/custom_skeleton_verify/mediapipe_custom_skeleton_verify.py:507`
  - `DDgo_AI_Server/app/services/mujoco_complete/runtime/custom_skeleton_verify/mediapipe_custom_skeleton_verify.py:551`

### 1-4. offset: pelvis를 world 기준점으로 정렬

- world offset은 pelvis가 대략 `[0, 0, 1.05]`에 오도록 잡는다.

```python
offset_world = [0, 0, 1.05] - pelvis_local * scale
```

- 근거:
  - `DDgo_AI_Server/app/services/mujoco_complete/runtime/custom_skeleton_verify/mediapipe_custom_skeleton_verify.py:564`

### 1-5. root pose: pelvis + torso 축으로 MuJoCo root 설정

- `root_pose_from_targets()`는 pelvis를 root position으로 쓰고, `up_axis`, `left_axis`, `forward_axis`로 root quaternion을 만든다.
- `forward_axis = cross(left_axis, up_axis)`이므로 world 좌표는 단순 픽셀계가 아니라 **인체 방향이 반영된 MuJoCo world 좌표**다.
- 근거:
  - `DDgo_AI_Server/app/services/mujoco_complete/runtime/custom_articulated_human/evaluate_static_fit.py:156`
  - `DDgo_AI_Server/app/services/mujoco_complete/runtime/custom_articulated_human/evaluate_static_fit.py:667`

### 1-6. personalized MuJoCo model에 맞춘 fitting

- 서버는 입력된 신체 치수로 personalized XML을 만들고, 이 XML의 inertial mass와 segment length를 갱신한다.
- 근거:
  - `DDgo_AI_Server/app/services/mujoco_complete/runtime/custom_articulated_human/personalize_articulated_model.py:246`
  - `DDgo_AI_Server/app/services/mujoco_complete/runtime/custom_articulated_human/personalize_articulated_model.py:511`
  - `DDgo_AI_Server/app/services/mujoco_complete/runtime/json_service_benchmark/run_json_service_benchmark.py:592`
  - `DDgo_AI_Server/app/services/mujoco_complete/runtime/json_service_benchmark/run_json_service_benchmark.py:959`

### 1-7. 최종 CoM 계산

- 최종 CoM은 MuJoCo `data.xipos`를 질량가중 평균해서 계산한다.

```python
masses = model.body_mass[1:]
positions = data.xipos[1:]
com = sum(positions * masses) / sum(masses)
```

- 이 값이 그대로 `frame["com_position_m"]`로 직렬화된다.
- 근거:
  - `DDgo_AI_Server/app/services/mujoco_complete/runtime/dynamic_sequence_pipeline/run_dynamic_sequence_analysis.py:142`
  - `DDgo_AI_Server/app/services/mujoco_complete/runtime/dynamic_sequence_pipeline/run_dynamic_sequence_analysis.py:757`
  - `DDgo_AI_Server/app/services/mujoco_complete/runtime/json_service_benchmark/run_json_service_benchmark.py:851`
  - `DDgo_AI_Server/app/services/mujoco_complete/runtime/json_service_benchmark/run_json_service_benchmark.py:920`

정리하면:

```text
user_body_json
-> personalization.applied_metrics_m
-> MediaPipe world landmark
-> MuJoCo local 축변환
-> shoulder width 기준 scale
-> pelvis 기준 offset
-> root_position / root_quat fitting
-> personalized MuJoCo body inertial 반영
-> data.xipos 질량가중 평균
-> com_position_m
```

---

## 2. 실제 적용 수치와 scale

### 2-1. MuJoCo world 좌표계의 기준

- MuJoCo 모델은 `gravity="0 0 -9.81"`를 사용한다.
- 따라서 `z`는 높이축이다.
- worldbody에 floor plane이 있고, 기본 pelvis 위치는 `(0, 0, 1.03)`이다.
- 근거:
  - `DDgo_AI_Server/app/services/mujoco_complete/runtime/custom_articulated_human/custom_articulated_human.xml:3`
  - `DDgo_AI_Server/app/services/mujoco_complete/runtime/custom_articulated_human/custom_articulated_human.xml:39`
  - `DDgo_AI_Server/app/services/mujoco_complete/runtime/custom_articulated_human/custom_articulated_human.xml:42`

실무 해석:

- `z`: 높이
- `y`: 좌우 치우침
- `x`: depth/벽 방향 힌트

주의:

- complete runtime의 `support_stability`는 `YZ projection`을 기준으로 stability를 계산한다.
- 즉 현재 서비스 로직은 사실상 `x`를 “지지 polygon 바깥쪽 depth 축”처럼 취급한다.
- 근거:
  - `DDgo_AI_Server/app/services/mujoco_complete/runtime/custom_articulated_human/support_stability.py:118`
  - `mujoco/AUDIT_04_COM_SUPPORT_STABILITY.md`

### 2-2. Android -> 서버로 전달되는 실제 개인화 수치

Android는 `user_body_json`에 아래 값을 만들어 넣는다.

- `height_m`
- `weight_kg`
- `wingspan_m`
- `upper_arm_m`
- `forearm_m`
- `thigh_m`
- `shin_m`
- `shoulder_width_m`

근거:

- `DDGo_android/app/src/main/java/com/ddgo/app/data/repository/AiAnalysisRepositoryImpl.kt:246`

예시로 저장된 benchmark report의 `personalization.applied_metrics_m`는 아래와 같다.

| 항목 | 값 |
|---|---:|
| `body_mass_kg` | `75.0` |
| `upper_arm_m` | `0.2487078` |
| `forearm_m` | `0.2205522` |
| `thigh_m` | `0.40915` |
| `shin_m` | `0.41082` |
| `shoulder_width_m` | `0.38076` |
| `torso_length_m` | `0.501` |
| `hip_width_m` | `0.31897` |
| `hand_extension_m` | `0.05` |

출처:

- `mujoco/json_service_benchmark/json_service_benchmark_report_audit_final_10fps_corrected_v10.json`

### 2-3. sample frame로 재현한 실제 scale / offset

`mujoco/dynamic_hold_verify/sample_pose_world.json`와 `mujoco/custom_skeleton_verify/calibration.json`을 사용해 `MetricSkeletonMapper`를 20회 반복 실행해 scale lock 이후 값을 재현했다.

재현 결과:

| 항목 | 값 |
|---|---:|
| `scale_lock_frames` | `8` |
| `scale_m_per_local` | `1.073560` |
| `offset_world` | `[0.053678, 0.0, 0.835288]` |
| `pelvis_world` | `[0.0, 0.0, 1.05]` |
| `thorax_world` | `[0.0, 0.0, 1.425746]` |
| `shoulder_width_world` | `0.386482` |

해석:

- mapper가 실제로 `shoulder_width_m`와 거의 같은 world shoulder width를 만들고 있다.
- 즉 `com_position_m`는 임의 숫자가 아니라, **body metric을 반영해 meter scale로 옮긴 좌표**다.

### 2-4. 질량 분배 비율

#### Skeleton 단계 `SEGMENT_MASS_RATIOS`

출처:

- `DDgo_AI_Server/app/services/mujoco_complete/runtime/custom_skeleton_verify/mediapipe_custom_skeleton_verify.py:111`

| 세그먼트 | 비율 |
|---|---:|
| `head_neck` | `0.0694` |
| `trunk` | `0.4346` |
| `left_upper_arm` / `right_upper_arm` | `0.0271` |
| `left_forearm` / `right_forearm` | `0.0162` |
| `left_hand` / `right_hand` | `0.0061` |
| `left_thigh` / `right_thigh` | `0.1416` |
| `left_shank` / `right_shank` | `0.0433` |
| `left_foot` / `right_foot` | `0.0137` |

#### Personalized MuJoCo 단계 `BODY_MASS_FRACTIONS`

출처:

- `DDgo_AI_Server/app/services/mujoco_complete/runtime/custom_articulated_human/personalize_articulated_model.py:19`

| body | 비율 |
|---|---:|
| `pelvis` | `0.0800` |
| `torso_base` | `0.1275` |
| `thorax` | `0.2221` |
| `head` | `0.0694` |
| `left_shoulder_mount` / `right_shoulder_mount` | `0.00125` |
| `left_upper_arm` / `right_upper_arm` | `0.0271` |
| `left_elbow` / `right_elbow` | `0.0162` |
| `left_hand` / `right_hand` | `0.0061` |
| `left_hip_mount` / `right_hip_mount` | `0.00125` |
| `left_thigh` / `right_thigh` | `0.1416` |
| `left_knee` / `right_knee` | `0.0433` |
| `left_foot` / `right_foot` | `0.0137` |

해석:

- trunk 계열 질량 비중이 매우 크다.
- 따라서 정상적인 자세에서는 CoM이 torso/pelvis 근처에 머무는 것이 맞다.

### 2-5. root blend / wall 관련 값

| 항목 | 값 | 의미 |
|---|---:|---|
| `ROOT_BLEND_ALPHA` | `0.35` | seed qpos가 있을 때 root pose를 새 target과 섞는 비율 |
| `support_margin_m` | `0.15` | `dynamic_hold_verify` 경로의 balance score margin 기본값 |
| `wall_axis` | hold metadata에서 resolve | `dynamic_hold_verify`에서 벽 평면 축을 결정 |
| `wall_plane_value` | hold metadata 또는 hold 평균값 | CoM의 wall distance 계산에 사용 |

근거:

- `DDgo_AI_Server/app/services/mujoco_complete/runtime/custom_articulated_human/evaluate_static_fit.py:63`
- `DDgo_AI_Server/app/services/mujoco_complete/runtime/dynamic_hold_verify/physics_worker.py:323`
- `DDgo_AI_Server/app/services/mujoco_complete/runtime/dynamic_hold_verify/physics_worker.py:1154`

주의:

- `support_margin_m`, `wall_axis`, `wall_plane_value`는 **dynamic_hold_verify 경로**에서 직접 사용되는 값이다.
- 현재 complete API의 `physics_result.frames[]`에는 이 값이 프레임별로 직접 노출되지 않는다.

---

## 3. 샘플 리포트 기반 범위

## 3-1. `dynamic_sequence_pipeline/dynamic_sequence_report.json`

이 리포트는 low-level dynamic sequence pipeline 산출물이라 `root_position_m`, `com_support_offset_m`까지 포함한다.

| 필드 | x min/max/mean | y min/max/mean | z min/max/mean |
|---|---|---|---|
| `com_position_m` | `-0.0589 / 0.1007 / 0.0364` | `-0.2478 / 0.1330 / 0.0024` | `1.1663 / 1.3728 / 1.2497` |
| `root_position_m` | `-0.0430 / 0.0739 / 0.0131` | `-0.0473 / 0.0753 / 0.0051` | `1.0047 / 1.1147 / 1.0586` |
| `com_support_offset_m` | `-0.6809 / 0.0981 / -0.2243` | `-0.9766 / 0.7393 / -0.1329` | `-1.0214 / 0.9895 / 0.0429` |

추가 요약:

- `stability_margin_m`: `min=-1.0683`, `max=0.3796`, `mean=-0.4078`
- `inside_support_count=171`
- `outside_support_count=793`

### 해석

- `root_position_m.z`가 `약 1.00~1.11m`이고 `com_position_m.z`가 `약 1.17~1.37m`인 것은 정상적이다.
- pelvis/root보다 torso mass center가 위쪽에 있기 때문이다.
- `com_support_offset_m`는 support center 기준이므로 변화 폭이 훨씬 크다. 이 값은 “support 대비 얼마나 벗어났는가”를 보는 데 유용하다.

## 3-2. `json_service_benchmark/json_service_benchmark_report_audit_final_10fps_corrected_v10.json`

이 리포트는 현재 complete API 구조와 가까운 benchmark 산출물이다.

| 필드 | x min/max/mean | y min/max/mean | z min/max/mean |
|---|---|---|---|
| `com_position_m` | `-0.1818 / 0.1885 / 0.0620` | `-0.1504 / 0.1253 / -0.0361` | `1.0906 / 1.2980 / 1.1926` |

추가 요약:

- `stability_margin_m`: `mean=-0.1715`, `median=-0.1295`, `min=-1.0798`, `max=0.3332`
- `inside_support_count=207`
- `outside_support_count=435`

### 해석

- 이 값들은 전형적인 “서 있는 사람의 body COM world 위치” 범위다.
- 즉 화면상의 점이라기보다:
  - `z`: 높이 약 `1.1~1.3m`
  - `y`: 좌우 치우침 약 `10~15cm`
  - `x`: depth/벽 방향 변화 약 `20cm` 내외
  로 읽는 것이 맞다.

## 3-3. 현재 complete API에 실제로 내려오는 필드

현재 complete benchmark report의 frame에는 아래 필드가 있다.

- 있음
  - `com_position_m`
  - `support_stability`
  - `body_loads`
  - `estimated_contact_forces_n`
  - `contact_force_status`
- 없음
  - `root_position_m`
  - `support_center_m`
  - `com_support_offset_m`

근거:

- `DDgo_AI_Server/app/services/mujoco_complete/runtime/json_service_benchmark/run_json_service_benchmark.py:845`

즉 `root_position_m`, `com_support_offset_m`는 내부 dynamic sequence pipeline에는 존재하지만, **현재 complete API 응답에서는 기본 제공되지 않는다고 보는 편이 안전하다.**

---

## 4. 정밀도 검증: 왜 “meter 계열”이지만 “정밀 실측”은 아닌가

`mujoco/custom_skeleton_verify/target_skeleton_gate1_report.json` 기준으로 gate1 검증은 통과하지 못했다.

실제 실패 항목:

- `left_forearm_mean_abs_error_above_5cm`
- `right_forearm_mean_abs_error_above_5cm`
- `reprojection_mean_above_25px`

주요 수치:

- `reprojection_error_px.mean = 92.33`
- `left_forearm.mean_abs_error_m = 0.0923`
- `right_forearm.mean_abs_error_m = 0.0921`

해석:

- 좌표계와 scale은 물리적으로 일관된 방향으로 맞춰져 있다.
- 하지만 pose reprojection 오차와 일부 segment length 오차가 아직 커서, `com_position_m`를 “센티미터급 실측 ground truth”처럼 쓰면 안 된다.

따라서 현재 신뢰수준은 아래처럼 보는 것이 맞다.

- 가능
  - 프레임 간 CoM 이동 경향
  - 지지 안정성/support와의 상대 비교
  - HUD형 설명 표시
- 부적절
  - 정확한 픽셀 투영점
  - 정밀 자세 계측
  - 카메라 공간 절대 위치 해석

---

## 5. Android에서 믿어도 되는 수준 / 금지해야 할 해석

## 5-1. 안전한 사용

- `torsoAnchor = avg(leftShoulder, rightShoulder, leftHip, rightHip)` 기준 HUD
- `CoM x/y/z` raw 값 표시
- `z`를 높이 변화로 해석
- `y`를 좌우 치우침 변화로 해석
- `x`는 점 위치가 아니라 depth/벽거리 힌트로 색상, 텍스트, alpha 등으로 해석
- `support_stability` / `body_loads` / `contact_force_status`와 함께 종합 해석

## 5-2. 금지해야 할 사용

- `com_position_m`를 영상 픽셀 좌표처럼 직접 찍기
- `0~1 normalized` 좌표라고 가정하기
- 카메라 기준 절대 위치라고 가정하기
- `x/y/z`를 곧바로 “실제 벽까지 거리/실제 좌우 cm”로 단정하기

## 5-3. Android에 권장되는 보조값

현재 complete API 기준으로 실제로 바로 쓰기 좋은 값:

- `support_stability.com_proj_yz`
- `support_stability.com_proj_xz`
- `support_stability.support_centroid_yz`
- `support_stability.inside_support`
- `support_stability.stability_margin_m`

추가로 서버가 내려주면 매우 유용한 값:

- `root_position_m`
- `support_center_m`
- `com_support_offset_m`
- `wall_axis`
- `wall_plane_value`
- `scale_m_per_local`
- `offset_world`

이 값들이 추가되면 Android에서 `torsoAnchor HUD`를 넘어, 더 일관된 상대 투영 규칙을 만들 수 있다.

---

## 6. 감사 결론

### 판정

- `com_position_m`의 좌표계: **Pass**
  - normalized가 아니라 MuJoCo world-space meter 계열 값이라는 점은 코드와 샘플 리포트가 일관되게 뒷받침한다.
- `com_position_m`의 물리적 의미: **Pass**
  - 질량 분배, personalized XML, `data.xipos` 기반 계산까지 연결이 명확하다.
- Android 직접 투영 가능성: **Fail**
  - 현재 응답만으로는 카메라/픽셀 좌표로 정확하게 재투영할 정보가 부족하다.
- Android HUD 표시 적합성: **Pass**
  - torsoAnchor 기준 설명형 overlay에는 충분히 사용할 수 있다.
- 정밀 계측 용도: **Borderline / Fail**
  - gate1 reprojection/segment error 기준으로는 아직 정밀 계측으로 보기 어렵다.

### 최종 권고

- 지금 당장은 Android에서 `CoM`을 **몸통 중심 기준 HUD**로 표시하는 것이 가장 안전하다.
- `x`는 depth 정보로, `y/z`는 상대 변화량 설명용으로 사용한다.
- `root_position_m`, `com_support_offset_m`, `wall_axis`, `wall_plane_value`, `scale_m_per_local`, `offset_world`를 서버가 추가로 제공하면 v2 투영 설계를 논의할 수 있다.

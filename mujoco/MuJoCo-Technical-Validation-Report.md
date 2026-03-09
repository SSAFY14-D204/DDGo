# MuJoCo 기술 검증 결과 보고서

## 1. 문서 목적

본 문서는 현재 저장소의 MuJoCo 기반 클라이밍 자세 분석 파이프라인에 대해, 실제로 수행한 구현/실험/로그 검증 결과를 정리한 기술 검증 보고서이다.  
일반적인 MuJoCo 소개 문서가 아니라, 다음 질문에 답하기 위해 작성되었다.

- MediaPipe 기반 3D 포즈를 MuJoCo humanoid에 얼마나 비슷하게 맵핑할 수 있는가
- 손/발을 홀드에 고정했을 때 관절 부하, 부위별 부하, CoM, 반발력 같은 물리량을 어느 수준까지 계산할 수 있는가
- 현재 결과를 실시간 서비스에 바로 사용할 수 있는가
- 어떤 부분은 이미 설득력이 있고, 어떤 부분은 아직 연구/고도화가 필요한가

## 1.1 문서 요약

- MuJoCo는 MediaPipe 기반 클라이밍 자세를 사람 눈으로 유사하게 재현하고, 관절 부하·부위별 부하·CoM 같은 물리 지표를 계산하는 분석 엔진으로 활용 가능함이 확인되었다.
- 자세 맵핑 정확도는 baseline보다 tuned advance 경로에서 뚜렷하게 개선되었고, 동적 테스트 기준 평균 위치 오차가 약 `66.9 cm -> 33.5 cm` 수준까지 감소하였다.
- 정적 분석에서는 손/발을 가상 홀드에 고정한 상태에서 관절 토크, 부위별 부하, CoM, 손/발 반력을 추출할 수 있었으며, 상대 비교 지표로는 충분한 해석력이 있었다.
- 다만 현재 손/발 반력은 실제 마찰 접촉력이 아니라 `weld` 제약 기반 anchor reaction이므로, 절대적인 힘 값으로 바로 서비스화하기에는 한계가 있다.
- 현재 단계에서 가장 신뢰할 수 있는 결과는 `자세 유사도 비교`, `과부하 관절 탐지`, `팔/다리/코어 단위 부하 분포`, `CoM의 상대 위치 해석`이며, friction/contact 정교화와 reach error 감소가 후속 고도화의 핵심 과제이다.
- 결론적으로 MuJoCo는 실시간 클라이밍 분석 서비스의 “물리 추론 엔진”으로 충분한 가능성을 보였고, 접촉/마찰 모델과 개인화 정합을 고도화할수록 결과의 설득력은 의미 있게 증가할 것으로 판단된다.

---

## 2. 검증 대상과 범위

### 2.1 검증 대상 코드 경로

- 안정형 baseline
  - `mujoco/verify/mediapipe_mujoco_verify.py`
- 실험형 advance
  - `mujoco/advance/mediapipe_mujoco_verify.py`
  - `mujoco/advance/mediapipe_advanced_v2.py`
- 물리 분석 및 정적 추출
  - `mujoco/pysical_verify/physics_worker.py`
  - `mujoco/pysical_verify/static_posture_physics.py`
  - `mujoco/static_load_extract/static_posture_physics.py`

### 2.2 검증 범위

- 2D/3D 포즈를 MuJoCo humanoid로 맵핑하는 정확도
- 정적/동적 상황에서의 end-effector(손/발) 위치 오차
- weld 기반 고정 상태에서의 inverse dynamics 출력
- 관절별 부하와 신체 부위별 부하 집계
- CoM 위치와 접점 중심 간 상대 관계
- 손/발 anchor 반력 추정

### 2.3 이번 보고서에서 제외한 항목

- 실제 contact + friction 기반 미끄러짐 검증
- 다중 카메라 또는 depth sensor를 이용한 절대 3D 정합
- 인체 실측 장비와의 ground-truth torque 비교
- 물리량의 생리학적 임계치 의료적 검증

---

## 3. 테스트 자산과 실험 환경

### 3.1 입력 자산

- 동영상
  - `mujoco/advance/video/주황.mp4`
  - `mujoco/pysical_verify/video/주황.mp4`
  - `mujoco/pysical_verify/video/준영주황.mp4`
- 정적 이미지
  - `mujoco/pysical_verify/video/static.png`
  - `mujoco/pysical_verify/video/fullbody_dg.png`
- 샘플 입력 JSON
  - `mujoco/verify/sample_pose_world.json`
  - `mujoco/pysical_verify/sample_pose_world.json`
  - `mujoco/static_load_extract/sample_pose_world.json`

### 3.2 모델 자산

- 기본 humanoid
  - `mujoco/verify/humanoid.xml`
  - `mujoco/pysical_verify/humanoid.xml`
- 어깨 3자유도 실험 모델
  - `mujoco/pysical_verify/humanoid_shoulder3.xml`
  - `mujoco/static_load_extract/humanoid_shoulder3.xml`

### 3.3 캘리브레이션 자산

- T-pose 기반 개인화 길이 추정 이미지
  - `mujoco/pysical_verify/video/fullbody_dg.png`
- 출력 캘리브레이션
  - `mujoco/pysical_verify/calibration.json`
  - `mujoco/static_load_extract/calibration.json`

### 3.4 로그/아티팩트

- baseline/advance 비교 로그
  - `mujoco/verify/artifacts/compare_verify_dynamic.jsonl`
  - `mujoco/advance/artifacts/compare_advance_dynamic.jsonl`
  - `mujoco/advance/artifacts/compare_advance_dynamic_fixed_default.jsonl`
  - `mujoco/verify/artifacts/compare_verify_kinematic.jsonl`
  - `mujoco/advance/artifacts/compare_advance_kinematic.jsonl`
  - `mujoco/advance/artifacts/compare_advance_kinematic_fixed_default.jsonl`
- advanced_v2 스모크 로그
  - `mujoco/advance/artifacts/advanced_v2_baseline30.jsonl`
  - `mujoco/advance/artifacts/advanced_v2_hybrid30.jsonl`
- 정적 물리 분석 결과
  - `mujoco/static_load_extract/static_posture_analysis.json`

---

## 4. 핵심 검증 질문

본 프로젝트에서 실제로 검증하려 한 기술 질문은 다음과 같았다.

1. MediaPipe 기반 자세를 MuJoCo humanoid에 충분히 비슷하게 재현할 수 있는가  
2. 단순 시각화가 아니라 inverse dynamics 기반 물리량을 계산할 수 있는가  
3. 손/발이 홀드에 붙어 있다는 제약을 주었을 때 어떤 관절/부위가 버티고 있는지 정량화할 수 있는가  
4. 현재 결과가 서비스에 바로 들어갈 수준인지, 아니면 연구/고도화가 더 필요한지

---

## 5. 구현 및 검증 과정 요약

### 5.1 baseline verify 경로

초기 안정형 구현은 `verify` 경로에서 해석식 기반 각도 추출과 MuJoCo joint mapping을 유지하는 방식으로 구성되었다.  
이 경로의 장점은 구조가 단순하고 baseline regression을 추적하기 쉽다는 점이었다.

확인된 특성:

- 해석식 기반이라 재현성은 높음
- 어깨/고관절의 복합 회전 표현력은 제한적
- baseline 유지와 비교 기준점 역할에는 적합

### 5.2 advance 경로

`advance/mediapipe_mujoco_verify.py`에서는 수치 IK, wall snap, root 추종, 추가 CLI 옵션을 도입하여 동적 포즈 정합을 개선하려 했다.  
이 경로는 실험 자유도는 높았으나, calibration 없이 바로 복합 IK를 적용할 경우 오히려 부자연스러운 해를 선택하는 문제가 반복적으로 나타났다.

핵심 실험 포인트:

- wall snap
- full-body numerical IK
- dynamic root tracking
- One Euro filter / damping / swap 가정

### 5.3 advanced_v2 경로

`advance/mediapipe_advanced_v2.py`는 baseline 우선 + 실험 분리 전략으로 재설계되었다.

핵심 아이디어:

- baseline mapping을 먼저 유지
- calibration JSON을 별도 계층으로 분리
- refinement는 off / hybrid로 분리
- 손/발만 소규모 IK로 미세 보정

이 구조는 “고급 모델이 baseline을 깨뜨리는 문제”를 줄이는 데 목적이 있었다.

### 5.4 pysical_verify 및 static_load_extract 경로

이후에는 맵핑 정확도 검증을 넘어, “현재 자세가 실제로 어느 부위에 얼마나 부담을 주는가”를 계산하는 쪽으로 범위를 확장했다.

핵심 구현:

- 손/발 target용 mocap body 생성
- `weld` equality constraint로 손/발 고정
- `mj_inverse`를 통한 정적 generalized force 계산
- 관절 부하, 부위별 부하, CoM, anchor 반력 추출

이때 중요한 기술적 판단은 다음과 같았다.

- 실제 마찰(contact + friction) 대신, 먼저 weld 기반 정적 고정 문제를 푼다
- 물리량을 absolute truth로 보기보다, “상대 부하 분포”와 “설명 가능한 trend”를 먼저 검증한다

---

## 6. 정량 검증 결과

### 6.1 동적 자세 정합 성능

동일 영상 기준으로 로그의 `mean_pos_error_cm` 평균을 비교한 결과는 아래와 같다.

| 실험 로그 | 프레임 수 | 평균 위치 오차(cm) | 최소(cm) | 최대(cm) |
| --- | ---: | ---: | ---: | ---: |
| `verify/artifacts/compare_verify_dynamic.jsonl` | 60 | 67.8889 | 38.7083 | 97.7752 |
| `advance/artifacts/compare_advance_dynamic.jsonl` | 60 | 66.9139 | 30.3233 | 132.1340 |
| `advance/artifacts/compare_advance_dynamic_fixed_default.jsonl` | 60 | 33.5497 | 28.1545 | 59.6980 |

해석:

- advance 실험 초기값은 baseline 대비 개선이 거의 없었다
- 그러나 보정 후 `33.55cm`까지 내려가며 평균 오차가 약 49.9% 감소했다
- 즉 동적 정합 자체는 MuJoCo 실시간 경로에서 충분히 의미 있는 개선 가능성이 확인되었다

### 6.2 운동학적(kinematic) 정합 성능

| 실험 로그 | 프레임 수 | 평균 위치 오차(cm) | 최소(cm) | 최대(cm) |
| --- | ---: | ---: | ---: | ---: |
| `verify/artifacts/compare_verify_kinematic.jsonl` | 60 | 58.3745 | 52.4672 | 63.7802 |
| `advance/artifacts/compare_advance_kinematic.jsonl` | 60 | 37.9751 | 32.7294 | 54.8181 |
| `advance/artifacts/compare_advance_kinematic_fixed_default.jsonl` | 60 | 34.8402 | 28.3723 | 56.1947 |

해석:

- kinematic 기준에서도 advance 쪽이 verify baseline보다 유의미하게 개선되었다
- 즉 root/IK/보정 전략은 적절히 튜닝되면 실제로 포즈 유사도를 끌어올릴 수 있음을 확인했다

### 6.3 advanced_v2 스모크 검증

30프레임 스모크 테스트 기준:

| 실험 로그 | 프레임 수 | 평균 위치 오차(cm) | 최소(cm) | 최대(cm) |
| --- | ---: | ---: | ---: | ---: |
| `advanced_v2_baseline30.jsonl` | 30 | 59.4091 | 55.5855 | 63.0066 |
| `advanced_v2_hybrid30.jsonl` | 30 | 51.8030 | 46.6025 | 57.2190 |

해석:

- hybrid refinement는 baseline only 대비 평균 오차를 약 12.8% 줄였다
- 즉 `baseline 우선 + 제한적 refinement` 전략은 전체 시스템 안정성을 해치지 않으면서도 개선 가능성이 있다

---

## 7. 정적 자세 물리 분석 검증 결과

정적 분석은 `mujoco/static_load_extract/static_posture_analysis.json` 결과를 기준으로 해석했다.

### 7.1 end-effector / 관절 위치 정합

post-IK 기준 주요 타깃 오차:

- 왼손목: `12.32 cm`
- 오른손목: `10.85 cm`
- 왼발목: `3.72 cm`
- 오른발목: `11.23 cm`
- 왼팔꿈치: `5.57 cm`
- 오른팔꿈치: `14.31 cm`
- 왼무릎: `10.54 cm`
- 오른무릎: `3.70 cm`

전체 평균은 약 `9.03 cm`였다.

해석:

- 손/발/중간관절이 모두 타깃에 수 cm 단위로 접근하는 상태까지는 도달했다
- 그러나 완전 고정 상태라고 보기에는 여전히 오차가 남아 있어, 이후 물리량에는 constraint 보정 성분이 일부 포함된다

### 7.2 관절 부하 및 부위별 부하

정적 분석 결과 부위별 평균 부하는 다음과 같았다.

- 왼팔: `79.05%`
- 오른팔: `58.72%`
- 왼다리: `46.98%`
- 오른다리: `29.77%`
- 코어: `6.24%`

상위 관절 부하:

1. `shoulder1_right`: `100%`
2. `elbow_left`: `100%`
3. `shoulder1_left`: `100%`
4. `ankle_x_left`: `84.44%`
5. `ankle_y_left`: `75.97%`

해석:

- 현재 예시 자세는 팔, 특히 왼팔에 매우 큰 부하가 집중되는 자세로 분석되었다
- 왼다리 역시 오른다리보다 더 많이 버티고 있는 것으로 집계되었다
- 코어 부하는 상대적으로 낮게 나타났다

이는 클라이밍 자세의 직관과 크게 모순되지 않으며, 적어도 “어느 부위가 더 힘든가”를 상대 비교하는 용도에는 설득력이 있다.

### 7.3 CoM 결과

정적 분석 결과:

- `com_position = [0.0232, -0.0365, 0.9054]`
- `support_center_position = [0.1478, -0.0561, 0.9048]`
- `com_stability_margin_m = 0.0196`

해석:

- 현재 구현에서는 손/발 4개 anchor의 중심과 CoM 사이 거리를 안정도 지표로 사용했다
- 예시 자세에서는 CoM이 접점 중심으로부터 약 `2 cm` 정도 떨어져 있는 것으로 계산되었다
- 따라서 현재 지표는 “CoM이 접점 중심에서 얼마나 벗어났는가”를 설명하는 용도로는 유효하다

다만 이것은 아직 support polygon 기반 full stability metric이 아니라, 정적 anchor center 기준의 단순화된 지표이다.

### 7.4 반발력(손/발 anchor reaction force)

정적 분석 결과 손/발 반력 크기:

- 왼손: `1467 N`
- 오른손: `1707 N`
- 왼발: `1112 N`
- 오른발: `2847 N`

해석:

- 수치 자체는 계산되었지만, 현재는 `weld` 기반 anchor 반력이다
- 즉 “실제 홀드 마찰 반력”이 아니라 “손/발을 그 위치에 고정했을 때 생기는 constraint 반력”이다
- 또한 reach error가 `3.72 cm ~ 12.32 cm` 남아 있으므로, 자연스러운 자세 유지 힘과 constraint 강제력이 혼합되어 있다

결론적으로 이 값은 절대값 해석보다, 좌우/상하지 간 상대 비교용으로 보는 것이 타당하다.

---

## 8. 실패한 시도와 부정적 결과도 포함한 기술 판단

이 문서에서 중요한 것은 “잘 된 것”만이 아니라, “해봤지만 현재 방식으로는 설득력이 낮았던 것”도 분명히 적는 것이다.

### 8.1 개인화 humanoid segment 실시간 스케일링

T-pose 이미지에서 상완/하완/허벅지/종아리 길이를 추정해 humanoid XML 자체를 segment 단위로 스케일링하는 실험을 진행했다.

관찰 결과:

- 모델 body length, geom, joint pos를 동시에 건드리면 전체 자세 안정성이 크게 떨어졌다
- 손/발 target은 개선되지 않거나 오히려 악화되었다
- 관절축과 해부학 축 차이 때문에, 모델을 늘이거나 줄이는 방식은 불안정했다

기술 판단:

- 현재 프로젝트에서는 “personalized model scaling”보다 “personalized pose correction”이 더 타당하다
- 따라서 개인화 정보는 모델 길이를 바꾸는 데 쓰기보다, elbow/knee depth prior로 제한적으로 쓰는 것이 맞다

### 8.2 thigh orientation 직접 타깃팅

고관절 외회전을 더 잘 살리기 위해 thigh quaternion target을 직접 IK에 넣는 실험을 했다.

관찰 결과:

- 일부 경우 다리가 몸통 뒤로 비정상적으로 뻗는 pathological pose가 발생했다
- 이는 MediaPipe에서 만든 허벅지 프레임과 MuJoCo thigh body의 로컬 기준축이 정확히 일치하지 않기 때문으로 보인다

기술 판단:

- 고관절 문제를 body quaternion으로 바로 푸는 접근은 현재 시점에서 위험하다
- 허벅지 orientation 직접 타깃팅은 기본 경로에서 비활성화하는 것이 타당하다

### 8.3 마찰 기반 contact 분석

현재는 실제 마찰(contact + friction) 기반 분석이 아니라 `weld` 기반 고정 분석이다.

관찰 결과:

- sample dynamic analysis에서 `contact_efficiency = 0.0`, `stability_score = 0.0`가 발생한 사례가 있었다
- 이는 hold assignment, contact 모델링, reach mismatch가 아직 성숙하지 않다는 의미다

기술 판단:

- 현재 단계에서 friction loss를 주장하는 것은 과도하다
- 먼저 weld 기반 정적/준정적 부하 분석을 안정화한 뒤, 그 다음 friction feasibility를 따로 검증하는 것이 맞다

---

## 9. 실시간 활용성 평가

### 9.1 현재 가능한 수준

현재 구현은 다음 파이프라인을 실제로 수행할 수 있다.

- MediaPipe pose 추출
- MuJoCo humanoid 맵핑
- 실시간/준실시간 viewer 루프
- 정적/프레임 단위 inverse dynamics
- 관절/부위별 부하, CoM, anchor 반력 계산

즉 “실시간으로 돌리는 코드 경로” 자체는 존재하고, 데모/개발자용 진단 도구 수준으로는 활용 가능하다.

### 9.2 아직 부족한 부분

그러나 현재 저장소에는 wall-clock latency, FPS, per-frame compute budget을 정량 측정한 벤치마크 로그가 없다.  
따라서 다음과 같은 강한 주장은 아직 하면 안 된다.

- production-grade real-time coaching 가능
- 저지연 상호작용이 충분히 검증됨
- 모든 프레임에서 안정적인 contact-aware physics가 보장됨

### 9.3 현 시점의 현실적 결론

- 연구/데모/내부검증 목적의 “실시간 시각화 및 저주기 물리량 업데이트”는 가능
- 서비스에 바로 넣으려면 먼저 latency benchmark, fall-back 정책, reach-error gating이 추가되어야 함
- 추천 운영 전략은 “영상 전체 재분석 또는 저주기(예: 5~10Hz) 물리량 갱신”부터 시작하는 것

---

## 10. 자세 유사도 평가

### 10.1 긍정적 결론

- verify baseline은 비교 기준점으로서 안정적이었다
- advance 튜닝 이후 동적 평균 위치 오차를 `66.91 cm -> 33.55 cm`까지 줄였다
- advanced_v2 hybrid도 baseline only보다 개선 경향을 보였다
- 정적 경로에서는 평균 약 `9.03 cm` 수준까지 주요 end-effector/중간관절 타깃 오차를 줄였다

### 10.2 남은 문제

- 어깨와 고관절은 여전히 가장 어려운 자유도다
- 오른팔꿈치처럼 2D에서는 맞아 보이지만 3D world consistency가 떨어지는 경우가 있다
- 손/발/무릎/팔꿈치 target을 동시에 맞추는 과정에서 제한된 관절 자유도로 인해 비정상 해가 발생할 수 있다

### 10.3 실무적 판단

- 현재 자세 유사도는 “인상적인 시각적 데모와 상대 비교” 용도로는 충분히 의미가 있다
- 그러나 코칭 엔진의 절대 기준으로 쓰려면 관절축 calibration과 contact-aware IK가 더 필요하다

---

## 11. 물리량 신뢰도 평가

현재 추출 가능한 물리량의 신뢰도를 정성적으로 구분하면 다음과 같다.

### 11.1 관절 부하

신뢰도: **중간 이상**

근거:

- `mj_inverse + actuator torque limit` 기반이라 계산 구조는 명확하다
- 과부하 관절 순위와 좌우 편중은 비교적 일관되게 해석 가능하다
- 다만 end-effector reach error가 남아 있으면 constraint 보정력이 토크에 섞인다

실무적 해석:

- 절대값보다 `상대 순위`, `좌우 차이`, `반복 프레임 경향`이 더 믿을 만하다

### 11.2 신체 부위별 부하

신뢰도: **중간 이상**

근거:

- 관절 load를 부위별로 평균/최대 집계하므로 단일 관절 노이즈에 덜 민감하다
- “왼팔이 더 힘든가, 오른다리가 더 버티는가” 같은 해석에 적합하다

실무적 해석:

- 프론트 피드백과 사용자 코칭 메시지에는 단일 joint보다 body part aggregation이 더 적합하다

### 11.3 CoM

신뢰도: **중간**

근거:

- MuJoCo body mass 기반 CoM 계산은 안정적이다
- 그러나 현재 stability metric은 support polygon full geometry가 아니라 anchor center 기반 단순화다

실무적 해석:

- CoM 위치와 접점 중심 거리 자체는 유용
- “실제 균형 실패 예측”까지 곧바로 확장하기에는 아직 이르다

### 11.4 손/발 반발력

신뢰도: **낮음 ~ 중간**

근거:

- 현재는 friction force가 아니라 weld anchor reaction이다
- reach error와 constraint 강제력이 섞여 있다
- sample에서도 수 kN 수준의 큰 값이 나와 절대값 해석은 조심해야 한다

실무적 해석:

- 절대 물리량보다는 “어느 손/발에 더 많이 실리는가”를 보는 상대 지표로 사용
- 서비스에 노출할 경우 “추정 반력”임을 명시하거나 내부 지표로만 쓰는 것이 안전하다

---

## 12. 결론

### 12.1 실시간 활용 가능성

현재 구현은 실시간 경로를 이미 갖추고 있으며, MuJoCo viewer와 프레임 단위 물리량 계산도 가능하다.  
그러나 정식 latency benchmark가 없고 contact/friction 검증이 끝나지 않았기 때문에, 현 단계의 가장 적절한 평가는 다음과 같다.

- **데모/개발자 검증용 실시간 시스템으로는 충분히 활용 가능**
- **상용 실시간 코칭 엔진으로 바로 단정하기에는 아직 근거가 부족**

### 12.2 자세 유사도 결론

자세 재현은 baseline 대비 분명히 개선되었다.  
특히 동적 로그에서 평균 위치 오차를 약 절반 수준까지 낮춘 것은 중요한 성과다.

다만 어깨/고관절/중간관절 정합은 여전히 어려운 문제이며,  
현재 수준은 **“상대 비교와 시각적 설득력은 확보했지만, 모든 프레임에서 인체 해부학적으로 충분히 정밀하다고 보긴 어려운 단계”** 로 판단된다.

### 12.3 물리량 신뢰도 결론

현재 가장 설득력 있는 출력은 다음 순서다.

1. 관절 부하의 상대 순위
2. 신체 부위별 집계 부하
3. CoM와 접점 중심의 상대 위치
4. 손/발 anchor 반력의 상대 편중

반대로 아직 조심해야 할 출력은 다음과 같다.

- reaction force의 절대값
- friction loss 해석
- failure type 자동 분류의 절대적 주장

즉 현재 물리량은 **질적 설명력과 상대 비교에는 의미가 있지만, 절대 수치 그 자체를 그대로 서비스 진실값으로 사용하는 단계는 아니다.**

### 12.4 앞으로 고도화할 경우의 전망

다음 항목이 추가되면 결과의 설득력은 현저히 높아질 가능성이 있다.

1. 손/발 reach error를 더 줄이는 contact-aware IK
2. friction feasibility와 실제 contact force 검증
3. 사람별 segment length를 이용한 3D pose correction 고도화
4. 어깨/고관절 관절축 calibration 정교화
5. wall-clock latency / FPS benchmark
6. 실제 코칭 사례에 대한 반복적 정성 검증

예상되는 변화:

- 자세 유사도는 “데모 수준”에서 “분석 기준으로 써볼 만한 수준”으로 올라갈 수 있음
- 관절/부위별 부하는 현재보다 훨씬 안정적인 코칭 피드백 근거가 될 수 있음
- 반발력과 stability 지표는 friction/contact가 들어갈 때 비로소 강한 설득력을 갖게 됨

최종적으로 현재 프로젝트는 다음 단계에 위치한다고 판단한다.

**MuJoCo 기반 클라이밍 디지털 트윈의 기술 가능성은 충분히 확인되었고,  
자세 정합과 기본 물리량 추출은 이미 실험적으로 성립했다.  
다만 실제 서비스에서 “믿을 수 있는 물리 코칭”으로 승격되기 위해서는  
contact/friction, reach consistency, calibration robustness를 중심으로 한 한 단계 이상의 고도화가 필요하다.**

---

## 13. 부록: 본 보고서에서 직접 사용한 근거 파일

- `mujoco/verify/artifacts/compare_verify_dynamic.jsonl`
- `mujoco/advance/artifacts/compare_advance_dynamic.jsonl`
- `mujoco/advance/artifacts/compare_advance_dynamic_fixed_default.jsonl`
- `mujoco/verify/artifacts/compare_verify_kinematic.jsonl`
- `mujoco/advance/artifacts/compare_advance_kinematic.jsonl`
- `mujoco/advance/artifacts/compare_advance_kinematic_fixed_default.jsonl`
- `mujoco/advance/artifacts/advanced_v2_baseline30.jsonl`
- `mujoco/advance/artifacts/advanced_v2_hybrid30.jsonl`
- `mujoco/pysical_verify/analysis_output.json`
- `mujoco/static_load_extract/static_posture_analysis.json`

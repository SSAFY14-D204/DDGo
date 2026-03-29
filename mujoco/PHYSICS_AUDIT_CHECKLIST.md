# MuJoCo 물리량 Audit Checklist

이 문서는 영상 기반 MuJoCo 물리량을 검증할 때 사용하는 기준 문서입니다.

목적:
- 어떤 물리량을 먼저 믿어야 하는지 정리
- 무엇을 보고 pass/fail 할지 통일
- 검증 결과를 팀 내에서 같은 형식으로 기록

대상:
- grip/step 상태
- pose fitting
- CoM / support / stability
- joint/body load
- estimated contact force
- crux 후보

---

## 1. 기본 원칙

### 1-1. 이 문서에서의 pass 의미
- `정확한 실측값`이 아니라
- `영상 체감`, `동작 맥락`, `상대 비교` 기준으로 납득 가능한 수준이면 pass로 본다.

### 1-2. 이 문서에서의 fail 의미
- 사람이 보기에도 설명이 안 되거나
- 같은 상황에서 일관성이 없거나
- 서비스에 그대로 노출하면 오해를 부를 정도로 왜곡된 경우

### 1-3. 판정 단계
- `Pass`
  - 서비스 노출 가능
- `Borderline`
  - 내부 참고 가능, 추가 보정 필요
- `Fail`
  - 서비스 노출 비권장, 원인 분석 후 수정 필요

---

## 2. 물리량별 Audit 기준

| 항목 | 무엇을 본다 | Pass 기준 | Fail 신호 | 보정 방향 |
|---|---|---|---|---|
| `Grip/Step 상태` | 영상과 hold overlay 비교, active hold 일치 여부, engage/release 시점 | 주요 손/발이 실제 홀드와 대체로 일치, 전이 시점 깜빡임 적음 | 엉뚱한 홀드에 자주 붙음, 같은 홀드에서 빈번한 튐 | hold subset, polygon 후보 필터, hysteresis 조정 |
| `Hold dwell` | 홀드별 총 체류시간, 최장 연속 체류시간 | 휴식/크럭스 후보가 영상 체감과 크게 어긋나지 않음 | 발 지지 홀드만 과도하게 상위, 명백한 크럭스 홀드 누락 | dwell 집계 기준 분리, confidence 가중치 |
| `Pose fitting` | fit error, overlay, 관절 꺾임 | 평균 오차가 기준 유지, 시각적으로 큰 붕괴 없음 | 팔/다리 뒤틀림, freeze/recovery 급증 | correction, IK 반복, interpolation 조정 |
| `Recovery ratio` | recovery 비율, pose_mode 분포 | 특정 구간에만 제한적으로 발생 | 전반적으로 많이 퍼짐 | raw pose correction, visibility 규칙 조정 |
| `CoM 절대 위치` | com_position_m, 높이/방향 | 서기/버티기/매달림에서 상식적 | CoM가 지나치게 위/아래, 몸통 밖 | 질량 분포, inertial 재검토 |
| `Support 판정` | support_type, active_contact_limbs | 정지 구간에서 tri/quad support 위주 | point/line support 과다, limb가 영상과 다름 | grip/step 품질 개선, fallback 조정 |
| `Stability margin` | inside_support, stability_margin_m | high-confidence 정지 프레임에서 margin이 대체로 0 근처 이상 | 안정 자세도 큰 음수, 정지/전이 구분 불가 | support geometry, confidence filtering |
| `Joint load` | joint_loads, top_joint_loads | 좌우 대칭/유사 자세에서 일관성 있음 | 프레임마다 순위 급변, 대칭 깨짐 | IK 안정화, smoothing, joint grouping |
| `Body load` | body_loads.core/arms/legs | 체감과 부위별 부담 순위가 맞음 | 체감과 전혀 다른 부위가 계속 1위 | body grouping, low-confidence 제외 |
| `Estimated contact force` | estimated_contact_forces_n, residual | ok + high-confidence 프레임에서 분배가 상식적 | 손 힘만 과도, residual 큼 | contact model 제한, hold state 품질 개선 |
| `Load shifting` | 전이 직후 limb force / body load 변화 | 손 release/발 step 전후에 하중 재분배가 보임 | 전이 전후 값 변화 없음 | transition segmentation, force distribution 조정 |
| `Crux 후보` | dwell 기반 후보, physics 기반 후보 | top 3 중 최소 1~2개는 체감 크럭스와 일치 | 휴식 홀드만 상위, 크럭스 누락 | score 조정, rest discount, confidence 반영 |

---

## 3. 우선순위

다음 순서로 검증한다.

1. `Grip/Step 상태`
2. `Pose fitting / Recovery`
3. `CoM / Support / Stability`
4. `Body load / Joint load`
5. `Estimated contact force`
6. `Crux`

원칙:
- `Grip/Step`이 맞아야 `Support`를 믿을 수 있다
- `Support`가 맞아야 `CoM`와 `Load`를 믿을 수 있다
- 그 다음에야 `Contact force`와 `Crux`가 의미가 생긴다

---

## 4. 대표 시나리오 기준

| 시나리오 | 꼭 볼 것 | Pass 기준 |
|---|---|---|
| 안정 버티기 구간 | inside_support, body load, hold 상태 | 안정 margin, hold 상태 일치, load 순위 일관 |
| 손 release 구간 | active_contact 변경, load shift | 남은 손/발 부하 증가가 보임 |
| 발 step 전환 구간 | step hold 일치, support 변화 | 실제 디딘 홀드와 일치, support 변화 자연스러움 |
| 휴식 홀드 구간 | dwell 길이, load 낮음 | 오래 머물러도 부하/불안정이 낮음 |
| 크럭스 구간 | dwell + load + instability | 체감상 어려운 구간이 후보에 포함 |

---

## 5. 서비스 노출 기준

### 5-1. 서비스 노출 가능
- `analysis_confidence = high`
- `phase = static_support` 또는 `loaded_transition`
- `contact_force_status = ok` 또는 force 비노출

### 5-2. 내부 참고만
- `analysis_confidence = low`
- `contact_force_status = high_residual`
- `phase = recovery`

### 5-3. 서비스 비노출
- pose 붕괴
- active contact 불명확
- residual이 지속적으로 큼

---

## 6. 실제 기록용 Audit 템플릿

아래 템플릿을 복붙해서 영상/버전별로 기록한다.

```md
# 물리량 Audit 기록

## 기본 정보
- 날짜:
- 담당자:
- 대상 영상:
- 분석 버전/브랜치:
- 입력 조건:
  - hold json:
  - pose json:
  - user_body json:

## 대표 시나리오
- [ ] 안정 버티기 구간
- [ ] 손 release 구간
- [ ] 발 step 전환 구간
- [ ] 휴식 홀드 구간
- [ ] 크럭스 구간

## 1. Grip/Step 상태
- 판정: Pass / Borderline / Fail
- 본 구간:
- 관찰:
- 문제:
- 수정 아이디어:

## 2. Hold dwell
- 판정: Pass / Borderline / Fail
- top hold 후보:
- 관찰:
- 문제:
- 수정 아이디어:

## 3. Pose fitting
- 판정: Pass / Borderline / Fail
- fit mean error:
- recovery ratio:
- 관찰:
- 문제:
- 수정 아이디어:

## 4. CoM / Support / Stability
- 판정: Pass / Borderline / Fail
- inside_support_count:
- outside_support_count:
- support_type_counts:
- 관찰:
- 문제:
- 수정 아이디어:

## 5. Joint / Body Load
- 판정: Pass / Borderline / Fail
- 관찰:
- top joint loads:
- dominant body loads:
- 문제:
- 수정 아이디어:

## 6. Estimated Contact Force
- 판정: Pass / Borderline / Fail
- ok frame count:
- high_residual frame count:
- 관찰:
- 문제:
- 수정 아이디어:

## 7. Crux 후보
- 판정: Pass / Borderline / Fail
- fast top 3:
- physics top 3:
- 관찰:
- 문제:
- 수정 아이디어:

## 종합 판정
- 전체 판정: Pass / Borderline / Fail
- 지금 서비스에 바로 써도 되는 항목:
- 아직 내부용으로만 봐야 하는 항목:
- 다음 수정 우선순위:
  1.
  2.
  3.
```

---

## 7. 프레임/구간 단위 세부 체크 템플릿

특정 문제 구간을 자세히 볼 때는 아래를 쓴다.

```md
## 구간 세부 체크
- 구간 이름:
- 시작 프레임:
- 종료 프레임:
- 시각(ms):
- 시나리오 유형: 안정 버티기 / 손 release / 발 step / 휴식 / 크럭스

### 관찰값
- active holds:
- active contact limbs:
- phase:
- analysis confidence:
- support type:
- stability margin:
- core load:
- dominant load region:
- contact force status:

### 영상 체감
- 실제 동작 설명:
- 사용자가 힘들어 보이는 지점:
- 하중 이동이 보이는 지점:

### 수치 해석
- 수치가 체감과 맞는가:
- 맞지 않으면 어디가 이상한가:

### 판정
- Pass / Borderline / Fail

### 후속 조치
- 수정할 로직:
- 다시 볼 지표:
```

---

## 8. 빠른 결론 작성 템플릿

```md
## 결론
- 현재 가장 신뢰 가능한 물리량:
- 현재 가장 불안정한 물리량:
- 서비스에 바로 써도 되는 것:
- 보정 후 다시 검증이 필요한 것:
- 다음 실험 한 줄 요약:
```

---

## 9. 사용 방법 요약

- 한 번에 모든 값을 고치지 않는다
- 대표 구간 5개를 먼저 정한다
- audit sheet로 Pass / Borderline / Fail을 남긴다
- 가장 위 우선순위 항목부터 하나씩 수정한다
- 수정 후 같은 구간으로 재측정한다

이 문서는 앞으로 물리량 고도화 작업의 기준 문서로 사용한다.

# Contact Force Service Fields

이 문서는 **서비스에 실제로 노출할 반력 필드만 추린 요약 문서**입니다.

기준:
- 최신 검증 리포트: [json_service_benchmark_report_audit_final_10fps_corrected_v17_force_axis_smoothing_safe.json](C:/ssafy/project-2/S14P21D204/mujoco/json_service_benchmark/json_service_benchmark_report_audit_final_10fps_corrected_v17_force_axis_smoothing_safe.json)
- 상세 audit: [AUDIT_06_ESTIMATED_CONTACT_FORCE.md](C:/ssafy/project-2/S14P21D204/mujoco/AUDIT_06_ESTIMATED_CONTACT_FORCE.md)

## 결론
서비스에는 `estimated_contact_forces_raw_n(원시 반력)`를 그대로 내보내지 말고,
**`estimated_contact_forces_n(표시용 smoothing 반력)` + 최소한의 신뢰도 필드**만 노출하는 것이 맞습니다.

---

## 최상위 필드

### 반드시 노출
- `contact_force_status(반력 계산 상태)`
  - 값:
    - `ok`
    - `high_residual`
    - `no_active_contacts`
  - 의미:
    - 이 프레임의 반력 분배를 믿고 볼 수 있는지 알려주는 핵심 상태값

- `contact_force_relative_residual(반력 설명 오차 비율)`
  - 단위 없음
  - 의미:
    - 현재 손발 힘 분배로 이 프레임을 얼마나 잘 설명했는지
  - 권장 해석:
    - 낮을수록 좋음
    - 서비스에서는 상태 보조용으로만 사용

- `estimated_contact_forces_n(표시용 smoothing 반력)`
  - 사용자 화면 표시용
  - limb별 force 요약이 들어 있음

### 내부 전용, 서비스 비노출
- `estimated_contact_forces_raw_n(원시 반력)`
  - 디버깅 / audit 전용
- `contact_force_distribution(내부 분배 상세)`
  - solver 내부 구조 설명용
- `contact_force_confidence_scores(접점 신뢰도 점수)`
  - 내부 튜닝용

---

## limb별 권장 필드

각 limb(`left_hand`, `right_hand`, `left_foot`, `right_foot`)에서 서비스에 노출할 필드는 아래만 추천합니다.

### 공통 필드
- `mode(접촉 상태)`
  - `GRIP`, `STEP`, `MOVE`
- `force_norm_n(전체 힘 크기)`
  - 전체 지지력 크기
- `vertical_force_n(상하 방향 힘)`
  - 위/아래 방향 성분
- `smoothed_for_display(표시용 smoothing 적용 여부)`
  - UI 디버깅이나 내부 QA용

### 발 `STEP`에서 특히 유용한 필드
- `compressive_wall_normal_force_n(벽 법선 방향 압축 반력)`
  - 발이 실제로 벽/홀드를 얼마나 강하게 미는지 설명할 때 핵심
- `wall_tangential_force_n(벽 접선 방향 힘)`
  - 발이 마찰로 얼마나 버티는지 설명할 때 사용

### 손 `GRIP`에서 특히 유용한 필드
- `force_norm_n(전체 힘 크기)`
  - 손 지지력 전체를 보여주기엔 이 값이 가장 직관적
- `vertical_force_n(상하 방향 힘)`
  - 당기는 느낌을 보조적으로 설명할 때 사용 가능

---

## 서비스에서 추천하는 실제 사용 조합

### 사용자 화면 기본값
- `contact_force_status`
- 각 limb의 `force_norm_n`

### 발 디딤 설명 화면
- 각 발의 `compressive_wall_normal_force_n`
- 각 발의 `wall_tangential_force_n`

### 신뢰도 보조
- `contact_force_relative_residual`

---

## 서비스에서 직접 보여주지 말아야 할 필드

아래 필드는 내부 디버깅에는 유용하지만, 사용자 화면에는 바로 노출하지 않는 것이 맞습니다.

- `force_xyz(3축 힘 벡터)`
- `wall_normal_component_n(벽 법선 방향 성분 원값)`
- `lateral_force_n(좌우 방향 힘)`
- `axis_regularization_scale_xyz(축별 정규화 가중치)`
- `regularization_scale(정규화 가중치)`
- `confidence_score(접점 신뢰도 점수)`
- `mode_bias_scale(상태별 가중치)`

이유:
- 해석이 어렵고
- 튜닝 로직이 바뀌면 의미가 달라질 수 있으며
- 사용자에게 설명 가치가 낮습니다

---

## 추천 응답 예시

```json
{
  "contact_force_status": "ok",
  "contact_force_relative_residual": 0.023,
  "estimated_contact_forces_n": {
    "left_hand": {
      "mode": "GRIP",
      "force_norm_n": 324.9,
      "vertical_force_n": 319.2
    },
    "right_hand": {
      "mode": "GRIP",
      "force_norm_n": 321.0,
      "vertical_force_n": 313.6
    },
    "left_foot": {
      "mode": "STEP",
      "force_norm_n": 137.3,
      "compressive_wall_normal_force_n": 117.5,
      "wall_tangential_force_n": 71.0,
      "vertical_force_n": 46.4
    },
    "right_foot": {
      "mode": "STEP",
      "force_norm_n": 31.7,
      "compressive_wall_normal_force_n": 24.8,
      "wall_tangential_force_n": 19.8,
      "vertical_force_n": 19.8
    }
  }
}
```

---

## 한 줄 결론
서비스에는 **`contact_force_status + contact_force_relative_residual + estimated_contact_forces_n(표시용 smoothing 반력)`**만 노출하고,
각 limb에서는 **`force_norm_n`과 발의 `compressive_wall_normal_force_n` 중심**으로 보여주는 것이 가장 납득 가능하고 안정적입니다.

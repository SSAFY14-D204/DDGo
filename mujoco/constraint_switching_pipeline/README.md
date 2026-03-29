# Constraint Switching Pipeline

`GRIP / STEP / MOVE` 상태를 MuJoCo 내부 제약으로 바꾸는 1차 프로토타입 폴더입니다.

현재 구현 범위:
- 손 `GRIP`만 지원
- `left_hand`, `right_hand`에 대해 `mocap anchor + weld equality`를 사용
- 각 프레임에서
  - `baseline(무제약)`
  - `constrained(손 weld on/off)`
  를 같은 상태에서 비교
- 출력:
  - `qfrc_constraint`
  - `efc_force`
  - baseline 대비 joint/body load 변화

## 실행

```bash
cd /c/ssafy/project-2/S14P21D204/mujoco/constraint_switching_pipeline

python run_hand_grip_constraint_sequence.py \
  --input-video ../video/주황.mp4 \
  --detections-json ../detections.json
```

기본 동작:
- personalized XML을 바탕으로 `hand_grip_constraint_model.xml` 생성
- 필요하면 `dynamic_sequence_report_with_state.json`을 새로 생성
  - `qpos / qvel / qacc` 포함
- 프레임별 손 GRIP 상태를 MuJoCo weld 제약에 매핑
- `hand_grip_constraint_report.json` 저장

## 출력 해석

- `constraint_mode`
  - `none`
  - `left_only`
  - `right_only`
  - `both`
- `baseline`
  - 손 weld 없이 계산한 결과
- `constrained`
  - 손 weld를 켠 결과
- `delta`
  - 두 결과 차이
  - `root_inverse_force`
  - `body_loads`
  - `top_joint_deltas`

즉 이 리포트는
`손 GRIP 제약을 MuJoCo 안에서 실제로 켰을 때, 하중과 constraint force가 얼마나 달라지는가`
를 보는 용도입니다.

## 현재 한계

- 손 `GRIP`만 구현됨
- 발 `STEP`은 아직 제약으로 넣지 않음
- hold 3D 좌표가 없어서 anchor는 `GRIP engage 순간의 손 pose`를 고정하는 방식
- 즉 `진짜 홀드 body`를 쓰는 완성형이 아니라, `constraint-switching 구조 검증용 프로토타입`입니다

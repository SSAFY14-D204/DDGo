# Custom Skeleton Verify

표준 `humanoid.xml` 대신, MediaPipe 3D landmark를 사람형 skeleton rig에 직접 매핑하는 검증 폴더입니다.

핵심 아이디어:
- `MediaPipe 3D -> bone-length corrected pose -> MuJoCo custom skeleton rig`
- head, thorax, pelvis, shoulders, elbows, hands, hips, knees, ankles, feet를 MuJoCo 안에서 거의 1:1로 시각화

## 파일
- `mediapipe_custom_skeleton_verify.py`
  - 영상에서 MediaPipe pose를 읽고 custom skeleton rig를 실시간 갱신
- `custom_skeleton_rig.xml`
  - joint marker + segment capsule mocap rig
- `pose_landmarker_lite.task`
  - MediaPipe task model
- `calibration.json`
  - T-pose 기반 신체 길이 보정값

## 실행
```bash
cd /c/ssafy/project-2/S14P21D204/mujoco/custom_skeleton_verify

python mediapipe_custom_skeleton_verify.py \
  --input-video C:/ssafy/project-2/S14P21D204/mujoco/video/주황.mp4
```

## 목적
- 영상 3D 좌표와 거의 같은 skeleton pose를 MuJoCo 안에서 확인
- 이후 skeleton-first CoM / load proxy / hold contact 분석의 기준 경로로 사용

## 현재 한계
- 아직 inverse dynamics humanoid 분석은 연결하지 않음
- head/thorax는 landmark 조합 기반 proxy
- 지금 단계의 목표는 “정확한 pose 재현”이다

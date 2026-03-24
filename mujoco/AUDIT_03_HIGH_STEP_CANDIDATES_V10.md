# AUDIT 03: High-Step 후보 프레임 진단

## 목적
- `high-step(발을 높이 들어 디뎌야 하는 프레임)`에서 왜 `knee flexion(무릎 굽힘)` 대신 `hip/ankle compensation(고관절/발목 보상)`이 선택되는지 직접 확인한다.
- 입력 target(목표 자세)와 fitted pose(맞춘 자세)를 분리해서 본다.

## 기준
- 후보 프레임 조건: `STEP` + `high_step_score >= 0.7` + `target_knee_flex - fitted_knee_flex >= 30.0deg`

## 요약
- 전체 후보 프레임 수: `2`
- 후보 segment 수: `2`
- 가장 큰 gap 프레임: `frame 148` / `right`
- 최악 gap: `target 81.5deg` vs `fit 50.3deg` (gap `31.2deg`)

## 대표 segment
- `right` / `frame 148~148` / 대표 `frame 148` / gap `31.2deg`
  - target knee `81.5deg`, fit knee `50.3deg`, high_step `0.841`, hold `2`
  - hip_y `-65.7deg`, hip_x `-55.0deg`, hip_z `5.9deg`, ankle_x `34.9deg`, ankle_y `79.9deg`
  - 이유: hip_x(고관절 벌림/모음)가 상한 근처라 다리 벌림 보상이 큼, ankle_x(발목 기울기)가 상한 근처라 발목 옆기울기 보상이 큼, ankle_y(발목 앞뒤 회전)가 상한 근처라 발목 회전 보상이 큼, 무릎 정면과 발 정면이 크게 어긋남
  - 보기: `C:/Users/SSAFY/miniforge3/envs/mujoco_env/python.exe visualize_corrected_benchmark_live.py --start-frame 146 --max-frames 18 --pause-at-start`
- `right` / `frame 107~107` / 대표 `frame 107` / gap `30.6deg`
  - target knee `102.2deg`, fit knee `71.7deg`, high_step `1.000`, hold `2`
  - hip_y `-59.6deg`, hip_x `-55.0deg`, hip_z `0.0deg`, ankle_x `15.7deg`, ankle_y `80.0deg`
  - 이유: hip_x(고관절 벌림/모음)가 상한 근처라 다리 벌림 보상이 큼, ankle_y(발목 앞뒤 회전)가 상한 근처라 발목 회전 보상이 큼, 무릎 정면과 발 정면이 크게 어긋남
  - 보기: `C:/Users/SSAFY/miniforge3/envs/mujoco_env/python.exe visualize_corrected_benchmark_live.py --start-frame 105 --max-frames 18 --pause-at-start`

## 해석
- 이 문서에서 `target knee flex(목표 무릎 굽힘)`는 입력 MediaPipe 3D와 사용자 체형 보정으로 만든 목표 자세가 요구하는 무릎 굽힘이다.
- `fit knee flex(실제 맞춰진 무릎 굽힘)`가 이 값보다 훨씬 작다면, 입력이 틀린 게 아니라 현재 제약/가중치 때문에 solver가 다른 해를 고른 것이다.
- `hip_x/hip_y/ankle_x/ankle_y`가 상한이나 하한 근처에 붙어 있으면, 무릎 대신 그 관절들로 보상하고 있다는 뜻이다.


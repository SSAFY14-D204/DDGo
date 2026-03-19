# Dynamic Sequence Pipeline

영상 전체 프레임을 대상으로 다음을 수행한다.

1. articulated full-sequence fitting
2. `qpos -> qvel -> qacc` 계산
3. hold/grip 상태 기반 active support 계산
4. frame-by-frame dynamic inverse dynamics
5. `GRIP / STEP / MOVE` 상태별 contact force distribution 추정
6. 서비스용 요약 지표 출력

기본 실행:

```bash
cd /c/ssafy/project-2/S14P21D204/mujoco/dynamic_sequence_pipeline

python run_dynamic_sequence_analysis.py \
  --input-video ../video/주황.mp4 \
  --detections-json ../detections.json
```

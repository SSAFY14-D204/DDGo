# JSON Service Benchmark

이 폴더는 실제 서비스 경로를 흉내 냅니다.

- 입력:
  - `holds.json`
  - `pose3d_sequence.json`
  - `user_body.json`
- 출력:
  - `json_service_benchmark_report.json`

핵심 원칙은 두 가지입니다.

1. `주황.mp4`와 `fullbody_dg.png`는 **오프라인 준비 단계**에서만 사용합니다.
2. 실제 시간 측정은 **3개 JSON -> 1개 물리 결과 JSON** 경로만 봅니다.

## 1. 준비용 JSON 생성

```bash
cd /c/ssafy/project-2/S14P21D204/mujoco/json_service_benchmark

python prepare_benchmark_inputs.py \
  --input-video ../video/주황.mp4 \
  --detections-json ../detections.json \
  --tpose-image ../video/fullbody_dg.png \
  --height-m 1.75 \
  --weight-kg 80
```

생성 파일:

- `benchmark_inputs/holds.json`
- `benchmark_inputs/pose3d_sequence.json`
- `benchmark_inputs/user_body.json`
- `benchmark_inputs/benchmark_input_manifest.json`

## 2. JSON-only 물리 벤치마크 실행

```bash
cd /c/ssafy/project-2/S14P21D204/mujoco/json_service_benchmark

python run_json_service_benchmark.py \
  --holds-json ./benchmark_inputs/holds.json \
  --pose-json ./benchmark_inputs/pose3d_sequence.json \
  --user-body-json ./benchmark_inputs/user_body.json
```

출력 파일:

- `json_service_benchmark_report.json`

## 3. 시간 지표

리포트에는 다음 시간이 들어갑니다.

- `load_inputs_s`
- `prepare_model_s`
- `fit_sequence_s`
- `inverse_dynamics_s`
- `serialize_s`
- `total_s`

## 4. 주의

- `estimated_contact_forces_n`은 현재 단계에선 **서비스용 추정 반력(proxy)** 입니다.
- 절대 실측 반력으로 보면 안 되고, 상태별 부하 변화와 상대 비교에 쓰는 값입니다.
- warm start를 보려면 같은 입력으로 한 번 더 실행하면 됩니다.
  - personalized XML cache가 재사용됩니다.

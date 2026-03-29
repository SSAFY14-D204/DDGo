# Foot Contact Only

Gate 4 검증용 분석기.

목표:

- 두 발 `STEP` 지지 프레임에서
- 손 grip을 무시하고
- 발 2점만으로 필요한 wrench를 얼마나 설명할 수 있는지 본다.

기본 실행:

```bash
cd /c/ssafy/project-2/S14P21D204/mujoco/foot_contact_only

python evaluate_foot_contact_only.py \
  --dynamic-report ../dynamic_sequence_pipeline/dynamic_sequence_report.json
```

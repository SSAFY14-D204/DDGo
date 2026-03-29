# Crux Detection Summary

## Fast Crux

- Goal: return crux candidates quickly after pose correction and grip/step tracking only.
- Input: `holds.json` + `pose3d_sequence.json` + `user_body.json`
- Pipeline:
  - raw pose correction
  - polygon grip/step tracking
  - hold dwell aggregation
  - top-3 crux hold ranking
- Score:
  - `fast_crux_score = 0.7 * longest_continuous_dwell_norm + 0.3 * total_active_time_norm`
- Why:
  - longest continuous dwell is the strongest signal for "blocked" or "stuck" moments
  - total dwell keeps repeated revisits from being ignored
- Latest benchmark:
  - total: `3.61s`
  - correction: `0.84s`
  - hold tracking: `2.53s`
  - scoring: `0.01s`
- Latest top-3:
  1. hold `15` / score `0.992` / best segment `10.33s` / dominant `left_foot STEP`
  2. hold `27` / score `0.891` / best segment `9.00s` / dominant `right_foot STEP`
  3. hold `9` / score `0.625` / best segment `4.80s` / dominant `right_foot STEP`
- Use when:
  - frontend needs immediate crux candidates
  - low latency is more important than explanation quality

## Physics Crux

- Goal: return more explainable crux candidates after full MuJoCo physics analysis.
- Input: `holds.json` + `pose3d_sequence.json` + `user_body.json`
- Pipeline:
  - raw pose correction
  - full corrected in-memory MuJoCo benchmark
  - hold segment extraction
  - crux score based on dwell/load/stability/load shift
- Score:
  - `segment_score = quality_weight * (0.35*dwell + 0.35*load + 0.15*instability + 0.15*load_shift) - rest_penalty`
  - `hold_score = 0.85 * best_segment_score + 0.15 * total_dwell_norm`
- Why:
  - dwell keeps the route-level "stuck" signal
  - load adds biomechanical difficulty
  - instability adds support difficulty
  - load shift adds transition difficulty
  - rest penalty reduces long but easy rest holds
- Latest benchmark:
  - total: `15.88s`
  - correction: `0.81s`
  - physics pipeline: `14.83s`
  - scoring: `0.03s`
- Physics quality summary:
  - fit mean error: `8.77cm`
  - recovery ratio: `5.60%`
  - high-confidence frames: `596`
  - ok contact-force frames: `679`
- Latest top-3:
  1. hold `9` / score `0.637` / best segment `4.80s` / tags `long_dwell, load_shift`
  2. hold `32` / score `0.580` / best segment `4.53s` / tags `long_dwell, load_shift`
  3. hold `26` / score `0.524` / best segment `3.10s` / tags `long_dwell, load_shift`
- Use when:
  - backend can wait for full physics analysis
  - explanation quality matters more than immediate latency

## Recommendation

- Service first response: use **Fast Crux**
- Detailed analysis / explanation tab: use **Physics Crux**
- The two outputs should be shown together, not treated as strict replacements for each other

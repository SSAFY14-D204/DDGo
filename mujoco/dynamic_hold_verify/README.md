# Dynamic Hold Verify

`mujoco/dynamic_hold_verify` is the working folder for live video mapping verification and the upcoming hold grip/step logic.

Current scope:
- Step 1: verify live video-to-humanoid mapping
- Step 2: implement hold-based `Reach -> Grip / Step -> Release`
- Step 3: verify live mapping again with the grip/step logic enabled

## Files

- `mediapipe_mujoco_verify.py`
  - live video mapping viewer
- `physics_worker.py`
  - pose target extraction, calibration-based depth correction, 2-link target correction
- `humanoid_shoulder3_175.xml`
  - default live humanoid XML, scaled to 175 cm
- `humanoid_shoulder3.xml`
  - 3-DoF shoulder baseline humanoid XML
- `humanoid.xml`
  - simpler baseline humanoid XML
- `calibration.json`
  - T-pose biometrics calibration
- `pose_landmarker_lite.task`
  - MediaPipe Pose Landmarker model
- `visualize_biometrics_calibration.py`
  - creates a T-pose skeleton overlay for calibration inspection
- `inspect_model_metrics.py`
  - prints runtime model height and total mass
- `scale_humanoid_xml.py`
  - regenerates a uniformly scaled humanoid XML

## Shared Media

Shared image/video assets live in:

`mujoco/video`

Examples:
- `../video/fullbody_dg.png`
- `../video/static.png`
- `../video/주황.mp4`

## Recommended Workflow

1. Generate or refresh T-pose calibration
2. Inspect the calibration overlay
3. Check live model height and mass
4. Run kinematic live mapping verification
5. Then move on to hold grip/step logic

## Commands

### 1. Generate T-pose calibration

```bash
cd /c/ssafy/project-2/S14P21D204/mujoco/pysical_verify

python calibrate_biometrics.py \
  --image ../video/fullbody_dg.png \
  --height-m 1.75 \
  --output calibration.json
```

### 2. Inspect T-pose calibration overlay

```bash
cd /c/ssafy/project-2/S14P21D204/mujoco/dynamic_hold_verify

python visualize_biometrics_calibration.py
```

Generated files:
- `fullbody_dg_biometrics_overlay.ppm`
- `fullbody_dg_biometrics_overlay.json`

### 3. Check current live model height and mass

```bash
cd /c/ssafy/project-2/S14P21D204/mujoco/dynamic_hold_verify

python inspect_model_metrics.py
```

Current default:
- height: 175 cm baseline XML
- total mass: 80 kg

### 4. Regenerate the 175 cm XML

```bash
cd /c/ssafy/project-2/S14P21D204/mujoco/dynamic_hold_verify

python scale_humanoid_xml.py --target-height-m 1.75
```

### 5. Verify live mapping in kinematic mode

```bash
cd /c/ssafy/project-2/S14P21D204/mujoco/dynamic_hold_verify

python mediapipe_mujoco_verify.py \
  --input-video C:/ssafy/project-2/S14P21D204/mujoco/video/주황.mp4 \
  --mode kinematic
```

If left/right looks flipped:

```bash
python mediapipe_mujoco_verify.py \
  --input-video ../video/주황.mp4 \
  --mode kinematic \
  --no-swap-lr
```

To compare limbs while freezing the root:

```bash
python mediapipe_mujoco_verify.py \
  --input-video ../video/주황.mp4 \
  --mode kinematic \
  --mirror-view \
  --freeze-root \
  --no-swap-lr
```

To view dynamic mode:

```bash
python mediapipe_mujoco_verify.py \
  --input-video ../video/주황.mp4 \
  --mode dynamic \
  --mirror-view \
  --no-swap-lr
```

### 6. Self-check

```bash
cd /c/ssafy/project-2/S14P21D204/mujoco/dynamic_hold_verify

python mediapipe_mujoco_verify.py --self-check
```

### 7. Syntax check

```bash
cd /c/ssafy/project-2/S14P21D204/mujoco/dynamic_hold_verify

python -m py_compile \
  mediapipe_mujoco_verify.py \
  physics_worker.py \
  inspect_model_metrics.py \
  scale_humanoid_xml.py \
  visualize_biometrics_calibration.py
```

## Notes

- The live path is still less stable than the static single-image path.
- `freeze-root` is only for debugging limb mapping; it is not the intended final runtime mode.
- Current target generation uses calibration-based depth correction and 2-link reachable target correction before MuJoCo pose fitting.
- `detections.json`-based hold association and the grip/step state machine are the next step.

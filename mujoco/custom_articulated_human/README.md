# Custom Articulated Human

Current stage: `Gate 2`

Goal:
- use the corrected target skeleton from `custom_skeleton_verify`
- build a custom articulated MuJoCo human model instead of forcing the standard humanoid
- evaluate static inverse dynamics on fitted articulated poses

Included now:
- pelvis freejoint
- trunk 3-DoF
- neck 3-DoF
- shoulder shrug + 3-DoF rotation
- elbow 1-DoF
- hip 3-DoF
- knee 1-DoF
- ankle 2-DoF
- hand / foot rigid bodies
- actuator ranges and default torque gears
- target comparison sites for pelvis, thorax, shoulders, elbows, hands, hips, knees, ankles, feet
- user-personalized XML generator from calibration + target skeleton samples
- static fitting evaluation script
- static inverse dynamics evaluation script
- single-frame static fitting visualization script
- full-video articulated fitting playback script

Not included yet:
- hold contact / grip
- dynamic sequence analysis

Commands:
```bash
cd /c/ssafy/project-2/S14P21D204/mujoco/custom_articulated_human

python personalize_articulated_model.py --input-video ../video/주황.mp4
python inspect_articulated_model.py
python evaluate_static_fit.py --input-video ../video/주황.mp4
python evaluate_static_inverse_dynamics.py --input-video ../video/주황.mp4
python evaluate_hold_contact_states.py --input-video ../video/주황.mp4 --detections-json ../detections.json
python visualize_static_fit.py --input-video ../video/주황.mp4 --frame-index 413
python play_articulated_fit_video.py --input-video ../video/주황.mp4 --show-overlay
```

Gate 1 focus:
- XML loads correctly in MuJoCo
- body / joint / actuator structure is valid
- personalized XML matches user limb lengths and body widths
- articulated model can statically fit sampled target skeleton frames with acceptable error

Gate 2 focus:
- use the fitted static articulated pose as a physically meaningful configuration
- run `mj_inverse` on sampled static frames
- inspect CoM, support center, and joint generalized inverse forces
- add 2D hold contact / grip state tracking for hands and feet

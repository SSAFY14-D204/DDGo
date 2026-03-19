from __future__ import annotations

import argparse
import json
from pathlib import Path

import cv2
import mediapipe as mp
import mujoco
import numpy as np

from evaluate_static_fit import fit_static_pose
from evaluate_static_fit import AUX_SITE_TARGETS, POLE_TARGETS, SITE_TARGETS
from mediapipe_custom_skeleton_verify import MetricSkeletonMapper, make_landmarker
from physics_worker import load_calibration_json

ROOT = Path(__file__).resolve().parent
CUSTOM_SKELETON_ROOT = ROOT.parent / "custom_skeleton_verify"
DEFAULT_PERSONALIZED_XML = ROOT / "custom_articulated_human_personalized.xml"
DEFAULT_XML = DEFAULT_PERSONALIZED_XML if DEFAULT_PERSONALIZED_XML.exists() else ROOT / "custom_articulated_human.xml"


def normalize(v: np.ndarray, eps: float = 1e-8) -> np.ndarray:
    arr = np.asarray(v, dtype=np.float64)
    norm = float(np.linalg.norm(arr))
    if norm < eps:
        return np.zeros_like(arr)
    return arr / norm


def sample_frame_indices(frame_count: int, sample_count: int) -> list[int]:
    if frame_count <= 0:
        return []
    sample_count = max(1, min(frame_count, sample_count))
    return sorted({int(round(v)) for v in np.linspace(0, frame_count - 1, sample_count)})


def bend_alignment(
    hip: np.ndarray,
    knee: np.ndarray,
    ankle: np.ndarray,
    foot_forward: np.ndarray,
) -> dict[str, float]:
    thigh_dir = normalize(knee - hip)
    shank_dir = normalize(ankle - knee)
    extension_dir = -thigh_dir
    bend_vec = shank_dir - float(np.dot(shank_dir, extension_dir)) * extension_dir
    bend_dir = normalize(bend_vec)

    foot_proj = foot_forward - float(np.dot(foot_forward, extension_dir)) * extension_dir
    foot_dir = normalize(foot_proj)
    dot = float(np.dot(bend_dir, foot_dir))
    return {
        "bend_alignment_dot": dot,
        "bend_norm": float(np.linalg.norm(bend_vec)),
        "foot_norm": float(np.linalg.norm(foot_proj)),
    }


def joint_qpos(data: mujoco.MjData, model: mujoco.MjModel, name: str) -> float:
    jnt_id = mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_JOINT, name)
    return float(data.qpos[int(model.jnt_qposadr[jnt_id])])


def main() -> None:
    parser = argparse.ArgumentParser(description="Diagnose lower-limb forward consistency from fitted articulated poses.")
    parser.add_argument("--xml", type=Path, default=DEFAULT_XML)
    parser.add_argument("--input-video", type=Path, default=ROOT.parent / "video" / "주황.mp4")
    parser.add_argument("--task-model", type=Path, default=CUSTOM_SKELETON_ROOT / "pose_landmarker_lite.task")
    parser.add_argument("--calibration-json", type=Path, default=CUSTOM_SKELETON_ROOT / "calibration.json")
    parser.add_argument("--sample-count", type=int, default=8)
    parser.add_argument("--ik-iters", type=int, default=45)
    parser.add_argument("--ik-damping", type=float, default=1e-3)
    parser.add_argument("--output", type=Path, default=ROOT / "lower_limb_orientation_report.json")
    args = parser.parse_args()

    calibration = load_calibration_json(args.calibration_json)
    model = mujoco.MjModel.from_xml_path(str(args.xml.resolve()))
    data = mujoco.MjData(model)
    required_sites = tuple(SITE_TARGETS.keys()) + tuple(POLE_TARGETS.keys()) + tuple(AUX_SITE_TARGETS.keys())
    site_ids = {
        site_name: mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_SITE, site_name)
        for site_name in required_sites
    }

    cap = cv2.VideoCapture(str(args.input_video))
    if not cap.isOpened():
        raise FileNotFoundError(f"Unable to open video: {args.input_video}")
    fps = float(cap.get(cv2.CAP_PROP_FPS) or 30.0)
    frame_count = int(cap.get(cv2.CAP_PROP_FRAME_COUNT) or 0)
    frame_indices = sample_frame_indices(frame_count, args.sample_count)

    landmarker = make_landmarker(args.task_model)
    mapper = MetricSkeletonMapper(calibration)

    prev_qpos = None
    samples: list[dict[str, object]] = []
    for frame_idx in frame_indices:
        cap.set(cv2.CAP_PROP_POS_FRAMES, int(frame_idx))
        ok, frame_bgr = cap.read()
        if not ok:
            continue
        timestamp_ms = int(round((frame_idx / max(fps, 1.0)) * 1000.0))
        frame_rgb = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2RGB)
        mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=frame_rgb)
        result = landmarker.detect_for_video(mp_image, timestamp_ms)
        if not result.pose_world_landmarks:
            continue

        target_points = mapper.map_frame(result.pose_world_landmarks[0])
        fit = fit_static_pose(
            model=model,
            data=data,
            site_ids=site_ids,
            target_points=target_points,
            seed_qpos=prev_qpos,
            iterations=args.ik_iters,
            damping=args.ik_damping,
        )
        prev_qpos = np.asarray(fit["qpos"], dtype=np.float64).copy()

        left_heel = data.site_xpos[site_ids["left_heel_site"]].copy()
        left_foot = data.site_xpos[site_ids["left_foot_site"]].copy()
        right_heel = data.site_xpos[site_ids["right_heel_site"]].copy()
        right_foot = data.site_xpos[site_ids["right_foot_site"]].copy()

        left_knee = data.site_xpos[site_ids["left_knee_site"]].copy()
        right_knee = data.site_xpos[site_ids["right_knee_site"]].copy()
        left_hip = data.site_xpos[site_ids["left_hip_site"]].copy()
        right_hip = data.site_xpos[site_ids["right_hip_site"]].copy()
        left_ankle = data.site_xpos[site_ids["left_ankle_site"]].copy()
        right_ankle = data.site_xpos[site_ids["right_ankle_site"]].copy()

        left_foot_forward = normalize(left_foot - left_heel)
        right_foot_forward = normalize(right_foot - right_heel)
        pelvis_id = mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_BODY, "pelvis")
        pelvis_forward = data.xmat[pelvis_id].reshape(3, 3)[:, 0].copy()
        pelvis_forward = normalize(pelvis_forward)

        left_align = bend_alignment(left_hip, left_knee, left_ankle, left_foot_forward)
        right_align = bend_alignment(right_hip, right_knee, right_ankle, right_foot_forward)

        samples.append(
            {
                "frame_index": frame_idx,
                "timestamp_ms": timestamp_ms,
                "fit_mean_error_m": float(fit["mean_error_m"]),
                "left": {
                    **left_align,
                    "foot_vs_pelvis_forward_dot": float(np.dot(left_foot_forward, pelvis_forward)),
                    "foot_forward": left_foot_forward.tolist(),
                    "hip_x": joint_qpos(data, model, "hip_x_left"),
                    "hip_z": joint_qpos(data, model, "hip_z_left"),
                    "hip_y": joint_qpos(data, model, "hip_y_left"),
                    "knee": joint_qpos(data, model, "knee_left"),
                    "ankle_y": joint_qpos(data, model, "ankle_y_left"),
                    "ankle_x": joint_qpos(data, model, "ankle_x_left"),
                },
                "right": {
                    **right_align,
                    "foot_vs_pelvis_forward_dot": float(np.dot(right_foot_forward, pelvis_forward)),
                    "foot_forward": right_foot_forward.tolist(),
                    "hip_x": joint_qpos(data, model, "hip_x_right"),
                    "hip_z": joint_qpos(data, model, "hip_z_right"),
                    "hip_y": joint_qpos(data, model, "hip_y_right"),
                    "knee": joint_qpos(data, model, "knee_right"),
                    "ankle_y": joint_qpos(data, model, "ankle_y_right"),
                    "ankle_x": joint_qpos(data, model, "ankle_x_right"),
                },
            }
        )

    cap.release()
    landmarker.close()

    def summarize(side: str) -> dict[str, float]:
        dots = [float(sample[side]["bend_alignment_dot"]) for sample in samples]  # type: ignore[index]
        foot_dots = [float(sample[side]["foot_vs_pelvis_forward_dot"]) for sample in samples]  # type: ignore[index]
        return {
            "mean_bend_alignment_dot": float(np.mean(dots)) if dots else 0.0,
            "min_bend_alignment_dot": float(np.min(dots)) if dots else 0.0,
            "negative_alignment_frames": int(sum(1 for dot in dots if dot < 0.0)),
            "mean_foot_vs_pelvis_forward_dot": float(np.mean(foot_dots)) if foot_dots else 0.0,
            "min_foot_vs_pelvis_forward_dot": float(np.min(foot_dots)) if foot_dots else 0.0,
            "backward_foot_frames": int(sum(1 for dot in foot_dots if dot < 0.0)),
        }

    report = {
        "xml": str(args.xml.resolve()),
        "video": str(args.input_video.resolve()),
        "sample_frame_indices": frame_indices,
        "left_summary": summarize("left"),
        "right_summary": summarize("right"),
        "samples": samples,
    }
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
